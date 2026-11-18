package com.anistream.tv;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

import com.anistream.tv.adapters.TvAnimeCardAdapter;
import com.anistream.tv.models.TvAnimeItem;
import com.anistream.tv.utils.TvApiClient;
import com.anistream.tv.utils.TvAppUpdater;
import com.anistream.tv.utils.TvFocusHelper;
import com.bumptech.glide.Glide;

import java.util.List;

public class TvMainActivity extends AppCompatActivity {

    private NestedScrollView tvScrollView;
    private ImageView ivTvHeroBackdrop;
    private TextView tvHeroTitle;
    private TextView tvHeroDesc;
    private Button btnTvHeroPlay;
    private Button btnTvHeroWatchlist;
    private ProgressBar tvProgressBar;

    private Button btnTvPrevPage;
    private Button btnTvNextPage;
    private TextView tvTvPageInfo;
    private int currentTvPage = 1;
    private TvApiClient.PageInfo currentTvPageInfo;

    private RecyclerView rvTvTrending;
    private RecyclerView rvTvPopular;
    private RecyclerView rvTvCartoons;
    private RecyclerView rvTvMovies;

    private TvAnimeCardAdapter trendingAdapter;
    private TvAnimeCardAdapter popularAdapter;
    private TvAnimeCardAdapter cartoonsAdapter;
    private TvAnimeCardAdapter moviesAdapter;

