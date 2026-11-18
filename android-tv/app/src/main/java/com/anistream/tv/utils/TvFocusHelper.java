package com.anistream.tv.utils;

import android.view.View;

public class TvFocusHelper {

    public static void applyTvFocusAnimation(View view) {
        if (view == null) return;
        view.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? 1.08f : 1.0f;
            float elevation = hasFocus ? 16f : 2f;
            v.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .translationZ(elevation)
                    .setDuration(150)
                    .start();
        });
    }
}
