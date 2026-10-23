package dev.middleshare;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.content.Intent;
import android.os.Looper;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class VideoQualityActivityTest {
    private static final AtomicInteger loads = new AtomicInteger();
    private static final AtomicInteger stops = new AtomicInteger();
    private static CountDownLatch gate;
    private static final java.util.concurrent.atomic.AtomicBoolean failNext = new java.util.concurrent.atomic.AtomicBoolean();
    public static class FixtureActivity extends VideoQualityActivity {
        @Override VideoFormatsJob newLookup() {
            loads.incrementAndGet();
            return new VideoFormatsJob(() -> {
                gate.await();
                if (failNext.getAndSet(false)) throw new java.io.IOException("Fixture offline");
                return VideoFormat.parse(VideoFormatsTest.METADATA);
            }, stops::incrementAndGet);
        }
    }
    @Before public void setup() { loads.set(0); stops.set(0); failNext.set(false); gate = new CountDownLatch(0); }
    private Intent incoming(VideoQuality quality) {
        return new Intent(RuntimeEnvironment.getApplication(), FixtureActivity.class)
                .putExtra(Intent.EXTRA_TEXT, "https://example.org/video")
                .putExtra(VideoAddon.QUALITY, quality.name());
    }
    private void waitForFormats(FixtureActivity activity) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!activity.findViewById(R.id.video_download).isEnabled() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10);
        }
        assertTrue("Formats never became ready", activity.findViewById(R.id.video_download).isEnabled());
        shadowOf(Looper.getMainLooper()).idle();
    }
    @Test public void presetsDoNotLoadFormatsAndDownloadCarriesChosenPreset() {
        var controller = Robolectric.buildActivity(FixtureActivity.class, incoming(VideoQuality.MOBILE)).setup();
        try {
            assertEquals(0, loads.get());
            assertTrue(controller.get().findViewById(R.id.video_download).isEnabled());
            controller.get().findViewById(R.id.video_download).performClick();
            Intent next = shadowOf(controller.get()).getNextStartedActivity();
            assertEquals("MOBILE", next.getStringExtra(VideoAddon.QUALITY));
            assertTrue(next.getBooleanExtra(VideoAddon.CONFIRMED, false));
            assertFalse(next.hasExtra(VideoAddon.FORMAT));
            controller.get().findViewById(R.id.video_download).performClick();
            assertNull(shadowOf(controller.get()).getNextStartedActivity());
        } finally { controller.pause().stop().destroy(); }
    }
    @Test public void customLoadsOnlyOnSelectionCachesResultsAndDownloadsExactChoice() throws Exception {
        var controller = Robolectric.buildActivity(FixtureActivity.class, incoming(VideoQuality.STANDARD)).setup();
        try {
            FixtureActivity activity = controller.get();
            assertEquals(0, loads.get());
            RadioButton custom = activity.findViewById(R.id.video_custom);
            custom.performClick();
            waitForFormats(activity);
            assertEquals(1, loads.get());
            RadioGroup group = (RadioGroup) custom.getParent();
            ((RadioButton) group.getChildAt(0)).performClick();
            custom.performClick();
            assertEquals(1, loads.get());
            Spinner formats = activity.findViewById(R.id.video_formats);
            formats.setSelection(1);
            shadowOf(Looper.getMainLooper()).idle();
            activity.findViewById(R.id.video_download).performClick();
            Intent next = shadowOf(activity).getNextStartedActivity();
            assertEquals("CUSTOM", next.getStringExtra(VideoAddon.QUALITY));
            assertEquals("18", next.getStringExtra(VideoAddon.FORMAT));
            assertFalse(next.getBooleanExtra(VideoAddon.AUDIO, true));
        } finally { controller.pause().stop().destroy(); }
    }
    @Test public void rotatingDuringCustomLookupRetainsItAndLeavingCustomCancelsIt() throws Exception {
        gate = new CountDownLatch(1);
        var controller = Robolectric.buildActivity(FixtureActivity.class, incoming(VideoQuality.CUSTOM)).setup();
        try {
            assertEquals(1, loads.get());
            assertFalse(controller.get().findViewById(R.id.video_download).isEnabled());
            controller.recreate();
            assertEquals(1, loads.get());
            assertEquals(0, stops.get());
            RadioButton custom = controller.get().findViewById(R.id.video_custom);
            ((RadioButton) ((RadioGroup) custom.getParent()).getChildAt(1)).performClick();
            assertEquals(1, stops.get());
            assertTrue(controller.get().findViewById(R.id.video_download).isEnabled());
        } finally { gate.countDown(); controller.pause().stop().destroy(); }
    }
    @Test public void completedLookupAndChosenFormatSurviveRotationWithoutRefetch() throws Exception {
        var controller = Robolectric.buildActivity(FixtureActivity.class, incoming(VideoQuality.CUSTOM)).setup();
        try {
            waitForFormats(controller.get());
            ((Spinner) controller.get().findViewById(R.id.video_formats)).setSelection(1);
            controller.recreate();
            waitForFormats(controller.get());
            assertEquals(1, loads.get());
            controller.get().findViewById(R.id.video_download).performClick();
            assertEquals("18", shadowOf(controller.get()).getNextStartedActivity().getStringExtra(VideoAddon.FORMAT));
        } finally { controller.pause().stop().destroy(); }
    }
    @Test public void failedLookupDoesNotEnableDownloadAndCustomCanBeRetried() throws Exception {
        failNext.set(true);
        var controller = Robolectric.buildActivity(FixtureActivity.class, incoming(VideoQuality.CUSTOM)).setup();
        try {
            android.widget.TextView status = controller.get().findViewById(R.id.video_format_status);
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (!status.getText().toString().contains("retry") && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10);
            }
            assertTrue(status.getText().toString().contains("retry"));
            assertFalse(controller.get().findViewById(R.id.video_download).isEnabled());
            controller.get().findViewById(R.id.video_custom).performClick();
            waitForFormats(controller.get());
            assertEquals(2, loads.get());
        } finally { controller.pause().stop().destroy(); }
    }
}
