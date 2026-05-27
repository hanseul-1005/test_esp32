package kr.windy.testesp32;

import android.net.Network;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WebView는 별도 프로세스에서 동작하므로 bindProcessToNetwork()가 적용되지 않는다.
 * 로컬 포트에 HTTP 프록시를 열어 WebView → localhost → ESP32 순으로 중계한다.
 */
class EspProxyServer {

    private static final String TAG = "EspProxy";
    private static final String ESP_HOST = "192.168.4.1";

    private final Network network;
    private ServerSocket serverSocket;
    private volatile boolean running;
    private int port;

    EspProxyServer(Network network) {
        this.network = network;
    }

    int start() throws IOException {
        serverSocket = new ServerSocket(0); // OS가 빈 포트를 할당
        port = serverSocket.getLocalPort();
        running = true;
        Thread t = new Thread(this::acceptLoop);
        t.setDaemon(true);
        t.start();
        return port;
    }

    void stop() {
        running = false;
        try { serverSocket.close(); } catch (Exception ignored) {}
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                Thread t = new Thread(() -> handle(socket));
                t.setDaemon(true);
                t.start();
            } catch (Exception e) {
                if (running) Log.e(TAG, "accept 실패", e);
            }
        }
    }

    private void handle(Socket socket) {
        try {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // 요청 첫 줄 파싱
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) return;
            String[] parts = requestLine.split(" ", 3);
            if (parts.length < 2) return;
            String method = parts[0];
            String path = parts[1];

            // 헤더 파싱
            Map<String, String> reqHeaders = new LinkedHashMap<>();
            int contentLength = 0;
            String hLine;
            while (!(hLine = readLine(in)).isEmpty()) {
                int colon = hLine.indexOf(':');
                if (colon > 0) {
                    String name = hLine.substring(0, colon).trim();
                    String value = hLine.substring(colon + 1).trim();
                    reqHeaders.put(name, value);
                    if (name.equalsIgnoreCase("Content-Length")) {
                        try { contentLength = Integer.parseInt(value); } catch (Exception ignored) {}
                    }
                }
            }

            // 요청 바디 읽기 (POST 등)
            byte[] body = null;
            if (contentLength > 0) {
                body = new byte[contentLength];
                int offset = 0;
                while (offset < contentLength) {
                    int r = in.read(body, offset, contentLength - offset);
                    if (r < 0) break;
                    offset += r;
                }
            }

            // ESP32로 포워딩
            URL url = new URL("http", ESP_HOST, 80, path);
            HttpURLConnection conn = (HttpURLConnection) network.openConnection(url);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod(method);
            conn.setInstanceFollowRedirects(false); // 리다이렉트는 WebView가 처리
            conn.setDoInput(true);

            for (Map.Entry<String, String> h : reqHeaders.entrySet()) {
                String name = h.getKey();
                if (name.equalsIgnoreCase("Host") || name.equalsIgnoreCase("Connection")) continue;
                conn.setRequestProperty(name, h.getValue());
            }

            if (body != null && body.length > 0) {
                conn.setDoOutput(true);
                conn.getOutputStream().write(body);
            }

            // 응답 읽기
            int code;
            try {
                code = conn.getResponseCode();
            } catch (Exception e) {
                sendError(out, 502, "ESP32 연결 실패: " + e.getMessage());
                return;
            }

            InputStream respStream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();

            // 응답 헤더 작성 (Location 헤더는 localhost로 재작성)
            StringBuilder respHeaders = new StringBuilder();
            respHeaders.append("HTTP/1.1 ").append(code).append(" OK\r\n");
            for (Map.Entry<String, List<String>> h : conn.getHeaderFields().entrySet()) {
                if (h.getKey() == null) continue;
                String name = h.getKey();
                if (name.equalsIgnoreCase("Transfer-Encoding")) continue;
                for (String v : h.getValue()) {
                    String value = v;
                    if (name.equalsIgnoreCase("Location")) {
                        value = value.replace("http://" + ESP_HOST, "http://127.0.0.1:" + port);
                    }
                    respHeaders.append(name).append(": ").append(value).append("\r\n");
                }
            }
            respHeaders.append("\r\n");
            out.write(respHeaders.toString().getBytes("UTF-8"));

            if (respStream != null) {
                byte[] buf = new byte[4096];
                int r;
                while ((r = respStream.read(buf)) != -1) {
                    out.write(buf, 0, r);
                }
            }
            out.flush();
            conn.disconnect();

        } catch (Exception e) {
            Log.e(TAG, "handle 실패", e);
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private void sendError(OutputStream out, int code, String message) throws IOException {
        byte[] body = message.getBytes("UTF-8");
        String resp = "HTTP/1.1 " + code + " Error\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\n\r\n";
        out.write(resp.getBytes("UTF-8"));
        out.write(body);
        out.flush();
    }

    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\r') { in.read(); break; }
            buf.write(b);
        }
        return buf.toString("UTF-8");
    }
}
