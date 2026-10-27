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

    @Test
    public void downloadPageMatchesTheBuildChannel() {
        assertEquals(
                "https://github.com/jc-tanedo/middleshare/releases/latest",
                VideoAddon.downloadsUrl(false, "0.8.5"));
        assertEquals(
                "https://github.com/jc-tanedo/middleshare/releases/tag/v0.8.5-rc.1",
                VideoAddon.downloadsUrl(false, "0.8.5-rc.1"));
        assertEquals(
                "https://github.com/jc-tanedo/middleshare/actions/workflows/android.yml",
                VideoAddon.downloadsUrl(true, "0.8.5-rc.1"));
    }

    @Before
    public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication();
        var main =
                context.getPackageManager()
                        .getPackageInfo(
                                context.getPackageName(),
                                PackageManager.GET_SIGNATURES
                                        | PackageManager.GET_PROVIDERS
                                        | PackageManager.GET_ACTIVITIES
                                        | PackageManager.GET_PERMISSIONS);
        main.signatures = new Signature[] {new Signature(new byte[] {1})};
        shadowOf(context.getPackageManager()).installPackage(main);
    }

    private void install(int version, int signature, boolean enabled) {
        install(VideoAddon.PACKAGE, version, signature, enabled);
    }

    private void install(String packageName, int version, int signature, boolean enabled) {
        PackageInfo addon = new PackageInfo();
        addon.packageName = packageName;
        addon.setLongVersionCode(version);
        addon.signatures = new Signature[] {new Signature(new byte[] {(byte) signature})};
        addon.applicationInfo = new ApplicationInfo();
        addon.applicationInfo.packageName = addon.packageName;
        addon.applicationInfo.enabled = enabled;
        ActivityInfo activity = new ActivityInfo();
        activity.name = ShareActivity.class.getName();
        activity.packageName = addon.packageName;
        activity.applicationInfo = addon.applicationInfo;
        activity.enabled = true;
        activity.exported = true;
        addon.activities = new ActivityInfo[] {activity};
        shadowOf(context.getPackageManager()).installPackage(addon);
    }

    @Test
    public void debugPairHasIsolatedPackagesProviderAndFavoritePermission() throws Exception {
        assertEquals("dev.middleshare.debug", context.getPackageName());
        assertEquals("dev.middleshare.video.debug", VideoAddon.PACKAGE);
        assertEquals(
                "dev.middleshare.debug.permission.SHARE_FAVORITE", VideoAddon.FAVORITE_PERMISSION);
        var provider =
                context.getPackageManager()
                        .getProviderInfo(
                                new android.content.ComponentName(context, ShareProvider.class), 0);
        assertEquals("dev.middleshare.debug.files", provider.authority);
        install("dev.middleshare.video", VideoAddon.MIN_VERSION, 1, true);
        assertFalse(VideoAddon.available(context));
        install(VideoAddon.MIN_VERSION, 1, true);
        assertTrue(VideoAddon.available(context));
    }

    @Test
    public void missingOldUntrustedAndDisabledAddonsAreRejected() {
        assertFalse(VideoAddon.available(context));
        install(VideoAddon.MIN_VERSION, 2, true);
        assertFalse(VideoAddon.available(context));
        install(VideoAddon.MIN_VERSION - 1, 1, true);
        assertFalse(VideoAddon.available(context));
        install(VideoAddon.MIN_VERSION, 1, false);
        assertFalse(VideoAddon.available(context));
        install(VideoAddon.MIN_VERSION, 1, true);
        assertTrue(VideoAddon.available(context));
    }

    @Test
    public void installedAddonReceivesOnlyTheParsedLinkAndMainFinishes() {
        install(VideoAddon.MIN_VERSION, 1, true);
        Settings.setVideoQuality(context, VideoQuality.MOBILE);
        Intent incoming =
                new Intent(context, ShareActivity.class)
                        .setAction(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "Watch https://example.org/video")
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
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void missingAddonOffersReleasesAndRetryAfterInstallation() {
        Intent incoming =
                new Intent(context, ShareActivity.class)
                        .setAction(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "https://example.org/video");
        var controller = Robolectric.buildActivity(ShareActivity.class, incoming).setup();
        try {
            assertNull(shadowOf(controller.get()).getNextStartedActivity());
            var dialog = ShadowAlertDialog.getLatestAlertDialog();
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
            shadowOf(android.os.Looper.getMainLooper()).idle();
            var releases = shadowOf(controller.get()).getNextStartedActivity();
            assertEquals(Intent.ACTION_VIEW, releases.getAction());
            assertEquals(VideoAddon.DOWNLOADS, releases.getDataString());
            install(VideoAddon.MIN_VERSION, 1, true);
            controller.get().findViewById(R.id.video_retry).performClick();
            assertEquals(
                    VideoAddon.PACKAGE,
                    shadowOf(controller.get())
                            .getNextStartedActivity()
                            .getComponent()
                            .getPackageName());
            assertTrue(controller.get().isFinishing());
        } finally {
            controller.pause().stop().destroy();
        }
    }
}
