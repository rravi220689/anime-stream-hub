package com.anistream.mobile;

import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anistream.mobile.adapters.DownloadOptionsAdapter;
import com.anistream.mobile.adapters.EpisodeAdapter;
import com.anistream.mobile.models.AnimeItem;
import com.anistream.mobile.models.DownloadOption;
import com.anistream.mobile.utils.ApiClient;
import com.anistream.mobile.utils.DownloadHelper;
import com.anistream.mobile.utils.WatchlistManager;

import java.util.List;

public class PlayerActivity extends AppCompatActivity {

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

    private ExoPlayer exoPlayer;
    private AnimeItem item;
    private int currentEpisode = 1;
    private WatchlistManager watchlistManager;
    private ApiClient apiClient;

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
        loadMediaStream();
    }

    private void initViews() {
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

        ImageView btnBack = findViewById(R.id.btnPlayerBack);
        btnBack.setOnClickListener(v -> finish());

        btnTriggerDownload.setOnClickListener(v -> openDownloadModal());
        btnToggleWatchlist.setOnClickListener(v -> toggleWatchlist());
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

        apiClient.resolveDownloads(streamUrl, item.title, item.duration, item.type, item.id, new ApiClient.ApiCallback<List<DownloadOption>>() {
            @Override
            public void onSuccess(List<DownloadOption> result) {
                runOnUiThread(() -> {
                    loading.setVisibility(View.GONE);
                    DownloadOptionsAdapter optAdapter = new DownloadOptionsAdapter(PlayerActivity.this, option -> {
                        String dlUrl = option.directUrl != null ? option.directUrl : option.externalUrl;
                        DownloadHelper.startDownload(PlayerActivity.this, dlUrl, item.title, option.quality, option.format);
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (playerWebView != null) {
            playerWebView.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        if (playerWebView != null) {
            playerWebView.destroy();
        }
    }
}
