package com.kreston8.islandcn;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * A thin shell around the islandcn site so the React island demos can run as an Android app.
 *
 * <p>islandcn is a Next.js site plus a shadcn registry, not a native app, so all the rendering
 * happens in the system WebView. This class only handles navigation: keeping the site's own pages
 * in the WebView, sending anything else to the browser, the back gesture, and an offline state.
 */
public final class MainActivity extends Activity {

    private static final String START_URL = "https://islandcn.dev/";
    private static final String SITE_HOST = "islandcn.dev";
    private static final int ACCENT = 0xFF34C759;

    private WebView web;
    private ProgressBar progress;
    private View errorPanel;
    private TextView errorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        web = new WebView(this);
        web.setLayoutParams(
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.setBackgroundColor(Color.BLACK);
        configure(web.getSettings());
        web.setWebViewClient(new SiteClient());
        web.setWebChromeClient(new ProgressClient());

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(ColorStateList.valueOf(ACCENT));
        FrameLayout.LayoutParams progressParams =
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        progressParams.gravity = Gravity.TOP;
        progress.setLayoutParams(progressParams);

        errorPanel = buildErrorPanel();
        errorPanel.setVisibility(View.GONE);

        root.addView(web);
        root.addView(progress);
        root.addView(errorPanel);
        setContentView(root);

        if (savedInstanceState == null) {
            web.loadUrl(START_URL);
        } else {
            web.restoreState(savedInstanceState);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configure(WebSettings settings) {
        // The island is a client component with spring motion; it needs JS, storage and modern APIs.
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(settings.getUserAgentString() + " islandcn-web/1.0");

        // The site follows prefers-color-scheme, so let the WebView report the system theme.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            settings.setAlgorithmicDarkeningAllowed(true);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            forceDark(settings);
        }
    }

    @SuppressWarnings("deprecation")
    private void forceDark(WebSettings settings) {
        settings.setForceDark(WebSettings.FORCE_DARK_AUTO);
    }

    private View buildErrorPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackgroundColor(Color.BLACK);
        panel.setPadding(dp(32), dp(32), dp(32), dp(32));
        panel.setLayoutParams(
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        errorText = new TextView(this);
        errorText.setText(R.string.offline_message);
        errorText.setTextColor(0xFFEDEDED);
        errorText.setTextSize(16f);
        errorText.setGravity(Gravity.CENTER);
        errorText.setLineSpacing(dp(4), 1f);

        Button retry = new Button(this);
        retry.setText(R.string.retry);
        retry.setOnClickListener(
                v -> {
                    errorPanel.setVisibility(View.GONE);
                    web.setVisibility(View.VISIBLE);
                    web.loadUrl(START_URL);
                });

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = dp(16);

        panel.addView(errorText);
        panel.addView(retry, buttonParams);
        return panel;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean isSiteHost(Uri uri) {
        String host = uri.getHost();
        return host != null && (host.equals(SITE_HOST) || host.endsWith("." + SITE_HOST));
    }

    private final class SiteClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (uri == null) {
                return false;
            }
            if (isSiteHost(uri)) {
                return false;
            }
            // Anything else (mail, X, GitHub) belongs to the system, not to this shell.
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException ignored) {
                // No handler installed; staying put is the better outcome.
            }
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            progress.setVisibility(View.GONE);
        }

        @Override
        public void onReceivedError(
                WebView view, WebResourceRequest request, WebResourceError error) {
            if (!request.isForMainFrame()) {
                return;
            }
            web.setVisibility(View.GONE);
            errorPanel.setVisibility(View.VISIBLE);
        }
    }

    private final class ProgressClient extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            progress.setProgress(newProgress);
        }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) {
            web.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
