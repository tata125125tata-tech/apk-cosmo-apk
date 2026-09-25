package com.cosmogamestore.app;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.cosmogamestore.app.R;
import com.cosmogamestore.app.data.ApkMetadataHelper;
import com.cosmogamestore.app.data.db.AppDatabase;
import com.cosmogamestore.app.data.db.DownloadedGameEntity;
import com.cosmogamestore.app.data.db.SearchHistoryEntity;
import com.cosmogamestore.app.notification.DownloadNotificationHelper;
import com.cosmogamestore.app.ui.library.LibraryFragment;
import com.cosmogamestore.app.ui.settings.SettingsFragment;
import com.cosmogamestore.app.ui.video.VideoTubeFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cosmo Game Store - Native Client & Seamless APK Downloader / Installer.
 */
public class MainActivity extends AppCompatActivity {

    public static final String TAG = "CosmoStore";
    public static final String DEFAULT_STORE_URL = "https://cosmo-game.pages.dev/";

    // UI Elements
    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private BottomNavigationView bottomNav;
    private LinearLayout errorView;
    private Button btnRetryLoad;

    // In-App Download Progress Card
    private LinearLayout cardDownloadProgress;
    private TextView tvActiveDownloadTitle;
    private TextView tvActiveDownloadPercent;
    private TextView tvActiveDownloadBytes;
    private ProgressBar pbActiveDownload;
    private ImageView btnCancelActiveDownload;
    private TextView btnViewInLibrary;

    // Containers
    private FrameLayout tabBrowseContainer;
    private FrameLayout fragmentContainer;

    // Database, Preferences & Threading
    private AppDatabase db;
    private SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // State
    private File pendingInstallApk = null;
    private int currentTabId = R.id.nav_browse;
    private long currentActiveDownloadId = -1L;
    private final Map<Long, String> activeDownloadNames = Collections.synchronizedMap(new HashMap<>());
    private final Map<Long, String> activeDownloadUrls = Collections.synchronizedMap(new HashMap<>());
    private final Set<Long> processedDownloads = Collections.synchronizedSet(new HashSet<>());

    // Fragments
    private LibraryFragment libraryFragment;
    private VideoTubeFragment videoTubeFragment;
    private SettingsFragment settingsFragment;

    // Pending download parameters if waiting for storage permission
    private static class PendingDownloadRequest {
        final String url;
        final String userAgent;
        final String contentDisposition;
        final String mimetype;
        final long contentLength;

        PendingDownloadRequest(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
            this.url = url;
            this.userAgent = userAgent;
            this.contentDisposition = contentDisposition;
            this.mimetype = mimetype;
            this.contentLength = contentLength;
        }
    }

    private PendingDownloadRequest pendingDownloadRequest = null;

