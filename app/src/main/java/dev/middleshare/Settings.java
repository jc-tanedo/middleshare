package dev.middleshare;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.IDN;
import java.net.URI;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        return VideoQuality.parse(prefs(context).getString("video_quality", VideoQuality.BEST.name()));
    }
    static String videoHost(String link) {
        try {
            String host = URI.create(link).getHost();
            if (host == null) return null;
            host = IDN.toASCII(host).toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) host = host.substring(4);
            return host;
        } catch (IllegalArgumentException ignored) { return null; }
    }
    static VideoQuality getVideoQuality(Context context, String host) {
        if (host == null) return getVideoQuality(context);
        return VideoQuality.parse(prefs(context).getString("video_quality_host_" + host, getVideoQuality(context).name()));
    }
    static void setVideoQuality(Context context, String host, VideoQuality quality) {
        if (host == null) return;
        prefs(context).edit().putString("video_quality_host_" + host, quality.name()).apply();
    }
    static List<String> videoQualityHosts(Context context) {
        List<String> hosts = new ArrayList<>();
        for (String key : prefs(context).getAll().keySet()) {
            if (key.startsWith("video_quality_host_")) hosts.add(key.substring("video_quality_host_".length()));
        }
        Collections.sort(hosts);
        return hosts;
    }
    static void clearVideoQuality(Context context, String host) {
        if (host != null) prefs(context).edit().remove("video_quality_host_" + host).apply();
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
