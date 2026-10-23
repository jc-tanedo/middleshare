package dev.middleshare;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class VideoEntrySecurityTest {
    @Test
    public void forgedConfirmationStillOpensQualityPickerWithoutStartingDownload() {
        var context = RuntimeEnvironment.getApplication();
        Intent incoming =
                new Intent(context, ShareActivity.class)
                        .setAction(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "https://example.org/video")
                        .putExtra("dev.middleshare.CONFIRMED", true)
                        .putExtra(VideoAddon.FORMAT, "137");
        var controller = Robolectric.buildActivity(ShareActivity.class, incoming).setup();
        try {
            Intent next = shadowOf(controller.get()).getNextStartedActivity();
            assertNotNull(next);
            assertEquals(VideoQualityActivity.class.getName(), next.getComponent().getClassName());
            assertFalse(next.hasExtra(VideoAddon.FORMAT));
            assertTrue(controller.get().isFinishing());
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void downloadEntryIsPrivateInTheManifest() throws Exception {
        var context = RuntimeEnvironment.getApplication();
        var info =
                context.getPackageManager()
                        .getActivityInfo(
                                new ComponentName(context, VideoDownloadActivity.class), 0);
        assertFalse(info.exported);
    }
}
