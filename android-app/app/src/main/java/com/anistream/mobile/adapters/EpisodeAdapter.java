package com.anistream.mobile.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.anistream.mobile.R;

public class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.ViewHolder> {
    private final Context context;
    private final int totalEpisodes;
    private int selectedEpisode = 1;
    private final OnEpisodeClickListener listener;

    public interface OnEpisodeClickListener {
        void onEpisodeClicked(int episode);
    }

    public EpisodeAdapter(Context context, int totalEpisodes, OnEpisodeClickListener listener) {
        this.context = context;
        this.totalEpisodes = Math.max(1, totalEpisodes);
        this.listener = listener;
    }

    public void setSelectedEpisode(int ep) {
        this.selectedEpisode = ep;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_episode_pill, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int ep = position + 1;
        holder.tvEpisodeNumber.setText("EP " + ep);

        if (ep == selectedEpisode) {
            holder.tvEpisodeNumber.setBackgroundResource(R.drawable.bg_pill);
            holder.tvEpisodeNumber.setTextColor(ContextCompat.getColor(context, R.color.accent_cyan));
        } else {
            holder.tvEpisodeNumber.setBackgroundResource(R.drawable.bg_card);
            holder.tvEpisodeNumber.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        }

        holder.itemView.setOnClickListener(v -> {
            setSelectedEpisode(ep);
            if (listener != null) {
                listener.onEpisodeClicked(ep);
            }
        });
    }

    @Override
    public int getItemCount() {
        return Math.min(totalEpisodes, 500);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvEpisodeNumber;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEpisodeNumber = itemView.findViewById(R.id.tvEpisodeNumber);
        }
    }
}
