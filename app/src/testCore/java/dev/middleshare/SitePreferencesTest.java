package dev.middleshare;

import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SitePreferencesTest {
    private Button siteButton(View view) {
        if (view instanceof Button && ((Button) view).getText().toString().equals("Site preferences")) return (Button) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            Button match = siteButton(((ViewGroup) view).getChildAt(i));
            if (match != null) return match;
        }
        return null;
    }
    @Test public void siteEditorValidatesAddsRenamesAndClearsOverrides() {
        var controller = Robolectric.buildActivity(MainActivity.class).setup();
        try {
            MainActivity activity = controller.get();
            Button sites = siteButton(activity.getWindow().getDecorView());
            assertNotNull(sites);
            sites.performClick();
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog editor = ShadowAlertDialog.getLatestAlertDialog();
            editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertTrue(editor.isShowing());
            assertNotNull(((EditText) editor.findViewById(R.id.video_domain)).getError());
            ((EditText) editor.findViewById(R.id.video_domain)).setText("https://WWW.Example.com/video");
            ((Spinner) editor.findViewById(R.id.video_domain_quality)).setSelection(1);
            editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertFalse(editor.isShowing());
            assertEquals(VideoQuality.MOBILE, Settings.getVideoQuality(activity, "example.com"));
            sites.performClick();
            ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null, 0, 0);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            editor = ShadowAlertDialog.getLatestAlertDialog();
            ((EditText) editor.findViewById(R.id.video_domain)).setText("example.net");
            editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(java.util.List.of("example.net"), Settings.videoQualityHosts(activity));
            assertEquals(VideoQuality.MOBILE, Settings.getVideoQuality(activity, "example.net"));
            sites.performClick();
            ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null, 0, 0);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertTrue(Settings.videoQualityHosts(activity).isEmpty());
            assertEquals(VideoQuality.BEST, Settings.getVideoQuality(activity, "example.net"));
        } finally { controller.pause().stop().destroy(); }
    }
}
