package kr.windy.testesp32;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String TAG = "ESP32";

    private WifiManager wifiManager;
    private ConnectivityManager cm;
    private ListView listWifi;
    private TextView tvStatus;
    private List<ScanResult> scanResults = new ArrayList<>();
    private BroadcastReceiver wifiScanReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        listWifi = findViewById(R.id.listWifi);
        tvStatus = findViewById(R.id.tvStatus);
        Button btnScan = findViewById(R.id.btnScan);

        btnScan.setOnClickListener(v -> checkPermissionAndScan());

        listWifi.setOnItemClickListener((parent, view, position, id) -> {
            if (position < scanResults.size()) {
                showPasswordDialog(scanResults.get(position));
            }
        });

        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                List<ScanResult> results = wifiManager.getScanResults();
                if (results.isEmpty()) {
                    tvStatus.setText(success ? "주변 WiFi를 찾을 수 없습니다" : "스캔 실패 — 위치 서비스가 켜져 있는지 확인하세요");
                } else {
                    updateWifiList(results);
                }
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(wifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(wifiScanReceiver);
    }

    private void checkPermissionAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSION_REQUEST_CODE);
        } else {
            startWifiScan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startWifiScan();
        } else {
            tvStatus.setText("위치 권한이 필요합니다 (WiFi 스캔 요구사항)");
        }
    }

    @SuppressWarnings("deprecation")
    private void startWifiScan() {
        if (!wifiManager.isWifiEnabled()) {
            tvStatus.setText("WiFi가 꺼져 있습니다. WiFi를 켜주세요.");
            return;
        }

        // 캐시된 결과 먼저 즉시 표시
        List<ScanResult> cached = wifiManager.getScanResults();
        if (!cached.isEmpty()) {
            updateWifiList(cached);
        } else {
            tvStatus.setText("스캔 중...");
        }

        boolean started = wifiManager.startScan();
        if (!started) {
            // 쓰로틀링 등으로 새 스캔 불가 시 캐시 결과만 표시
            if (cached.isEmpty()) {
                tvStatus.setText("스캔 실패 — 위치 서비스(GPS)가 켜져 있는지 확인하세요");
            } else {
                tvStatus.setText("WiFi " + scanResults.size() + "개 발견 (캐시된 결과) — 전송할 네트워크를 선택하세요");
            }
        }
    }

    private void updateWifiList(List<ScanResult> results) {
        // SSID 중복 제거 (신호 강한 것 우선)
        List<ScanResult> deduped = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (ScanResult r : results) {
            if (r.SSID == null || r.SSID.isEmpty()) continue;
            if (!seen.contains(r.SSID)) {
                seen.add(r.SSID);
                deduped.add(r);
            }
        }
        scanResults = deduped;

        List<String> items = new ArrayList<>();
        for (ScanResult r : deduped) {
            String security = r.capabilities.contains("WPA") ? "🔒" : "🔓";
            items.add(security + " " + r.SSID + "  (" + r.level + " dBm)");
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, items);
        listWifi.setAdapter(adapter);
        tvStatus.setText("WiFi " + deduped.size() + "개 발견 — 전송할 네트워크를 선택하세요");
    }

    private void showPasswordDialog(ScanResult network) {
        boolean isOpen = !network.capabilities.contains("WPA") && !network.capabilities.contains("WEP");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(network.SSID);

        EditText input = new EditText(this);
        input.setHint(isOpen ? "비밀번호 없음 (오픈 네트워크)" : "WiFi 비밀번호 입력");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);
        if (isOpen) input.setEnabled(false);
        builder.setView(input);

        builder.setPositiveButton("ESP32에 전송", (dialog, which) -> {
            String password = isOpen ? "" : input.getText().toString();
            connectAndSendToEsp32(network.SSID, password);
        });
        builder.setNegativeButton("취소", null);
        builder.show();
    }

    private void connectAndSendToEsp32(String targetSsid, String targetPass) {
        tvStatus.setText("ESP32 AP에 연결 중...");

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
                runOnUiThread(() -> tvStatus.setText("ESP32 연결됨 — WiFi 정보 전송 중..."));

                new Thread(() -> {
                    try {
                        URL url = new URL("http://192.168.4.1/save");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setDoOutput(true);
                        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                        String body = "ssid=" + URLEncoder.encode(targetSsid, "UTF-8")
                                + "&pass=" + URLEncoder.encode(targetPass, "UTF-8");
                        conn.getOutputStream().write(body.getBytes());

                        int code = conn.getResponseCode();
                        Log.d(TAG, "응답: " + code);
                        conn.disconnect();

                        runOnUiThread(() -> tvStatus.setText("전송 완료! (HTTP " + code + ") ESP32가 재부팅됩니다."));
                    } catch (Exception e) {
                        Log.e(TAG, "전송 실패", e);
                        runOnUiThread(() -> tvStatus.setText("전송 실패: " + e.getMessage()));
                    }
                }).start();
            }

            @Override
            public void onUnavailable() {
                runOnUiThread(() -> tvStatus.setText("ESP32 AP 연결 실패 — SSID/비밀번호를 확인하세요"));
            }
        });
    }
}
