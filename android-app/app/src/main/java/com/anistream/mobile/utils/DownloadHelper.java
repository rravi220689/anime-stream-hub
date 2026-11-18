package com.anistream.mobile.utils;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import java.io.File;

public class DownloadHelper {

    public static void startDownload(Context context, String downloadUrl, String title, String quality, String format) {
        try {
            if (downloadUrl.startsWith("/")) {
                String serverUrl = ApiClient.getInstance(context).getServerUrl();
                downloadUrl = serverUrl + downloadUrl;
            }

            Uri uri = Uri.parse(downloadUrl);
            DownloadManager.Request request = new DownloadManager.Request(uri);

            String cleanTitle = title.replaceAll("[^a-zA-Z0-9_-]", "_").replaceAll("_+", "_");
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

            // Save in standard public Downloads folder
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AniStream");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AniStream/" + fileName);

            DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                Toast.makeText(context, "Download started: " + fileName, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(context, "DownloadManager unavailable", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "Download error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
