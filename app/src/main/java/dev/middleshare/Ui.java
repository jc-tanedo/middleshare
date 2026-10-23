package dev.middleshare;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class Ui {
    static final int INK = R.color.ink;
    static final int MUTED = R.color.muted;
    static final int GREEN = R.color.accent;
    static android.content.Context themedContext(android.content.Context base) {
        Settings.Theme theme = Settings.getTheme(base);
        if (theme == Settings.Theme.SYSTEM) return base;
        android.content.res.Configuration config = new android.content.res.Configuration(base.getResources().getConfiguration());
        int night = theme == Settings.Theme.DARK ? android.content.res.Configuration.UI_MODE_NIGHT_YES
                : android.content.res.Configuration.UI_MODE_NIGHT_NO;
        config.uiMode = (config.uiMode & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK) | night;
        return base.createConfigurationContext(config);
    }
    static int dp(Activity a, int value) { return Math.round(value * a.getResources().getDisplayMetrics().density); }
    static LinearLayout screen(Activity a) {
        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(a, 24), dp(a, 28), dp(a, 24), dp(a, 28));
        scroll.addView(content);
        installScreen(a, scroll);
        return content;
    }
    static void installScreen(Activity a, View screen) {
        android.widget.FrameLayout root = new android.widget.FrameLayout(a);
        root.addView(screen, new android.widget.FrameLayout.LayoutParams(-1, -1));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        a.setContentView(root);
        root.requestApplyInsets();
    }
    static TextView text(Activity a, String value, int size, int color, boolean bold) {
        TextView text = new TextView(a);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(a.getColor(color));
        text.setLineSpacing(dp(a, 3), 1);
        if (bold) text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return text;
    }
    static void gap(Activity a, LinearLayout layout, int height) {
        View gap = new View(a);
        layout.addView(gap, new LinearLayout.LayoutParams(1, dp(a, height)));
    }
    static LinearLayout card(Activity a, LinearLayout parent) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(a, 20), dp(a, 20), dp(a, 20), dp(a, 20));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(a.getColor(R.color.surface));
        bg.setCornerRadius(dp(a, 24));
        card.setBackground(bg);
        parent.addView(card, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }
}
