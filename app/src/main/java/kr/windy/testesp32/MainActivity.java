package kr.windy.testesp32;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    public MainActivity() {
        super();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        // 1. ESP32 와이파이 자동 연결
        WifiNetworkSpecifier specifier = new WifiNetworkSpecifier.Builder()
                .setSsid("ESP32-WIFI-SETUP")
                .setWpa2Passphrase("12345678")
                .build();

        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build();

        cm.requestNetwork(request, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                cm.bindProcessToNetwork(network);

                // 2. 192.168.4.1/save 로 와이파이 정보 전송
                new Thread(() -> {
                    try {
                        URL url = new URL("http://192.168.4.1/save");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setDoOutput(true);
                        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                        // ⚠️ 파라미터명 ssid, pass 로 고정!
                        String body = "ssid=실제와이파이이름&pass=실제비밀번호";
                        conn.getOutputStream().write(body.getBytes());

                        int code = conn.getResponseCode(); // 200 오면 성공, 이후 ESP32 재부팅됨
                        Log.d("ESP32", "응답: " + code);
                        conn.disconnect();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        });
    }
}