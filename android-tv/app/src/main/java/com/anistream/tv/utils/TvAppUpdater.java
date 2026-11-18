package com.anistream.tv.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class TvAppUpdater {

    public static int getAppVersionCode(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return (int) pInfo.getLongVersionCode();
            } else {
                return pInfo.versionCode;
            }
        } catch (Exception e) {
            return 1;
        }
    }

    public static String getAppVersionName(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName != null ? pInfo.versionName : "1.0.0";
        } catch (Exception e) {
            return "1.0.0";
        }
    }

    public static void checkForUpdates(Activity activity, boolean userInitiated) {
        if (activity == null || activity.isFinishing()) return;

        String serverUrl = TvApiClient.getInstance(activity).getServerUrl();
        String versionUrl = serverUrl + "/api/app/version";

        if (userInitiated) {
            Toast.makeText(activity, "Checking for TV app updates...", Toast.LENGTH_SHORT).show();
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(versionUrl)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (userInitiated && !activity.isFinishing()) {
                        Toast.makeText(activity, "Could not check for updates: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws java.io.IOException {
                if (!response.isSuccessful()) {
                    if (userInitiated) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (!activity.isFinishing()) {
                                Toast.makeText(activity, "Version check failed (HTTP " + response.code() + ")", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    return;
                }

                try {
                    String jsonStr = response.body().string();
                    JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();
                    if (!obj.has("success") || !obj.get("success").getAsBoolean()) return;

                    int serverVersionCode = obj.has("versionCode") ? obj.get("versionCode").getAsInt() : 0;
                    String serverVersionName = obj.has("versionName") ? obj.get("versionName").getAsString() : "latest";
                    String apkPath = obj.has("tvApkUrl") ? obj.get("tvApkUrl").getAsString() : "/releases/AniStream-TV-v1.0.1-release.apk";
                    String releaseNotes = obj.has("releaseNotes") ? obj.get("releaseNotes").getAsString() : "TV player performance improvements and bug fixes.";

                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (activity.isFinishing()) return;

                        int currentCode = getAppVersionCode(activity);
                        String currentName = getAppVersionName(activity);

                        if (serverVersionCode > currentCode) {
                            showUpdateDialog(activity, serverUrl, apkPath, serverVersionName, serverVersionCode, currentName, currentCode, releaseNotes);
                        } else if (userInitiated) {
                            Toast.makeText(activity, "AniStream TV is up to date! (v" + currentName + ")", Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Exception e) {
                    if (userInitiated) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (!activity.isFinishing()) {
                                Toast.makeText(activity, "Update parse error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }
        });
    }

    private static void showUpdateDialog(Activity activity, String serverUrl, String apkPath, String newVersionName, int newVersionCode, String currentName, int currentCode, String notes) {
        String fullApkUrl = apkPath.startsWith("http") ? apkPath : (serverUrl + apkPath);

        new AlertDialog.Builder(activity)
                .setTitle("🚀 TV App Update Available (v" + newVersionName + ")")
                .setMessage("A new version of AniStream TV is available!\n\n" +
                        "Current Version: v" + currentName + " (Build " + currentCode + ")\n" +
                        "New Version: v" + newVersionName + " (Build " + newVersionCode + ")\n\n" +
                        "What's New:\n" + notes)
                .setPositiveButton("Update Now", (dialog, which) -> {
                    checkPermissionAndDownload(activity, fullApkUrl, newVersionName);
                })
                .setNegativeButton("Later", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }

    private static void checkPermissionAndDownload(Activity activity, String apkUrl, String versionName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(activity, "Please allow unknown apps permission to install updates", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
                return;
            }
        }
        startApkDownload(activity, apkUrl, versionName);
    }

    private static void startApkDownload(Activity activity, String apkUrl, String versionName) {
        AlertDialog progressDialog = new AlertDialog.Builder(activity)
                .setTitle("Downloading TV Update")
                .setMessage("Downloading AniStream TV v" + versionName + "...\nPlease wait.")
                .setCancelable(false)
                .show();

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(apkUrl)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!activity.isFinishing()) {
                        progressDialog.dismiss();
                        Toast.makeText(activity, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws java.io.IOException {
                if (!response.isSuccessful()) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (!activity.isFinishing()) {
                            progressDialog.dismiss();
                            Toast.makeText(activity, "Server returned error " + response.code(), Toast.LENGTH_LONG).show();
                        }
                    });
                    return;
                }

                ResponseBody body = response.body();
                if (body == null) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (!activity.isFinishing()) {
                            progressDialog.dismiss();
                            Toast.makeText(activity, "Empty response from server", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }

                try {
                    File cacheDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (cacheDir == null) cacheDir = activity.getCacheDir();
                    File apkFile = new File(cacheDir, "AniStream-TV-v" + versionName + "-update.apk");
                    if (apkFile.exists()) apkFile.delete();

                    InputStream is = body.byteStream();
                    FileOutputStream fos = new FileOutputStream(apkFile);
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                    }
                    fos.flush();
                    fos.close();
                    is.close();

                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (!activity.isFinishing()) {
                            progressDialog.dismiss();
                            installApk(activity, apkFile);
                        }
                    });
                } catch (Exception e) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (!activity.isFinishing()) {
                            progressDialog.dismiss();
                            Toast.makeText(activity, "Failed to save update: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    public static void installApk(Context context, File apkFile) {
        try {
            if (!apkFile.exists()) {
                Toast.makeText(context, "Update file not found", Toast.LENGTH_SHORT).show();
                return;
            }

            Uri apkUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", apkFile);
            } else {
                apkUri = Uri.fromFile(apkFile);
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Could not launch package installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
