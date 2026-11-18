package com.anistream.mobile.adapters;

import android.app.DownloadManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.anistream.mobile.R;
import com.anistream.mobile.models.DownloadTask;
import com.anistream.mobile.utils.DownloadHelper;

import java.util.ArrayList;
import java.util.List;

public class DownloadsAdapter extends RecyclerView.Adapter<DownloadsAdapter.ViewHolder> {

    private final Context context;
    private final List<DownloadTask> tasks = new ArrayList<>();

    public DownloadsAdapter(Context context) {
        this.context = context;
    }

    public void setTasks(List<DownloadTask> newTasks) {
        this.tasks.clear();
        if (newTasks != null) {
            this.tasks.addAll(newTasks);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_download_task, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DownloadTask task = tasks.get(position);

        holder.tvTaskTitle.setText(task.title);
        holder.tvTaskBadge.setText(task.quality != null ? task.quality : (task.isAudio ? "MP3 Audio" : "Video"));
        holder.tvTaskFileName.setText(task.fileName);

        boolean isCompleted = (task.status == DownloadManager.STATUS_SUCCESSFUL);
        boolean isFailed = (task.status == DownloadManager.STATUS_FAILED);

        holder.pbTaskProgress.setProgress(task.progressPercent);

        if (isCompleted) {
            holder.pbTaskProgress.setProgress(100);
            holder.pbTaskProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF00FF88));
            holder.tvTaskProgressText.setTextColor(0xFF00FF88);
            holder.tvTaskProgressText.setText("✅ " + task.statusText);
            holder.btnTaskOpen.setVisibility(View.VISIBLE);
            holder.btnTaskCancel.setVisibility(View.GONE);
            holder.btnTaskOpen.setOnClickListener(v -> DownloadHelper.openDownloadedFile(context, task));
        } else if (isFailed) {
            holder.pbTaskProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFFF4D4D));
            holder.tvTaskProgressText.setTextColor(0xFFFF4D4D);
            holder.tvTaskProgressText.setText("❌ " + task.statusText);
            holder.btnTaskOpen.setVisibility(View.GONE);
            holder.btnTaskCancel.setVisibility(View.GONE);
        } else {
            holder.pbTaskProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF00F0FF));
            holder.tvTaskProgressText.setTextColor(0xFF00F0FF);
            holder.tvTaskProgressText.setText("⏳ " + task.statusText);
            holder.btnTaskOpen.setVisibility(View.GONE);
            holder.btnTaskCancel.setVisibility(View.VISIBLE);
            holder.btnTaskCancel.setOnClickListener(v -> {
                DownloadHelper.cancelDownload(context, task.downloadId);
                notifyItemChanged(holder.getAdapterPosition());
            });
        }
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTaskTitle;
        TextView tvTaskBadge;
        TextView tvTaskFileName;
        ProgressBar pbTaskProgress;
        TextView tvTaskProgressText;
        Button btnTaskOpen;
        Button btnTaskCancel;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTaskTitle = itemView.findViewById(R.id.tvTaskTitle);
            tvTaskBadge = itemView.findViewById(R.id.tvTaskBadge);
            tvTaskFileName = itemView.findViewById(R.id.tvTaskFileName);
            pbTaskProgress = itemView.findViewById(R.id.pbTaskProgress);
            tvTaskProgressText = itemView.findViewById(R.id.tvTaskProgressText);
            btnTaskOpen = itemView.findViewById(R.id.btnTaskOpen);
            btnTaskCancel = itemView.findViewById(R.id.btnTaskCancel);
        }
    }
}
