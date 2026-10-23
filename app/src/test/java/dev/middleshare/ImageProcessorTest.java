package dev.middleshare;

import static org.junit.Assert.*;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
// Native ImageDecoder shadows are supported through API 34, including in current Robolectric.
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ImageProcessorTest {
    private Context context;
    private ShareProvider provider;
    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        TestEnvironment.installMimeMappings();
        provider = Robolectric.setupContentProvider(ShareProvider.class, context.getPackageName() + ".files");
        ShareProvider.directory(context).mkdirs();
    }
    private File image(Bitmap.CompressFormat format, String ext) throws IOException {
        File file = new File(ShareProvider.directory(context), UUID.randomUUID() + ext);
        Bitmap bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.TRANSPARENT);
        for (int y = 8; y < 24; y++) for (int x = 8; x < 24; x++) bitmap.setPixel(x, y, Color.RED);
        try (FileOutputStream stream = new FileOutputStream(file)) { assertTrue(bitmap.compress(format, 100, stream)); }
        bitmap.recycle();
        return file;
    }
    @Test public void webpToJpegFlattensAlphaAndSuppliesReadableContentUri() throws Exception {
        File source = image(Bitmap.CompressFormat.WEBP_LOSSLESS, ".webp");
        SharedFile output = ImageProcessor.process(context, ShareProvider.uriFor(context, source), Settings.Format.JPEG);
        assertEquals("image/jpeg", output.mime);
        assertEquals("content", output.uri.getScheme());
        assertEquals("image/jpeg", provider.getType(output.uri));
        Bitmap bitmap = BitmapFactory.decodeFile(output.file.getPath());
        assertEquals(24, bitmap.getWidth());
        assertEquals(24, bitmap.getHeight());
        assertFalse(bitmap.hasAlpha());
        int white = bitmap.getPixel(1, 1);
        assertTrue(Color.red(white) > 245 && Color.green(white) > 245 && Color.blue(white) > 245);
        int red = bitmap.getPixel(20, 20);
        assertTrue(Color.red(red) > 240 && Color.green(red) < 15);
        bitmap.recycle();
        try (var pfd = provider.openFile(output.uri, "r")) { assertTrue(pfd.getStatSize() > 0); }
        try (Cursor cursor = provider.query(output.uri, null, null, null, null)) {
            assertTrue(cursor.moveToFirst());
            assertTrue(cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)).endsWith(".jpg"));
            assertEquals(output.file.length(), cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)));
        }
    }
    @Test public void webpToPngPreservesAlphaAndPixels() throws Exception {
        File source = image(Bitmap.CompressFormat.WEBP_LOSSLESS, ".webp");
        SharedFile output = ImageProcessor.process(context, ShareProvider.uriFor(context, source), Settings.Format.PNG);
        assertEquals("image/png", output.mime);
        Bitmap bitmap = BitmapFactory.decodeFile(output.file.getPath());
        assertEquals(0, Color.alpha(bitmap.getPixel(1, 1)));
        assertEquals(Color.RED, bitmap.getPixel(20, 20));
        bitmap.recycle();
    }
    @Test public void nonWebpIsCopiedByteForByte() throws Exception {
        File source = image(Bitmap.CompressFormat.PNG, ".png");
        byte[] expected = Files.readAllBytes(source.toPath());
        SharedFile output = ImageProcessor.process(context, ShareProvider.uriFor(context, source), Settings.Format.JPEG);
        assertEquals("image/png", output.mime);
        assertNotEquals(source, output.file);
        assertArrayEquals(expected, Files.readAllBytes(output.file.toPath()));
        assertTrue(source.exists());
    }
    @Test public void actualWebpHeaderOverridesMisleadingExtension() throws Exception {
        File source = image(Bitmap.CompressFormat.WEBP_LOSSLESS, ".png");
        assertEquals("image/jpeg", ImageProcessor.process(context, ShareProvider.uriFor(context, source), Settings.Format.JPEG).mime);
    }
    @Test public void corruptImageFailsAndRemovesSpoolFile() throws Exception {
        File source = new File(ShareProvider.directory(context), UUID.randomUUID() + ".webp");
        Files.write(source.toPath(), new byte[]{1, 2, 3});
        assertThrows(IOException.class, () -> ImageProcessor.process(context, ShareProvider.uriFor(context, source), Settings.Format.JPEG));
        File[] leftovers = context.getCacheDir().listFiles((dir, name) -> name.startsWith("incoming-"));
        assertNotNull(leftovers);
        assertEquals(0, leftovers.length);
    }
    @Test public void oversizedInputIsRejectedBeforeDecoding() throws Exception {
        File source = new File(ShareProvider.directory(context), UUID.randomUUID() + ".webp");
        try (RandomAccessFile file = new RandomAccessFile(source, "rw")) { file.setLength(ImageProcessor.MAX_INPUT_BYTES + 1); }
        IOException error = assertThrows(IOException.class,
                () -> ImageProcessor.process(context, ShareProvider.uriFor(context, source), Settings.Format.JPEG));
        assertTrue(error.getMessage().contains("64 MB"));
    }
    @Test public void providerRejectsWritesAndTraversal() {
        assertThrows(IOException.class, () -> provider.openFile(Uri.parse("content://dev.middleshare.files/../../private.jpg"), "r"));
        assertThrows(IOException.class, () -> provider.openFile(Uri.parse("content://dev.middleshare.files/image.jpg"), "w"));
    }
    @Test public void cleanupExpiresOldOutputsButKeepsRecentImages() throws Exception {
        File expired = image(Bitmap.CompressFormat.PNG, ".png");
        File recent = image(Bitmap.CompressFormat.PNG, ".png");
        assertTrue(expired.setLastModified(System.currentTimeMillis() - ImageProcessor.CACHE_LIFETIME_MS - 1000));
        ImageProcessor.cleanExpired(context);
        assertFalse(expired.exists());
        assertTrue(recent.exists());
    }
    @Test public void oversizedDecodedImageIsRejectedWithoutResizing() throws Exception {
        File source = new File(ShareProvider.directory(context), UUID.randomUUID() + ".webp");
        Bitmap bitmap = Bitmap.createBitmap(5000, 4000, Bitmap.Config.ARGB_8888);
        try (FileOutputStream stream = new FileOutputStream(source)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, stream));
        } finally { bitmap.recycle(); }
        IOException error = assertThrows(IOException.class,
                () -> ImageProcessor.process(context, ShareProvider.uriFor(context, source), Settings.Format.JPEG));
        assertTrue(error.getMessage().contains("too large"));
    }
    @Test public void fileAndNetworkUrisAreRejected() {
        assertThrows(IOException.class, () -> ImageProcessor.process(context, Uri.parse("file:///etc/passwd"), Settings.Format.JPEG));
        assertThrows(IOException.class, () -> ImageProcessor.process(context, Uri.parse("https://example.com/image.webp"), Settings.Format.JPEG));
    }
}
