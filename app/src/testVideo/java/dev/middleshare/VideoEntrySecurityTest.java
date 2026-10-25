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
                        .putExtra(VideoAddon.DESTINATION, "malicious.app/malicious.app.Send")
                        .putExtra(VideoAddon.FORMAT, "137");
        var controller = Robolectric.buildActivity(ShareActivity.class, incoming).setup();
        try {
            Intent next = shadowOf(controller.get()).getNextStartedActivity();
            assertNotNull(next);
            assertEquals(VideoQualityActivity.class.getName(), next.getComponent().getClassName());
            assertFalse(next.hasExtra(VideoAddon.FORMAT));
            assertFalse(next.hasExtra(VideoAddon.DESTINATION));
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

    @Test
    public void serviceAndFavoriteEntryHaveTheRequiredManifestBoundaries() throws Exception {
        var context = RuntimeEnvironment.getApplication();
        var pm = context.getPackageManager();
        var service = pm.getServiceInfo(new ComponentName(context, VideoDownloadService.class), 0);
        assertFalse(service.exported);
        assertEquals(
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                service.getForegroundServiceType());
        var favorite =
                pm.getActivityInfo(new ComponentName(context, FavoriteVideoActivity.class), 0);
        assertTrue(favorite.exported);
        assertEquals("dev.middleshare.permission.SHARE_FAVORITE", favorite.permission);
    }

    @Test
    public void favoriteCustomQualityKeepsTheDestinationButStillRequiresAFormatChoice() {
        var context = RuntimeEnvironment.getApplication();
        Intent incoming =
                new Intent(context, FavoriteVideoActivity.class)
                        .setAction(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "https://example.org/video")
                        .putExtra(VideoAddon.QUALITY, "CUSTOM")
                        .putExtra(
                                VideoAddon.DESTINATION, "example.receiver/example.receiver.Video");
        var controller = Robolectric.buildActivity(FavoriteVideoActivity.class, incoming).setup();
        try {
            Intent next = shadowOf(controller.get()).getNextStartedActivity();
            assertEquals(VideoQualityActivity.class.getName(), next.getComponent().getClassName());
            assertEquals(
                    "example.receiver/example.receiver.Video",
                    next.getStringExtra(VideoAddon.DESTINATION));
            assertTrue(controller.get().isFinishing());
        } finally {
            controller.pause().stop().destroy();
        }
    }
}
