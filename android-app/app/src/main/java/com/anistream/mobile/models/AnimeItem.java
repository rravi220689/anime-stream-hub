package com.anistream.mobile.models;

import java.io.Serializable;
import java.util.List;

public class AnimeItem implements Serializable {
    public String id;
    public String title;
    public String poster;
    public String banner;
    public String format;
    public List<String> genres;
    public String duration;
    public int score;
    public int totalEpisodes;
    public String type; // "anime", "youtube", "direct"
    public String videoUrl;
    public String streamType; // "direct", "hls", "embed"
    public String description;

    public AnimeItem() {}

    public AnimeItem(String id, String title, String poster, String format, int score) {
        this.id = id;
        this.title = title;
        this.poster = poster;
        this.format = format;
        this.score = score;
    }
}
