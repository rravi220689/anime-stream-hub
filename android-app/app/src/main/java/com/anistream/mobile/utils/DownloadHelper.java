package com.anistream.mobile.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.anistream.mobile.R;
import com.anistream.mobile.adapters.DownloadsAdapter;
import com.anistream.mobile.models.DownloadTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DownloadHelper {

    private static final String CHANNEL_ID = "anistream_downloads_channel";
    private static final int NOTIFICATION_ID = 4008;

    private static final List<DownloadTask> tasks = new CopyOnWriteArrayList<>();
    private static final List<DownloadProgressListener> listeners = new CopyOnWriteArrayList<>();
    private static Handler pollHandler = null;
    private static boolean isPolling = false;

    public interface DownloadProgressListener {
        void onProgressUpdate(DownloadTask task);
        void onDownloadComplete(DownloadTask task);
        void onDownloadsChanged();
    }

    public static void addListener(DownloadProgressListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void removeListener(DownloadProgressListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public static List<DownloadTask> getTasks() {
        return Collections.unmodifiableList(tasks);
    }

    public static int getActiveTaskCount() {
        int count = 0;
        for (DownloadTask t : tasks) {
            if (t.status == DownloadManager.STATUS_RUNNING || t.status == DownloadManager.STATUS_PENDING) {
                count++;
            }
        }
        return count;
    }

    public static DownloadTask getLatestActiveTask() {
        for (int i = tasks.size() - 1; i >= 0; i--) {
            DownloadTask t = tasks.get(i);
            if (t.status == DownloadManager.STATUS_RUNNING || t.status == DownloadManager.STATUS_PENDING) {
                return t;
            }
        }
        return tasks.isEmpty() ? null : tasks.get(tasks.size() - 1);
    }

    public static void startDownload(Context context, String downloadUrl, String title, String quality, String format) {
        startDownload(context, downloadUrl, null, title, quality, format, null);
    }

    public static void startDownload(Context context, String downloadUrl, String subtitleUrl, String title, String quality, String format) {
        startDownload(context, downloadUrl, subtitleUrl, title, quality, format, null);
    }

    public static void startDownload(Context context, String downloadUrl, String subtitleUrl, String title, String quality, String format, String sizeStr) {
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
            long expectedBytes = parseBytesFromSize(sizeStr);

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
                long subId = manager.enqueue(subRequest);
                DownloadTask subTask = new DownloadTask(subId, title + " Subtitles", "ENG SUB", format, subFileName, 45000);
                tasks.add(0, subTask);
                notifyDownloadsChanged();
                startPolling(context);
                Toast.makeText(context, "Downloading English Subtitles: " + subFileName, Toast.LENGTH_LONG).show();
                return;
            }

            // Case 2: Video or Audio download
            Uri uri = Uri.parse(downloadUrl);
            DownloadManager.Request request = new DownloadManager.Request(uri);

            String fileName = cleanTitle + "_" + quality.replaceAll("[^a-zA-Z0-9]", "") + "." + format.toLowerCase();

            boolean isAudio = "mp3".equalsIgnoreCase(format);
            request.setTitle(title + " (" + quality + ")");
            request.setDescription(isAudio ? "Downloading High Quality MP3 Audio" : ("Downloading " + quality + " " + format.toUpperCase()));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            // Mime type
            if (isAudio) {
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

            long downloadId = manager.enqueue(request);

            DownloadTask task = new DownloadTask(downloadId, title, quality, format, fileName, expectedBytes);
            tasks.add(0, task);

            // Auto-download companion English subtitle (.srt) file into the same AniStream folder
            // Ensures 100% visibility on all Android video players (Samsung Gallery, Google Photos, Files, VLC)
            if (subtitleUrl != null && !subtitleUrl.trim().isEmpty() && !isAudio && !"srt".equalsIgnoreCase(format)) {
                try {
                    String srtUrl = subtitleUrl.startsWith("/") ? (serverUrl + subtitleUrl) : subtitleUrl;
                    String srtFileName = cleanTitle + "_" + quality.replaceAll("[^a-zA-Z0-9]", "") + ".srt";
                    Uri srtUri = Uri.parse(srtUrl);
                    DownloadManager.Request srtReq = new DownloadManager.Request(srtUri);
                    srtReq.setTitle(title + " English Subtitles");
                    srtReq.setDescription("Companion subtitles for " + quality);
                    srtReq.setMimeType("application/x-subrip");
                    srtReq.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    try {
                        srtReq.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AniStream/" + srtFileName);
                    } catch (Exception e) {
                        srtReq.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, srtFileName);
                    }
                    long srtDlId = manager.enqueue(srtReq);
                    DownloadTask srtTask = new DownloadTask(srtDlId, title + " Subtitles", "ENG SUB", "srt", srtFileName, 45000);
                    tasks.add(0, srtTask);
                } catch (Exception subErr) {
                    android.util.Log.w("DownloadHelper", "Companion sub enqueue error: " + subErr.getMessage());
                }
            }

            notifyDownloadsChanged();
            startPolling(context);

            String msg = isAudio ? ("Downloading Audio: " + fileName) : ("Downloading Video (Subtitles Included): " + fileName);
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(context, "Download error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static void cancelDownload(Context context, long downloadId) {
        try {
            DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.remove(downloadId);
            }
            for (DownloadTask t : tasks) {
                if (t.downloadId == downloadId) {
                    t.status = DownloadManager.STATUS_FAILED;
                    t.statusText = "Cancelled";
                    break;
                }
            }
            notifyDownloadsChanged();
        } catch (Exception ignored) {}
    }

    public static void openDownloadedFile(Context context, DownloadTask task) {
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AniStream");
            File file = new File(dir, task.fileName);
            if (!file.exists()) {
                file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), task.fileName);
            }

            if (!file.exists()) {
                Toast.makeText(context, "File not found on storage: " + task.fileName, Toast.LENGTH_SHORT).show();
                return;
            }

            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
            } else {
                uri = Uri.fromFile(file);
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            String mime = task.isAudio ? "audio/*" : ("srt".equalsIgnoreCase(task.format) ? "text/plain" : "video/*");
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(Intent.createChooser(intent, "Open with"));
        } catch (Exception e) {
            Toast.makeText(context, "Could not open file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static void showDownloadsDialog(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_downloads, null);
        RecyclerView rv = dialogView.findViewById(R.id.rvDownloadsList);
        TextView tvEmpty = dialogView.findViewById(R.id.tvEmptyDownloads);
        View btnClose = dialogView.findViewById(R.id.btnCloseDownloadsDialog);

        DownloadsAdapter adapter = new DownloadsAdapter(activity);
        rv.setAdapter(adapter);

        List<DownloadTask> currentList = new ArrayList<>(tasks);
        adapter.setTasks(currentList);
        tvEmpty.setVisibility(currentList.isEmpty() ? View.VISIBLE : View.GONE);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());

        DownloadProgressListener listener = new DownloadProgressListener() {
            @Override
            public void onProgressUpdate(DownloadTask task) {
                activity.runOnUiThread(() -> adapter.notifyDataSetChanged());
            }

            @Override
            public void onDownloadComplete(DownloadTask task) {
                activity.runOnUiThread(() -> adapter.notifyDataSetChanged());
            }

            @Override
            public void onDownloadsChanged() {
                activity.runOnUiThread(() -> {
                    List<DownloadTask> updated = new ArrayList<>(tasks);
                    adapter.setTasks(updated);
                    tvEmpty.setVisibility(updated.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }
        };

        addListener(listener);
        dialog.setOnDismissListener(d -> removeListener(listener));
        dialog.show();
    }

    private static synchronized void startPolling(Context context) {
        if (isPolling) return;
        isPolling = true;

        if (pollHandler == null) {
            pollHandler = new Handler(Looper.getMainLooper());
        }

        createNotificationChannel(context);

        pollHandler.post(new Runnable() {
            @Override
            public void run() {
                Context appContext = context.getApplicationContext();
                DownloadManager manager = (DownloadManager) appContext.getSystemService(Context.DOWNLOAD_SERVICE);
                if (manager == null) {
                    isPolling = false;
                    return;
                }

                boolean anyActive = false;

                for (DownloadTask task : tasks) {
                    if (task.status == DownloadManager.STATUS_SUCCESSFUL || task.status == DownloadManager.STATUS_FAILED) {
                        continue;
                    }

                    DownloadManager.Query q = new DownloadManager.Query();
                    q.setFilterById(task.downloadId);
                    Cursor cursor = null;
                    try {
                        cursor = manager.query(q);
                        if (cursor != null && cursor.moveToFirst()) {
                            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                            int downloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                            int totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);

                            if (statusIndex != -1) task.status = cursor.getInt(statusIndex);
                            long downloaded = (downloadedIndex != -1) ? cursor.getLong(downloadedIndex) : 0;
                            long total = (totalIndex != -1) ? cursor.getLong(totalIndex) : -1;

                            task.bytesDownloaded = downloaded;
                            if (total > 0) {
                                task.totalBytes = total;
                            } else if (task.expectedBytes > 0) {
                                task.totalBytes = task.expectedBytes;
                            }

                            if (task.totalBytes > 0) {
                                int percent = (int) ((downloaded * 100) / task.totalBytes);
                                task.progressPercent = Math.min(100, Math.max(0, percent));
                            }

                            double mbDown = downloaded / (1024.0 * 1024.0);
                            double mbTotal = task.totalBytes / (1024.0 * 1024.0);

                            if (task.status == DownloadManager.STATUS_SUCCESSFUL) {
                                task.progressPercent = 100;
                                task.statusText = String.format("Completed • %.1f MB", mbDown > 0 ? mbDown : mbTotal);
                                notifyComplete(task);
                                updateSystemNotification(appContext, task);
                            } else if (task.status == DownloadManager.STATUS_FAILED) {
                                task.statusText = "Download Failed";
                                notifyProgress(task);
                            } else if (task.status == DownloadManager.STATUS_RUNNING || task.status == DownloadManager.STATUS_PENDING) {
                                anyActive = true;
                                if (mbTotal > 0) {
                                    task.statusText = String.format("%.1f MB / %.1f MB (%d%%)", mbDown, mbTotal, task.progressPercent);
                                } else {
                                    task.statusText = String.format("%.1f MB downloaded", mbDown);
                                }
                                notifyProgress(task);
                                updateSystemNotification(appContext, task);
                            }
                        }
                    } catch (Exception ignored) {
                    } finally {
                        if (cursor != null) cursor.close();
                    }
                }

                if (anyActive) {
                    pollHandler.postDelayed(this, 800);
                } else {
                    isPolling = false;
                }
            }
        });
    }

    private static void notifyProgress(DownloadTask task) {
        new Handler(Looper.getMainLooper()).post(() -> {
            for (DownloadProgressListener l : listeners) {
                l.onProgressUpdate(task);
            }
        });
    }

    private static void notifyComplete(DownloadTask task) {
        new Handler(Looper.getMainLooper()).post(() -> {
            for (DownloadProgressListener l : listeners) {
                l.onDownloadComplete(task);
            }
        });
    }

    private static void notifyDownloadsChanged() {
        new Handler(Looper.getMainLooper()).post(() -> {
            for (DownloadProgressListener l : listeners) {
                l.onDownloadsChanged();
            }
        });
    }

    private static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "AniStream Downloads",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows live video and audio download completion progress");
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private static void updateSystemNotification(Context context, DownloadTask task) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(task.title + " (" + task.quality + ")")
                    .setOngoing(task.status != DownloadManager.STATUS_SUCCESSFUL && task.status != DownloadManager.STATUS_FAILED)
                    .setOnlyAlertOnce(true);

            if (task.status == DownloadManager.STATUS_SUCCESSFUL) {
                builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentText("✅ Download Complete (" + task.statusText + ")")
                        .setProgress(0, 0, false);
            } else {
                builder.setContentText(task.statusText)
                        .setProgress(100, task.progressPercent, false);
            }

            nm.notify(NOTIFICATION_ID, builder.build());
        } catch (Exception ignored) {}
    }

    private static long parseBytesFromSize(String sizeStr) {
        if (sizeStr == null || sizeStr.trim().isEmpty()) {
            return 347000000L; // default ~330 MB
        }
        try {
            String clean = sizeStr.replaceAll("[^0-9.]", "").trim();
            double val = Double.parseDouble(clean);
            String lower = sizeStr.toLowerCase();
            if (lower.contains("gb")) {
                return (long) (val * 1024L * 1024L * 1024L);
            } else if (lower.contains("kb")) {
                return (long) (val * 1024L);
            } else {
                // Default to MB
                return (long) (val * 1024L * 1024L);
            }
        } catch (Exception e) {
            return 347000000L;
        }
    }
}
