package com.anistream.mobile.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.anistream.mobile.models.AnimeItem;
import com.anistream.mobile.models.DownloadOption;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ApiClient {
    private static ApiClient instance;
    private final OkHttpClient client;
    private final Gson gson;
    private final SharedPreferences prefs;

    private static final String PREF_NAME = "anistream_prefs";
    private static final String KEY_SERVER_URL = "server_url";
    public static final String DEFAULT_SERVER_URL = "http://rravi.ddns.net:4008";

    private ApiClient(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    public static synchronized ApiClient getInstance(Context context) {
        if (instance == null) {
            instance = new ApiClient(context);
        }
        return instance;
    }

    public String getServerUrl() {
        return prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL);
    }

    public void setServerUrl(String url) {
        if (url != null && url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        prefs.edit().putString(KEY_SERVER_URL, url).apply();
    }

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    public static class PageInfo implements Serializable {
        public int currentPage = 1;
        public int lastPage = 1;
        public int total = 0;
        public boolean hasNextPage = false;
        public int perPage = 24;
    }

    public static class AnimePageResult {
        public List<AnimeItem> items = new ArrayList<>();
        public PageInfo pageInfo = new PageInfo();
    }

    // 1. Fetch Anime Collection with Pagination (trending, popular, latest, movies)
    public void fetchAnimeList(String endpoint, int page, ApiCallback<AnimePageResult> callback) {
        String separator = endpoint.contains("?") ? "&" : "?";
        String url = getServerUrl() + endpoint + separator + "page=" + page + "&limit=24";
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("Unexpected code " + response));
                    return;
                }
                try {
                    String jsonStr = response.body().string();
                    JsonObject root = gson.fromJson(jsonStr, JsonObject.class);
                    AnimePageResult result = new AnimePageResult();

                    if (root.has("pageInfo") && root.get("pageInfo").isJsonObject()) {
                        result.pageInfo = gson.fromJson(root.get("pageInfo"), PageInfo.class);
                    } else {
                        result.pageInfo.currentPage = page;
                    }

                    if (root.has("data") && root.get("data").isJsonArray()) {
                        JsonArray dataArr = root.getAsJsonArray("data");
                        for (JsonElement el : dataArr) {
                            JsonObject obj = el.getAsJsonObject();
                            AnimeItem item = parseAnimeItem(obj);
                            if (item != null) result.items.add(item);
                        }
                    }
                    callback.onSuccess(result);
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }

    // Overload for backwards compatibility
    public void fetchAnimeList(String endpoint, ApiCallback<List<AnimeItem>> callback) {
        fetchAnimeList(endpoint, 1, new ApiCallback<AnimePageResult>() {
            @Override
            public void onSuccess(AnimePageResult result) {
                callback.onSuccess(result.items);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    // 2. Fetch YouTube Cartoons
    public void fetchCartoons(String category, ApiCallback<List<AnimeItem>> callback) {
        String url = getServerUrl() + "/api/youtube/cartoons?category=" + category;
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("Unexpected code " + response));
                    return;
                }
                try {
                    String jsonStr = response.body().string();
                    JsonObject root = gson.fromJson(jsonStr, JsonObject.class);
                    List<AnimeItem> items = new ArrayList<>();

                    if (root.has("data") && root.get("data").isJsonArray()) {
                        JsonArray dataArr = root.getAsJsonArray("data");
                        for (JsonElement el : dataArr) {
                            JsonObject obj = el.getAsJsonObject();
                            AnimeItem item = new AnimeItem();
                            item.id = obj.has("id") ? obj.get("id").getAsString() : "";
                            item.title = obj.has("title") ? obj.get("title").getAsString() : "Cartoon";
                            item.poster = obj.has("thumbnail") ? obj.get("thumbnail").getAsString() : "";
                            item.duration = obj.has("duration") ? obj.get("duration").getAsString() : "";
                            item.format = "CARTOON";
                            item.type = "youtube";
                            item.videoUrl = "https://www.youtube.com/watch?v=" + item.id;
                            item.streamType = "embed";
                            item.description = obj.has("channel") ? "Channel: " + obj.get("channel").getAsString() : "";
                            items.add(item);
                        }
                    }
                    callback.onSuccess(items);
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }

    // 3. Search Endpoint with Pagination
    public void search(String query, int page, ApiCallback<AnimePageResult> callback) {
        String encQ = "";
        try {
            encQ = URLEncoder.encode(query != null ? query : "", StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {}
        String url = getServerUrl() + "/api/anime/search?q=" + encQ + "&page=" + page + "&limit=24";
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("Unexpected code " + response));
                    return;
                }
                try {
                    String jsonStr = response.body().string();
                    JsonObject root = gson.fromJson(jsonStr, JsonObject.class);
                    AnimePageResult result = new AnimePageResult();

                    if (root.has("pageInfo") && root.get("pageInfo").isJsonObject()) {
                        result.pageInfo = gson.fromJson(root.get("pageInfo"), PageInfo.class);
                    } else {
                        result.pageInfo.currentPage = page;
                    }

                    if (root.has("data") && root.get("data").isJsonArray()) {
                        JsonArray dataArr = root.getAsJsonArray("data");
                        for (JsonElement el : dataArr) {
                            JsonObject obj = el.getAsJsonObject();
                            AnimeItem item = parseAnimeItem(obj);
                            if (item != null) result.items.add(item);
                        }
                    }
                    callback.onSuccess(result);
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }

    public void search(String query, ApiCallback<List<AnimeItem>> callback) {
        search(query, 1, new ApiCallback<AnimePageResult>() {
            @Override
            public void onSuccess(AnimePageResult result) {
                callback.onSuccess(result.items);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    // 4. Resolve Download Formats (1080p, 720p, 480p, 360p, MP3, English Subtitles)
    public void resolveDownloads(String streamUrl, String title, String duration, String type, String id, ApiCallback<List<DownloadOption>> callback) {
        resolveDownloads(streamUrl, title, duration, type, id, 1, callback);
    }

    public void resolveDownloads(String streamUrl, String title, String duration, String type, String id, int episode, ApiCallback<List<DownloadOption>> callback) {
        String encUrl = "";
        String encTitle = "";
        String encDuration = "";
        String encType = "";
        String encId = "";
        try {
            encUrl = URLEncoder.encode(streamUrl != null ? streamUrl : "", StandardCharsets.UTF_8.name());
            encTitle = URLEncoder.encode(title != null ? title : "", StandardCharsets.UTF_8.name());
            encDuration = URLEncoder.encode(duration != null ? duration : "", StandardCharsets.UTF_8.name());
            encType = URLEncoder.encode(type != null ? type : "", StandardCharsets.UTF_8.name());
            encId = URLEncoder.encode(id != null ? id : "", StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {}

        String url = getServerUrl() + "/api/download/resolve?url=" + encUrl
                + "&title=" + encTitle
                + "&duration=" + encDuration
                + "&type=" + encType
                + "&id=" + encId
                + "&episode=" + episode;

        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("Unexpected code " + response));
                    return;
                }
                try {
                    String jsonStr = response.body().string();
                    JsonObject root = gson.fromJson(jsonStr, JsonObject.class);
                    List<DownloadOption> options = new ArrayList<>();

                    String fallbackSubtitleUrl = null;
                    if (root.has("subtitleUrl") && !root.get("subtitleUrl").isJsonNull()) {
                        fallbackSubtitleUrl = root.get("subtitleUrl").getAsString();
                    }

                    if (root.has("formats") && root.get("formats").isJsonArray()) {
                        JsonArray arr = root.getAsJsonArray("formats");
                        for (JsonElement el : arr) {
                            DownloadOption opt = gson.fromJson(el, DownloadOption.class);
                            if (opt != null) {
                                if ((opt.subtitleUrl == null || opt.subtitleUrl.isEmpty()) && fallbackSubtitleUrl != null && !"mp3".equalsIgnoreCase(opt.format)) {
                                    opt.subtitleUrl = fallbackSubtitleUrl;
                                }
                                options.add(opt);
                            }
                        }
                    }
                    callback.onSuccess(options);
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }

    // 4b. Direct Native Anime HLS Stream Resolver (Fast loading with ExoPlayer)
    public void resolveAnimeStream(String id, int episode, ApiCallback<String> callback) {
        String encId = "";
        try {
            encId = URLEncoder.encode(id != null ? id : "", StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {}

        String url = getServerUrl() + "/api/anime/stream?id=" + encId + "&episode=" + episode;
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("Server HTTP " + response.code()));
                    return;
                }
                try {
                    String json = response.body().string();
                    JsonObject root = gson.fromJson(json, JsonObject.class);
                    if (root.has("streamUrl") && !root.get("streamUrl").isJsonNull()) {
                        callback.onSuccess(root.get("streamUrl").getAsString());
                    } else {
                        callback.onError(new IOException("No stream URL in server response"));
                    }
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }

    // 5. Test Server Connection
    public void testServerConnection(String targetUrl, ApiCallback<Boolean> callback) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("URL cannot be empty"));
            return;
        }
        String cleanUrl = targetUrl.trim();
        if (cleanUrl.endsWith("/")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
        }
        String testEndpoint = cleanUrl + "/api/anime/trending?page=1";
        OkHttpClient testClient = client.newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();

        Request req = new Request.Builder().url(testEndpoint).build();
        testClient.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(new IOException("Server responded with HTTP " + response.code()));
                }
            }
        });
    }

    private AnimeItem parseAnimeItem(JsonObject obj) {
        try {
            AnimeItem item = new AnimeItem();
            item.id = obj.has("id") ? obj.get("id").getAsString() : "";

            if (obj.has("title") && obj.get("title").isJsonObject()) {
                JsonObject tObj = obj.getAsJsonObject("title");
                if (tObj.has("english") && !tObj.get("english").isJsonNull()) {
                    item.title = tObj.get("english").getAsString();
                } else if (tObj.has("romaji") && !tObj.get("romaji").isJsonNull()) {
                    item.title = tObj.get("romaji").getAsString();
                }
            }
            if (item.title == null || item.title.isEmpty()) {
                item.title = obj.has("title") ? obj.get("title").getAsString() : "Anime";
            }

            if (obj.has("coverImage") && obj.get("coverImage").isJsonObject()) {
                JsonObject cObj = obj.getAsJsonObject("coverImage");
                if (cObj.has("large") && !cObj.get("large").isJsonNull()) {
                    item.poster = cObj.get("large").getAsString();
                }
            }
            if (item.poster == null || item.poster.isEmpty()) {
                item.poster = obj.has("thumbnail") ? obj.get("thumbnail").getAsString() : "";
            }

            item.format = obj.has("format") && !obj.get("format").isJsonNull() ? obj.get("format").getAsString() : "TV";
            item.score = obj.has("averageScore") && !obj.get("averageScore").isJsonNull() ? obj.get("averageScore").getAsInt() : 0;
            item.duration = obj.has("duration") && !obj.get("duration").isJsonNull() ? obj.get("duration").getAsString() + " min" : "24 min";
            item.totalEpisodes = obj.has("episodes") && !obj.get("episodes").isJsonNull() ? obj.get("episodes").getAsInt() : 24;
            item.description = obj.has("description") && !obj.get("description").isJsonNull() ? obj.get("description").getAsString() : "";
            item.type = obj.has("type") ? obj.get("type").getAsString() : "anime";
            item.streamType = "embed";
            item.videoUrl = "https://animeembed.com/embed/ani/" + item.id + "/1?accent=6366f1";

            return item;
        } catch (Exception e) {
            return null;
        }
    }
}
