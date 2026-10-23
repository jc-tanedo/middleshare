package dev.middleshare;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Updates the wrapper's extracted zipapp from the verified payload shipped in this APK. */
final class BundledYtDlp {
    static final String VERSION = "2026.10.21";
    static final String SHA256 = "b7af77f686e95f7845ce8d09d21957718febb8b48b30555adb9dd2292ad7bf14";
    static synchronized void install(Context context) throws IOException {
        File target = new File(context.getNoBackupFilesDir(), "youtubedl-android/yt-dlp/yt-dlp");
        if (target.isFile()) {
            try (InputStream input = new FileInputStream(target)) {
                if (SHA256.equals(digest(input))) return;
            }
        }
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot prepare the video extractor.");
        File temp = File.createTempFile("yt-dlp-", ".tmp", parent);
        try {
            try (InputStream input = context.getResources().openRawResource(R.raw.ytdlp);
                 FileOutputStream output = new FileOutputStream(temp)) {
                byte[] buffer = new byte[64 * 1024];
                int length;
                while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
            }
            try (InputStream input = new FileInputStream(temp)) {
                if (!SHA256.equals(digest(input))) throw new IOException("The bundled video extractor failed its integrity check.");
            }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally { temp.delete(); }
    }
    static String digest(InputStream input) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int length;
            while ((length = input.read(buffer)) != -1) digest.update(buffer, 0, length);
            StringBuilder hash = new StringBuilder();
            for (byte value : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            return hash.toString();
        } catch (NoSuchAlgorithmException e) { throw new IOException("SHA-256 is unavailable.", e); }
    }
}
