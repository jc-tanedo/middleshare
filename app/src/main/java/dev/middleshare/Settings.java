package dev.middleshare;

import android.content.Context;
import android.content.SharedPreferences;

final class Settings {
    static final int JPEG_QUALITY = 92;
    enum Format {
        JPEG("image/jpeg", ".jpg"), PNG("image/png", ".png");
        final String mime;
        final String extension;
        Format(String mime, String extension) { this.mime = mime; this.extension = extension; }
    }
    enum Theme { SYSTEM, LIGHT, DARK }
    static VideoQuality getVideoQuality(Context context) {
        return VideoQuality.parse(prefs(context).getString("video_quality", VideoQuality.STANDARD.name()));
    }
    static void setVideoQuality(Context context, VideoQuality quality) {
        prefs(context).edit().putString("video_quality", quality.name()).apply();
    }
    static Theme getTheme(Context context) {
        try { return Theme.valueOf(prefs(context).getString("theme", Theme.SYSTEM.name())); }
        catch (IllegalArgumentException | NullPointerException ignored) { return Theme.SYSTEM; }
    }
    static void setTheme(Context context, Theme theme) {
        prefs(context).edit().putString("theme", theme.name()).apply();
    }
    static Format getFormat(Context context) {
        String value = prefs(context).getString("format", Format.JPEG.name());
        try { return Format.valueOf(value); }
        catch (IllegalArgumentException | NullPointerException ignored) { return Format.JPEG; }
    }
    static void setFormat(Context context, Format format) {
        prefs(context).edit().putString("format", format.name()).apply();
    }
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("conversion", Context.MODE_PRIVATE);
    }
}
