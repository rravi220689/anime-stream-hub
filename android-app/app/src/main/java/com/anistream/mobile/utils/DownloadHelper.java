package com.anistream.mobile.utils;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import java.io.File;

public class DownloadHelper {

    public static void startDownload(Context context, String downloadUrl, String title, String quality, String format) {
        startDownload(context, downloadUrl, null, title, quality, format);
    }

    public static void startDownload(Context context, String downloadUrl, String subtitleUrl, String title, String quality, String format) {
        try {
            String serverUrl = ApiClient.getInstance(context).getServerUrl();
            if (downloadUrl != null && downloadUrl.startsWith("/")) {
                downloadUrl = serverUrl + downloadUrl;
            }

            DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) {
                Toast.makeText(context, "DownloadManager unavailable", Toast.LENGTH_SHORT).show();
                return;
            }

            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AniStream");
            try {
                if (!dir.exists()) {
                    dir.mkdirs();
                }
            } catch (Exception ignored) {}

            String cleanTitle = title.replaceAll("[^a-zA-Z0-9_-]", "_").replaceAll("_+", "_");

            // Case 1: Subtitle only download
            if ("srt".equalsIgnoreCase(format) || "vtt".equalsIgnoreCase(format)) {
                Uri subUri = Uri.parse(downloadUrl);
                DownloadManager.Request subRequest = new DownloadManager.Request(subUri);
                String subFileName = cleanTitle + "." + format.toLowerCase();
                subRequest.setTitle(title + " English Subtitles");
                subRequest.setDescription("Downloading English Subtitles (." + format.toLowerCase() + ")");
                subRequest.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                subRequest.setMimeType("application/x-subrip");
                try {
                    subRequest.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AniStream/" + subFileName);
                } catch (Exception e) {
                    subRequest.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, subFileName);
                }
                manager.enqueue(subRequest);
                Toast.makeText(context, "Downloading English Subtitles: " + subFileName, Toast.LENGTH_LONG).show();
                return;
            }

            // Case 2: Video / Audio download (+ English subtitles companion)
            Uri uri = Uri.parse(downloadUrl);
            DownloadManager.Request request = new DownloadManager.Request(uri);

            String fileName = cleanTitle + "_" + quality + "." + format;

            request.setTitle(title + " (" + quality + ")");
            request.setDescription("Downloading " + quality + " " + format.toUpperCase());
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            // Mime type
            if ("mp3".equalsIgnoreCase(format)) {
                request.setMimeType("audio/mpeg");
            } else if ("webm".equalsIgnoreCase(format)) {
                request.setMimeType("video/webm");
            } else {
                request.setMimeType("video/mp4");
            }

            try {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AniStream/" + fileName);
            } catch (Exception e) {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            }
            manager.enqueue(request);

            // Auto-download English subtitles companion with matching base filename
            boolean subQueued = false;
            if (subtitleUrl != null && !subtitleUrl.isEmpty() && !"mp3".equalsIgnoreCase(format)) {
                try {
                    String fullSubUrl = subtitleUrl.startsWith("/") ? (serverUrl + subtitleUrl) : subtitleUrl;
                    Uri subUri = Uri.parse(fullSubUrl);
                    DownloadManager.Request subRequest = new DownloadManager.Request(subUri);
                    String subFileName = cleanTitle + "_" + quality + ".srt";

                    subRequest.setTitle(title + " English Subtitles");
                    subRequest.setDescription("Companion English subtitles for " + quality);
                    subRequest.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    subRequest.setMimeType("application/x-subrip");
                    try {
                        subRequest.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AniStream/" + subFileName);
                    } catch (Exception e) {
                        subRequest.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, subFileName);
                    }
                    manager.enqueue(subRequest);
                    subQueued = true;
                } catch (Exception e) {
                    // Subtitle error non-fatal to video download
                }
            }

            if (subQueued) {
                Toast.makeText(context, "Download started: " + fileName + "\n+ English Subtitles included!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(context, "Download started: " + fileName, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "Download error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
