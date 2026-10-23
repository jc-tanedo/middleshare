package dev.middleshare;

import static org.junit.Assert.*;

import android.content.res.Configuration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ThemeTest {
    @Test
    @Config(qualifiers = "night")
    public void defaultsToDeviceDarkTheme() {
        assertEquals(Settings.Theme.SYSTEM, Settings.getTheme(RuntimeEnvironment.getApplication()));
        checkNight(true);
    }

    @Test
    @Config(qualifiers = "notnight")
    public void defaultsToDeviceLightTheme() {
        checkNight(false);
    }

    @Test
    @Config(qualifiers = "notnight")
    public void explicitDarkOverridesLightDevice() {
        Settings.setTheme(RuntimeEnvironment.getApplication(), Settings.Theme.DARK);
        checkNight(true);
    }

    @Test
    @Config(qualifiers = "night")
    public void explicitLightOverridesDarkDevice() {
        Settings.setTheme(RuntimeEnvironment.getApplication(), Settings.Theme.LIGHT);
        checkNight(false);
    }

    private void checkNight(boolean night) {
        var controller = Robolectric.buildActivity(MainActivity.class).setup();
        try {
            MainActivity activity = controller.get();
            int mode =
                    activity.getResources().getConfiguration().uiMode
                            & Configuration.UI_MODE_NIGHT_MASK;
            assertEquals(
                    night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO, mode);
            assertEquals(night ? 0xff2e3440 : 0xfff5f6f2, activity.getColor(R.color.background));
            android.util.TypedValue bar = new android.util.TypedValue();
            assertTrue(
                    activity.getTheme()
                            .resolveAttribute(android.R.attr.windowLightStatusBar, bar, true));
            assertEquals(!night, bar.data != 0);
            assertNotEquals(activity.getColor(R.color.background), activity.getColor(R.color.ink));
        } finally {
            controller.pause().stop().destroy();
        }
    }
}
