package com.anistream.mobile.models;

public class DownloadTask {
    public long downloadId;
    public String title;
    public String quality;
    public String format;
    public String fileName;
    public long expectedBytes;
    public long bytesDownloaded;
    public long totalBytes;
    public int progressPercent;
    public int status; // android.app.DownloadManager STATUS_*
    public String statusText;
    public boolean isAudio;
    public String localUri;
    public long timestamp;

    public DownloadTask(long downloadId, String title, String quality, String format, String fileName, long expectedBytes) {
        this.downloadId = downloadId;
        this.title = title;
        this.quality = quality;
        this.format = format != null ? format.toLowerCase() : "mp4";
        this.fileName = fileName;
        this.expectedBytes = expectedBytes;
        this.bytesDownloaded = 0;
        this.totalBytes = expectedBytes;
        this.progressPercent = 0;
        this.status = 1; // STATUS_PENDING
        this.statusText = "Starting download...";
        this.isAudio = "mp3".equalsIgnoreCase(format);
        this.timestamp = System.currentTimeMillis();
    }
}
