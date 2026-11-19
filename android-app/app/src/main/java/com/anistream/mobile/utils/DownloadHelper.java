package com.anistream.mobile.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class DownloadHelper {

    private static final String CHANNEL_ID = "anistream_downloads_channel";
    private static final int NOTIFICATION_ID = 4008;

    private static final List<DownloadTask> tasks = new CopyOnWriteArrayList<>();
    private static final List<DownloadProgressListener> listeners = new CopyOnWriteArrayList<>();
    private static final ConcurrentHashMap<Long, Call> activeCalls = new ConcurrentHashMap<>();
    private static final ExecutorService downloadExecutor = Executors.newFixedThreadPool(3);

    private static final OkHttpClient dlClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

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
        for (int i = 0; i < tasks.size(); i++) {
            DownloadTask t = tasks.get(i);
            if (t.status == DownloadManager.STATUS_RUNNING || t.status == DownloadManager.STATUS_PENDING) {
                return t;
            }
        }
        return tasks.isEmpty() ? null : tasks.get(0);
    }

    public static File getDownloadDir(Context context) {
        try {
            File pubDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AniStream");
            if (!pubDir.exists()) {
                pubDir.mkdirs();
            }
            if (pubDir.exists() && pubDir.canWrite()) {
                return pubDir;
            }
        } catch (Exception ignored) {}

        File appExt = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (appExt != null) {
            File sub = new File(appExt, "AniStream");
            if (!sub.exists()) sub.mkdirs();
            return sub;
        }
        return context.getFilesDir();
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

            Context appContext = context.getApplicationContext();
            createNotificationChannel(appContext);

            String cleanTitle = title.replaceAll("[^a-zA-Z0-9_-]", "_").replaceAll("_+", "_");
            long expectedBytes = parseBytesFromSize(sizeStr);
            boolean isAudio = "mp3".equalsIgnoreCase(format);
            boolean isSubOnly = "srt".equalsIgnoreCase(format) || "vtt".equalsIgnoreCase(format);

            String fileName = isSubOnly
                    ? (cleanTitle + "." + format.toLowerCase())
                    : (cleanTitle + "_" + quality.replaceAll("[^a-zA-Z0-9]", "") + "." + format.toLowerCase());

            long downloadId = System.currentTimeMillis() + (long) (Math.random() * 1000);
            DownloadTask task = new DownloadTask(downloadId, title, quality, format, fileName, expectedBytes);
            tasks.add(0, task);
            notifyDownloadsChanged();

            Toast.makeText(appContext, (isSubOnly ? "Downloading Subtitles: " : "Starting download: ") + fileName, Toast.LENGTH_SHORT).show();

            // 1. Submit main video / audio / subtitle download to background executor
            final String finalDownloadUrl = downloadUrl;
            downloadExecutor.submit(() -> executeDownload(appContext, task, finalDownloadUrl));

            // 2. Auto-download companion English subtitle (.srt) file into the same folder
            // Guarantees external subtitle detection on all video players (Samsung Gallery, Google Photos, Files, VLC)
            if (subtitleUrl != null && !subtitleUrl.trim().isEmpty() && !isAudio && !isSubOnly) {
                try {
                    String srtUrl = subtitleUrl.startsWith("/") ? (serverUrl + subtitleUrl) : subtitleUrl;
                    String srtFileName = cleanTitle + "_" + quality.replaceAll("[^a-zA-Z0-9]", "") + ".srt";
                    long srtDlId = downloadId + 1;
                    DownloadTask srtTask = new DownloadTask(srtDlId, title + " Subtitles", "ENG SUB", "srt", srtFileName, 45000);
                    tasks.add(0, srtTask);
                    notifyDownloadsChanged();
                    downloadExecutor.submit(() -> executeDownload(appContext, srtTask, srtUrl));
                } catch (Exception subErr) {
                    android.util.Log.w("DownloadHelper", "Companion subtitle enqueue error: " + subErr.getMessage());
                }
            }

        } catch (Exception e) {
            Toast.makeText(context, "Could not start download: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static void executeDownload(Context context, DownloadTask task, String url) {
        File dir = getDownloadDir(context);
        File targetFile = new File(dir, task.fileName);
        Call call = null;

        try {
            task.status = DownloadManager.STATUS_RUNNING;
            task.statusText = "Connecting...";
            notifyProgress(task);
            updateSystemNotification(context, task);

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "AniStream-Mobile/1.0.3")
                    .build();

            call = dlClient.newCall(request);
            activeCalls.put(task.downloadId, call);

            Response response = call.execute();
            if (!response.isSuccessful()) {
                task.status = DownloadManager.STATUS_FAILED;
                task.statusText = "Server error HTTP " + response.code();
                notifyProgress(task);
                updateSystemNotification(context, task);
                return;
            }

            ResponseBody body = response.body();
            if (body == null) {
                task.status = DownloadManager.STATUS_FAILED;
                task.statusText = "Empty response body from server";
                notifyProgress(task);
                updateSystemNotification(context, task);
                return;
            }

            long contentLength = body.contentLength();
            if (contentLength > 0) {
                task.totalBytes = contentLength;
            } else if (task.expectedBytes > 0) {
                task.totalBytes = task.expectedBytes;
            }

            InputStream is = body.byteStream();
            FileOutputStream fos = new FileOutputStream(targetFile);
            byte[] buffer = new byte[32768];
            int read;
            long downloaded = 0;
            long lastNotifyTime = System.currentTimeMillis();

            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
                downloaded += read;
                task.bytesDownloaded = downloaded;

                long now = System.currentTimeMillis();
                if (now - lastNotifyTime >= 500) {
                    lastNotifyTime = now;
                    if (task.totalBytes > 0) {
                        int percent = (int) ((downloaded * 100) / task.totalBytes);
                        task.progressPercent = Math.min(100, Math.max(0, percent));
                        double mbDown = downloaded / (1024.0 * 1024.0);
                        double mbTotal = task.totalBytes / (1024.0 * 1024.0);
                        task.statusText = String.format("%.1f MB / %.1f MB (%d%%)", mbDown, mbTotal, task.progressPercent);
                    } else {
                        double mbDown = downloaded / (1024.0 * 1024.0);
                        task.statusText = String.format("%.1f MB downloaded", mbDown);
                    }
                    notifyProgress(task);
                    updateSystemNotification(context, task);
                }
            }

            fos.flush();
            fos.close();
            is.close();

            task.status = DownloadManager.STATUS_SUCCESSFUL;
            task.progressPercent = 100;
            task.bytesDownloaded = downloaded;
            task.localUri = targetFile.getAbsolutePath();
            double mbFinal = downloaded / (1024.0 * 1024.0);
            task.statusText = String.format("Completed • %.1f MB", mbFinal);

            notifyComplete(task);
            updateSystemNotification(context, task);

            // Trigger system media scanner so the video/audio appears immediately in Gallery and Media Players
            MediaScannerConnection.scanFile(context, new String[]{ targetFile.getAbsolutePath() }, null, null);

        } catch (Exception e) {
            boolean isCancelled = call != null && call.isCanceled();
            task.status = DownloadManager.STATUS_FAILED;
            task.statusText = isCancelled ? "Cancelled" : ("Failed: " + e.getMessage());
            try {
                if (targetFile.exists() && task.bytesDownloaded < 50000) {
                    targetFile.delete();
                }
            } catch (Exception ignored) {}
            notifyProgress(task);
            updateSystemNotification(context, task);
        } finally {
            activeCalls.remove(task.downloadId);
        }
    }

    public static void cancelDownload(Context context, long downloadId) {
        try {
            Call call = activeCalls.remove(downloadId);
            if (call != null) {
                call.cancel();
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
            File dir = getDownloadDir(context);
            File file = new File(dir, task.fileName);
            if (!file.exists()) {
                file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AniStream/" + task.fileName);
            }
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
            } else if (task.status == DownloadManager.STATUS_FAILED) {
                builder.setSmallIcon(android.R.drawable.stat_notify_error)
                        .setContentText("❌ " + task.statusText)
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
