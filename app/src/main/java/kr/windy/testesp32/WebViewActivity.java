package kr.windy.testesp32;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

public class WebViewActivity extends AppCompatActivity {

    private EspProxyServer proxyServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);

        WebView webView = findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient());

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network boundNetwork = cm.getBoundNetworkForProcess();

        if (boundNetwork != null) {
            proxyServer = new EspProxyServer(boundNetwork);
            try {
                int port = proxyServer.start();
                webView.loadUrl("http://127.0.0.1:" + port);
            } catch (IOException e) {
                // 프록시 실패 시 직접 접속 시도
                webView.loadUrl("http://192.168.4.1");
            }
        } else {
            webView.loadUrl("http://192.168.4.1");
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (proxyServer != null) proxyServer.stop();
    }
}