    private TvApiClient apiClient;
    private TvAnimeItem heroFeaturedItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_main);

        apiClient = TvApiClient.getInstance(this);

        initViews();
        setupNavigation();
        setupRails();
        loadTvContent();

        // Check for TV updates silently in background
        TvAppUpdater.checkForUpdates(this, false);
    }

    private void initViews() {
        tvScrollView = findViewById(R.id.tvScrollView);
        ivTvHeroBackdrop = findViewById(R.id.ivTvHeroBackdrop);
        tvHeroTitle = findViewById(R.id.tvHeroTitle);
        tvHeroDesc = findViewById(R.id.tvHeroDesc);
        btnTvHeroPlay = findViewById(R.id.btnTvHeroPlay);
        btnTvHeroWatchlist = findViewById(R.id.btnTvHeroWatchlist);
        tvProgressBar = findViewById(R.id.tvProgressBar);

        btnTvPrevPage = findViewById(R.id.btnTvPrevPage);
        btnTvNextPage = findViewById(R.id.btnTvNextPage);
        tvTvPageInfo = findViewById(R.id.tvTvPageInfo);

        rvTvTrending = findViewById(R.id.rvTvTrending);
        rvTvPopular = findViewById(R.id.rvTvPopular);
        rvTvCartoons = findViewById(R.id.rvTvCartoons);
        rvTvMovies = findViewById(R.id.rvTvMovies);

        TvFocusHelper.applyTvFocusAnimation(btnTvHeroPlay);
        TvFocusHelper.applyTvFocusAnimation(btnTvHeroWatchlist);
        if (btnTvPrevPage != null) TvFocusHelper.applyTvFocusAnimation(btnTvPrevPage);
        if (btnTvNextPage != null) TvFocusHelper.applyTvFocusAnimation(btnTvNextPage);

        btnTvHeroPlay.setOnClickListener(v -> {
            if (heroFeaturedItem != null) {
                Intent intent = new Intent(this, TvPlayerActivity.class);
                intent.putExtra("item", heroFeaturedItem);
                startActivity(intent);
            }
        });

        btnTvHeroWatchlist.setOnClickListener(v -> {
            Toast.makeText(this, "Saved to Big Screen Favorites!", Toast.LENGTH_SHORT).show();
        });

        if (btnTvPrevPage != null) {
            btnTvPrevPage.setOnClickListener(v -> {
                if (currentTvPage > 1) {
                    currentTvPage--;
                    loadTvContent(currentTvPage);
                    tvScrollView.smoothScrollTo(0, rvTvTrending.getTop() - 80);
                }
            });
        }

        if (btnTvNextPage != null) {
            btnTvNextPage.setOnClickListener(v -> {
                if (currentTvPageInfo == null || currentTvPageInfo.hasNextPage || currentTvPage < currentTvPageInfo.lastPage) {
                    currentTvPage++;
                    loadTvContent(currentTvPage);
                    tvScrollView.smoothScrollTo(0, rvTvTrending.getTop() - 80);
                }
            });
        }
    }

    private void setupNavigation() {
        Button navHome = findViewById(R.id.navTvHome);
        Button navTrending = findViewById(R.id.navTvTrending);
        Button navCartoons = findViewById(R.id.navTvCartoons);
        Button navMovies = findViewById(R.id.navTvMovies);
        Button navSettings = findViewById(R.id.navTvSettings);

        TvFocusHelper.applyTvFocusAnimation(navHome);
        TvFocusHelper.applyTvFocusAnimation(navTrending);
        TvFocusHelper.applyTvFocusAnimation(navCartoons);
        TvFocusHelper.applyTvFocusAnimation(navMovies);
        TvFocusHelper.applyTvFocusAnimation(navSettings);

        navHome.setOnClickListener(v -> tvScrollView.smoothScrollTo(0, 0));
        navTrending.setOnClickListener(v -> tvScrollView.smoothScrollTo(0, rvTvTrending.getTop() - 100));
        navCartoons.setOnClickListener(v -> tvScrollView.smoothScrollTo(0, rvTvCartoons.getTop() - 100));
        navMovies.setOnClickListener(v -> tvScrollView.smoothScrollTo(0, rvTvMovies.getTop() - 100));
        navSettings.setOnClickListener(v -> showTvSettingsDialog());

        // Default focus to Play button on hero
        btnTvHeroPlay.requestFocus();
    }

    private void setupRails() {
        trendingAdapter = new TvAnimeCardAdapter(this);
        popularAdapter = new TvAnimeCardAdapter(this);
        cartoonsAdapter = new TvAnimeCardAdapter(this);
        moviesAdapter = new TvAnimeCardAdapter(this);

        rvTvTrending.setAdapter(trendingAdapter);
        rvTvPopular.setAdapter(popularAdapter);
        rvTvCartoons.setAdapter(cartoonsAdapter);
        rvTvMovies.setAdapter(moviesAdapter);
    }

    private void loadTvContent() {
        loadTvContent(1);
    }

    private void loadTvContent(int page) {
        tvProgressBar.setVisibility(View.VISIBLE);
        this.currentTvPage = page;

        // 1. Trending Anime (with page)
        apiClient.fetchAnimeList("/api/anime/trending", page, new TvApiClient.ApiCallback<TvApiClient.TvAnimePageResult>() {
            @Override
            public void onSuccess(TvApiClient.TvAnimePageResult result) {
                runOnUiThread(() -> {
                    tvProgressBar.setVisibility(View.GONE);
                    trendingAdapter.setItems(result.items);
                    updateTvPaginationUi(result.pageInfo);
                    if (!result.items.isEmpty() && heroFeaturedItem == null) {
                        updateHeroSpotlight(result.items.get(0));
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> tvProgressBar.setVisibility(View.GONE));
            }
        });

        // 2. Popular Anime (with page)
        apiClient.fetchAnimeList("/api/anime/popular", page, new TvApiClient.ApiCallback<TvApiClient.TvAnimePageResult>() {
            @Override
            public void onSuccess(TvApiClient.TvAnimePageResult result) {
                runOnUiThread(() -> popularAdapter.setItems(result.items));
            }

            @Override
            public void onError(Exception e) {}
        });

        // 3. Cartoons
        apiClient.fetchCartoons("popular", new TvApiClient.ApiCallback<List<TvAnimeItem>>() {
            @Override
            public void onSuccess(List<TvAnimeItem> result) {
                runOnUiThread(() -> cartoonsAdapter.setItems(result));
            }

            @Override
            public void onError(Exception e) {}
        });

        // 4. Feature Films (with page)
        apiClient.fetchAnimeList("/api/anime/movies", page, new TvApiClient.ApiCallback<TvApiClient.TvAnimePageResult>() {
            @Override
            public void onSuccess(TvApiClient.TvAnimePageResult result) {
                runOnUiThread(() -> moviesAdapter.setItems(result.items));
            }

            @Override
            public void onError(Exception e) {}
        });
    }

    private void updateTvPaginationUi(TvApiClient.PageInfo pageInfo) {
        if (pageInfo == null || tvTvPageInfo == null) return;
        this.currentTvPageInfo = pageInfo;
        this.currentTvPage = pageInfo.currentPage;

        int totalPages = Math.max(pageInfo.lastPage, pageInfo.currentPage);
        String infoText = "Page " + pageInfo.currentPage + (totalPages > 1 ? " of " + totalPages : "");
        if (pageInfo.total > 0) {
            infoText += " • " + pageInfo.total + " titles";
        }
        tvTvPageInfo.setText(infoText);

        if (btnTvPrevPage != null) {
            btnTvPrevPage.setEnabled(pageInfo.currentPage > 1);
            btnTvPrevPage.setAlpha(pageInfo.currentPage > 1 ? 1.0f : 0.4f);
        }

        if (btnTvNextPage != null) {
            boolean hasNext = pageInfo.hasNextPage || pageInfo.currentPage < pageInfo.lastPage;
            btnTvNextPage.setEnabled(hasNext);
            btnTvNextPage.setAlpha(hasNext ? 1.0f : 0.4f);
        }
    }

    private void updateHeroSpotlight(TvAnimeItem item) {
        this.heroFeaturedItem = item;
        tvHeroTitle.setText(item.title);
        tvHeroDesc.setText(item.description != null && !item.description.isEmpty() ? item.description : "Watch the top trending animated series in high definition on AniStream TV.");

        String bannerUrl = item.banner != null && !item.banner.isEmpty() ? item.banner : item.poster;
        Glide.with(this)
                .load(bannerUrl)
                .centerCrop()
                .placeholder(R.color.tv_card)
                .into(ivTvHeroBackdrop);
    }

    private void showTvSettingsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_tv_server, null);
        EditText etServerUrl = dialogView.findViewById(R.id.etTvServerUrl);
        Button btnPresetDefault = dialogView.findViewById(R.id.btnTvPresetDefault);
        Button btnPresetEmulator = dialogView.findViewById(R.id.btnTvPresetEmulator);
        Button btnTestServer = dialogView.findViewById(R.id.btnTvTestServer);
        TextView tvConnectionStatus = dialogView.findViewById(R.id.tvTvConnectionStatus);
        TextView tvTvAppVersionInfo = dialogView.findViewById(R.id.tvTvAppVersionInfo);
        Button btnTvCheckUpdates = dialogView.findViewById(R.id.btnTvCheckUpdates);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelTvServer);
        Button btnSave = dialogView.findViewById(R.id.btnSaveTvServer);

        TvFocusHelper.applyTvFocusAnimation(btnPresetDefault);
        TvFocusHelper.applyTvFocusAnimation(btnPresetEmulator);
        TvFocusHelper.applyTvFocusAnimation(btnTestServer);
        if (btnTvCheckUpdates != null) {
            TvFocusHelper.applyTvFocusAnimation(btnTvCheckUpdates);
            btnTvCheckUpdates.setOnClickListener(v -> TvAppUpdater.checkForUpdates(this, true));
        }
        if (tvTvAppVersionInfo != null) {
            tvTvAppVersionInfo.setText("v" + TvAppUpdater.getAppVersionName(this) + " (Build " + TvAppUpdater.getAppVersionCode(this) + ")");
        }
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
                loadTvContent();
            }
        });

        dialog.show();
    }
}
