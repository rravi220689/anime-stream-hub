package com.anistream.mobile.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.anistream.mobile.PlayerActivity;
import com.anistream.mobile.R;
import com.anistream.mobile.models.AnimeItem;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.util.ArrayList;
import java.util.List;

public class AnimeAdapter extends RecyclerView.Adapter<AnimeAdapter.ViewHolder> {
    private final Context context;
    private final List<AnimeItem> items = new ArrayList<>();

    public AnimeAdapter(Context context) {
        this.context = context;
    }

    public void setItems(List<AnimeItem> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_anime_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AnimeItem item = items.get(position);

        holder.tvTitle.setText(item.title);
        String sub = (item.format != null ? item.format : "TV");
        if (item.duration != null && !item.duration.isEmpty()) {
            sub += " • " + item.duration;
        }
        holder.tvSubInfo.setText(sub);
        holder.tvFormatBadge.setText(item.format != null ? item.format : "HD");

        if (item.score > 0) {
            holder.scoreContainer.setVisibility(View.VISIBLE);
            holder.tvScore.setText(item.score + "%");
        } else {
            holder.scoreContainer.setVisibility(View.GONE);
        }

        Glide.with(context)
                .load(item.poster)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.color.surface_card)
                .error(R.color.surface_card_stroke)
                .into(holder.ivPoster);

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, PlayerActivity.class);
            intent.putExtra("item", item);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPoster;
        TextView tvFormatBadge;
        TextView tvScore;
        View scoreContainer;
        TextView tvTitle;
        TextView tvSubInfo;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPoster = itemView.findViewById(R.id.ivPoster);
            tvFormatBadge = itemView.findViewById(R.id.tvFormatBadge);
            tvScore = itemView.findViewById(R.id.tvScore);
            scoreContainer = itemView.findViewById(R.id.scoreContainer);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvSubInfo = itemView.findViewById(R.id.tvSubInfo);
        }
    }
}
