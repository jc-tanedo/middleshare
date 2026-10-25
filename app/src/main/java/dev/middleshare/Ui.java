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
    static final int ACCENT = R.color.accent;

    static android.content.Context themedContext(android.content.Context base) {
        Settings.Theme theme = Settings.getTheme(base);
        if (theme == Settings.Theme.SYSTEM) return base;
        android.content.res.Configuration config =
                new android.content.res.Configuration(base.getResources().getConfiguration());
        int night =
                theme == Settings.Theme.DARK
                        ? android.content.res.Configuration.UI_MODE_NIGHT_YES
                        : android.content.res.Configuration.UI_MODE_NIGHT_NO;
        config.uiMode =
                (config.uiMode & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK) | night;
        return base.createConfigurationContext(config);
    }

    static int dp(Activity a, int value) {
        return Math.round(value * a.getResources().getDisplayMetrics().density);
    }

    static void layoutSheet(Activity activity) {
        android.view.Window window = activity.getWindow();
        window.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
        int width =
                Math.min(
                        activity.getResources().getDisplayMetrics().widthPixels, dp(activity, 600));
        window.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setWindowAnimations(0);
    }

    static LinearLayout screen(Activity a) {
        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(a, 20), dp(a, 24), dp(a, 20), dp(a, 24));
        scroll.addView(content);
        installScreen(a, scroll);
        return content;
    }

    static void installScreen(Activity a, View screen) {
        android.widget.FrameLayout root = new android.widget.FrameLayout(a);
        android.util.TypedValue floating = new android.util.TypedValue();
        a.getTheme().resolveAttribute(android.R.attr.windowIsFloating, floating, true);
        if (floating.data == 0) root.setBackgroundColor(a.getColor(R.color.background));
        root.addView(screen, new android.widget.FrameLayout.LayoutParams(-1, -1));
        root.setOnApplyWindowInsetsListener(
                (view, insets) -> {
                    if (Build.VERSION.SDK_INT >= 30) {
                        android.graphics.Insets bars =
                                insets.getInsets(
                                        WindowInsets.Type.systemBars()
                                                | WindowInsets.Type.displayCutout());
                        view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    } else {
                        view.setPadding(
                                insets.getSystemWindowInsetLeft(),
                                insets.getSystemWindowInsetTop(),
                                insets.getSystemWindowInsetRight(),
                                insets.getSystemWindowInsetBottom());
                    }
                    return insets;
                });
        a.setContentView(root);
        root.requestApplyInsets();
    }

    static android.widget.Button button(Activity a, int label, boolean primary) {
        android.widget.Button button = new android.widget.Button(a);
        button.setText(label);
        styleButton(a, button, primary);
        return button;
    }

    static void styleButton(Activity a, android.widget.Button button, boolean primary) {
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setMinHeight(dp(a, 56));
        button.setMinimumHeight(dp(a, 56));
        button.setMinWidth(0);
        button.setPadding(dp(a, 24), dp(a, 12), dp(a, 24), dp(a, 12));
        button.setStateListAnimator(null);
        int[][] states = {new int[] {-android.R.attr.state_enabled}, new int[] {}};
        button.setTextColor(
                new android.content.res.ColorStateList(
                        states,
                        new int[] {
                            a.getColor(R.color.on_disabled),
                            a.getColor(primary ? R.color.on_accent : R.color.on_accent_container)
                        }));
        android.graphics.drawable.StateListDrawable backgrounds =
                new android.graphics.drawable.StateListDrawable();
        backgrounds.addState(states[0], shape(a, R.color.disabled, 28));
        backgrounds.addState(
                states[1], shape(a, primary ? R.color.accent : R.color.accent_container, 28));
        button.setBackground(
                new android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(a.getColor(R.color.ripple)),
                        backgrounds,
                        shape(a, R.color.ink, 28)));
    }

    static void styleDialog(Activity a, android.app.AlertDialog dialog) {
        for (int which :
                new int[] {
                    android.app.AlertDialog.BUTTON_POSITIVE,
                    android.app.AlertDialog.BUTTON_NEGATIVE,
                    android.app.AlertDialog.BUTTON_NEUTRAL
                }) {
            android.widget.Button button = dialog.getButton(which);
            if (button != null) {
                styleButton(a, button, which == android.app.AlertDialog.BUTTON_POSITIVE);
                button.setPadding(dp(a, 16), dp(a, 8), dp(a, 16), dp(a, 8));
                button.setMinHeight(dp(a, 48));
                button.setMinimumHeight(dp(a, 48));
            }
        }
    }

    static GradientDrawable shape(Activity a, int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(a.getColor(color));
        shape.setCornerRadius(dp(a, radius));
        return shape;
    }

    static void choice(Activity a, android.widget.RadioButton button) {
        button.setTextColor(a.getColor(INK));
        button.setTextSize(15);
        button.setMinHeight(dp(a, 60));
        button.setPadding(dp(a, 12), dp(a, 10), dp(a, 16), dp(a, 10));
        android.graphics.drawable.StateListDrawable states =
                new android.graphics.drawable.StateListDrawable();
        states.addState(
                new int[] {android.R.attr.state_checked}, shape(a, R.color.accent_container, 20));
        states.addState(new int[] {}, shape(a, R.color.surface_variant, 20));
        button.setBackground(
                new android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(a.getColor(R.color.ripple)),
                        states,
                        shape(a, R.color.ink, 20)));
        android.widget.RadioGroup.LayoutParams params =
                new android.widget.RadioGroup.LayoutParams(-1, -2);
        params.bottomMargin = dp(a, 6);
        button.setLayoutParams(params);
    }

    // Framework Activity and drawables on API 28+; no AppCompat wrapper is needed.
    @android.annotation.SuppressLint("UseCompatLoadingForDrawables")
    static void field(Activity a, View field) {
        field.setMinimumHeight(dp(a, 56));
        field.setPadding(dp(a, 16), dp(a, 8), dp(a, 16), dp(a, 8));
        android.graphics.drawable.Drawable background = shape(a, R.color.surface_variant, 16);
        if (field instanceof android.widget.Spinner) {
            android.graphics.drawable.LayerDrawable layers =
                    new android.graphics.drawable.LayerDrawable(
                            new android.graphics.drawable.Drawable[] {
                                background, a.getDrawable(R.drawable.ic_chevron_down)
                            });
            layers.setLayerSize(1, dp(a, 24), dp(a, 24));
            layers.setLayerGravity(
                    1, android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
            layers.setLayerInset(1, 0, 0, dp(a, 12), 0);
            background = layers;
            field.setPadding(dp(a, 16), dp(a, 8), dp(a, 44), dp(a, 8));
        }
        field.setBackground(
                new android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(a.getColor(R.color.ripple)),
                        background,
                        shape(a, R.color.ink, 16)));
    }

    static void heading(Activity a, LinearLayout parent, String title, String subtitle) {
        parent.addView(text(a, title, 22, INK, true));
        gap(a, parent, 6);
        parent.addView(text(a, subtitle, 14, MUTED, false));
        gap(a, parent, 16);
    }

    static void handle(Activity a, LinearLayout parent) {
        View handle = new View(a);
        handle.setBackground(shape(a, R.color.outline, 3));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(a, 36), dp(a, 4));
        params.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = dp(a, 20);
        parent.addView(handle, params);
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
        bg.setCornerRadius(dp(a, 28));
        card.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(a, 12);
        parent.addView(card, params);
        return card;
    }
}
