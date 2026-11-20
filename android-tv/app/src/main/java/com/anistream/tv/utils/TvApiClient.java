package com.anistream.tv.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.anistream.tv.models.TvAnimeItem;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TvApiClient {
    private static TvApiClient instance;
    private final OkHttpClient client;
    private final Gson gson;
    private final SharedPreferences prefs;

    private static final String PREF_NAME = "anistream_tv_prefs";
    private static final String KEY_SERVER_URL = "server_url";
    public static final String DEFAULT_SERVER_URL = "http://rravi.ddns.net:4008";

    private TvApiClient(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    public static synchronized TvApiClient getInstance(Context context) {
        if (instance == null) {
            instance = new TvApiClient(context);
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

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    public static class PageInfo {
        public int total;
        public int currentPage = 1;
        public int lastPage = 1;
        public boolean hasNextPage = false;
        public int perPage = 24;
    }

    public static class TvAnimePageResult {
        public List<TvAnimeItem> items;
        public PageInfo pageInfo;

        public TvAnimePageResult(List<TvAnimeItem> items, PageInfo pageInfo) {
            this.items = items;
            this.pageInfo = pageInfo;
        }
    }

    public void fetchAnimeList(String endpoint, ApiCallback<List<TvAnimeItem>> callback) {
        fetchAnimeList(endpoint, 1, new ApiCallback<TvAnimePageResult>() {
            @Override
            public void onSuccess(TvAnimePageResult result) {
                callback.onSuccess(result.items);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    public void fetchAnimeList(String endpoint, int page, ApiCallback<TvAnimePageResult> callback) {
        String connector = endpoint.contains("?") ? "&" : "?";
        String url = getServerUrl() + endpoint + connector + "page=" + page;
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
                    List<TvAnimeItem> items = new ArrayList<>();

                    if (root.has("data") && root.get("data").isJsonArray()) {
                        JsonArray dataArr = root.getAsJsonArray("data");
                        for (JsonElement el : dataArr) {
                            JsonObject obj = el.getAsJsonObject();
                            TvAnimeItem item = parseAnimeItem(obj);
                            if (item != null) items.add(item);
                        }
                    }

                    PageInfo pageInfo = new PageInfo();
                    pageInfo.currentPage = page;
                    if (root.has("pageInfo") && root.get("pageInfo").isJsonObject()) {
                        JsonObject pObj = root.getAsJsonObject("pageInfo");
                        if (pObj.has("total")) pageInfo.total = pObj.get("total").getAsInt();
                        if (pObj.has("currentPage")) pageInfo.currentPage = pObj.get("currentPage").getAsInt();
                        if (pObj.has("lastPage")) pageInfo.lastPage = pObj.get("lastPage").getAsInt();
                        if (pObj.has("hasNextPage")) pageInfo.hasNextPage = pObj.get("hasNextPage").getAsBoolean();
                        if (pObj.has("perPage")) pageInfo.perPage = pObj.get("perPage").getAsInt();
                    }

                    callback.onSuccess(new TvAnimePageResult(items, pageInfo));
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }

    public void fetchCartoons(String category, ApiCallback<List<TvAnimeItem>> callback) {
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
                    List<TvAnimeItem> items = new ArrayList<>();

                    if (root.has("data") && root.get("data").isJsonArray()) {
                        JsonArray dataArr = root.getAsJsonArray("data");
                        for (JsonElement el : dataArr) {
                            JsonObject obj = el.getAsJsonObject();
                            TvAnimeItem item = new TvAnimeItem();
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

    private TvAnimeItem parseAnimeItem(JsonObject obj) {
        try {
            TvAnimeItem item = new TvAnimeItem();
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
            if (obj.has("bannerImage") && !obj.get("bannerImage").isJsonNull()) {
                item.banner = obj.get("bannerImage").getAsString();
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

    public static class StreamInfo {
        public String streamUrl;
        public String subtitleUrl;
    }

    public void resolveAnimeStream(String id, int episode, ApiCallback<StreamInfo> callback) {
        String encId = "";
        try {
            encId = java.net.URLEncoder.encode(id != null ? id : "", java.nio.charset.StandardCharsets.UTF_8.name());
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
                        StreamInfo info = new StreamInfo();
                        info.streamUrl = root.get("streamUrl").getAsString();
                        if (root.has("subtitleUrl") && !root.get("subtitleUrl").isJsonNull()) {
                            info.subtitleUrl = root.get("subtitleUrl").getAsString();
                        }
                        callback.onSuccess(info);
                    } else {
                        callback.onError(new IOException("No stream URL in server response"));
                    }
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }
}
