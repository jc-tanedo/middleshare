package dev.middleshare;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlertDialog;
import android.content.Intent;
import android.widget.ListView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

import java.util.HashSet;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class AboutActivityTest {
    @Test
    public void homeOpensAboutAndComponentsExposeOfflineNoticesAndSource() throws Exception {
        var home = Robolectric.buildActivity(MainActivity.class).setup();
        home.get().findViewById(R.id.about_button).performClick();
        Intent launch = shadowOf(home.get()).getNextStartedActivity();
        assertEquals(AboutActivity.class.getName(), launch.getComponent().getClassName());
        home.pause().stop().destroy();
        var controller = Robolectric.buildActivity(AboutActivity.class).setup();
        try {
            ListView list = controller.get().findViewById(R.id.credits_list);
            var entries = CreditsCatalog.load(controller.get());
            assertEquals(entries.size() + list.getHeaderViewsCount(), list.getCount());
            int index = -1;
            for (int i = 0; i < entries.size(); i++)
                if (entries.get(i)
                        .getString("id")
                        .startsWith("org.jetbrains.kotlin:kotlin-stdlib:")) {
                    index = i;
                    break;
                }
            assertTrue(index >= 0);
            list.performItemClick(null, index + list.getHeaderViewsCount(), index);
            AlertDialog component = ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(component.isShowing());
            component.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog license = ShadowAlertDialog.getLatestAlertDialog();
            assertNotSame(component, license);
            assertTrue(license.isShowing());
            assertTrue(shadowOf(license).getView() instanceof android.widget.ScrollView);
            var licenseView = (android.widget.ScrollView) shadowOf(license).getView();
            assertTrue(
                    ((android.widget.TextView) licenseView.getChildAt(0))
                            .getText()
                            .toString()
                            .contains("Apache License"));
            license.dismiss();
            list.performItemClick(null, index + list.getHeaderViewsCount(), index);
            ShadowAlertDialog.getLatestAlertDialog()
                    .getButton(AlertDialog.BUTTON_NEUTRAL)
                    .performClick();
            shadowOf(android.os.Looper.getMainLooper()).idle();
            Intent source = shadowOf(controller.get()).getNextStartedActivity();
            assertEquals(Intent.ACTION_VIEW, source.getAction());
            assertEquals(entries.get(index).getString("source"), source.getDataString());
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void everyCreditHasReadableOfflineNoticesIncludingNestedRuntimeAndJavascript()
            throws Exception {
        var context = RuntimeEnvironment.getApplication();
        var entries = CreditsCatalog.load(context);
        HashSet<String> ids = new HashSet<>();
        HashSet<String> notices = new HashSet<>();
        for (var entry : entries) {
            assertTrue("Duplicate credit", ids.add(entry.getString("id")));
            assertFalse(entry.getString("license").isBlank());
            assertTrue(entry.getString("source").startsWith("http"));
            var assets = entry.getJSONArray("noticeAssets");
            assertTrue("Missing license text", assets.length() > 0);
            for (int i = 0; i < assets.length(); i++) notices.add(assets.getString(i));
        }
        for (String notice : notices)
            assertTrue(notice, CreditsCatalog.read(context, notice).length() > 100);
        assertTrue(ids.contains("native:python"));
        assertTrue(ids.contains("native:ffmpeg"));
        assertTrue(ids.contains("native:pycryptodome"));
        assertTrue(ids.contains("javascript:meriyah"));
        assertTrue(ids.contains("javascript:astring"));
        assertTrue(ids.contains("vendored:yt-dlp"));
    }

    @Test
    @Config(qualifiers = "notnight")
    public void aboutHonorsExplicitDarkTheme() {
        Settings.setTheme(RuntimeEnvironment.getApplication(), Settings.Theme.DARK);
        var controller = Robolectric.buildActivity(AboutActivity.class).setup();
        try {
            assertEquals(0xff2e3440, controller.get().getColor(R.color.background));
        } finally {
            controller.pause().stop().destroy();
        }
    }
}
