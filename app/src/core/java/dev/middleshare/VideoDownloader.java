package dev.middleshare;

import android.content.Context;

import java.io.File;
import java.io.IOException;

/** The image-only APK never loads or embeds a video runtime. */
final class VideoDownloader {
    interface Progress {
        void update(String message);
    }

    static String mimeFor(String name) {
        return null;
    }

    void cancel() {}

    SharedFile download(
            Context context,
            String url,
            VideoQuality quality,
            String customId,
            boolean needsAudio,
            Progress progress)
            throws IOException {
        throw new IOException("Install MiddleShare Video from Releases to download videos.");
    }

    static void remove(File file) {
        File[] files = file.listFiles();
        if (files != null) for (File child : files) remove(child);
        file.delete();
    }
}
