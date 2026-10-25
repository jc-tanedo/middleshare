package dev.middleshare;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.Looper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowAlertDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FavoriteShareTest {
    private Context context;
    private final ComponentName target =
            new ComponentName("example.favorite", "example.favorite.Send");

    @Before
    public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication();
        TestEnvironment.installMimeMappings();
        Robolectric.setupContentProvider(ShareProvider.class, context.getPackageName() + ".files");
        var packages = shadowOf(context.getPackageManager());
        ActivityInfo activity = packages.addActivityIfNotPresent(target);
        activity.enabled = true;
        activity.exported = true;
        activity.nonLocalizedLabel = "Favorite receiver";
        activity.applicationInfo.enabled = true;
        var filter = new android.content.IntentFilter(Intent.ACTION_SEND);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        filter.addDataType("image/png");
        packages.addIntentFilterForActivity(target, filter);
    }

    @Test
    public void settingsCanChooseAndClearFavorite() {
        var controller = Robolectric.buildActivity(MainActivity.class).setup();
        try {
            controller.get().findViewById(R.id.favorite_choose).performClick();
            ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null, 0, 0);
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(target, Settings.getFavorite(context));
            controller.recreate();
            assertEquals(target, Settings.getFavorite(controller.get()));
            controller.get().findViewById(R.id.favorite_clear).performClick();
            assertNull(Settings.getFavorite(context));
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void imageShortcutProcessesThenSharesOnlyToConfiguredFavorite() throws Exception {
        Settings.setFavorite(context, target);
        ShareProvider.directory(context).mkdirs();
        File image = new File(ShareProvider.directory(context), UUID.randomUUID() + ".png");
        Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        try (var output = new FileOutputStream(image)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
        bitmap.recycle();
        Intent incoming =
                new Intent(context, FavoriteShareActivity.class)
                        .setAction(Intent.ACTION_SEND)
                        .setType("image/png")
                        .putExtra(Intent.EXTRA_STREAM, ShareProvider.uriFor(context, image))
                        .putExtra(VideoAddon.DESTINATION, "malicious.app/malicious.app.Send");
        var controller = Robolectric.buildActivity(FavoriteShareActivity.class, incoming).setup();
        try {
            Intent outgoing = null;
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (outgoing == null && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle();
                outgoing = shadowOf(controller.get()).getNextStartedActivity();
                Thread.sleep(10);
            }
            assertNotNull(outgoing);
            assertEquals(Intent.ACTION_SEND, outgoing.getAction());
            assertEquals(target, outgoing.getComponent());
            assertEquals("image/png", outgoing.getType());
            assertTrue((outgoing.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
            assertNull(outgoing.getStringExtra(VideoAddon.DESTINATION));
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void videoFavoriteHandoffIsExplicitAndProtectedByASignaturePermission()
            throws Exception {
        Settings.setFavorite(context, target);
        var controller = Robolectric.buildActivity(FavoriteShareActivity.class).create();
        try {
            Intent next = controller.get().videoHandoff("https://example.org/video");
            assertEquals(
                    "dev.middleshare.FavoriteVideoActivity", next.getComponent().getClassName());
            assertEquals(target.flattenToString(), next.getStringExtra(VideoAddon.DESTINATION));
            var permission =
                    context.getPackageManager()
                            .getPermissionInfo("dev.middleshare.permission.SHARE_FAVORITE", 0);
            assertEquals(
                    android.content.pm.PermissionInfo.PROTECTION_SIGNATURE,
                    permission.protectionLevel
                            & android.content.pm.PermissionInfo.PROTECTION_MASK_BASE);
        } finally {
            controller.destroy();
        }
    }
}
