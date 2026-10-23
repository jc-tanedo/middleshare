package dev.middleshare;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Looper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

@RunWith(RobolectricTestRunner.class)
// Native ImageDecoder shadows are supported through API 34, including in current Robolectric.
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ConversionJobTest {
    private Context context;

    @Before
    public void setup() {
        context = RuntimeEnvironment.getApplication();
        TestEnvironment.installMimeMappings();
        Robolectric.setupContentProvider(ShareProvider.class, context.getPackageName() + ".files");
        ShareProvider.directory(context).mkdirs();
    }

    private File png() throws Exception {
        File file = new File(ShareProvider.directory(context), UUID.randomUUID() + ".png");
        Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        try (FileOutputStream stream = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
        bitmap.recycle();
        return file;
    }

    @Test
    public void failedBatchRemovesPreviouslyPreparedImages() throws Exception {
        File valid = png();
        File corrupt = new File(ShareProvider.directory(context), UUID.randomUUID() + ".png");
        Files.write(corrupt.toPath(), new byte[] {0});
        ConversionJob job =
                new ConversionJob(
                        context,
                        List.of(
                                ShareProvider.uriFor(context, valid),
                                ShareProvider.uriFor(context, corrupt)),
                        Settings.Format.JPEG);
        try {
            long deadline = System.nanoTime() + 10_000_000_000L;
            while (!job.completed && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle();
                Thread.sleep(10);
            }
            assertTrue("Batch worker did not complete", job.completed);
            assertNotNull(job.error);
            assertTrue(job.outputs.isEmpty());
            assertEquals(2, ShareProvider.directory(context).listFiles().length);
        } finally {
            job.cancel();
        }
    }

    @Test
    public void receivingAnImageOpensChooserWithReadableOutput() throws Exception {
        File source = png();
        Intent incoming =
                new Intent(context, ShareActivity.class)
                        .setAction(Intent.ACTION_SEND)
                        .setType("image/png")
                        .putExtra(Intent.EXTRA_STREAM, ShareProvider.uriFor(context, source));
        var controller = Robolectric.buildActivity(ShareActivity.class, incoming).setup();
        try {
            var activity = controller.get();
            Intent launched = null;
            long deadline = System.nanoTime() + 10_000_000_000L;
            while (launched == null && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle();
                launched = shadowOf(activity).getNextStartedActivity();
                if (launched == null) Thread.sleep(10);
            }
            assertNotNull("Share sheet did not open", launched);
            assertEquals(Intent.ACTION_CHOOSER, launched.getAction());
            Intent share = launched.getParcelableExtra(Intent.EXTRA_INTENT);
            assertEquals("image/png", share.getType());
            try (var stream =
                    context.getContentResolver()
                            .openInputStream(share.getParcelableExtra(Intent.EXTRA_STREAM))) {
                assertArrayEquals(Files.readAllBytes(source.toPath()), stream.readAllBytes());
            }
            assertNull(shadowOf(activity).getNextStartedActivity());
        } finally {
            controller.pause().stop().destroy();
        }
    }
}
