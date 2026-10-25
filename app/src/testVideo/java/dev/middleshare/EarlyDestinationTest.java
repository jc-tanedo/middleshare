package dev.middleshare;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.os.Looper;

import com.yausername.youtubedl_android.YoutubeDLRequest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, shadows = TestStorage.class)
public class EarlyDestinationTest {
    private static CountDownLatch release;
    private static CountDownLatch written;
    private static AtomicInteger downloads;
    private static ConversionJob activeJob;
    private static final ComponentName TARGET =
            new ComponentName("example.receiver", "example.receiver.SendVideo");

    public static class FixtureActivity extends ShareActivity {
        @Override
        protected boolean isVideoDownloadConfirmed() {
            return true;
        }

        @Override
        ConversionJob newVideoJob(String link, VideoQuality quality, String format, boolean audio) {
            VideoDownloader downloader =
                    new VideoDownloader(
                            new VideoDownloader.Engine() {
                                public void init(Context app) {}

                                public void execute(
                                        YoutubeDLRequest request,
                                        String id,
                                        VideoDownloader.Progress progress)
                                        throws Exception {
                                    downloads.incrementAndGet();
                                    release.await();
                                    File output =
                                            new File(
                                                    request.getOption("-o")
                                                            .replace("%(ext)s", "mp4"));
                                    Files.write(output.toPath(), new byte[] {0, 1, 2, 3});
                                    written.countDown();
                                }

                                public void cancel(String id) {
                                    release.countDown();
                                }
                            });
            activeJob =
                    new ConversionJob(
                            getApplicationContext(), link, quality, format, audio, downloader);
            return activeJob;
        }
    }

    @Before
    public void setup() {
        TestStorage.allocatable = 2L * 1024 * 1024 * 1024;
        release = new CountDownLatch(1);
        written = new CountDownLatch(1);
        downloads = new AtomicInteger();
    }

    private void receiver(boolean actualMime) throws Exception {
        var packages = shadowOf(RuntimeEnvironment.getApplication().getPackageManager());
        ActivityInfo activity = packages.addActivityIfNotPresent(TARGET);
        activity.nonLocalizedLabel = "Video receiver";
        activity.enabled = true;
        activity.exported = true;
        if (activity.applicationInfo == null) activity.applicationInfo = new ApplicationInfo();
        activity.applicationInfo.packageName = TARGET.getPackageName();
        activity.applicationInfo.enabled = true;
        android.content.IntentFilter filter = new android.content.IntentFilter(Intent.ACTION_SEND);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        filter.addDataType(actualMime ? "video/mp4" : "video/webm");
        packages.addIntentFilterForActivity(TARGET, filter);
    }

    private ActivityController<FixtureActivity> start() {
        Context context = RuntimeEnvironment.getApplication();
        return Robolectric.buildActivity(
                        FixtureActivity.class,
                        new Intent(context, FixtureActivity.class)
                                .setAction(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, "https://example.org/video"))
                .setup();
    }

    private void pick(FixtureActivity activity) {
        activity.findViewById(R.id.video_destination).performClick();
        Intent picker = shadowOf(activity).getNextStartedActivity();
        assertEquals(Intent.ACTION_PICK_ACTIVITY, picker.getAction());
        Intent prototype = picker.getParcelableExtra(Intent.EXTRA_INTENT);
        assertEquals(Intent.ACTION_SEND, prototype.getAction());
        assertEquals("video/*", prototype.getType());
        assertFalse(prototype.hasExtra(Intent.EXTRA_STREAM));
        assertNull(prototype.getClipData());
    }

    private void finishDownload() throws Exception {
        release.countDown();
        assertTrue(written.await(5, TimeUnit.SECONDS));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!activeJob.completed && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(10);
        }
        assertTrue("Download never completed", activeJob.completed);
        assertNull(activeJob.error);
    }

    private void selected(FixtureActivity activity) {
        activity.onActivityResult(
                ShareActivity.PICK_DESTINATION,
                Activity.RESULT_OK,
                new Intent().setComponent(TARGET).putExtra("untrusted-extra", "never forward"));
    }

    @Test
    public void selectedAppAndDownloadSurviveRotationAndReceiveOnlyReadyFile() throws Exception {
        receiver(true);
        var controller = start();
        try {
            pick(controller.get());
            selected(controller.get());
            controller.recreate();
            finishDownload();
            Intent share = shadowOf(controller.get()).getNextStartedActivity();
            assertEquals(TARGET, share.getComponent());
            assertEquals(Intent.ACTION_SEND, share.getAction());
            assertEquals("video/mp4", share.getType());
            assertNotNull(share.getParcelableExtra(Intent.EXTRA_STREAM));
            assertNotNull(share.getClipData());
            assertTrue((share.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
            assertFalse(share.hasExtra("untrusted-extra"));
            assertEquals(1, downloads.get());
            assertNull(shadowOf(controller.get()).getNextStartedActivity());
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void completedDownloadWaitsForPickerAndResumeBeforeOpeningReceiver() throws Exception {
        receiver(true);
        var controller = start();
        try {
            pick(controller.get());
            controller.pause();
            finishDownload();
            assertNull(shadowOf(controller.get()).getNextStartedActivity());
            selected(controller.get());
            assertNull(shadowOf(controller.get()).getNextStartedActivity());
            controller.resume();
            assertEquals(
                    TARGET, shadowOf(controller.get()).getNextStartedActivity().getComponent());
            assertNull(shadowOf(controller.get()).getNextStartedActivity());
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void cancelingPickerUsesNormalShareSheetWhenReady() throws Exception {
        var controller = start();
        try {
            pick(controller.get());
            controller.pause();
            finishDownload();
            controller
                    .get()
                    .onActivityResult(
                            ShareActivity.PICK_DESTINATION, Activity.RESULT_CANCELED, null);
            controller.resume();
            assertEquals(
                    Intent.ACTION_CHOOSER,
                    shadowOf(controller.get()).getNextStartedActivity().getAction());
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void incompatibleFinishedMimeFallsBackToShareSheet() throws Exception {
        receiver(false);
        var controller = start();
        try {
            pick(controller.get());
            selected(controller.get());
            finishDownload();
            assertEquals(
                    Intent.ACTION_CHOOSER,
                    shadowOf(controller.get()).getNextStartedActivity().getAction());
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void cancelingAChangeKeepsThePreviousDestination() throws Exception {
        receiver(true);
        var controller = start();
        try {
            pick(controller.get());
            selected(controller.get());
            pick(controller.get());
            controller.pause();
            finishDownload();
            controller
                    .get()
                    .onActivityResult(
                            ShareActivity.PICK_DESTINATION, Activity.RESULT_CANCELED, null);
            controller.resume();
            assertEquals(
                    TARGET, shadowOf(controller.get()).getNextStartedActivity().getComponent());
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void disabledDestinationFallsBackToTheShareSheet() throws Exception {
        receiver(true);
        var controller = start();
        try {
            pick(controller.get());
            selected(controller.get());
            controller
                    .get()
                    .getPackageManager()
                    .setComponentEnabledSetting(
                            TARGET,
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            0);
            finishDownload();
            assertEquals(
                    Intent.ACTION_CHOOSER,
                    shadowOf(controller.get()).getNextStartedActivity().getAction());
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void pickerSelectionCannotTargetAnInternalOrUnresolvedComponent() {
        Context context = RuntimeEnvironment.getApplication();
        assertFalse(
                ShareDestinations.available(
                        context, new ComponentName(context, VideoDownloadActivity.class)));
        assertFalse(
                ShareDestinations.available(
                        context, new ComponentName("missing.app", "missing.app.Send")));
    }
}
