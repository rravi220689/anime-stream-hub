package com.anistream.tv.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.anistream.tv.R;
import com.anistream.tv.utils.TvFocusHelper;

public class TvEpisodeAdapter extends RecyclerView.Adapter<TvEpisodeAdapter.ViewHolder> {
    private final Context context;
    private final int totalEpisodes;
    private int selectedEpisode = 1;
    private final OnEpisodeClickListener listener;

    public interface OnEpisodeClickListener {
        void onEpisodeClicked(int episode);
    }

    public TvEpisodeAdapter(Context context, int totalEpisodes, OnEpisodeClickListener listener) {
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
        View view = LayoutInflater.from(context).inflate(R.layout.item_tv_card, parent, false);
        // Style as horizontal episode tile
        view.setLayoutParams(new ViewGroup.LayoutParams(
                (int) (120 * context.getResources().getDisplayMetrics().density),
                (int) (54 * context.getResources().getDisplayMetrics().density)
        ));
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int ep = position + 1;
        holder.tvEpisode.setText("EP " + ep);
        TvFocusHelper.applyTvFocusAnimation(holder.itemView);

        if (ep == selectedEpisode) {
            holder.tvEpisode.setTextColor(ContextCompat.getColor(context, R.color.tv_focus_cyan));
        } else {
            holder.tvEpisode.setTextColor(ContextCompat.getColor(context, R.color.tv_text_primary));
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
        TextView tvEpisode;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEpisode = itemView.findViewById(R.id.tvTvTitle);
            View poster = itemView.findViewById(R.id.ivTvPoster);
            if (poster != null) poster.setVisibility(View.GONE);
            View badge = itemView.findViewById(R.id.tvTvFormatBadge);
            if (badge != null) badge.setVisibility(View.GONE);
            View score = itemView.findViewById(R.id.tvTvScore);
            if (score != null) score.setVisibility(View.GONE);
        }
    }
}
