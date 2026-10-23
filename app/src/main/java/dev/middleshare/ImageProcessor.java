package dev.middleshare;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.UUID;

final class ImageProcessor {
    static final long MAX_INPUT_BYTES = 64L * 1024 * 1024;
    static final long CACHE_LIFETIME_MS = 24L * 60 * 60 * 1000;
    static SharedFile process(Context context, Uri source, Settings.Format format) throws IOException {
        if (source == null || !"content".equals(source.getScheme())) {
            throw new IOException("Share an image file from another app. Image links are not supported yet.");
        }
        File root = ShareProvider.directory(context);
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot create temporary image storage.");
        File input = File.createTempFile("incoming-", ".tmp", context.getCacheDir());
        File output = null;
        boolean complete = false;
        try {
            try (InputStream stream = context.getContentResolver().openInputStream(source);
                 FileOutputStream sink = new FileOutputStream(input)) {
                if (stream == null) throw new IOException("The sending app did not provide a readable image.");
                byte[] buffer = new byte[64 * 1024];
                long bytes = 0;
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    checkCancelled();
                    bytes += count;
                    if (bytes > MAX_INPUT_BYTES) throw new IOException("This image exceeds the 64 MB input limit.");
                    sink.write(buffer, 0, count);
                }
            }
            checkCancelled();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(input.getPath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outMimeType == null) {
                throw new IOException("This file is not a supported image, or it is damaged.");
            }
            boolean webp = isWebp(input);
            String mime = webp ? format.mime : bounds.outMimeType;
            String extension = webp ? format.extension : extensionFor(mime);
            output = new File(root, UUID.randomUUID() + extension);
            if (webp) {
                convert(input, output, format);
            } else if (!input.renameTo(output)) {
                throw new IOException("Cannot prepare the image for sharing.");
            }
            checkCancelled();
            complete = true;
            return new SharedFile(context, output, mime);
        } finally {
            input.delete();
            if (!complete && output != null) output.delete();
        }
    }
    private static String extensionFor(String mime) throws IOException {
        if ("image/jpeg".equals(mime)) return ".jpg";
        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        if (ext == null || !ext.matches("[a-z0-9]{1,10}")) throw new IOException("This image format is not supported yet.");
        return "." + ext;
    }
    static boolean isWebp(File input) throws IOException {
        byte[] header = new byte[12];
        try (FileInputStream stream = new FileInputStream(input)) {
            if (stream.read(header) != header.length) return false;
        }
        return header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
    }
    private static void convert(File input, File output, Settings.Format format) throws IOException {
        Bitmap decoded = null;
        Bitmap flattened = null;
        try {
            decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(input), (decoder, info, source) -> {
                // Budget for decoding plus a second bitmap when flattening JPEG transparency.
                long budget = Math.min(16_000_000L, Runtime.getRuntime().maxMemory() / 16);
                long pixels = (long) info.getSize().getWidth() * info.getSize().getHeight();
                if (pixels > budget) throw new ImageTooLargeException();
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB));
            });
            checkCancelled();
            Bitmap result = decoded;
            if (format == Settings.Format.JPEG && decoded.hasAlpha()) {
                flattened = Bitmap.createBitmap(decoded.getWidth(), decoded.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(flattened);
                canvas.drawColor(Color.WHITE);
                canvas.drawBitmap(decoded, 0, 0, null);
                result = flattened;
            }
            try (FileOutputStream stream = new FileOutputStream(output)) {
                Bitmap.CompressFormat codec = format == Settings.Format.JPEG
                        ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG;
                if (!result.compress(codec, Settings.JPEG_QUALITY, stream)) throw new IOException("Image encoding failed.");
            }
        } catch (ImageTooLargeException | OutOfMemoryError e) {
            throw new IOException("This image is too large to convert safely. Try a smaller image or free some memory.");
        } finally {
            if (flattened != null) flattened.recycle();
            if (decoded != null) decoded.recycle();
        }
    }
    static void checkCancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Conversion cancelled.");
    }
    static void cleanExpired(Context context) {
        long cutoff = System.currentTimeMillis() - CACHE_LIFETIME_MS;
        File[] files = ShareProvider.directory(context).listFiles();
        if (files != null) for (File file : files) if (file.lastModified() < cutoff) file.delete();
        File[] jobs = context.getCacheDir().listFiles((dir, name) -> name.matches("video-[a-f0-9\\-]{36}"));
        if (jobs != null) for (File job : jobs) if (job.lastModified() < cutoff) VideoDownloader.remove(job);
    }
    private static final class ImageTooLargeException extends RuntimeException { }
}
