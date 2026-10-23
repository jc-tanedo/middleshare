package dev.middleshare;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class VideoAddonTest {
    private Context context;
    @Before public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication();
        var main = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
        main.signatures = new Signature[]{new Signature(new byte[]{1})};
        shadowOf(context.getPackageManager()).installPackage(main);
    }
    private void install(int version, int signature, boolean enabled) {
        PackageInfo addon = new PackageInfo();
        addon.packageName = VideoAddon.PACKAGE;
        addon.setLongVersionCode(version);
        addon.signatures = new Signature[]{new Signature(new byte[]{(byte) signature})};
        addon.applicationInfo = new ApplicationInfo();
        addon.applicationInfo.packageName = addon.packageName;
        addon.applicationInfo.enabled = enabled;
        ActivityInfo activity = new ActivityInfo();
        activity.name = ShareActivity.class.getName();
        activity.packageName = addon.packageName;
        activity.applicationInfo = addon.applicationInfo;
        activity.enabled = true;
        activity.exported = true;
        addon.activities = new ActivityInfo[]{activity};
        shadowOf(context.getPackageManager()).installPackage(addon);
    }
    @Test public void missingOldUntrustedAndDisabledAddonsAreRejected() {
        assertFalse(VideoAddon.available(context));
        install(5, 2, true);
        assertFalse(VideoAddon.available(context));
        install(4, 1, true);
        assertFalse(VideoAddon.available(context));
        install(5, 1, false);
        assertFalse(VideoAddon.available(context));
        install(6, 1, true);
        assertTrue(VideoAddon.available(context));
    }
    @Test public void installedAddonReceivesOnlyTheParsedLinkAndMainFinishes() {
        install(6, 1, true);
        Settings.setVideoQuality(context, VideoQuality.MOBILE);
        Intent incoming = new Intent(context, ShareActivity.class).setAction(Intent.ACTION_SEND)
                .setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Watch https://example.org/video")
                .putExtra("untrusted-extra", "never forward");
        var controller = Robolectric.buildActivity(ShareActivity.class, incoming).setup();
        try {
            var outgoing = shadowOf(controller.get()).getNextStartedActivity();
            assertNotNull(outgoing);
            assertEquals(VideoAddon.PACKAGE, outgoing.getComponent().getPackageName());
            assertEquals(ShareActivity.class.getName(), outgoing.getComponent().getClassName());
            assertEquals("https://example.org/video", outgoing.getStringExtra(Intent.EXTRA_TEXT));
            assertFalse(outgoing.hasExtra("untrusted-extra"));
            assertEquals("MOBILE", outgoing.getStringExtra(VideoAddon.QUALITY));
            assertTrue(controller.get().isFinishing());
        } finally { controller.pause().stop().destroy(); }
    }
    @Test public void missingAddonOffersReleasesAndRetryAfterInstallation() {
        Intent incoming = new Intent(context, ShareActivity.class).setAction(Intent.ACTION_SEND)
                .setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://example.org/video");
        var controller = Robolectric.buildActivity(ShareActivity.class, incoming).setup();
        try {
            assertNull(shadowOf(controller.get()).getNextStartedActivity());
            var dialog = ShadowAlertDialog.getLatestAlertDialog();
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
            shadowOf(android.os.Looper.getMainLooper()).idle();
            var releases = shadowOf(controller.get()).getNextStartedActivity();
            assertEquals(Intent.ACTION_VIEW, releases.getAction());
            assertEquals(VideoAddon.RELEASES, releases.getDataString());
            install(6, 1, true);
            controller.get().findViewById(R.id.video_retry).performClick();
            assertEquals(VideoAddon.PACKAGE, shadowOf(controller.get()).getNextStartedActivity()
                    .getComponent().getPackageName());
            assertTrue(controller.get().isFinishing());
        } finally { controller.pause().stop().destroy(); }
    }
}
