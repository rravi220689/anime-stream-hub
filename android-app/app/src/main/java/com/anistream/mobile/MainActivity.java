package com.anistream.mobile;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.anistream.mobile.adapters.AnimeAdapter;
import com.anistream.mobile.models.AnimeItem;
import com.anistream.mobile.utils.ApiClient;
import com.anistream.mobile.utils.WatchlistManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView rvMediaGrid;
    private AnimeAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private View emptyState;
    private TextView emptyStateText;
    private BottomNavigationView bottomNav;
    private View searchContainer;
    private EditText etSearch;
    private View chipScrollView;
    private ChipGroup cartoonChipGroup;

    private ApiClient apiClient;
    private WatchlistManager watchlistManager;

    private int currentTab = R.id.nav_home;
    private String currentCartoonCategory = "popular";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        apiClient = ApiClient.getInstance(this);
        watchlistManager = new WatchlistManager(this);

        initViews();
        setupRecyclerView();
        setupListeners();

        loadContentForCurrentTab();
    }

    private void initViews() {
        rvMediaGrid = findViewById(R.id.rvMediaGrid);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        progressBar = findViewById(R.id.progressBar);
        emptyState = findViewById(R.id.emptyState);
        emptyStateText = findViewById(R.id.emptyStateText);
        bottomNav = findViewById(R.id.bottomNav);
        searchContainer = findViewById(R.id.searchContainer);
        etSearch = findViewById(R.id.etSearch);
        chipScrollView = findViewById(R.id.chipScrollView);
        cartoonChipGroup = findViewById(R.id.cartoonChipGroup);
    }

    private void setupRecyclerView() {
        int spanCount = getResources().getConfiguration().screenWidthDp >= 600 ? 3 : 2;
        rvMediaGrid.setLayoutManager(new GridLayoutManager(this, spanCount));
        adapter = new AnimeAdapter(this);
        rvMediaGrid.setAdapter(adapter);
    }

    private void setupListeners() {
        swipeRefresh.setOnRefreshListener(this::loadContentForCurrentTab);

        bottomNav.setOnItemSelectedListener(item -> {
            currentTab = item.getItemId();
            searchContainer.setVisibility(View.GONE);
            chipScrollView.setVisibility(currentTab == R.id.nav_cartoons ? View.VISIBLE : View.GONE);
            loadContentForCurrentTab();
            return true;
        });

        // Search toggle
        ImageView btnSearchToggle = findViewById(R.id.btnSearchToggle);
        btnSearchToggle.setOnClickListener(v -> {
            if (searchContainer.getVisibility() == View.VISIBLE) {
                searchContainer.setVisibility(View.GONE);
            } else {
                searchContainer.setVisibility(View.VISIBLE);
                etSearch.requestFocus();
            }
        });

        Button btnSearchExecute = findViewById(R.id.btnSearchExecute);
        btnSearchExecute.setOnClickListener(v -> executeSearch());

        // Settings / Server IP
        ImageView btnSettings = findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(v -> showServerSettingsDialog());

        // Cartoon filter chips
        cartoonChipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipTomJerry) currentCartoonCategory = "tom-jerry";
            else if (checkedId == R.id.chipLooneyTunes) currentCartoonCategory = "looney-tunes";
            else if (checkedId == R.id.chipBen10) currentCartoonCategory = "ben-10";
            else if (checkedId == R.id.chipPokemon) currentCartoonCategory = "pokemon";
            else if (checkedId == R.id.chipPopeye) currentCartoonCategory = "popeye";
            else currentCartoonCategory = "popular";

            loadContentForCurrentTab();
        });
    }

    private void loadContentForCurrentTab() {
        if (currentTab == R.id.nav_home) {
            loadAnimeList("/api/anime/trending");
        } else if (currentTab == R.id.nav_cartoons) {
            loadCartoons(currentCartoonCategory);
        } else if (currentTab == R.id.nav_movies) {
            loadAnimeList("/api/anime/movies");
        } else if (currentTab == R.id.nav_watchlist) {
            loadWatchlist();
        }
    }

    private void loadAnimeList(String endpoint) {
        showLoading(true);
        apiClient.fetchAnimeList(endpoint, new ApiClient.ApiCallback<List<AnimeItem>>() {
            @Override
            public void onSuccess(List<AnimeItem> result) {
                runOnUiThread(() -> {
                    showLoading(false);
                    adapter.setItems(result);
                    emptyState.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(MainActivity.this, "Server error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    emptyState.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
                });
            }
        });
    }

    private void loadCartoons(String category) {
        showLoading(true);
        apiClient.fetchCartoons(category, new ApiClient.ApiCallback<List<AnimeItem>>() {
            @Override
            public void onSuccess(List<AnimeItem> result) {
                runOnUiThread(() -> {
                    showLoading(false);
                    adapter.setItems(result);
                    emptyState.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(MainActivity.this, "Cartoons error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void loadWatchlist() {
        showLoading(false);
        List<AnimeItem> items = watchlistManager.getWatchlist();
        adapter.setItems(items);
        emptyState.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        if (items.isEmpty()) {
            emptyStateText.setText("Your watchlist is empty. Save shows to view them offline!");
        }
    }

    private void executeSearch() {
        String q = etSearch.getText().toString().trim();
        if (q.isEmpty()) return;

        showLoading(true);
        apiClient.search(q, new ApiClient.ApiCallback<List<AnimeItem>>() {
            @Override
            public void onSuccess(List<AnimeItem> result) {
                runOnUiThread(() -> {
                    showLoading(false);
                    adapter.setItems(result);
                    emptyState.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
                    if (result.isEmpty()) {
                        emptyStateText.setText("No results found for \"" + q + "\"");
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(MainActivity.this, "Search error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showServerSettingsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_server_ip, null);
        EditText etServerUrl = dialogView.findViewById(R.id.etServerUrl);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelServer);
        Button btnSave = dialogView.findViewById(R.id.btnSaveServer);

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
                Toast.makeText(this, "Server updated to: " + newUrl, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                loadContentForCurrentTab();
            }
        });

        dialog.show();
    }

    private void showLoading(boolean loading) {
        swipeRefresh.setRefreshing(false);
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
