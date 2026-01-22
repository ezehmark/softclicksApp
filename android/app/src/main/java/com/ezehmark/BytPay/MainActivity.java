package com.softclicks.app;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.identity.BeginSignInRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.SignInClient;
import com.google.android.gms.auth.api.identity.SignInCredential;
import com.google.android.gms.common.api.ApiException;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "WebViewConsole";
    private WebView webView;
    private View splashScreen;
    private ImageView noWifiImage;
    private static final int SPLASH_DURATION = 5000; // 5 seconds

    // GIS One Tap
    private SignInClient oneTapClient;
    private BeginSignInRequest signInRequest;
    private static final int REQ_ONE_TAP = 100;

    // Network monitoring
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        splashScreen = findViewById(R.id.splash_screen);
        webView = findViewById(R.id.webview);
        noWifiImage = findViewById(R.id.no_wifi_image);

        // start hidden
        noWifiImage.setVisibility(View.GONE);

        applySystemThemeUI();
        setupGoogleOneTap();

        // WebView setup
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        String modernUA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36";
        webSettings.setUserAgentString(modernUA);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            webSettings.setForceDark(WebSettings.FORCE_DARK_AUTO);
        }

        WebView.setWebContentsDebuggingEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                setThemeForWebView();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (consoleMessage != null) {
                    String msg = consoleMessage.message();
                    int line = consoleMessage.lineNumber();
                    String source = consoleMessage.sourceId() != null ? consoleMessage.sourceId() : "unknown";
                    Log.d(TAG, msg + " -- From line " + line + " of " + source);
                }
                return true;
            }
        });

        // Add JS interface for triggering native sign-in
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void triggerGoogleSignIn() {
                runOnUiThread(() -> startGoogleOneTap());
            }
        }, "AndroidApp");

        // Splash delay & internet check
        new Handler().postDelayed(() -> {
            if (isConnected()) {
                webView.setVisibility(View.VISIBLE);
                webView.loadUrl("https://v0-softclicksapp.vercel.app");
            } else {
                // overlay no wifi
                noWifiImage.setVisibility(View.VISIBLE);
                webView.setVisibility(View.VISIBLE); // keep webview ready underneath
            }

            splashScreen.animate()
                    .alpha(0f)
                    .setDuration(500)
                    .withEndAction(() -> splashScreen.setVisibility(View.GONE));

        }, SPLASH_DURATION);
    }

    /** INTERNET CHECK **/
    private boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null &&
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            return netInfo != null && netInfo.isConnected();
        }
    }

    /** START NETWORK LISTENER **/
    @Override
    protected void onStart() {
        super.onStart();
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                runOnUiThread(() -> {
                    noWifiImage.setVisibility(View.GONE);
                    if (webView.getUrl() == null) {
                        webView.loadUrl("https://bytpay.live");
                    }
                });
            }

            @Override
            public void onLost(Network network) {
                runOnUiThread(() -> {
                    // Show overlay while keeping webview behind
                    noWifiImage.setVisibility(View.VISIBLE);
                });
            }
        };

        cm.registerDefaultNetworkCallback(networkCallback);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            cm.unregisterNetworkCallback(networkCallback);
        }
    }

    /** THEME SETUP **/
    private void applySystemThemeUI() {
        boolean isDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;

        Window window = getWindow();
        int barColor = isDark ? Color.parseColor("#6B7280") : Color.parseColor("#E5E7EB");
        window.setStatusBarColor(barColor);
        window.setNavigationBarColor(barColor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            int appearance = isDark
                    ? 0
                    : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            window.getInsetsController().setSystemBarsAppearance(
                    appearance,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            );
        } else {
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (!isDark) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            decor.setSystemUiVisibility(flags);
        }
    }

    private void setThemeForWebView() {
        boolean isDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        String theme = isDark ? "dark" : "light";

        String js =
                "document.documentElement.setAttribute('data-theme', '" + theme + "');" +
                        "if (window.themeChange) { window.themeChange('" + theme + "'); }" +
                        "try {" +
                        "  const mql = window.matchMedia('(prefers-color-scheme: dark)');" +
                        "  Object.defineProperty(mql, 'matches', { value: " + (isDark ? "true" : "false") + ", configurable: true });" +
                        "  window.dispatchEvent(new Event('change'));" +
                        "} catch(e) { console.log('Theme event injection failed!', e); }";

        webView.evaluateJavascript(js, null);
    }

    /** GOOGLE ONE TAP **/
    private void setupGoogleOneTap() {
        oneTapClient = Identity.getSignInClient(this);

        signInRequest = BeginSignInRequest.builder()
                .setGoogleIdTokenRequestOptions(
                        BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                                .setSupported(true)
                                .setServerClientId(getString(R.string.server_client_id)) // from Google Cloud Console
                                .setFilterByAuthorizedAccounts(false)
                                .build()
                )
                .setAutoSelectEnabled(false)
                .build();
    }

    private void startGoogleOneTap() {
        oneTapClient.beginSignIn(signInRequest)
                .addOnSuccessListener(this, result -> {
                    try {
                        startIntentSenderForResult(
                                result.getPendingIntent().getIntentSender(),
                                REQ_ONE_TAP, null, 0, 0, 0
                        );
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })
                .addOnFailureListener(this, e -> {
                    Log.e(TAG, "One Tap failed: " + e.getLocalizedMessage());
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_ONE_TAP && data != null) {
            try {
                SignInCredential credential = oneTapClient.getSignInCredentialFromIntent(data);
                String idToken = credential.getGoogleIdToken();
                if (idToken != null) {
                    sendTokenToWebView(idToken);
                }
            } catch (ApiException e) {
                e.printStackTrace();
            }
        }
    }

    private void sendTokenToWebView(String token) {
        String js = "if (window.onGoogleSignIn) { window.onGoogleSignIn('" + token + "'); }";
        webView.evaluateJavascript(js, null);
    }

    /** THEME CHANGE HANDLER **/
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applySystemThemeUI();
        setThemeForWebView();
    }

    /** BACK BUTTON BEHAVIOR **/
    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
