package com.oriondev.moneywallet.utils;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;

import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;

public class CardBackgroundHelper {

    public enum Position {
        TOP,
        BOTTOM,
        MIDDLE,
        SINGLE
    }

    public static void applyCardBackground(View view, Position position, boolean isHeader, boolean hasRipple) {
        Context context = view.getContext();
        float radius = 18f * context.getResources().getDisplayMetrics().density;
        
        int backgroundColor;
        if (isHeader) {
            int primary = ThemeEngine.getTheme().getColorPrimary();
            backgroundColor = android.graphics.Color.argb(26, android.graphics.Color.red(primary), android.graphics.Color.green(primary), android.graphics.Color.blue(primary));
        } else {
            backgroundColor = ThemeEngine.getTheme().getColorCardBackground();
        }

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setColor(backgroundColor);

        switch (position) {
            case TOP:
                shape.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
                break;
            case BOTTOM:
                shape.setCornerRadii(new float[]{0, 0, 0, 0, radius, radius, radius, radius});
                break;
            case MIDDLE:
                shape.setCornerRadius(0);
                break;
            case SINGLE:
                shape.setCornerRadius(radius);
                break;
        }

        if (hasRipple) {
            StateListDrawable selected = new StateListDrawable();
            selected.addState(new int[]{android.R.attr.state_activated}, new ColorDrawable(ThemeEngine.getTheme().getColorRipple()));
            
            // Getting default ripple from android attribute
            android.util.TypedValue outValue = new android.util.TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            Drawable ripple = androidx.core.content.ContextCompat.getDrawable(context, outValue.resourceId);
            
            view.setBackground(new LayerDrawable(new Drawable[]{shape, selected, ripple}));
        } else {
            view.setBackground(shape);
        }
    }
}
