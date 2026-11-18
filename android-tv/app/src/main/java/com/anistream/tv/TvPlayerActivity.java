package com.anistream.tv;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import com.anistream.tv.adapters.TvEpisodeAdapter;
import com.anistream.tv.models.TvAnimeItem;
import com.anistream.tv.utils.TvApiClient;
import com.anistream.tv.utils.TvFocusHelper;

public class TvPlayerActivity extends AppCompatActivity {

    private PlayerView tvExoPlayer;
    private WebView tvPlayerWebView;
    private ProgressBar tvPlayerLoading;
    private View tvControlsOverlay;
    private TextView tvTvPlayerTitle;
    private TextView tvTvAspectBadge;
    private Button btnTvBack;
    private Button btnTvAspectRatio;
    private Button btnTvServerSettings;
    private Button btnTvRewind;
    private Button btnTvPlayPause;
    private Button btnTvForward;
    private RecyclerView rvTvEpisodes;

    private ExoPlayer exoPlayer;
    private TvAnimeItem item;
    private int currentEpisode = 1;
    private int currentResizeMode = 0; // 0: FIT, 1: ZOOM, 2: FILL

    private final Handler hideHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideRunnable = () -> {
        if (tvControlsOverlay != null && (exoPlayer == null || exoPlayer.isPlaying())) {
            tvControlsOverlay.animate().alpha(0f).setDuration(400).withEndAction(() -> {
                tvControlsOverlay.setVisibility(View.GONE);
            }).start();
        }
    };

