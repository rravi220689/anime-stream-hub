package com.anistream.mobile.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.anistream.mobile.R;
import com.anistream.mobile.models.DownloadOption;

import java.util.ArrayList;
import java.util.List;

public class DownloadOptionsAdapter extends RecyclerView.Adapter<DownloadOptionsAdapter.ViewHolder> {
    private final Context context;
    private final List<DownloadOption> options = new ArrayList<>();
    private final OnDownloadClickListener listener;

    public interface OnDownloadClickListener {
        void onDownloadClicked(DownloadOption option);
    }

    public DownloadOptionsAdapter(Context context, OnDownloadClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setOptions(List<DownloadOption> newOptions) {
        this.options.clear();
        if (newOptions != null) {
            this.options.addAll(newOptions);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_download_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DownloadOption opt = options.get(position);

        holder.tvDlBadge.setText(opt.badge != null ? opt.badge : opt.quality);
        String name = opt.name != null ? opt.name : (opt.quality + " • " + opt.format.toUpperCase());
        if (opt.recommended) {
            name += " ⭐ BEST";
        }
        holder.tvDlName.setText(name);
        holder.tvDlSize.setText("Est. Size: " + (opt.size != null ? opt.size : "Variable"));

        holder.btnRowDownload.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDownloadClicked(opt);
            }
        });
    }

    @Override
    public int getItemCount() {
        return options.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDlBadge;
        TextView tvDlName;
        TextView tvDlSize;
        Button btnRowDownload;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDlBadge = itemView.findViewById(R.id.tvDlBadge);
            tvDlName = itemView.findViewById(R.id.tvDlName);
            tvDlSize = itemView.findViewById(R.id.tvDlSize);
            btnRowDownload = itemView.findViewById(R.id.btnRowDownload);
        }
    }
}
