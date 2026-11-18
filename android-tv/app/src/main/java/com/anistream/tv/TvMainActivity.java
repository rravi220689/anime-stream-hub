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
    }

    private void initViews() {
        tvScrollView = findViewById(R.id.tvScrollView);
        ivTvHeroBackdrop = findViewById(R.id.ivTvHeroBackdrop);
        tvHeroTitle = findViewById(R.id.tvHeroTitle);
        tvHeroDesc = findViewById(R.id.tvHeroDesc);
        btnTvHeroPlay = findViewById(R.id.btnTvHeroPlay);
        btnTvHeroWatchlist = findViewById(R.id.btnTvHeroWatchlist);
        tvProgressBar = findViewById(R.id.tvProgressBar);

        rvTvTrending = findViewById(R.id.rvTvTrending);
        rvTvPopular = findViewById(R.id.rvTvPopular);
        rvTvCartoons = findViewById(R.id.rvTvCartoons);
        rvTvMovies = findViewById(R.id.rvTvMovies);

        TvFocusHelper.applyTvFocusAnimation(btnTvHeroPlay);
        TvFocusHelper.applyTvFocusAnimation(btnTvHeroWatchlist);

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
        tvProgressBar.setVisibility(View.VISIBLE);

        // 1. Trending Anime
        apiClient.fetchAnimeList("/api/anime/trending", new TvApiClient.ApiCallback<List<TvAnimeItem>>() {
            @Override
            public void onSuccess(List<TvAnimeItem> result) {
                runOnUiThread(() -> {
                    tvProgressBar.setVisibility(View.GONE);
                    trendingAdapter.setItems(result);
                    if (!result.isEmpty() && heroFeaturedItem == null) {
                        updateHeroSpotlight(result.get(0));
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> tvProgressBar.setVisibility(View.GONE));
            }
        });

        // 2. Popular Anime
        apiClient.fetchAnimeList("/api/anime/popular", new TvApiClient.ApiCallback<List<TvAnimeItem>>() {
            @Override
            public void onSuccess(List<TvAnimeItem> result) {
                runOnUiThread(() -> popularAdapter.setItems(result));
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

        // 4. Feature Films
        apiClient.fetchAnimeList("/api/anime/movies", new TvApiClient.ApiCallback<List<TvAnimeItem>>() {
            @Override
            public void onSuccess(List<TvAnimeItem> result) {
                runOnUiThread(() -> moviesAdapter.setItems(result));
            }

            @Override
            public void onError(Exception e) {}
        });
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
        Button btnCancel = dialogView.findViewById(R.id.btnCancelTvServer);
        Button btnSave = dialogView.findViewById(R.id.btnSaveTvServer);

        TvFocusHelper.applyTvFocusAnimation(btnCancel);
        TvFocusHelper.applyTvFocusAnimation(btnSave);

        etServerUrl.setText(apiClient.getServerUrl());

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
