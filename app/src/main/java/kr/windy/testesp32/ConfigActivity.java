package kr.windy.testesp32;

import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

public class ConfigActivity extends AppCompatActivity {

    private static final String TAG = "ConfigActivity";
    public static final String EXTRA_SSID_LIST = "ssid_list";

    private EditText etSsid, etPassword, etApiIp, etApiPort, etApiPath, etDeviceCode, etProductCode;
    private CheckBox cbShowPassword;
    private TextView tvConfigStatus;
    private Button btnSave;
    private ArrayList<String> ssidList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        etSsid        = findViewById(R.id.etSsid);
        etPassword    = findViewById(R.id.etPassword);
        etApiIp       = findViewById(R.id.etApiIp);
        etApiPort     = findViewById(R.id.etApiPort);
        etApiPath     = findViewById(R.id.etApiPath);
        etDeviceCode  = findViewById(R.id.etDeviceCode);
        etProductCode = findViewById(R.id.etProductCode);
        cbShowPassword = findViewById(R.id.cbShowPassword);
        tvConfigStatus = findViewById(R.id.tvConfigStatus);
        btnSave       = findViewById(R.id.btnSave);

        // 주변 WiFi 목록 수신
        ArrayList<String> received = getIntent().getStringArrayListExtra(EXTRA_SSID_LIST);
        if (received != null) ssidList = received;

        // SSID 목록 선택
        findViewById(R.id.btnSelectSsid).setOnClickListener(v -> showSsidPicker());

        // 비밀번호 표시 토글
        cbShowPassword.setOnCheckedChangeListener((btn, checked) -> {
            int type = checked
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
            etPassword.setInputType(type);
            etPassword.setSelection(etPassword.getText().length());
        });

        btnSave.setOnClickListener(v -> saveConfig());
    }

    private void showSsidPicker() {
        if (ssidList.isEmpty()) {
            Toast.makeText(this, "스캔된 WiFi 목록이 없습니다", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = ssidList.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("WiFi 선택")
                .setItems(items, (dialog, which) -> etSsid.setText(ssidList.get(which)))
                .setNegativeButton("취소", null)
                .show();
    }

    private void saveConfig() {
        String ssid        = etSsid.getText().toString().trim();
        String pass        = etPassword.getText().toString();
        String apiIp       = etApiIp.getText().toString().trim();
        String apiPort     = etApiPort.getText().toString().trim();
        String apiPath     = etApiPath.getText().toString().trim();
        String deviceCode  = etDeviceCode.getText().toString().trim();
        String productCode = etProductCode.getText().toString().trim();

        if (ssid.isEmpty()) {
            Toast.makeText(this, "SSID를 입력하세요", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSave.setEnabled(false);
        tvConfigStatus.setText("저장 중...");

        new Thread(() -> {
            try {
                URL url = new URL("http://192.168.4.1/save");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                String body = "ssid="         + URLEncoder.encode(ssid,        "UTF-8")
                        + "&pass="        + URLEncoder.encode(pass,        "UTF-8")
                        + "&api_ip="      + URLEncoder.encode(apiIp,       "UTF-8")
                        + "&api_port="    + URLEncoder.encode(apiPort,     "UTF-8")
                        + "&api_path="    + URLEncoder.encode(apiPath,     "UTF-8")
                        + "&device_code=" + URLEncoder.encode(deviceCode,  "UTF-8")
                        + "&product_code="+ URLEncoder.encode(productCode, "UTF-8");

                conn.getOutputStream().write(body.getBytes("UTF-8"));

                int code = conn.getResponseCode();
                Log.d(TAG, "응답 코드: " + code);
                conn.disconnect();

                runOnUiThread(() -> {
                    if (code == 200) {
                        tvConfigStatus.setText("저장 완료! ESP32가 재부팅됩니다.");
                    } else {
                        tvConfigStatus.setText("저장 실패 (HTTP " + code + ")");
                        btnSave.setEnabled(true);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "저장 실패", e);
                runOnUiThread(() -> {
                    tvConfigStatus.setText("오류: " + e.getMessage());
                    btnSave.setEnabled(true);
                });
            }
        }).start();
    }
}
