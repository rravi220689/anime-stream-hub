package com.anistream.tv.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.anistream.tv.R;
import com.anistream.tv.TvPlayerActivity;
import com.anistream.tv.models.TvAnimeItem;
import com.anistream.tv.utils.TvFocusHelper;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.util.ArrayList;
import java.util.List;

public class TvAnimeCardAdapter extends RecyclerView.Adapter<TvAnimeCardAdapter.ViewHolder> {
    private final Context context;
    private final List<TvAnimeItem> items = new ArrayList<>();

    public TvAnimeCardAdapter(Context context) {
        this.context = context;
    }

    public void setItems(List<TvAnimeItem> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_tv_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TvAnimeItem item = items.get(position);

        holder.tvTvTitle.setText(item.title);
        holder.tvTvFormatBadge.setText(item.format != null ? item.format : "HD");

        if (item.score > 0) {
            holder.tvTvScore.setVisibility(View.VISIBLE);
            holder.tvTvScore.setText("★ " + item.score + "%");
        } else {
            holder.tvTvScore.setVisibility(View.GONE);
        }

        Glide.with(context)
                .load(item.poster)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.color.tv_card)
                .error(R.color.tv_card_stroke)
                .into(holder.ivTvPoster);

        TvFocusHelper.applyTvFocusAnimation(holder.itemView);

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, TvPlayerActivity.class);
            intent.putExtra("item", item);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivTvPoster;
        TextView tvTvFormatBadge;
        TextView tvTvScore;
        TextView tvTvTitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivTvPoster = itemView.findViewById(R.id.ivTvPoster);
            tvTvFormatBadge = itemView.findViewById(R.id.tvTvFormatBadge);
            tvTvScore = itemView.findViewById(R.id.tvTvScore);
            tvTvTitle = itemView.findViewById(R.id.tvTvTitle);
        }
    }
}
