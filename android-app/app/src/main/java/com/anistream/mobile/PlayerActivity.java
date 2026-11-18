package com.anistream.mobile;

import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anistream.mobile.adapters.DownloadOptionsAdapter;
import com.anistream.mobile.adapters.EpisodeAdapter;
import com.anistream.mobile.models.AnimeItem;
import com.anistream.mobile.models.DownloadOption;
import com.anistream.mobile.models.DownloadTask;
import com.anistream.mobile.utils.ApiClient;
import com.anistream.mobile.utils.AppUpdater;
import com.anistream.mobile.utils.DownloadHelper;
import com.anistream.mobile.utils.WatchlistManager;

import java.util.List;

public class PlayerActivity extends AppCompatActivity {

    private FrameLayout playerContainer;
    private NestedScrollView playerScrollView;
    private PlayerView exoPlayerView;
    private WebView playerWebView;
    private ProgressBar playerLoading;
    private TextView tvPlayerTitle;
    private TextView tvDetailsTitle;
    private TextView tvDetailsMeta;
    private TextView tvDescription;
    private Button btnTriggerDownload;
    private Button btnToggleWatchlist;
    private RecyclerView rvEpisodes;
    private LinearLayout serverChipsContainer;
    private TextView tvEpisodesHeading;
    private TextView tvAspectRatioBadge;

    private ImageView btnPlayerBack;
    private ImageView btnPlayerDownloads;
    private ImageView btnPlayerServerSettings;
    private ImageView btnPlayerPip;
    private ImageView btnToggleAspectRatio;
    private ImageView btnToggleFullscreen;

    private View playerDownloadProgressCard;
    private TextView tvPlayerDlTitle;
    private ProgressBar pbPlayerDlProgress;
    private TextView tvPlayerDlStatusText;
    private DownloadHelper.DownloadProgressListener playerDownloadListener;

    private ExoPlayer exoPlayer;
    private AnimeItem item;
    private int currentEpisode = 1;
    private WatchlistManager watchlistManager;
    private ApiClient apiClient;

