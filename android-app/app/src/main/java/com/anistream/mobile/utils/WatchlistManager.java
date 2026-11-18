package com.anistream.mobile.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.anistream.mobile.models.AnimeItem;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class WatchlistManager {
    private static final String PREF_NAME = "anistream_watchlist";
    private static final String KEY_WATCHLIST = "watchlist_items";
    private final SharedPreferences prefs;
    private final Gson gson;

    public WatchlistManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    public List<AnimeItem> getWatchlist() {
        String json = prefs.getString(KEY_WATCHLIST, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<AnimeItem>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public boolean isInWatchlist(String id) {
        if (id == null) return false;
        List<AnimeItem> list = getWatchlist();
        for (AnimeItem item : list) {
            if (id.equals(item.id)) return true;
        }
        return false;
    }

    public boolean toggleWatchlist(AnimeItem item) {
        if (item == null || item.id == null) return false;
        List<AnimeItem> list = getWatchlist();
        boolean removed = false;
        for (int i = 0; i < list.size(); i++) {
            if (item.id.equals(list.get(i).id)) {
                list.remove(i);
                removed = true;
                break;
            }
        }
        if (!removed) {
            list.add(0, item);
        }
        prefs.edit().putString(KEY_WATCHLIST, gson.toJson(list)).apply();
        return !removed;
    }
}
