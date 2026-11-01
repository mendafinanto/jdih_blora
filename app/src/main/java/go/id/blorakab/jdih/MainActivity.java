package go.id.blorakab.jdih;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Gravity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String HOME = "https://jdih.blorakab.go.id/mobile";
    private WebView webView;
    private FrameLayout root;
    private View loadingView;
    private View offlineView;
    private boolean tryingCachedHome = false;
    private String lastPageUrl = HOME;
    private SharedPreferences preferences;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        preferences = getSharedPreferences("jdih_app", MODE_PRIVATE);\n        lastPageUrl = preferences.getString("last_page_url", HOME);\n        buildScreen();

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);\n        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return route(r.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String u) {
                return route(Uri.parse(u));
            }
            private boolean route(Uri u) {
                String scheme = u.getScheme() == null ? "" : u.getScheme();
                String host = u.getHost() == null ? "" : u.getHost().toLowerCase();
                if ("https".equals(scheme) && (host.equals("jdih.blorakab.go.id") || host.endsWith(".blorakab.go.id"))) return false;
                if ("http".equals(scheme) || "https".equals(scheme) || "mailto".equals(scheme) || "tel".equals(scheme)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); }
                    catch (Exception e) { Toast.makeText(MainActivity.this, "Tautan tidak dapat dibuka", Toast.LENGTH_SHORT).show(); }
                }
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                showLoading();
            }
            @Override public void onPageFinished(WebView view, String url) {
                hideLoading();
                hideOffline();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) handleMainFrameError();
            }
            @SuppressWarnings("deprecation")
            @Override public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                if (android.os.Build.VERSION.SDK_INT < 23) handleMainFrameError();
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.setDownloadListener((url, ua, disposition, mime, length) -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (Exception e) { Toast.makeText(this, "Tidak dapat membuka file unduhan", Toast.LENGTH_LONG).show(); }
        });

        if (savedInstanceState != null) {
            if (webView.restoreState(savedInstanceState) == null) loadHome();
        } else {
            loadHome();
        }
    }

    private void buildScreen() {
        root = new FrameLayout(this);
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        loadingView = createLoadingView();
        offlineView = createOfflineView();
        root.addView(loadingView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(offlineView, new FrameLayout.LayoutParams(-1, -1));
        offlineView.setVisibility(View.GONE);
        setContentView(root);
    }

    private View createLoadingView() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackgroundColor(Color.WHITE);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(148), dp(148));
        lp.bottomMargin = dp(22);
        panel.addView(logo, lp);
        ProgressBar progress = new ProgressBar(this);
        panel.addView(progress, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView label = new TextView(this);
        label.setText("Memuat JDIH Kabupaten Blora...");
        label.setTextColor(Color.rgb(75, 75, 75));
        label.setTextSize(14);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(-2, -2);
        textLp.topMargin = dp(14);
        panel.addView(label, textLp);
        return panel;
    }

    private View createOfflineView() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(28), dp(24), dp(28), dp(24));
        panel.setBackgroundColor(Color.WHITE);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(140), dp(140));
        logoLp.bottomMargin = dp(24);
        panel.addView(logo, logoLp);
        TextView title = new TextView(this);
        title.setText("Koneksi internet terputus");
        title.setTextColor(Color.rgb(45, 45, 45));
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);
        TextView message = new TextView(this);
        message.setText("Halaman JDIH Kabupaten Blora belum dapat dibuka. Periksa koneksi internet Anda, lalu coba lagi.");
        message.setTextColor(Color.rgb(100, 100, 100));
        message.setTextSize(14);
        message.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(-1, -2);
        msgLp.topMargin = dp(12);
        panel.addView(message, msgLp);
        Button retry = new Button(this);
        retry.setText("Coba Lagi");
        retry.setOnClickListener(v -> {
            if (hasConnection()) loadHome();
            else Toast.makeText(this, "Periksa koneksi internet Anda", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-2, -2);
        btnLp.topMargin = dp(22);
        panel.addView(retry, btnLp);
        return panel;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private boolean hasConnection() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private void loadHome() {
        hideOffline();
        showLoading();
        webView.loadUrl(HOME);
    }

    private void showLoading() { loadingView.setVisibility(View.VISIBLE); }
    private void hideLoading() { loadingView.setVisibility(View.GONE); }
    private void showOffline() {
        hideLoading();
        offlineView.setVisibility(View.VISIBLE);
    }
    private void hideOffline() { offlineView.setVisibility(View.GONE); }

    @Override protected void onSaveInstanceState(Bundle out) {
        if (webView != null) webView.saveState(out);
        super.onSaveInstanceState(out);
    }

    @Override public void onBackPressed() {
        if (offlineView != null && offlineView.getVisibility() == View.VISIBLE) {
            if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override protected void onDestroy() {
        if (webView != null) { webView.stopLoading(); webView.destroy(); }
        super.onDestroy();
    }
}