    // Storage Permission Launcher (Android 9 and below / API <= 28)
    private final ActivityResultLauncher<String> storagePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.i(TAG, "Storage permission granted");
                    if (pendingDownloadRequest != null) {
                        PendingDownloadRequest req = pendingDownloadRequest;
                        pendingDownloadRequest = null;
                        startDownloadProcess(req.url, req.userAgent, req.contentDisposition, req.mimetype, req.contentLength);
                    }
                } else {
                    Toast.makeText(this, "Storage permission is required to save game packages", Toast.LENGTH_LONG).show();
                    pendingDownloadRequest = null;
                }
            });

    // Notification Permission Launcher (Android 13+)
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.i(TAG, "POST_NOTIFICATIONS permission granted");
                } else {
                    Log.w(TAG, "POST_NOTIFICATIONS permission denied");
                }
            });

    // BroadcastReceiver for DownloadManager
    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                return;
            }
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
            if (downloadId != -1L) {
                Log.d(TAG, "Download completed broadcast for ID: " + downloadId);
                handleCompletedDownload(downloadId);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Dedicated Notification Channel for APK downloads
        DownloadNotificationHelper.INSTANCE.createNotificationChannel(this);

        // Request POST_NOTIFICATIONS on Android 13+
        requestNotificationPermissionIfNeeded();

        // Database & Preferences
        db = AppDatabase.Companion.getDatabase(getApplicationContext());
        prefs = getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);

        // Apply saved Dark / Light theme preference
        boolean isDarkMode = prefs.getBoolean(SettingsFragment.KEY_DARK_MODE, true);
        AppCompatDelegate.setDefaultNightMode(
                isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );

        setContentView(R.layout.activity_main);

        initViews();
        setupWebView();
        setupBottomNavigation();
        setupBackPressHandler();
        registerDownloadReceiver();

        // Handle Deep Link / App Link if launched with one
        boolean handledLink = handleIncomingIntent(getIntent());
        if (!handledLink) {
            loadStoreUrl(DEFAULT_STORE_URL);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private boolean handleIncomingIntent(Intent intent) {
        if (intent == null) return false;
        Uri uri = intent.getData();
        if (uri != null && "cosmo-game.pages.dev".equalsIgnoreCase(uri.getHost())) {
            String deepLinkUrl = uri.toString();
            Log.i(TAG, "Opened via Deep Link / App Link: " + deepLinkUrl);
            Toast.makeText(this, "Opening game...", Toast.LENGTH_SHORT).show();
            switchToBrowseTab();
            loadStoreUrl(deepLinkUrl);
            return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingInstallApk != null) {
            File apk = pendingInstallApk;
            pendingInstallApk = null;
            if (isInstallPermissionGranted()) {
                launchPackageInstaller(apk);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(downloadReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver", e);
        }
        executor.shutdown();
        if (webView != null) {
            webView.destroy();
        }
    }

    private void initViews() {
        webView = findViewById(R.id.web_view);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        progressBar = findViewById(R.id.progress_bar);
        bottomNav = findViewById(R.id.bottom_navigation);
        errorView = findViewById(R.id.error_view);
        btnRetryLoad = findViewById(R.id.btn_retry_load);
        Button btnErrorOpenLibrary = findViewById(R.id.btn_error_open_library);
        if (btnErrorOpenLibrary != null) {
            btnErrorOpenLibrary.setOnClickListener(v -> switchToLibraryTab());
        }

        tabBrowseContainer = findViewById(R.id.tab_browse_container);
        fragmentContainer = findViewById(R.id.fragment_container);

        // In-App Download Progress Card
        cardDownloadProgress = findViewById(R.id.card_download_progress);
        tvActiveDownloadTitle = findViewById(R.id.tv_active_download_title);
        tvActiveDownloadPercent = findViewById(R.id.tv_active_download_percent);
        tvActiveDownloadBytes = findViewById(R.id.tv_active_download_bytes);
        pbActiveDownload = findViewById(R.id.pb_active_download);
        btnCancelActiveDownload = findViewById(R.id.btn_cancel_active_download);
        btnViewInLibrary = findViewById(R.id.btn_view_in_library);

        btnCancelActiveDownload.setOnClickListener(v -> {
            if (currentActiveDownloadId != -1L) {
                cancelActiveDownload(currentActiveDownloadId);
            }
        });

        btnViewInLibrary.setOnClickListener(v -> switchToLibraryTab());

        // Error retry action
        btnRetryLoad.setOnClickListener(v -> {
            errorView.setVisibility(View.GONE);
            try {
                webView.reload();
            } catch (Exception e) {
                recreateWebView();
            }
        });
    }

    private void recreateWebView() {
        try {
            swipeRefresh.removeView(webView);
            webView.destroy();
        } catch (Exception e) {
            Log.w(TAG, "Error cleaning up dead WebView", e);
        }

        try {
            WebView newWebView = new WebView(this);
            newWebView.setId(R.id.web_view);
            newWebView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
            newWebView.setBackgroundColor(ContextCompat.getColor(this, R.color.background_dark));

            webView = newWebView;
            swipeRefresh.addView(webView);
            setupWebView();
            errorView.setVisibility(View.GONE);
            loadStoreUrl(DEFAULT_STORE_URL);
        } catch (Exception e) {
            Log.e(TAG, "Failed to recreate WebView", e);
            errorView.setVisibility(View.VISIBLE);
        }
    }

    public void performSearch(String query) {
        if (query == null) return;
        String trimmed = query.trim();
        if (trimmed.isEmpty()) return;

        switchToBrowseTab();
        try {
            String encodedQuery = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.name());
            loadStoreUrl(DEFAULT_STORE_URL + "?q=" + encodedQuery);
        } catch (Exception e) {
            loadStoreUrl(DEFAULT_STORE_URL + "?q=" + trimmed);
        }
    }

    public void saveSearchQuery(String term) {
        if (term == null) return;
        final String clean = term.trim();
        if (clean.isEmpty()) return;

        executor.execute(() -> {
            try {
                db.searchHistoryDao().insertSearch(
                        new SearchHistoryEntity(clean, System.currentTimeMillis())
                );
            } catch (Exception e) {
                Log.w(TAG, "Failed to save search history", e);
            }
        });
    }

    /**
     * Configure WebView with full JavaScript, DOM Storage, and Seamless Download Listeners.
     */
    private void setupWebView() {
        try {
            webView.setLayerType(View.LAYER_TYPE_NONE, null);
        } catch (Exception e) {
            Log.w(TAG, "Could not set layer type on WebView", e);
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        String ua = settings.getUserAgentString();
        if (ua != null) {
            settings.setUserAgentString(ua.replace("; wv", ""));
        }

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // JavaScript Interface for search and download interception
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onWebSearch(String term) {
                if (term != null && !term.trim().isEmpty()) {
                    runOnUiThread(() -> saveSearchQuery(term));
                }
            }

            @JavascriptInterface
            public void onDownloadTriggered(String downloadUrl) {
                if (downloadUrl != null && !downloadUrl.trim().isEmpty()) {
                    runOnUiThread(() -> handleInterceptedDownload(downloadUrl));
                }
            }
        }, "CosmoSearchBridge");

        // WebChromeClient for loading progress
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                }
            }
        });

        // WebViewClient to handle page navigation & intercept downloads seamlessly
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return false;
                String url = request.getUrl().toString();
                extractAndSaveSearchFromUrl(url);
                return handleUrlNavigation(url);
            }

            @Deprecated
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url == null) return false;
                extractAndSaveSearchFromUrl(url);
                return handleUrlNavigation(url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
                errorView.setVisibility(View.GONE);
                if (url != null) extractAndSaveSearchFromUrl(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);

                // Inject hook to capture in-page search forms and download buttons
                String jsSearchHook =
                        "(function() {" +
                        "  if (window.__cosmoInjected) return;" +
                        "  window.__cosmoInjected = true;" +
                        "  document.addEventListener('submit', function(e) {" +
                        "    var inputs = e.target.querySelectorAll('input');" +
                        "    for (var i = 0; i < inputs.length; i++) {" +
                        "      var val = inputs[i].value;" +
                        "      if (val && (inputs[i].type === 'search' || inputs[i].name.toLowerCase().indexOf('search') !== -1 || inputs[i].name.toLowerCase().indexOf('q') !== -1)) {" +
                        "        if (window.CosmoSearchBridge) window.CosmoSearchBridge.onWebSearch(val);" +
                        "      }" +
                        "    }" +
                        "  }, true);" +
                        "  document.addEventListener('click', function(e) {" +
                        "    var el = e.target;" +
                        "    while (el && el.tagName !== 'A' && el.tagName !== 'BUTTON') { el = el.parentElement; }" +
                        "    if (el && el.tagName === 'A' && el.href) {" +
                        "      var h = el.href.toLowerCase();" +
                        "      if (h.endsWith('.apk') || h.indexOf('.apk?') !== -1 || h.indexOf('/download/') !== -1) {" +
                        "        e.preventDefault();" +
                        "        if (window.CosmoSearchBridge) window.CosmoSearchBridge.onDownloadTriggered(el.href);" +
                        "      }" +
                        "    }" +
                        "  }, true);" +
                        "})();";
                view.evaluateJavascript(jsSearchHook, null);
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                boolean didCrash = detail != null && detail.didCrash();
                Log.e(TAG, "WebView render process exited: didCrash=" + didCrash + ". Recreating...");
                runOnUiThread(MainActivity.this::recreateWebView);
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame()) {
                    progressBar.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    errorView.setVisibility(View.VISIBLE);
                }
            }
        });

        // Swipe-to-refresh listener
        swipeRefresh.setColorSchemeResources(R.color.secondary, R.color.primary);
        swipeRefresh.setOnRefreshListener(() -> {
            errorView.setVisibility(View.GONE);
            try {
                webView.reload();
            } catch (Exception e) {
                recreateWebView();
            }
        });

        // 1. Capture download requests using WebView setDownloadListener
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            Log.d(TAG, "WebView setDownloadListener triggered: " + url + " (" + mimetype + ", " + contentLength + " bytes)");
            initiateDownloadWithPermissions(url, userAgent, contentDisposition, mimetype, contentLength);
        });
    }

    private void handleInterceptedDownload(String url) {
        String ua = (webView != null) ? webView.getSettings().getUserAgentString() : null;
        initiateDownloadWithPermissions(url, ua, null, "application/vnd.android.package-archive", -1L);
    }

    private void extractAndSaveSearchFromUrl(String url) {
        try {
            Uri uri = Uri.parse(url);
            if (uri.getHost() != null && uri.getHost().contains("cosmo-game.pages.dev")) {
                String queryTerm = uri.getQueryParameter("q");
                if (queryTerm == null) queryTerm = uri.getQueryParameter("search");
                if (queryTerm == null) queryTerm = uri.getQueryParameter("query");
                if (queryTerm == null) queryTerm = uri.getQueryParameter("term");

                if (queryTerm != null && !queryTerm.trim().isEmpty()) {
                    saveSearchQuery(queryTerm);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing search parameter from " + url, e);
        }
    }

    private boolean isDownloadUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.endsWith(".apk")
                || lower.contains(".apk?")
                || lower.contains(".apk#")
                || (lower.contains("/download/") && lower.contains(".apk"))
                || lower.endsWith(".zip");
    }

    private boolean handleUrlNavigation(String url) {
        // Direct APK or download link detected in URL navigation
        if (isDownloadUrl(url)) {
            handleInterceptedDownload(url);
            return true;
        }

        // External protocols
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
                return true;
            } catch (Exception e) {
                Log.w(TAG, "Cannot launch external scheme: " + url, e);
                return true;
            }
        }

        return false;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private String deriveGameTitle(String fileName) {
        String baseName = fileName;
        if (baseName.toLowerCase(Locale.ROOT).endsWith(".apk")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }
        String cleaned = baseName.replaceAll("[-_]+", " ")
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .trim();
        if (!cleaned.isEmpty()) {
            String[] words = cleaned.split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < words.length; i++) {
                if (words[i].length() > 0) {
                    sb.append(Character.toUpperCase(words[i].charAt(0)));
                    if (words[i].length() > 1) {
                        sb.append(words[i].substring(1).toLowerCase(Locale.ROOT));
                    }
                    if (i < words.length - 1) sb.append(" ");
                }
            }
            return sb.toString();
        }
        return "Game Package";
    }

    /**
     * Checks storage/download permissions and initiates download process.
     */
    private void initiateDownloadWithPermissions(
            String url,
            String userAgent,
            String contentDisposition,
            String mimetype,
            long contentLength
    ) {
        requestNotificationPermissionIfNeeded();

        // Android 9 and lower (API <= 28) requires WRITE_EXTERNAL_STORAGE for public Downloads
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingDownloadRequest = new PendingDownloadRequest(url, userAgent, contentDisposition, mimetype, contentLength);
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                return;
            }
        }

        startDownloadProcess(url, userAgent, contentDisposition, mimetype, contentLength);
    }

    /**
     * Starts DownloadManager, registers item in My Library immediately, and displays in-app progress.
     */
    private void startDownloadProcess(
            String url,
            String userAgent,
            String contentDisposition,
            String mimetype,
            long contentLength
    ) {
        try {
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
            if (!fileName.toLowerCase(Locale.ROOT).endsWith(".apk")) {
                fileName = fileName + ".apk";
            }

            String gameTitle = deriveGameTitle(fileName);

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(gameTitle);
            request.setDescription("Downloading " + fileName + " from Cosmo Game Store...");
            request.setMimeType("application/vnd.android.package-archive");

            // Silent Background Downloading: Hide system download notifications and UI completely
            try {
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
            } catch (Exception e) {
                try {
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                } catch (Exception ignored) {
                }
            }

            // Suppress system Download UI so downloads run strictly in the background silently
            try {
                request.setVisibleInDownloadsUi(false);
            } catch (Exception ignored) {
            }

            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            boolean wifiOnly = prefs.getBoolean(SettingsFragment.KEY_WIFI_ONLY, false);
            if (wifiOnly) {
                request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
            } else {
                request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            }

            if (userAgent != null && !userAgent.isEmpty()) {
                request.addRequestHeader("User-Agent", userAgent);
            }
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null && !cookie.isEmpty()) {
                request.addRequestHeader("Cookie", cookie);
            }

            DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager == null) {
                Toast.makeText(this, "Download service unavailable", Toast.LENGTH_SHORT).show();
                return;
            }

            long downloadId = downloadManager.enqueue(request);
            currentActiveDownloadId = downloadId;
            activeDownloadNames.put(downloadId, fileName);
            activeDownloadUrls.put(downloadId, url);

            File targetFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
            String initialPackageKey = "download." + Math.abs(url.hashCode());

            // 2. Automatically register downloading item in "My Library" section
            DownloadedGameEntity pendingEntity = new DownloadedGameEntity(
                    initialPackageKey,
                    gameTitle,
                    targetFile.getAbsolutePath(),
                    contentLength > 0 ? contentLength : 0L,
                    System.currentTimeMillis(),
                    "1.0",
                    1L,
                    url,
                    DownloadedGameEntity.STATUS_DOWNLOADING,
                    0,
                    downloadId
            );

            executor.execute(() -> {
                try {
                    db.gameDao().insertGame(pendingEntity);
                } catch (Exception e) {
                    Log.e(TAG, "Error registering game in database", e);
                }
            });

            // 3. Display real-time in-app download progress indicator in the UI
            showInAppDownloadProgress(gameTitle);

            Toast.makeText(this, "Downloading: " + gameTitle, Toast.LENGTH_SHORT).show();
            Log.i(TAG, "Enqueued download ID: " + downloadId + " (" + gameTitle + ")");

            // Initial notification
            DownloadNotificationHelper.INSTANCE.showProgressNotification(
                    this,
                    downloadId,
                    gameTitle,
                    0,
                    0L,
                    contentLength
            );

            // Track live progress
            trackDownloadProgress(downloadId, gameTitle, fileName, url, initialPackageKey);

        } catch (Exception e) {
            Log.e(TAG, "Failed to start download via DownloadManager", e);
            Toast.makeText(this, "Download error: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showInAppDownloadProgress(String gameTitle) {
        cardDownloadProgress.setVisibility(View.VISIBLE);
        tvActiveDownloadTitle.setText("Downloading " + gameTitle);
        tvActiveDownloadPercent.setText("0%");
        tvActiveDownloadBytes.setText("Starting download...");
        pbActiveDownload.setProgress(0);
        pbActiveDownload.setIndeterminate(true);
        btnCancelActiveDownload.setVisibility(View.VISIBLE);
        btnViewInLibrary.setVisibility(View.VISIBLE);
    }

    private void trackDownloadProgress(
            long downloadId,
            String gameTitle,
            String fileName,
            String downloadUrl,
            String packageKey
    ) {
        executor.execute(() -> {
            DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager == null) return;

            while (!isDestroyed() && !processedDownloads.contains(downloadId)) {
                DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
                try (Cursor cursor = downloadManager.query(query)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        int downloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                        int totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);

                        int status = statusIdx != -1 ? cursor.getInt(statusIdx) : -1;
                        long bytesDownloaded = downloadedIdx != -1 ? cursor.getLong(downloadedIdx) : 0L;
                        long totalBytes = totalIdx != -1 ? cursor.getLong(totalIdx) : -1L;

                        int progress = (totalBytes > 0) ? (int) ((bytesDownloaded * 100) / totalBytes) : 0;
                        progress = Math.max(0, Math.min(progress, 100));

                        if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) {
                            final int finalProgress = progress;
                            final long finalDownloaded = bytesDownloaded;
                            final long finalTotal = totalBytes;

                            // Update in-app progress UI
                            mainHandler.post(() -> {
                                if (currentActiveDownloadId == downloadId) {
                                    cardDownloadProgress.setVisibility(View.VISIBLE);
                                    tvActiveDownloadPercent.setText(finalProgress + "%");
                                    pbActiveDownload.setIndeterminate(finalTotal <= 0);
                                    pbActiveDownload.setProgress(finalProgress);

                                    if (finalTotal > 0) {
                                        tvActiveDownloadBytes.setText(
                                                DownloadNotificationHelper.INSTANCE.formatBytes(finalDownloaded) +
                                                " / " +
                                                DownloadNotificationHelper.INSTANCE.formatBytes(finalTotal)
                                        );
                                    } else {
                                        tvActiveDownloadBytes.setText("Downloading...");
                                    }
                                }
                            });

                            // Update Room database for "My Library" real-time view
                            try {
                                db.gameDao().updateDownloadProgress(downloadId, finalProgress, finalTotal > 0 ? finalTotal : finalDownloaded);
                            } catch (Exception e) {
                                Log.w(TAG, "Error updating db progress", e);
                            }

                            // Silent background downloading: In-app UI and Room DB update smoothly without active notification popups
                        } else if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                            break;
                        }
                    } else {
                        break;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error polling download progress", e);
                    break;
                }

                try {
                    Thread.sleep(600);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private void handleCompletedDownload(long downloadId) {
        if (processedDownloads.contains(downloadId)) return;
        processedDownloads.add(downloadId);

        executor.execute(() -> {
            DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager == null) return;

            DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
            try (Cursor cursor = downloadManager.query(query)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int localUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);

                    int status = statusIdx != -1 ? cursor.getInt(statusIdx) : -1;
                    String localUri = localUriIdx != -1 ? cursor.getString(localUriIdx) : null;
                    String fallbackFileName = activeDownloadNames.get(downloadId);
                    if (fallbackFileName == null) fallbackFileName = "game_" + downloadId + ".apk";
                    String downloadUrl = activeDownloadUrls.get(downloadId);
                    if (downloadUrl == null) downloadUrl = "";

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        File apkFile = resolveDownloadedFile(localUri, fallbackFileName);
                        if (apkFile != null && apkFile.exists()) {
                            // Extract real metadata and app icon from the downloaded APK
                            ApkMetadataHelper.ExtractedGame extracted =
                                    ApkMetadataHelper.INSTANCE.extract(MainActivity.this, apkFile);

                            String title = (extracted != null) ? extracted.getEntity().getTitle() : deriveGameTitle(fallbackFileName);

                            // Clean up pending placeholder and save completed entity in Room
                            try {
                                db.gameDao().deleteByDownloadId(downloadId);
                            } catch (Exception e) {
                                Log.w(TAG, "Error clearing pending download row", e);
                            }

                            if (extracted != null) {
                                DownloadedGameEntity finalEntity = new DownloadedGameEntity(
                                        extracted.getEntity().getPackageName(),
                                        extracted.getEntity().getTitle(),
                                        extracted.getEntity().getFilePath(),
                                        extracted.getEntity().getFileSize(),
                                        extracted.getEntity().getDownloadDate(),
                                        extracted.getEntity().getVersionName(),
                                        extracted.getEntity().getVersionCode(),
                                        downloadUrl,
                                        DownloadedGameEntity.STATUS_COMPLETED,
                                        100,
                                        downloadId
                                );
                                db.gameDao().insertGame(finalEntity);
                            } else {
                                DownloadedGameEntity fallbackEntity = new DownloadedGameEntity(
                                        "com.game." + apkFile.getName().hashCode(),
                                        title,
                                        apkFile.getAbsolutePath(),
                                        apkFile.length(),
                                        System.currentTimeMillis(),
                                        "1.0",
                                        1L,
                                        downloadUrl,
                                        DownloadedGameEntity.STATUS_COMPLETED,
                                        100,
                                        downloadId
                                );
                                db.gameDao().insertGame(fallbackEntity);
                            }

                            // Update in-app card to show completion
                            mainHandler.post(() -> {
                                if (currentActiveDownloadId == downloadId) {
                                    tvActiveDownloadTitle.setText(title + " Downloaded");
                                    tvActiveDownloadPercent.setText("✓");
                                    tvActiveDownloadBytes.setText("Ready to install • Tap to view");
                                    pbActiveDownload.setProgress(100);
                                    pbActiveDownload.setIndeterminate(false);

                                    // Auto dismiss after 5 seconds
                                    mainHandler.postDelayed(() -> {
                                        if (currentActiveDownloadId == downloadId) {
                                            cardDownloadProgress.setVisibility(View.GONE);
                                        }
                                    }, 5000);
                                }
                            });

                            // Notification with direct Install button
                            DownloadNotificationHelper.INSTANCE.showCompleteNotification(
                                    MainActivity.this,
                                    downloadId,
                                    title,
                                    apkFile
                            );

                            boolean autoInstall = prefs.getBoolean(SettingsFragment.KEY_AUTO_INSTALL, true);
                            if (autoInstall) {
                                mainHandler.post(() -> launchPackageInstaller(apkFile));
                            }
                        }
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        int reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                        int reason = reasonIdx != -1 ? cursor.getInt(reasonIdx) : -1;
                        String gameTitle = deriveGameTitle(fallbackFileName);

                        // Mark as failed in database
                        try {
                            db.gameDao().updateDownloadStatus(downloadId, DownloadedGameEntity.STATUS_FAILED);
                        } catch (Exception e) {
                            Log.w(TAG, "Error updating failed status", e);
                        }

                        mainHandler.post(() -> {
                            if (currentActiveDownloadId == downloadId) {
                                tvActiveDownloadTitle.setText(gameTitle + " Failed");
                                tvActiveDownloadBytes.setText("Download encountered an error (" + reason + ")");
                                tvActiveDownloadPercent.setText("!");
                                pbActiveDownload.setProgress(0);
                            }
                        });

                        DownloadNotificationHelper.INSTANCE.showFailedNotification(
                                MainActivity.this,
                                downloadId,
                                gameTitle,
                                "Download error code: " + reason
                        );
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling completed download", e);
            }
        });
    }

    private File resolveDownloadedFile(String localUriString, String fallbackFileName) {
        if (localUriString != null && !localUriString.isEmpty()) {
            try {
                Uri uri = Uri.parse(localUriString);
                if ("file".equals(uri.getScheme())) {
                    String path = uri.getPath();
                    if (path != null) {
                        File file = new File(path);
                        if (file.exists()) return file;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not resolve file from URI: " + localUriString, e);
            }
        }

        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File directFile = new File(downloadsDir, fallbackFileName);
        if (directFile.exists()) {
            return directFile;
        }

        return null;
    }

    /**
     * Cancels an active download and cleans up both DownloadManager and local library state.
     */
    public void cancelActiveDownload(long downloadId) {
        try {
            DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager != null && downloadId != -1L) {
                downloadManager.remove(downloadId);
            }

            executor.execute(() -> {
                try {
                    db.gameDao().deleteByDownloadId(downloadId);
                } catch (Exception e) {
                    Log.w(TAG, "Error deleting cancelled game from db", e);
                }
            });

            DownloadNotificationHelper.INSTANCE.cancelNotification(this, downloadId);

            if (currentActiveDownloadId == downloadId) {
                cardDownloadProgress.setVisibility(View.GONE);
                currentActiveDownloadId = -1L;
            }

            Toast.makeText(this, "Download cancelled", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error cancelling download", e);
        }
    }

    /**
     * Retries a previously failed or interrupted download.
     */
    public void retryDownload(String url, String title) {
        if (url == null || url.isEmpty()) return;
        Toast.makeText(this, "Retrying download: " + title, Toast.LENGTH_SHORT).show();
        handleInterceptedDownload(url);
    }

    /**
     * Triggers Android Package Installer securely using FileProvider content URI.
     */
    public void launchPackageInstaller(File apkFile) {
        if (apkFile == null || !apkFile.exists()) {
            Toast.makeText(this, "Package file not found", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                pendingInstallApk = apkFile;
                showUnknownSourcesPermissionDialog();
                return;
            }
        }

        try {
            String fileProviderAuthority = getApplicationContext().getPackageName() + ".fileprovider";
            Uri apkContentUri = FileProvider.getUriForFile(this, fileProviderAuthority, apkFile);

            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkContentUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            startActivity(installIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error invoking package installer", e);
            Toast.makeText(this, "Install failed: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showUnknownSourcesPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.unknown_sources_dialog_title)
                .setMessage(R.string.unknown_sources_dialog_message)
                .setPositiveButton("Settings", (dialog, which) -> openInstallPermissionSettings())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Could not open unknown sources settings", e);
                startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
            }
        }
    }

    private boolean isInstallPermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return getPackageManager().canRequestPackageInstalls();
        }
        return true;
    }

    private void setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener(menuItem -> {
            int itemId = menuItem.getItemId();
            currentTabId = itemId;

            if (itemId == R.id.nav_browse) {
                showBrowseTab();
            } else if (itemId == R.id.nav_library) {
                if (libraryFragment == null) {
                    libraryFragment = LibraryFragment.Companion.newInstance();
                }
                showFragment(libraryFragment);
            } else if (itemId == R.id.nav_updates) {
                if (videoTubeFragment == null) {
                    videoTubeFragment = VideoTubeFragment.Companion.newInstance();
                }
                showFragment(videoTubeFragment);
            } else if (itemId == R.id.nav_settings) {
                if (settingsFragment == null) {
                    settingsFragment = SettingsFragment.Companion.newInstance();
                }
                showFragment(settingsFragment);
            }
            return true;
        });
    }

    public void switchToBrowseTab() {
        bottomNav.setSelectedItemId(R.id.nav_browse);
    }

    public void switchToLibraryTab() {
        bottomNav.setSelectedItemId(R.id.nav_library);
    }

    public void switchToVideoTubeTab() {
        bottomNav.setSelectedItemId(R.id.nav_updates);
    }

    private void showBrowseTab() {
        tabBrowseContainer.setVisibility(View.VISIBLE);
        fragmentContainer.setVisibility(View.GONE);
    }

    private void showFragment(Fragment fragment) {
        tabBrowseContainer.setVisibility(View.GONE);
        fragmentContainer.setVisibility(View.VISIBLE);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private void setupBackPressHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentTabId != R.id.nav_browse) {
                    switchToBrowseTab();
                } else if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    public void loadStoreUrl(String url) {
        errorView.setVisibility(View.GONE);
        if (webView != null) {
            webView.loadUrl(url);
        }
    }

    private void registerDownloadReceiver() {
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(downloadReceiver, filter);
        }
    }
}
