package com.anistream.mobile.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.anistream.mobile.R;
import com.anistream.mobile.adapters.DownloadsAdapter;
import com.anistream.mobile.models.AnimeItem;
import com.anistream.mobile.models.DownloadTask;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
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
            // Subtitles and AAC stereo sound are already integrated inside the video download
            final String finalDownloadUrl = downloadUrl;
            downloadExecutor.submit(() -> executeDownload(appContext, task, finalDownloadUrl));

        } catch (Exception e) {
            Toast.makeText(context, "Could not start download: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static void executeDownload(Context context, DownloadTask task, String url) {
        Call call = null;
        OutputStream fos = null;
        Uri mediaStoreUri = null;
        File localFallbackFile = null;

        boolean isAudio = task.isAudio;
        boolean isSubOnly = "srt".equalsIgnoreCase(task.format) || "vtt".equalsIgnoreCase(task.format);
        String mimeType = isAudio ? "audio/mpeg" : (isSubOnly ? "text/plain" : "video/mp4");

        try {
            task.status = DownloadManager.STATUS_RUNNING;
            task.statusText = "Connecting...";
            notifyProgress(task);
            updateSystemNotification(context, task);

            // Establish destination OutputStream:
            // Strategy 1 (Android 10+ / API 29+): MediaStore.Downloads (Public Downloads/AniStream, zero permission errors!)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, task.fileName);
                    values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AniStream");
                    values.put(MediaStore.Downloads.IS_PENDING, 1);

                    mediaStoreUri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (mediaStoreUri != null) {
                        fos = context.getContentResolver().openOutputStream(mediaStoreUri);
                        task.mediaUri = mediaStoreUri.toString();
                    }
                } catch (Exception msErr) {
                    android.util.Log.w("DownloadHelper", "MediaStore insert failed: " + msErr.getMessage());
                }
            }

            // Strategy 2: Standard public Downloads directory (Android 9 and below or fallback)
            if (fos == null) {
                try {
                    File pubDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AniStream");
                    if (!pubDir.exists()) pubDir.mkdirs();
                    localFallbackFile = new File(pubDir, task.fileName);
                    fos = new FileOutputStream(localFallbackFile);
                    task.localUri = localFallbackFile.getAbsolutePath();
                } catch (Exception pubErr) {
                    // Strategy 3: App external files directory (always writable!)
                    File appDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (appDir == null) appDir = context.getFilesDir();
                    File sub = new File(appDir, "AniStream");
                    if (!sub.exists()) sub.mkdirs();
                    localFallbackFile = new File(sub, task.fileName);
                    fos = new FileOutputStream(localFallbackFile);
                    task.localUri = localFallbackFile.getAbsolutePath();
                }
            }

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "AniStream-Mobile/1.0.4")
                    .build();

            call = dlClient.newCall(request);
            activeCalls.put(task.downloadId, call);

            Response response = call.execute();
            if (!response.isSuccessful()) {
                task.status = DownloadManager.STATUS_FAILED;
                task.statusText = "Server error HTTP " + response.code();
                notifyProgress(task);
                updateSystemNotification(context, task);
                if (fos != null) fos.close();
                return;
            }

            ResponseBody body = response.body();
            if (body == null) {
                task.status = DownloadManager.STATUS_FAILED;
                task.statusText = "Empty response body from server";
                notifyProgress(task);
                updateSystemNotification(context, task);
                if (fos != null) fos.close();
                return;
            }

            long contentLength = body.contentLength();
            if (contentLength > 0) {
                task.totalBytes = contentLength;
            } else if (task.expectedBytes > 0) {
                task.totalBytes = task.expectedBytes;
            }

            InputStream is = body.byteStream();
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

            // Mark completed in MediaStore or MediaScanner
            if (mediaStoreUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues completeValues = new ContentValues();
                completeValues.put(MediaStore.Downloads.IS_PENDING, 0);
                context.getContentResolver().update(mediaStoreUri, completeValues, null, null);
            } else if (localFallbackFile != null && localFallbackFile.exists()) {
                MediaScannerConnection.scanFile(context, new String[]{ localFallbackFile.getAbsolutePath() }, null, null);
            }

            task.status = DownloadManager.STATUS_SUCCESSFUL;
            task.progressPercent = 100;
            task.bytesDownloaded = downloaded;
            double mbFinal = downloaded / (1024.0 * 1024.0);
            task.statusText = String.format("Completed • %.1f MB", mbFinal);

            notifyComplete(task);
            updateSystemNotification(context, task);

        } catch (Exception e) {
            boolean isCancelled = call != null && call.isCanceled();
            task.status = DownloadManager.STATUS_FAILED;
            task.statusText = isCancelled ? "Cancelled" : ("Failed: " + e.getMessage());

            try {
                if (fos != null) fos.close();
            } catch (Exception ignored) {}

            try {
                if (mediaStoreUri != null && task.bytesDownloaded < 50000) {
                    context.getContentResolver().delete(mediaStoreUri, null, null);
                } else if (localFallbackFile != null && localFallbackFile.exists() && task.bytesDownloaded < 50000) {
                    localFallbackFile.delete();
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
            Intent intent = new Intent(Intent.ACTION_VIEW);
            String mime = task.isAudio ? "audio/*" : ("srt".equalsIgnoreCase(task.format) ? "text/plain" : "video/*");
            Uri playUri = null;

            if (task.mediaUri != null && !task.mediaUri.isEmpty()) {
                playUri = Uri.parse(task.mediaUri);
            } else if (task.localUri != null && !task.localUri.isEmpty()) {
                File f = new File(task.localUri);
                if (f.exists()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        playUri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", f);
                    } else {
                        playUri = Uri.fromFile(f);
                    }
                }
            }

            if (playUri == null) {
                File pubFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AniStream/" + task.fileName);
                if (pubFile.exists()) {
                    playUri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", pubFile);
                } else {
                    File appFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "AniStream/" + task.fileName);
                    if (appFile.exists()) {
                        playUri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", appFile);
                    }
                }
            }

            if (playUri == null) {
                Toast.makeText(context, "File not found on storage: " + task.fileName, Toast.LENGTH_SHORT).show();
                return;
            }

            // 1. Play directly in AniStream native ExoPlayer for video/audio
            if (!"srt".equalsIgnoreCase(task.format) && !"vtt".equalsIgnoreCase(task.format)) {
                try {
                    AnimeItem offlineItem = new AnimeItem();
                    offlineItem.id = String.valueOf(task.downloadId);
                    offlineItem.title = task.title;
                    offlineItem.videoUrl = playUri.toString();
                    offlineItem.type = "direct";
                    offlineItem.duration = task.quality != null ? task.quality : "Downloaded";
                    offlineItem.description = "Offline video in Downloads/AniStream/" + task.fileName;

                    Intent playerIntent = new Intent(context, com.anistream.mobile.PlayerActivity.class);
                    playerIntent.putExtra("item", offlineItem);
                    playerIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(playerIntent);
                    return;
                } catch (Exception ignored) {}
            }

            // 2. Fallback to external chooser
            intent.setDataAndType(playUri, mime);
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
