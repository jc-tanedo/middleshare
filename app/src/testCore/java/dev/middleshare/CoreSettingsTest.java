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
