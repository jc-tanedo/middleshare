package dev.middleshare;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class DestinationPickerTest {
    private static final ComponentName TARGET =
            new ComponentName("example.app", "example.app.Send");

    private void install(String mime) throws Exception {
        var pm = shadowOf(RuntimeEnvironment.getApplication().getPackageManager());
        var activity = pm.addActivityIfNotPresent(TARGET);
        activity.exported = true;
        activity.enabled = true;
        activity.applicationInfo.enabled = true;
        activity.nonLocalizedLabel = "Video app";
        IntentFilter filter = new IntentFilter(Intent.ACTION_SEND);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        filter.addDataType(mime);
        pm.addIntentFilterForActivity(TARGET, filter);
    }

    @Test
    public void onlyCompatibleReceiversAreOfferedAndCancelReturnsNoPayload() throws Exception {
        install("image/png");
        Context context = RuntimeEnvironment.getApplication();
        var controller =
                Robolectric.buildActivity(
                                DestinationPickerActivity.class,
                                ShareDestinations.picker(context, "video/mp4"))
                        .setup();
        try {
            assertNull(button(controller.get().getWindow().getDecorView(), "Video app"));
            button(controller.get().getWindow().getDecorView(), context.getString(R.string.cancel))
                    .performClick();
            assertTrue(controller.get().isFinishing());
            assertEquals(Activity.RESULT_CANCELED, shadowOf(controller.get()).getResultCode());
            assertNull(shadowOf(controller.get()).getResultIntent());
            assertNull(shadowOf(controller.get()).getNextStartedActivity());
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void receiverDisabledWhilePickerIsOpenCannotBeSelected() throws Exception {
        install("video/mp4");
        Context context = RuntimeEnvironment.getApplication();
        var controller =
                Robolectric.buildActivity(
                                DestinationPickerActivity.class,
                                ShareDestinations.picker(context, "video/mp4"))
                        .setup();
        try {
            Button receiver = button(controller.get().getWindow().getDecorView(), "Video app");
            assertNotNull(receiver);
            context.getPackageManager()
                    .setComponentEnabledSetting(
                            TARGET,
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            0);
            receiver.performClick();
            assertFalse(controller.get().isFinishing());
            assertEquals(Activity.RESULT_CANCELED, shadowOf(controller.get()).getResultCode());
            assertNull(shadowOf(controller.get()).getResultIntent());
        } finally {
            controller.pause().stop().destroy();
        }
    }

    private static Button button(View view, String label) {
        if (view instanceof Button && label.contentEquals(((Button) view).getText()))
            return (Button) view;
        if (view instanceof ViewGroup) {
            var group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = button(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }
}