    private final Runnable hideBadgeRunnable = () -> {
        if (tvTvAspectBadge != null) {
            tvTvAspectBadge.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                tvTvAspectBadge.setVisibility(View.GONE);
            }).start();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_player);

        // Immersive Sticky edge-to-edge mode for TV screens
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.hide(WindowInsetsCompat.Type.systemBars());
        insetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        item = (TvAnimeItem) getIntent().getSerializableExtra("item");
        if (item == null) {
            Toast.makeText(this, "Title unavailable", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupPlayer();
        loadMedia();
        showControlsTemporarily();
    }

    private void initViews() {
        tvExoPlayer = findViewById(R.id.tvExoPlayer);
        tvPlayerWebView = findViewById(R.id.tvPlayerWebView);
        tvPlayerLoading = findViewById(R.id.tvPlayerLoading);
        tvControlsOverlay = findViewById(R.id.tvControlsOverlay);
        tvTvPlayerTitle = findViewById(R.id.tvTvPlayerTitle);
        tvTvAspectBadge = findViewById(R.id.tvTvAspectBadge);
        btnTvBack = findViewById(R.id.btnTvBack);
        btnTvAspectRatio = findViewById(R.id.btnTvAspectRatio);
        btnTvServerSettings = findViewById(R.id.btnTvServerSettings);
        btnTvRewind = findViewById(R.id.btnTvRewind);
        btnTvPlayPause = findViewById(R.id.btnTvPlayPause);
        btnTvForward = findViewById(R.id.btnTvForward);
        rvTvEpisodes = findViewById(R.id.rvTvEpisodes);

        TvFocusHelper.applyTvFocusAnimation(btnTvBack);
        TvFocusHelper.applyTvFocusAnimation(btnTvAspectRatio);
        if (btnTvServerSettings != null) {
            TvFocusHelper.applyTvFocusAnimation(btnTvServerSettings);
            btnTvServerSettings.setOnClickListener(v -> showTvSettingsDialog());
        }
        TvFocusHelper.applyTvFocusAnimation(btnTvRewind);
        TvFocusHelper.applyTvFocusAnimation(btnTvPlayPause);
        TvFocusHelper.applyTvFocusAnimation(btnTvForward);

        btnTvBack.setOnClickListener(v -> finish());
        btnTvAspectRatio.setOnClickListener(v -> cycleAspectRatio());

        btnTvRewind.setOnClickListener(v -> seekRelative(-10000));
        btnTvForward.setOnClickListener(v -> seekRelative(10000));
        btnTvPlayPause.setOnClickListener(v -> togglePlayPause());

        updateTitle();

        // Episode rail
        if ("anime".equals(item.type)) {
            rvTvEpisodes.setVisibility(View.VISIBLE);
            TvEpisodeAdapter epAdapter = new TvEpisodeAdapter(this, item.totalEpisodes, ep -> {
                currentEpisode = ep;
                updateTitle();
                loadMedia();
            });
            rvTvEpisodes.setAdapter(epAdapter);
        } else {
            findViewById(R.id.tvPlayerBottom).setVisibility(View.GONE);
        }

        btnTvPlayPause.requestFocus();
    }

    @OptIn(markerClass = UnstableApi.class)
    private void cycleAspectRatio() {
        currentResizeMode = (currentResizeMode + 1) % 3;
        String modeName;
        String btnLabel;
        int mode;
        switch (currentResizeMode) {
            case 1:
                mode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
                modeName = "Zoom (Crop to Full TV Screen)";
                btnLabel = "📺 Screen: Zoom / Fill";
                break;
            case 2:
                mode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
                modeName = "Stretch (Full 16:9 Screen)";
                btnLabel = "📺 Screen: Stretch";
                break;
            default:
                mode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                modeName = "Fit (16:9 Original Aspect)";
                btnLabel = "📺 Screen: Fit (16:9)";
                break;
        }
        tvExoPlayer.setResizeMode(mode);
        btnTvAspectRatio.setText(btnLabel);
        showAspectBadge(modeName);
        showControlsTemporarily();
    }

    private void showAspectBadge(String text) {
        tvTvAspectBadge.setText(text);
        tvTvAspectBadge.setVisibility(View.VISIBLE);
        tvTvAspectBadge.setAlpha(1f);
        hideHandler.removeCallbacks(hideBadgeRunnable);
        hideHandler.postDelayed(hideBadgeRunnable, 2200);
    }

    private void updateTitle() {
        String epStr = "anime".equals(item.type) ? " Episode " + currentEpisode : "";
        tvTvPlayerTitle.setText(item.title + epStr + " • 1080p Full HD");
    }

    @OptIn(markerClass = UnstableApi.class)
    private void setupPlayer() {
        exoPlayer = new ExoPlayer.Builder(this).build();
        tvExoPlayer.setPlayer(exoPlayer);

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_BUFFERING) {
                    tvPlayerLoading.setVisibility(View.VISIBLE);
                } else {
                    tvPlayerLoading.setVisibility(View.GONE);
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                btnTvPlayPause.setText(isPlaying ? "⏸ Pause" : "▶ Play");
                if (isPlaying) {
                    showControlsTemporarily();
                } else {
                    tvControlsOverlay.setVisibility(View.VISIBLE);
                    hideHandler.removeCallbacks(hideRunnable);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                fallbackToWebView();
            }
        });

        // WebView configuration for TV
        WebSettings ws = tvPlayerWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setAllowFileAccess(true);
        tvPlayerWebView.setWebChromeClient(new WebChromeClient());
        tvPlayerWebView.setWebViewClient(new WebViewClient());
    }

    private void loadMedia() {
        if ("direct".equals(item.type) && item.videoUrl != null && !item.videoUrl.isEmpty()) {
            tvPlayerWebView.setVisibility(View.GONE);
            tvExoPlayer.setVisibility(View.VISIBLE);
            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(item.videoUrl));
            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.prepare();
            exoPlayer.play();
        } else {
            fallbackToWebView();
        }
    }

    private void fallbackToWebView() {
        if (exoPlayer != null) {
            exoPlayer.stop();
        }
        tvExoPlayer.setVisibility(View.GONE);
        tvPlayerWebView.setVisibility(View.VISIBLE);

        String embedUrl;
        if ("youtube".equals(item.type)) {
            embedUrl = "https://www.youtube-nocookie.com/embed/" + item.id + "?autoplay=1&enablejsapi=1";
        } else {
            embedUrl = "https://animeembed.com/embed/ani/" + item.id + "/" + currentEpisode + "?accent=00F0FF";
        }
        tvPlayerWebView.loadUrl(embedUrl);
    }

    private void togglePlayPause() {
        if (exoPlayer != null && tvExoPlayer.getVisibility() == View.VISIBLE) {
            if (exoPlayer.isPlaying()) {
                exoPlayer.pause();
            } else {
                exoPlayer.play();
            }
        }
        showControlsTemporarily();
    }

    private void seekRelative(long deltaMs) {
        if (exoPlayer != null && tvExoPlayer.getVisibility() == View.VISIBLE) {
            long newPos = Math.max(0, exoPlayer.getCurrentPosition() + deltaMs);
            exoPlayer.seekTo(newPos);
        }
        showControlsTemporarily();
    }

    private void showControlsTemporarily() {
        tvControlsOverlay.setVisibility(View.VISIBLE);
        tvControlsOverlay.animate().alpha(1f).setDuration(200).start();
        hideHandler.removeCallbacks(hideRunnable);
        hideHandler.postDelayed(hideRunnable, 4000);
    }

    // Android TV Remote D-Pad Key Interceptor
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (tvControlsOverlay.getVisibility() != View.VISIBLE) {
                    showControlsTemporarily();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (exoPlayer != null) exoPlayer.play();
                showControlsTemporarily();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (exoPlayer != null) exoPlayer.pause();
                showControlsTemporarily();
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (tvControlsOverlay.getVisibility() != View.VISIBLE) {
                    seekRelative(10000);
                    showControlsTemporarily();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (tvControlsOverlay.getVisibility() != View.VISIBLE) {
                    seekRelative(-10000);
                    showControlsTemporarily();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (tvControlsOverlay.getVisibility() != View.VISIBLE) {
                    showControlsTemporarily();
                    btnTvPlayPause.requestFocus();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_BACK:
                if (tvControlsOverlay.getVisibility() == View.VISIBLE) {
                    tvControlsOverlay.setVisibility(View.GONE);
                    return true;
                }
                break;
        }
        showControlsTemporarily();
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (exoPlayer != null) exoPlayer.pause();
        if (tvPlayerWebView != null) tvPlayerWebView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tvPlayerWebView != null) tvPlayerWebView.onResume();
    }

    private void showTvSettingsDialog() {
        TvApiClient apiClient = TvApiClient.getInstance(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_tv_server, null);
        EditText etServerUrl = dialogView.findViewById(R.id.etTvServerUrl);
        Button btnPresetDefault = dialogView.findViewById(R.id.btnTvPresetDefault);
        Button btnPresetEmulator = dialogView.findViewById(R.id.btnTvPresetEmulator);
        Button btnTestServer = dialogView.findViewById(R.id.btnTvTestServer);
        TextView tvConnectionStatus = dialogView.findViewById(R.id.tvTvConnectionStatus);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelTvServer);
        Button btnSave = dialogView.findViewById(R.id.btnSaveTvServer);

        TvFocusHelper.applyTvFocusAnimation(btnPresetDefault);
        TvFocusHelper.applyTvFocusAnimation(btnPresetEmulator);
        TvFocusHelper.applyTvFocusAnimation(btnTestServer);
        TvFocusHelper.applyTvFocusAnimation(btnCancel);
        TvFocusHelper.applyTvFocusAnimation(btnSave);

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
            apiClient.testServerConnection(testUrl, new TvApiClient.ApiCallback<Boolean>() {
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
                Toast.makeText(this, "TV Server updated to: " + newUrl, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                loadMedia();
            }
        });

        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideHandler.removeCallbacks(hideRunnable);
        hideHandler.removeCallbacks(hideBadgeRunnable);
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        if (tvPlayerWebView != null) {
            tvPlayerWebView.destroy();
        }
    }
}