    private boolean isFullscreen = false;
    private int currentResizeMode = 0; // 0: FIT, 1: ZOOM, 2: FILL
    private final Handler hudHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideHudRunnable = this::hideControlsOverlay;
    private final Runnable hideBadgeRunnable = () -> {
        if (tvAspectRatioBadge != null) {
            tvAspectRatioBadge.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                tvAspectRatioBadge.setVisibility(View.GONE);
            }).start();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        item = (AnimeItem) getIntent().getSerializableExtra("item");
        if (item == null) {
            Toast.makeText(this, "Item not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        watchlistManager = new WatchlistManager(this);
        apiClient = ApiClient.getInstance(this);

        initViews();
        setupPlayer();
        setupDetails();
        setupDownloadListener();
        loadMediaStream();
    }

    private void setupDownloadListener() {
        playerDownloadListener = new DownloadHelper.DownloadProgressListener() {
            @Override
            public void onProgressUpdate(DownloadTask task) {
                runOnUiThread(() -> updatePlayerDownloadBanner());
            }

            @Override
            public void onDownloadComplete(DownloadTask task) {
                runOnUiThread(() -> updatePlayerDownloadBanner());
            }

            @Override
            public void onDownloadsChanged() {
                runOnUiThread(() -> updatePlayerDownloadBanner());
            }
        };
    }

    private void initViews() {
        playerContainer = findViewById(R.id.playerContainer);
        playerScrollView = findViewById(R.id.playerScrollView);
        exoPlayerView = findViewById(R.id.exoPlayerView);
        playerWebView = findViewById(R.id.playerWebView);
        playerLoading = findViewById(R.id.playerLoading);
        tvPlayerTitle = findViewById(R.id.tvPlayerTitle);
        tvDetailsTitle = findViewById(R.id.tvDetailsTitle);
        tvDetailsMeta = findViewById(R.id.tvDetailsMeta);
        tvDescription = findViewById(R.id.tvDescription);
        btnTriggerDownload = findViewById(R.id.btnTriggerDownload);
        btnToggleWatchlist = findViewById(R.id.btnToggleWatchlist);
        rvEpisodes = findViewById(R.id.rvEpisodes);
        serverChipsContainer = findViewById(R.id.serverChipsContainer);
        tvEpisodesHeading = findViewById(R.id.tvEpisodesHeading);
        tvAspectRatioBadge = findViewById(R.id.tvAspectRatioBadge);

        btnPlayerBack = findViewById(R.id.btnPlayerBack);
        btnPlayerBack.setOnClickListener(v -> {
            if (isFullscreen) {
                toggleFullscreen(false);
            } else {
                finish();
            }
        });

        btnPlayerDownloads = findViewById(R.id.btnPlayerDownloads);
        if (btnPlayerDownloads != null) {
            btnPlayerDownloads.setOnClickListener(v -> DownloadHelper.showDownloadsDialog(this));
        }

        playerDownloadProgressCard = findViewById(R.id.playerDownloadProgressCard);
        tvPlayerDlTitle = findViewById(R.id.tvPlayerDlTitle);
        pbPlayerDlProgress = findViewById(R.id.pbPlayerDlProgress);
        tvPlayerDlStatusText = findViewById(R.id.tvPlayerDlStatusText);
        if (playerDownloadProgressCard != null) {
            playerDownloadProgressCard.setOnClickListener(v -> DownloadHelper.showDownloadsDialog(this));
        }
        View btnPlayerDlViewAll = findViewById(R.id.btnPlayerDlViewAll);
        if (btnPlayerDlViewAll != null) {
            btnPlayerDlViewAll.setOnClickListener(v -> DownloadHelper.showDownloadsDialog(this));
        }

        btnPlayerServerSettings = findViewById(R.id.btnPlayerServerSettings);
        if (btnPlayerServerSettings != null) {
            btnPlayerServerSettings.setOnClickListener(v -> showServerSettingsDialog());
        }

        btnPlayerPip = findViewById(R.id.btnPlayerPip);
        btnPlayerPip.setOnClickListener(v -> enterPip());

        btnToggleAspectRatio = findViewById(R.id.btnToggleAspectRatio);
        btnToggleAspectRatio.setOnClickListener(v -> cycleAspectRatio());

        btnToggleFullscreen = findViewById(R.id.btnToggleFullscreen);
        btnToggleFullscreen.setOnClickListener(v -> toggleFullscreen(!isFullscreen));

        btnTriggerDownload.setOnClickListener(v -> openDownloadModal());
        btnToggleWatchlist.setOnClickListener(v -> toggleWatchlist());

        // Tap video to toggle controls overlay
        playerContainer.setOnClickListener(v -> togglePlayerControlsOverlay());

        // Handle Back button to exit fullscreen first
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isFullscreen) {
                    toggleFullscreen(false);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    public void toggleFullscreen(boolean enable) {
        isFullscreen = enable;
        if (enable) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
        }
        updateFullscreenUi(enable);
    }

    private void updateFullscreenUi(boolean fullscreen) {
        isFullscreen = fullscreen;
        ViewGroup.LayoutParams lp = playerContainer.getLayoutParams();
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());

        if (fullscreen) {
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            playerContainer.setLayoutParams(lp);
            playerScrollView.setVisibility(View.GONE);

            // Immersive Sticky: Hide Status Bar and Navigation Bar
            insetsController.hide(WindowInsetsCompat.Type.systemBars());
            insetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

            btnToggleFullscreen.setImageResource(R.drawable.ic_fullscreen_exit);
            scheduleHideHud();
        } else {
            float density = getResources().getDisplayMetrics().density;
            lp.height = (int) (240 * density);
            playerContainer.setLayoutParams(lp);
            playerScrollView.setVisibility(View.VISIBLE);

            // Restore System Bars
            insetsController.show(WindowInsetsCompat.Type.systemBars());

            btnToggleFullscreen.setImageResource(R.drawable.ic_fullscreen);
            showControlsOverlay();
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private void cycleAspectRatio() {
        currentResizeMode = (currentResizeMode + 1) % 3;
        String modeName;
        int mode;
        switch (currentResizeMode) {
            case 1:
                mode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
                modeName = "Zoom (Crop / Fill Screen)";
                break;
            case 2:
                mode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
                modeName = "Stretch (Full Screen)";
                break;
            default:
                mode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                modeName = "Fit (16:9 Original)";
                break;
        }
        exoPlayerView.setResizeMode(mode);
        showAspectRatioBadge(modeName);
    }

    private void showAspectRatioBadge(String text) {
        tvAspectRatioBadge.setText(text);
        tvAspectRatioBadge.setVisibility(View.VISIBLE);
        tvAspectRatioBadge.setAlpha(1f);
        hudHandler.removeCallbacks(hideBadgeRunnable);
        hudHandler.postDelayed(hideBadgeRunnable, 2200);
    }

    private void togglePlayerControlsOverlay() {
        View topBar = findViewById(R.id.playerTopBar);
        if (topBar.getVisibility() == View.VISIBLE) {
            hideControlsOverlay();
        } else {
            showControlsOverlay();
            if (isFullscreen) {
                scheduleHideHud();
            }
        }
    }

    private void showControlsOverlay() {
        View topBar = findViewById(R.id.playerTopBar);
        topBar.setVisibility(View.VISIBLE);
        topBar.animate().alpha(1f).setDuration(200).start();
    }

    private void hideControlsOverlay() {
        if (!isFullscreen) return; // Always keep visible in portrait mode
        View topBar = findViewById(R.id.playerTopBar);
        topBar.animate().alpha(0f).setDuration(300).withEndAction(() -> topBar.setVisibility(View.GONE)).start();
    }

    private void scheduleHideHud() {
        hudHandler.removeCallbacks(hideHudRunnable);
        hudHandler.postDelayed(hideHudRunnable, 3500);
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Rational rational = new Rational(16, 9);
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(rational)
                    .build();
            enterPictureInPictureMode(params);
        } else {
            Toast.makeText(this, "Picture-in-Picture requires Android 8.0+", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!isInPictureInPictureMode()) {
            boolean isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
            updateFullscreenUi(isLandscape);
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private void setupPlayer() {
        exoPlayer = new ExoPlayer.Builder(this).build();
        exoPlayerView.setPlayer(exoPlayer);

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_BUFFERING) {
                    playerLoading.setVisibility(View.VISIBLE);
                } else {
                    playerLoading.setVisibility(View.GONE);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                // Fallback to WebView if ExoPlayer fails to stream directly
                fallbackToWebView(item.videoUrl);
            }
        });

        // Setup WebView for embeds
        WebSettings ws = playerWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setAllowFileAccess(true);
        playerWebView.setWebChromeClient(new WebChromeClient());
        playerWebView.setWebViewClient(new WebViewClient());
    }

    private void setupDetails() {
        tvPlayerTitle.setText(item.title);
        tvDetailsTitle.setText(item.title);
        updateMetaText();
        tvDescription.setText(item.description != null && !item.description.isEmpty() ? item.description : "No description available.");

        updateWatchlistButton();

        // Episodes setup
        if ("anime".equals(item.type)) {
            tvEpisodesHeading.setVisibility(View.VISIBLE);
            rvEpisodes.setVisibility(View.VISIBLE);
            EpisodeAdapter epAdapter = new EpisodeAdapter(this, item.totalEpisodes, ep -> {
                currentEpisode = ep;
                updateMetaText();
                loadMediaStream();
            });
            rvEpisodes.setAdapter(epAdapter);
        } else {
            tvEpisodesHeading.setVisibility(View.GONE);
            rvEpisodes.setVisibility(View.GONE);
        }

        // Servers setup
        setupServers();
    }

    private void updateMetaText() {
        String meta = "anime".equals(item.type) ? "Episode " + currentEpisode + " of " + item.totalEpisodes : (item.format != null ? item.format : "Full Video");
        if (item.duration != null) meta += " • " + item.duration;
        tvDetailsMeta.setText(meta);
        tvPlayerTitle.setText(item.title + ("anime".equals(item.type) ? " Ep " + currentEpisode : ""));
    }

    private void setupServers() {
        serverChipsContainer.removeAllViews();
        String[] servers = {"Server 1 (HD Sub)", "Server 2 (English Dub)", "Server 3 (Edge Mirror)"};
        for (int i = 0; i < servers.length; i++) {
            Button btn = new Button(this);
            btn.setText(servers[i]);
            btn.setAllCaps(false);
            btn.setTextSize(12f);
            btn.setBackgroundResource(i == 0 ? R.drawable.bg_pill : R.drawable.bg_card);
            btn.setTextColor(getResources().getColor(i == 0 ? R.color.accent_cyan : R.color.text_primary, null));
            int idx = i;
            btn.setOnClickListener(v -> switchServer(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (int) (36 * getResources().getDisplayMetrics().density)
            );
            lp.setMargins(0, 0, (int) (8 * getResources().getDisplayMetrics().density), 0);
            serverChipsContainer.addView(btn, lp);
        }
    }

    private void switchServer(int index) {
        for (int i = 0; i < serverChipsContainer.getChildCount(); i++) {
            View child = serverChipsContainer.getChildAt(i);
            if (child instanceof Button) {
                ((Button) child).setBackgroundResource(i == index ? R.drawable.bg_pill : R.drawable.bg_card);
                ((Button) child).setTextColor(getResources().getColor(i == index ? R.color.accent_cyan : R.color.text_primary, null));
            }
        }
        loadMediaStream();
    }

    private void loadMediaStream() {
        if ("direct".equals(item.type) && item.videoUrl != null && !item.videoUrl.isEmpty()) {
            playerWebView.setVisibility(View.GONE);
            exoPlayerView.setVisibility(View.VISIBLE);
            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(item.videoUrl));
            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.prepare();
            exoPlayer.play();
        } else {
            // For anime episodes or YouTube, use WebView player or direct stream
            String embedUrl;
            if ("youtube".equals(item.type)) {
                embedUrl = "https://www.youtube-nocookie.com/embed/" + item.id + "?autoplay=1&enablejsapi=1";
            } else {
                embedUrl = "https://animeembed.com/embed/ani/" + item.id + "/" + currentEpisode + "?accent=6366f1";
            }
            fallbackToWebView(embedUrl);
        }
    }

    private void fallbackToWebView(String url) {
        if (exoPlayer != null) {
            exoPlayer.stop();
        }
        exoPlayerView.setVisibility(View.GONE);
        playerWebView.setVisibility(View.VISIBLE);
        playerWebView.loadUrl(url);
    }

    private void openDownloadModal() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_download, null);
        TextView title = dialogView.findViewById(R.id.dialogTitle);
        TextView sub = dialogView.findViewById(R.id.dialogSub);
        ProgressBar loading = dialogView.findViewById(R.id.dialogLoading);
        RecyclerView rvOptions = dialogView.findViewById(R.id.rvDownloadOptions);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelDownload);

        title.setText(item.title + ("anime".equals(item.type) ? " Ep " + currentEpisode : ""));
        sub.setText("Multi-Resolution Downloads (1080p, 720p, 480p, 360p, MP3)");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        loading.setVisibility(View.VISIBLE);
        rvOptions.setLayoutManager(new LinearLayoutManager(this));

        String streamUrl = "anime".equals(item.type)
                ? "https://animeembed.com/embed/ani/" + item.id + "/" + currentEpisode
                : (item.videoUrl != null ? item.videoUrl : "");

        String downloadTitle = "anime".equals(item.type)
                ? (item.title + " Episode " + currentEpisode)
                : item.title;

        apiClient.resolveDownloads(streamUrl, downloadTitle, item.duration, item.type, item.id, new ApiClient.ApiCallback<List<DownloadOption>>() {
            @Override
            public void onSuccess(List<DownloadOption> result) {
                runOnUiThread(() -> {
                    loading.setVisibility(View.GONE);
                    DownloadOptionsAdapter optAdapter = new DownloadOptionsAdapter(PlayerActivity.this, option -> {
                        String dlUrl = option.directUrl != null ? option.directUrl : option.externalUrl;
                        DownloadHelper.startDownload(PlayerActivity.this, dlUrl, option.subtitleUrl, downloadTitle, option.quality, option.format, option.size);
                        dialog.dismiss();
                    });
                    optAdapter.setOptions(result);
                    rvOptions.setAdapter(optAdapter);
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    loading.setVisibility(View.GONE);
                    Toast.makeText(PlayerActivity.this, "Could not resolve formats: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });

        dialog.show();
    }

    private void showServerSettingsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_server_ip, null);
        EditText etServerUrl = dialogView.findViewById(R.id.etServerUrl);
        Button btnPresetDefault = dialogView.findViewById(R.id.btnPresetDefault);
        Button btnPresetEmulator = dialogView.findViewById(R.id.btnPresetEmulator);
        Button btnTestServer = dialogView.findViewById(R.id.btnTestServer);
        TextView tvConnectionStatus = dialogView.findViewById(R.id.tvConnectionStatus);
        TextView tvAppVersionInfo = dialogView.findViewById(R.id.tvAppVersionInfo);
        Button btnCheckUpdates = dialogView.findViewById(R.id.btnCheckUpdates);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelServer);
        Button btnSave = dialogView.findViewById(R.id.btnSaveServer);

        if (tvAppVersionInfo != null) {
            tvAppVersionInfo.setText("v" + AppUpdater.getAppVersionName(this) + " (Build " + AppUpdater.getAppVersionCode(this) + ")");
        }
        if (btnCheckUpdates != null) {
            btnCheckUpdates.setOnClickListener(v -> AppUpdater.checkForUpdates(this, true));
        }

        etServerUrl.setText(apiClient.getServerUrl());

        btnPresetDefault.setOnClickListener(v -> etServerUrl.setText("http://rravi.ddns.net:4008"));
        btnPresetEmulator.setOnClickListener(v -> etServerUrl.setText("http://10.0.2.2:5050"));

        btnTestServer.setOnClickListener(v -> {
            String testUrl = etServerUrl.getText().toString().trim();
            if (testUrl.isEmpty()) {
                tvConnectionStatus.setText("Enter a URL first");
                tvConnectionStatus.setTextColor(0xFFFF5555);
                return;
            }
            tvConnectionStatus.setText("Testing connection...");
            tvConnectionStatus.setTextColor(0xFF00F0FF);
            apiClient.testServerConnection(testUrl, new ApiClient.ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    runOnUiThread(() -> {
                        tvConnectionStatus.setText("✓ Connected! Server online");
                        tvConnectionStatus.setTextColor(0xFF00FF88);
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> {
                        tvConnectionStatus.setText("✗ Failed: " + e.getMessage());
                        tvConnectionStatus.setTextColor(0xFFFF5555);
                    });
                }
            });
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String newUrl = etServerUrl.getText().toString().trim();
            if (!newUrl.isEmpty()) {
                apiClient.setServerUrl(newUrl);
                Toast.makeText(this, "Server updated to: " + newUrl, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                loadMediaStream();
            }
        });

        dialog.show();
    }

    private void toggleWatchlist() {
        boolean saved = watchlistManager.toggleWatchlist(item);
        Toast.makeText(this, saved ? "Saved to Watchlist!" : "Removed from Watchlist", Toast.LENGTH_SHORT).show();
        updateWatchlistButton();
    }

    private void updateWatchlistButton() {
        boolean inList = watchlistManager.isInWatchlist(item.id);
        btnToggleWatchlist.setText(inList ? "In Watchlist ⭐" : getString(R.string.save_watchlist));
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Rational rational = new Rational(16, 9);
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(rational)
                    .build();
            enterPictureInPictureMode(params);
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        findViewById(R.id.playerTopBar).setVisibility(isInPictureInPictureMode ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (exoPlayer != null && !isInPictureInPictureMode()) {
            exoPlayer.pause();
        }
        if (playerWebView != null) {
            playerWebView.onPause();
        }
        if (playerDownloadListener != null) {
            DownloadHelper.removeListener(playerDownloadListener);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (playerWebView != null) {
            playerWebView.onResume();
        }
        if (playerDownloadListener != null) {
            DownloadHelper.addListener(playerDownloadListener);
        }
        updatePlayerDownloadBanner();
    }

    private void updatePlayerDownloadBanner() {
        if (playerDownloadProgressCard == null) return;
        DownloadTask task = DownloadHelper.getLatestActiveTask();
        if (task != null && (task.status == android.app.DownloadManager.STATUS_RUNNING || task.status == android.app.DownloadManager.STATUS_PENDING)) {
            playerDownloadProgressCard.setVisibility(View.VISIBLE);
            tvPlayerDlTitle.setText((task.isAudio ? "🎵 Audio: " : "🎬 Video: ") + task.title + " (" + task.quality + ")");
            pbPlayerDlProgress.setProgress(task.progressPercent);
            tvPlayerDlStatusText.setText(task.statusText);
        } else {
            playerDownloadProgressCard.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hudHandler.removeCallbacksAndMessages(null);
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        if (playerWebView != null) {
            playerWebView.destroy();
        }
    }
}
