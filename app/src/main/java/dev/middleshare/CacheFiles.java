package dev.middleshare;

import java.io.File;

/** Best-effort cleanup of app-owned temporary files, independent of video runtime availability. */
final class CacheFiles {
    private CacheFiles() {}

    static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
