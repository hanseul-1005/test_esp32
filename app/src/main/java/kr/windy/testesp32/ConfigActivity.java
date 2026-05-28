package kr.windy.testesp32;

import android.content.Context;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

public class ConfigActivity extends AppCompatActivity {

    private static final String TAG = "ConfigActivity";
    public static final String EXTRA_SSID_LIST = "ssid_list";
    public static final String EXTRA_NETWORK   = "esp32_network";

    private EditText etSsid, etPassword, etApiServer, etApiPort, etApiPath, etDeviceCode, etProductCode;
    private CheckBox cbShowPassword;
    private TextView tvConfigStatus;
    private Button btnSave;
    private ArrayList<String> ssidList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        NestedScrollView scrollView = findViewById(R.id.configRoot);

        ViewCompat.setOnApplyWindowInsetsListener(scrollView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomInset = Math.max(systemBars.bottom, ime.bottom);
            v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), bottomInset);
            return insets;
        });

        etSsid        = findViewById(R.id.etSsid);
        etPassword    = findViewById(R.id.etPassword);
        etApiServer   = findViewById(R.id.etApiIp);
        etApiPort     = findViewById(R.id.etApiPort);
        etApiPath     = findViewById(R.id.etApiPath);
        etDeviceCode  = findViewById(R.id.etDeviceCode);
        etProductCode = findViewById(R.id.etProductCode);
        cbShowPassword = findViewById(R.id.cbShowPassword);
        tvConfigStatus = findViewById(R.id.tvConfigStatus);
        btnSave       = findViewById(R.id.btnSave);

        // 포커스 시 EditText 위치를 직접 계산해 키보드 바로 위로 스크롤
        View.OnFocusChangeListener scrollToFocused = (view, hasFocus) -> {
            if (!hasFocus) return;
            scrollView.postDelayed(() -> {
                Rect rect = new Rect();
                view.getDrawingRect(rect);
                scrollView.offsetDescendantRectToMyCoords(view, rect);
                int visibleHeight = scrollView.getHeight() - scrollView.getPaddingBottom();
                int targetScroll = rect.bottom - visibleHeight + dpToPx(8);
                if (targetScroll > scrollView.getScrollY()) {
                    scrollView.smoothScrollTo(0, targetScroll);
                }
            }, 350);
        };
        etSsid.setOnFocusChangeListener(scrollToFocused);
        etPassword.setOnFocusChangeListener(scrollToFocused);
        etApiServer.setOnFocusChangeListener(scrollToFocused);
        etApiPort.setOnFocusChangeListener(scrollToFocused);
        etApiPath.setOnFocusChangeListener(scrollToFocused);
        etDeviceCode.setOnFocusChangeListener(scrollToFocused);
        etProductCode.setOnFocusChangeListener(scrollToFocused);

        ArrayList<String> received = getIntent().getStringArrayListExtra(EXTRA_SSID_LIST);
        if (received != null) ssidList = received;

        findViewById(R.id.btnSelectSsid).setOnClickListener(v -> showSsidPicker());

        cbShowPassword.setOnCheckedChangeListener((btn, checked) -> {
            int type = checked
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
            etPassword.setInputType(type);
            etPassword.setSelection(etPassword.getText().length());
        });

        btnSave.setOnClickListener(v -> saveConfig());
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
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
        String ssid    = etSsid.getText().toString().trim();
        String pass    = etPassword.getText().toString();
        String server  = etApiServer.getText().toString().trim();
        String port    = etApiPort.getText().toString().trim();
        String path    = etApiPath.getText().toString().trim();
        String code    = etDeviceCode.getText().toString().trim();
        String product = etProductCode.getText().toString().trim();

        if (ssid.isEmpty()) {
            Toast.makeText(this, "SSID를 입력하세요", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSave.setEnabled(false);
        tvConfigStatus.setText("ESP32에 저장 중...");

        new Thread(() -> {
            try {
                URL url = new URL("http://192.168.4.1/wifisave");

                // bindProcessToNetwork()로 바인딩된 네트워크를 실시간으로 가져와 사용
                // (전달받은 Network 객체는 시간이 지나면 만료될 수 있음)
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                Network boundNetwork = cm.getBoundNetworkForProcess();
                HttpURLConnection conn = boundNetwork != null
                        ? (HttpURLConnection) boundNetwork.openConnection(url)
                        : (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

                // 문서 기준 필드명: s, p, server, port, path, code, product
                String body = "s="       + URLEncoder.encode(ssid,    "UTF-8")
                        + "&p="       + URLEncoder.encode(pass,    "UTF-8")
                        + "&server="  + URLEncoder.encode(server,  "UTF-8")
                        + "&port="    + URLEncoder.encode(port,    "UTF-8")
                        + "&path="    + URLEncoder.encode(path,    "UTF-8")
                        + "&code="    + URLEncoder.encode(code,    "UTF-8")
                        + "&product=" + URLEncoder.encode(product, "UTF-8");

                OutputStream os = conn.getOutputStream();
                try {
                    os.write(body.getBytes("UTF-8"));
                } finally {
                    os.close();
                }

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "응답 코드: " + responseCode);
                conn.disconnect();

                runOnUiThread(() -> {
                    if (responseCode == 200) {
                        tvConfigStatus.setText("저장 완료! ESP32가 재부팅됩니다.");
                    } else {
                        tvConfigStatus.setText("저장 실패 (HTTP " + responseCode + ")");
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
