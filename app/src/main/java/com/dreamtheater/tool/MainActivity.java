package com.dreamtheater.tool;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int REQ_FILE = 42;
    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private OutputStream out;
    private Uri outUri;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(100);
        web.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    startActivityForResult(p.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });
        web.loadUrl("https://appassets.androidplatform.net/assets/tool.html");
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE && fileCb != null) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCb = null;
        } else super.onActivityResult(req, res, data);
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.__dtBack&&window.__dtBack())===true", v -> {
            if (!"true".equals(v)) moveTaskToBack(true);
        });
    }

    private void toast(final String m) {
        runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show());
    }

    /** Guarda en Descargas/DreamTheater por trozos (sin permisos, Android 10+). */
    private class Bridge {
        @JavascriptInterface
        public void open(String name, String mime) {
            try {
                ContentResolver cr = getContentResolver();
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DreamTheater");
                cv.put(MediaStore.Downloads.IS_PENDING, 1);
                outUri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                out = cr.openOutputStream(outUri);
            } catch (Exception e) {
                toast("No se pudo guardar: " + e.getMessage());
            }
        }

        @JavascriptInterface
        public void write(String b64) {
            try { out.write(Base64.decode(b64, Base64.DEFAULT)); } catch (Exception e) { toast("Error: " + e.getMessage()); }
        }

        @JavascriptInterface
        public void close() {
            try {
                out.close();
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(outUri, cv, null, null);
            } catch (Exception e) { toast("Error: " + e.getMessage()); }
            out = null;
        }

        @JavascriptInterface
        public void abort() {
            try { if (out != null) out.close(); if (outUri != null) getContentResolver().delete(outUri, null, null); } catch (Exception ignored) {}
            out = null;
        }
    }
}
