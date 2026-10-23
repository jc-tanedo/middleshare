package dev.middleshare;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CoreSettingsTest {
    @Test public void videoQualityDefaultAndCustomChoicePersistWithoutAnyFormatLookup() {
        var controller = Robolectric.buildActivity(MainActivity.class).setup();
        try {
            assertEquals(VideoQuality.STANDARD, Settings.getVideoQuality(controller.get()));
            android.widget.Spinner picker = controller.get().findViewById(R.id.video_quality);
            picker.setSelection(VideoQuality.CUSTOM.ordinal());
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(VideoQuality.CUSTOM, Settings.getVideoQuality(controller.get()));
            controller.recreate();
            picker = controller.get().findViewById(R.id.video_quality);
            assertEquals(VideoQuality.CUSTOM.ordinal(), picker.getSelectedItemPosition());
        } finally { controller.pause().stop().destroy(); }
    }
    @Test public void formatPersistsAcrossScreenRecreation() {
        var controller = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();
        ((android.widget.RadioButton) activity.findViewById(2)).performClick();
        assertEquals(Settings.Format.PNG, Settings.getFormat(activity));
        controller.recreate();
        assertTrue(((android.widget.RadioButton) controller.get().findViewById(2)).isChecked());
        controller.pause().stop().destroy();
    }
}
