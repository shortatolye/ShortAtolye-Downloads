package com.shortatolye.gokyuzuharitam;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Gokyuzu Haritam: programin tamami assets/index.html icindedir.
 * Sayfa https://appassets.androidplatform.net/assets/ adresinden yuklenir; boylece
 * kayitli kisiler (localStorage) kalici olur ve guncelleme baglantilari calisir.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String START = "https://" + HOST + "/assets/index.html";
    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        web.setBackgroundColor(0xFF0D1022);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setTextZoom(100);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        setContentView(web);

        if (state != null) web.restoreState(state);
        else web.loadUrl(START);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    private void toast(final String msg) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
    }

    private static String mimeOf(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".html")) return "text/html";
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

    private static String safeName(String name) {
        String n = name == null ? "dosya" : name.replaceAll("[\\\\/:*?\"<>|]", "-");
        return n.isEmpty() ? "dosya" : n;
    }

    /** Sayfadaki JavaScript'in cagirdigi telefon islevleri. */
    private class Bridge {

        @JavascriptInterface
        public String saveFile(String name, String b64) {
            name = safeName(name);
            if (Build.VERSION.SDK_INT < 29) return shareFile(name, b64);
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                ContentResolver res = getContentResolver();
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(MediaStore.Downloads.MIME_TYPE, mimeOf(name));
                cv.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = res.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) return "fail";
                try (OutputStream os = res.openOutputStream(uri)) {
                    if (os == null) return "fail";
                    os.write(data);
                }
                cv.clear();
                cv.put(MediaStore.Downloads.IS_PENDING, 0);
                res.update(uri, cv, null, null);
                toast("İndirilenler klasörüne kaydedildi: " + name);
                return "ok";
            } catch (Exception e) {
                toast("Kaydedilemedi");
                return "fail";
            }
        }

        @JavascriptInterface
        public String shareFile(String name, String b64) {
            name = safeName(name);
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                File dir = new File(getCacheDir(), "share");
                if (!dir.exists() && !dir.mkdirs()) return "fail";
                File f = new File(dir, name);
                try (FileOutputStream os = new FileOutputStream(f)) {
                    os.write(data);
                }
                Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", f);
                final Intent i = new Intent(Intent.ACTION_SEND);
                i.setType(mimeOf(name));
                i.putExtra(Intent.EXTRA_STREAM, uri);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> startActivity(Intent.createChooser(i, "Paylaş")));
                return "ok";
            } catch (Exception e) {
                toast("Paylaşılamadı");
                return "fail";
            }
        }

        @JavascriptInterface
        public void copyText(final String text) {
            runOnUiThread(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Gökyüzü Haritam", text));
                    if (Build.VERSION.SDK_INT < 33) toast("Kopyalandı");
                }
            });
        }

        @JavascriptInterface
        public void printPage() {
            runOnUiThread(() -> {
                PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                if (pm != null) {
                    pm.print("Gokyuzu Haritam Raporu",
                            web.createPrintDocumentAdapter("Gokyuzu-Haritam-Raporu"),
                            new PrintAttributes.Builder().build());
                }
            });
        }
    }
}
