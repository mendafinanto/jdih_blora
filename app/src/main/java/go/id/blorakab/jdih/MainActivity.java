package go.id.blorakab.jdih;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.MotionEvent;
import android.view.Gravity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.os.Handler;
import android.os.Looper;
import java.io.File;

public class MainActivity extends Activity {
    private static final String HOME = "https://jdih.blorakab.go.id/mobile";
    private WebView webView;
    private FrameLayout root;
    private View loadingView;
    private View offlineView;
    private boolean tryingCachedHome = false;
    private String lastPageUrl = HOME;
    private SharedPreferences preferences;
    private boolean offlineModeLoaded = false;
    private boolean loadingOfflineArchive = false;
    private final Handler archiveHandler = new Handler(Looper.getMainLooper());
    private static final String ARCHIVE_NAME = "jdih-home.mhtml";
    private boolean offlineDialogShowing = false;
    private float touchStartY = 0f;
    private float lastTouchY = 0f;
    private static final long PULL_HOLD_DURATION_MS = 1500L;
    private boolean pullRefreshTriggered = false;
    private boolean pullRefreshHolding = false;
    private final Runnable delayedPullRefresh = () -> {
        if (!pullRefreshHolding || pullRefreshTriggered) return;
        pullRefreshTriggered = true;
        if (hasConnection()) {
            webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
            webView.reload();
            showLoading();
        } else {
            showOfflineDialog();
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        preferences = getSharedPreferences("jdih_app", MODE_PRIVATE);
        buildScreen();

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setAllowFileAccess(true);
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
                if ("jdihapp".equals(scheme) && "retry".equals(host)) { loadHome(); return true; }
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
                if (url.startsWith("https://jdih.blorakab.go.id")) {
                    tryingCachedHome = false;
                    offlineModeLoaded = false;
                    loadingOfflineArchive = false;
                    lastPageUrl = url;
                    preferences.edit().putString("last_page_url", url).apply();
                    view.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
                    if (isHomePage(url)) scheduleHomeArchive();
                } else if (url.startsWith("file://")) {
                    hideOffline();
                    hideLoading();
                }
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

        // Setiap aplikasi dibuka ulang, mulai dari beranda.
        loadHome();
    }

    private void buildScreen() {
        root = new FrameLayout(this);
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        webView.setOnTouchListener((v, event) -> handlePullToRefresh(event));
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
        panel.setPadding(dp(30), dp(30), dp(30), dp(30));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(12, 48, 91), Color.rgb(22, 105, 158)});
        panel.setBackground(bg);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(126), dp(126));
        logoLp.bottomMargin = dp(24);
        panel.addView(logo, logoLp);

        TextView title = new TextView(this);
        title.setText("JDIH KABUPATEN BLORA");
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Jaringan Dokumentasi dan Informasi Hukum");
        subtitle.setTextColor(Color.rgb(220, 235, 248));
        subtitle.setTextSize(12);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(7);
        panel.addView(subtitle, subLp);

        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(dp(190), dp(4));
        progressLp.topMargin = dp(30);
        panel.addView(progress, progressLp);

        TextView label = new TextView(this);
        label.setText("Menyiapkan informasi hukum untuk Anda...");
        label.setTextColor(Color.WHITE);
        label.setTextSize(13);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(-1, -2);
        textLp.topMargin = dp(16);
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
            if (hasConnection()) {
                hideOffline(); showLoading();
                webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
                webView.loadUrl(lastPageUrl);
            } else {
                loadSavedHomeOrBundled();
            }
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
        if (hasConnection()) {
            showLoading();
            offlineModeLoaded = false;
            webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
            webView.loadUrl(HOME);
        } else {
            loadSavedHomeOrBundled();
            showOfflineDialog();
        }
    }

    private boolean isHomePage(String url) {
        try {
            Uri uri = Uri.parse(url);
            String path = uri.getPath() == null ? "" : uri.getPath();
            return "jdih.blorakab.go.id".equalsIgnoreCase(uri.getHost())
                    && ("/mobile".equals(path) || "/mobile/".equals(path));
        } catch (Exception e) {
            return false;
        }
    }

    // Simpan salinan beranda asli, termasuk berita, daftar produk, stylesheet, dan gambar
    // yang sudah dimuat WebView, sebagai arsip MHTML di penyimpanan internal aplikasi.
    private void scheduleHomeArchive() {
        archiveHandler.removeCallbacksAndMessages(null);
        archiveHandler.postDelayed(() -> {
            if (webView == null || isFinishing() || offlineModeLoaded) return;
            webView.evaluateJavascript(
                    "(function(){document.querySelectorAll('img[loading=lazy]').forEach(function(i){i.loading='eager';});return true;})()",
                    value -> archiveHandler.postDelayed(this::saveHomeArchive, 1800)
            );
        }, 2200);
    }

    private void saveHomeArchive() {
        if (webView == null || offlineModeLoaded || !hasConnection()) return;
        File temp = new File(getFilesDir(), "jdih-home-temp.mhtml");
        File saved = new File(getFilesDir(), ARCHIVE_NAME);
        if (temp.exists()) temp.delete();
        webView.saveWebArchive(temp.getAbsolutePath(), false, path -> {
            if (path != null && temp.exists() && temp.length() > 0) {
                if (saved.exists()) saved.delete();
                if (!temp.renameTo(saved)) {
                    try {
                        java.io.InputStream in = new java.io.FileInputStream(temp);
                        java.io.OutputStream out = new java.io.FileOutputStream(saved);
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                        in.close();
                        out.close();
                        temp.delete();
                    } catch (Exception ignored) { }
                }
                preferences.edit().putLong("home_archive_saved_at", System.currentTimeMillis()).apply();
            }
        });
    }

    private void handleMainFrameError() {
        if (loadingOfflineArchive) {
            loadingOfflineArchive = false;
            showBundledOfflineHome();
        } else if (!offlineModeLoaded) {
            loadSavedHomeOrBundled();
            showOfflineDialog();
        }
    }

    private void loadSavedHomeOrBundled() {
        offlineModeLoaded = true;
        tryingCachedHome = false;
        hideLoading();
        hideOffline();
        File saved = new File(getFilesDir(), ARCHIVE_NAME);
        if (saved.exists() && saved.length() > 0) {
            loadingOfflineArchive = true;
            webView.getSettings().setAllowFileAccess(true);
            webView.getSettings().setJavaScriptEnabled(true);
            webView.loadUrl(Uri.fromFile(saved).toString());
        } else {
            showBundledOfflineHome();
        }
    }

    private void showBundledOfflineHome() {
        loadingOfflineArchive = false;
        offlineModeLoaded = true;
        hideLoading();
        hideOffline();
        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.loadUrl("file:///android_asset/offline_home.html");
    }

    private void showOfflineDialog() {
        if (isFinishing() || offlineDialogShowing) return;
        offlineDialogShowing = true;
        new AlertDialog.Builder(this)
                .setTitle("Tidak Terhubung ke Internet")
                .setMessage("Anda tidak terhubung ke internet. Periksa koneksi, lalu pilih Reload untuk mencoba kembali atau Tutup untuk keluar dari aplikasi.")
                .setCancelable(false)
                .setNegativeButton("Tutup", (dialog, which) -> {
                    offlineDialogShowing = false;
                    finish();
                })
                .setPositiveButton("Reload", (dialog, which) -> {
                    offlineDialogShowing = false;
                    if (hasConnection()) {
                        offlineModeLoaded = false;
                        loadingOfflineArchive = false;
                        webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
                        webView.loadUrl(lastPageUrl == null ? HOME : lastPageUrl);
                    } else {
                        loadSavedHomeOrBundled();
                        showOfflineDialog();
                    }
                })
                .setOnCancelListener(dialog -> offlineDialogShowing = false)
                .show();
    }

    private boolean handlePullToRefresh(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchStartY = event.getY();
                lastTouchY = event.getY();
                pullRefreshTriggered = false;
                pullRefreshHolding = false;
                archiveHandler.removeCallbacks(delayedPullRefresh);
                break;
            case MotionEvent.ACTION_MOVE:
                float distance = event.getY() - touchStartY;
                float movement = Math.abs(event.getY() - lastTouchY);
                lastTouchY = event.getY();

                if (webView.getScrollY() > 0 || distance < dp(80)) {
                    pullRefreshHolding = false;
                    archiveHandler.removeCallbacks(delayedPullRefresh);
                    break;
                }

                // Mulai timer saat melewati batas. Getaran kecil tidak membatalkan timer.
                if (!pullRefreshTriggered && !pullRefreshHolding) {
                    pullRefreshHolding = true;
                    archiveHandler.postDelayed(delayedPullRefresh, 1000L);
                } else if (pullRefreshHolding && movement > dp(35)) {
                    // Jika masih menggeser cukup jauh, hitung ulang waktu tahan.
                    archiveHandler.removeCallbacks(delayedPullRefresh);
                    archiveHandler.postDelayed(delayedPullRefresh, 1000L);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!pullRefreshTriggered) {
                    pullRefreshHolding = false;
                    archiveHandler.removeCallbacks(delayedPullRefresh);
                }
                break;
        }
        return false;
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
