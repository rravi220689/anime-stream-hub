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
import com.anistream.mobile.models.DownloadTask;
import com.anistream.mobile.utils.ApiClient;
import com.anistream.mobile.utils.AppUpdater;
import com.anistream.mobile.utils.DownloadHelper;
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
    private View animeChipScrollView;
    private ChipGroup animeChipGroup;
    private View chipScrollView;
    private ChipGroup cartoonChipGroup;

    private View paginationBar;
    private Button btnPrevPage;
    private Button btnNextPage;
    private TextView tvPaginationInfo;

    private View mainDownloadProgressCard;
    private TextView tvMainDlTitle;
    private ProgressBar pbMainDlProgress;
    private TextView tvMainDlStatusText;
    private DownloadHelper.DownloadProgressListener downloadProgressListener;

    private ApiClient apiClient;
    private WatchlistManager watchlistManager;

    private int currentTab = R.id.nav_home;
    private String currentAnimeEndpoint = "/api/anime/trending";
    private String currentCartoonCategory = "popular";
    private int currentPage = 1;
    private ApiClient.PageInfo currentPageInfo;
    private String activeSearchQuery = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        apiClient = ApiClient.getInstance(this);
        watchlistManager = new WatchlistManager(this);

        initViews();
        setupRecyclerView();
        setupListeners();
        setupDownloadListener();

        loadContentForCurrentTab();
    }

    private void setupDownloadListener() {
        downloadProgressListener = new DownloadHelper.DownloadProgressListener() {
            @Override
            public void onProgressUpdate(DownloadTask task) {
                runOnUiThread(() -> updateDownloadBanner());
            }

            @Override
            public void onDownloadComplete(DownloadTask task) {
                runOnUiThread(() -> updateDownloadBanner());
            }

            @Override
            public void onDownloadsChanged() {
                runOnUiThread(() -> updateDownloadBanner());
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (downloadProgressListener != null) {
            DownloadHelper.addListener(downloadProgressListener);
        }
        updateDownloadBanner();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (downloadProgressListener != null) {
            DownloadHelper.removeListener(downloadProgressListener);
        }
    }

    private void updateDownloadBanner() {
        if (mainDownloadProgressCard == null) return;
        DownloadTask task = DownloadHelper.getLatestActiveTask();
        if (task != null && (task.status == android.app.DownloadManager.STATUS_RUNNING || task.status == android.app.DownloadManager.STATUS_PENDING)) {
            mainDownloadProgressCard.setVisibility(View.VISIBLE);
            tvMainDlTitle.setText((task.isAudio ? "🎵 Audio: " : "🎬 Video: ") + task.title + " (" + task.quality + ")");
            pbMainDlProgress.setProgress(task.progressPercent);
            tvMainDlStatusText.setText(task.statusText);
        } else {
            mainDownloadProgressCard.setVisibility(View.GONE);
        }
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
        animeChipScrollView = findViewById(R.id.animeChipScrollView);
        animeChipGroup = findViewById(R.id.animeChipGroup);
        chipScrollView = findViewById(R.id.chipScrollView);
        cartoonChipGroup = findViewById(R.id.cartoonChipGroup);

        paginationBar = findViewById(R.id.paginationBar);
        btnPrevPage = findViewById(R.id.btnPrevPage);
        btnNextPage = findViewById(R.id.btnNextPage);
        tvPaginationInfo = findViewById(R.id.tvPaginationInfo);

        mainDownloadProgressCard = findViewById(R.id.mainDownloadProgressCard);
        tvMainDlTitle = findViewById(R.id.tvMainDlTitle);
        pbMainDlProgress = findViewById(R.id.pbMainDlProgress);
        tvMainDlStatusText = findViewById(R.id.tvMainDlStatusText);
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
            currentPage = 1;
            activeSearchQuery = null;
            searchContainer.setVisibility(View.GONE);

            // Toggle category chips
            if (animeChipScrollView != null) {
                animeChipScrollView.setVisibility(currentTab == R.id.nav_home ? View.VISIBLE : View.GONE);
            }
            if (chipScrollView != null) {
                chipScrollView.setVisibility(currentTab == R.id.nav_cartoons ? View.VISIBLE : View.GONE);
            }

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
        btnSearchExecute.setOnClickListener(v -> {
            String q = etSearch.getText().toString().trim();
            if (!q.isEmpty()) {
                activeSearchQuery = q;
                currentPage = 1;
                executeSearch();
            }
        });

        // Settings / Server IP
        ImageView btnSettings = findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(v -> showServerSettingsDialog());

        // Downloads Manager
        ImageView btnMainDownloads = findViewById(R.id.btnMainDownloads);
        if (btnMainDownloads != null) {
            btnMainDownloads.setOnClickListener(v -> DownloadHelper.showDownloadsDialog(this));
        }

        if (mainDownloadProgressCard != null) {
            mainDownloadProgressCard.setOnClickListener(v -> DownloadHelper.showDownloadsDialog(this));
        }

        View btnMainDlViewAll = findViewById(R.id.btnMainDlViewAll);
        if (btnMainDlViewAll != null) {
            btnMainDlViewAll.setOnClickListener(v -> DownloadHelper.showDownloadsDialog(this));
        }

        // Anime filter chips (Home tab)
        if (animeChipGroup != null) {
            animeChipGroup.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.chipPopular) currentAnimeEndpoint = "/api/anime/popular";
                else if (checkedId == R.id.chipLatest) currentAnimeEndpoint = "/api/anime/latest";
                else currentAnimeEndpoint = "/api/anime/trending";

                currentPage = 1;
                activeSearchQuery = null;
                loadContentForCurrentTab();
            });
        }

        // Cartoon filter chips
        cartoonChipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipTomJerry) currentCartoonCategory = "tom-jerry";
            else if (checkedId == R.id.chipLooneyTunes) currentCartoonCategory = "looney-tunes";
            else if (checkedId == R.id.chipBen10) currentCartoonCategory = "ben-10";
            else if (checkedId == R.id.chipPokemon) currentCartoonCategory = "pokemon";
            else if (checkedId == R.id.chipPopeye) currentCartoonCategory = "popeye";
            else currentCartoonCategory = "popular";

            currentPage = 1;
            activeSearchQuery = null;
            loadContentForCurrentTab();
        });

        // Pagination buttons
        btnPrevPage.setOnClickListener(v -> {
            if (currentPage > 1) {
                currentPage--;
                loadContentForCurrentTab();
            }
        });

        btnNextPage.setOnClickListener(v -> {
            if (currentPageInfo == null || currentPageInfo.hasNextPage || currentPage < currentPageInfo.lastPage) {
                currentPage++;
                loadContentForCurrentTab();
            }
        });

        tvPaginationInfo.setOnClickListener(v -> showPageJumpDialog());

        // Check for app updates silently in background
        AppUpdater.checkForUpdates(this, false);
    }

    private void loadContentForCurrentTab() {
        if (activeSearchQuery != null && !activeSearchQuery.isEmpty()) {
            executeSearch();
            return;
        }
        if (currentTab == R.id.nav_home) {
            loadAnimeList(currentAnimeEndpoint, currentPage);
        } else if (currentTab == R.id.nav_cartoons) {
            loadCartoons(currentCartoonCategory);
        } else if (currentTab == R.id.nav_movies) {
            loadAnimeList("/api/anime/movies", currentPage);
        } else if (currentTab == R.id.nav_watchlist) {
            loadWatchlist();
        }
    }

    private void loadAnimeList(String endpoint, int page) {
        showLoading(true);
        apiClient.fetchAnimeList(endpoint, page, new ApiClient.ApiCallback<ApiClient.AnimePageResult>() {
            @Override
            public void onSuccess(ApiClient.AnimePageResult result) {
                runOnUiThread(() -> {
                    showLoading(false);
                    adapter.setItems(result.items);
                    updatePaginationUi(result.pageInfo);
                    emptyState.setVisibility(result.items.isEmpty() ? View.VISIBLE : View.GONE);
                    rvMediaGrid.smoothScrollToPosition(0);
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
        if (paginationBar != null) {
            paginationBar.setVisibility(View.GONE);
        }
        apiClient.fetchCartoons(category, new ApiClient.ApiCallback<List<AnimeItem>>() {
            @Override
            public void onSuccess(List<AnimeItem> result) {
                runOnUiThread(() -> {
                    showLoading(false);
                    adapter.setItems(result);
                    emptyState.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
                    rvMediaGrid.smoothScrollToPosition(0);
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
        if (paginationBar != null) {
            paginationBar.setVisibility(View.GONE);
        }
        List<AnimeItem> items = watchlistManager.getWatchlist();
        adapter.setItems(items);
        emptyState.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        if (items.isEmpty()) {
            emptyStateText.setText("Your watchlist is empty. Save shows to view them offline!");
        }
    }

    private void executeSearch() {
        if (activeSearchQuery == null || activeSearchQuery.isEmpty()) return;

        showLoading(true);
        apiClient.search(activeSearchQuery, currentPage, new ApiClient.ApiCallback<ApiClient.AnimePageResult>() {
            @Override
            public void onSuccess(ApiClient.AnimePageResult result) {
                runOnUiThread(() -> {
                    showLoading(false);
                    adapter.setItems(result.items);
                    updatePaginationUi(result.pageInfo);
                    emptyState.setVisibility(result.items.isEmpty() ? View.VISIBLE : View.GONE);
                    if (result.items.isEmpty()) {
                        emptyStateText.setText("No results found for \"" + activeSearchQuery + "\"");
                    }
                    rvMediaGrid.smoothScrollToPosition(0);
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

    private void updatePaginationUi(ApiClient.PageInfo pageInfo) {
        if (paginationBar == null) return;
        if (pageInfo == null || currentTab == R.id.nav_watchlist) {
            paginationBar.setVisibility(View.GONE);
            return;
        }
        this.currentPageInfo = pageInfo;
        this.currentPage = pageInfo.currentPage;
        paginationBar.setVisibility(View.VISIBLE);

        int totalPages = Math.max(pageInfo.lastPage, pageInfo.currentPage);
        String infoText = "Page " + pageInfo.currentPage + (totalPages > 1 ? " of " + totalPages : "");
        if (pageInfo.total > 0) {
            infoText += " • (" + pageInfo.total + ")";
        }
        tvPaginationInfo.setText(infoText);

        btnPrevPage.setEnabled(pageInfo.currentPage > 1);
        btnPrevPage.setAlpha(pageInfo.currentPage > 1 ? 1.0f : 0.4f);

        boolean hasNext = pageInfo.hasNextPage || pageInfo.currentPage < pageInfo.lastPage;
        btnNextPage.setEnabled(hasNext);
        btnNextPage.setAlpha(hasNext ? 1.0f : 0.4f);
    }

    private void showPageJumpDialog() {
        if (currentPageInfo == null || currentPageInfo.lastPage <= 1) return;
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("1 - " + currentPageInfo.lastPage);
        input.setPadding(32, 24, 32, 24);
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF718096);

        new AlertDialog.Builder(this)
                .setTitle("Jump to Page")
                .setView(input)
                .setPositiveButton("Go", (d, w) -> {
                    String val = input.getText().toString().trim();
                    if (!val.isEmpty()) {
                        try {
                            int target = Integer.parseInt(val);
                            if (target >= 1 && target <= Math.max(currentPageInfo.lastPage, 500)) {
                                currentPage = target;
                                loadContentForCurrentTab();
                            } else {
                                Toast.makeText(this, "Page out of range", Toast.LENGTH_SHORT).show();
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
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
