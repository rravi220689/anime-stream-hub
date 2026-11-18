package com.anistream.tv.models;

import java.io.Serializable;
import java.util.List;

public class TvAnimeItem implements Serializable {
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
    public String streamType;
    public String description;

    public TvAnimeItem() {}
}
