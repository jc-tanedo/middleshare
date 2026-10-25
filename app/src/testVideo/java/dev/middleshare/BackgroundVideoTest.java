package dev.middleshare;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Looper;

import com.yausername.youtubedl_android.YoutubeDLRequest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, shadows = TestStorage.class)
public class BackgroundVideoTest {
    private Context context;
    private CountDownLatch gate;
    private static final ComponentName TARGET =
            new ComponentName("example.receiver", "example.receiver.Video");

    @Before
    public void setup() {
        context = RuntimeEnvironment.getApplication();
        shadowOf(RuntimeEnvironment.getApplication())
                .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        TestStorage.allocatable = 2L * 1024 * 1024 * 1024;
        gate = new CountDownLatch(1);
        var pm = shadowOf(context.getPackageManager());
        ActivityInfo info = pm.addActivityIfNotPresent(TARGET);
        info.enabled = true;
        info.exported = true;
        info.applicationInfo.enabled = true;
        info.nonLocalizedLabel = "Receiver";
        android.content.IntentFilter filter = new android.content.IntentFilter(Intent.ACTION_SEND);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        try {
            filter.addDataType("video/mp4");
        } catch (Exception impossible) {
            throw new AssertionError(impossible);
        }
        pm.addIntentFilterForActivity(TARGET, filter);
    }

    private VideoTransfers.Task task(ComponentName destination) throws Exception {
        VideoDownloader downloader =
                new VideoDownloader(
                        new VideoDownloader.Engine() {
                            public void init(Context app) {}

                            public void execute(
                                    YoutubeDLRequest request,
                                    String id,
                                    VideoDownloader.Progress progress)
                                    throws Exception {
                                progress.update("Downloading video… 50%");
                                gate.await();
                                Files.write(
                                        new File(request.getOption("-o").replace("%(ext)s", "mp4"))
                                                .toPath(),
                                        new byte[] {0, 1, 2, 3});
                            }

                            public void cancel(String id) {
                                gate.countDown();
                            }
                        });
        return VideoTransfers.create(
                context,
                "https://example.org/video?private=query",
                VideoQuality.BEST,
                null,
                false,
                destination,
                downloader);
    }

    private void complete(VideoTransfers.Task task) throws Exception {
        gate.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!task.job.completed && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(10);
        }
        assertTrue(task.job.completed);
        assertNull(task.job.error);
    }

    @Test
    public void closingDrawerKeepsServiceDownloadAndReadyNotificationUntilHandoff()
            throws Exception {
        VideoTransfers.Task task = task(TARGET);
        var serviceController = Robolectric.buildService(VideoDownloadService.class).create();
        var service = serviceController.get();
        service.onStartCommand(
                new Intent(context, VideoDownloadService.class)
                        .setAction(VideoDownloadService.START)
                        .putExtra(VideoDownloadActivity.TRANSFER, task.id),
                0,
                1);
        var activity =
                Robolectric.buildActivity(
                                VideoDownloadActivity.class,
                                new Intent(context, VideoDownloadActivity.class)
                                        .setAction(VideoDownloadActivity.OPEN)
                                        .putExtra(VideoDownloadActivity.TRANSFER, task.id))
                        .setup();
        try {
            activity.pause().stop().destroy();
            complete(task);
            Notification ready =
                    shadowOf(context.getSystemService(NotificationManager.class))
                            .getNotification(task.notificationId);
            assertNotNull(ready);
            assertEquals(
                    context.getString(R.string.transfer_ready),
                    ready.extras.getString(Notification.EXTRA_TITLE));
            assertEquals(0, ready.flags & Notification.FLAG_FOREGROUND_SERVICE);
            assertTrue((ready.flags & Notification.FLAG_ONGOING_EVENT) != 0);
            assertTrue(ready.contentIntent.isImmutable());
            Intent open = shadowOf(ready.contentIntent).getSavedIntent();
            assertEquals(VideoDownloadActivity.class.getName(), open.getComponent().getClassName());
            assertEquals(VideoDownloadActivity.SEND_READY, open.getAction());
            assertNull(open.getParcelableExtra(Intent.EXTRA_STREAM));
            var send = Robolectric.buildActivity(VideoDownloadActivity.class, open).setup();
            try {
                Intent outgoing = shadowOf(send.get()).getNextStartedActivity();
                assertNotNull(outgoing);
                assertEquals(TARGET, outgoing.getComponent());
                assertEquals("video/mp4", outgoing.getType());
                assertNotNull(outgoing.getClipData());
                assertTrue((outgoing.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
                assertNull(
                        shadowOf(context.getSystemService(NotificationManager.class))
                                .getNotification(task.notificationId));
                assertNull(VideoTransfers.find(context, task.id));
                assertTrue(task.job.outputs.get(0).file.isFile());
            } finally {
                send.pause().stop().destroy();
            }
        } finally {
            VideoTransfers.remove(task, false);
            serviceController.destroy();
        }
    }

    @Test
    public void cancelActionDeletesStateAndNeverProducesAReadyVideo() throws Exception {
        VideoTransfers.Task task = task(null);
        var controller = Robolectric.buildService(VideoDownloadService.class).create();
        var service = controller.get();
        try {
            service.onStartCommand(
                    new Intent(context, VideoDownloadService.class)
                            .setAction(VideoDownloadService.START)
                            .putExtra(VideoDownloadActivity.TRANSFER, task.id),
                    0,
                    1);
            task.status = context.getString(R.string.transfer_starting);
            Notification progress = service.notification(task);
            assertEquals(VideoDownloadService.CHANNEL, progress.getChannelId());
            assertTrue(progress.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE));
            assertEquals(1, progress.actions.length);
            task.status = "Downloading video… 50%";
            assertEquals(50, service.notification(task).extras.getInt(Notification.EXTRA_PROGRESS));
            Intent cancel = shadowOf(progress.actions[0].actionIntent).getSavedIntent();
            assertEquals(VideoDownloadService.CANCEL, cancel.getAction());
            service.onStartCommand(cancel, 0, 2);
            assertTrue(task.removed);
            assertNull(VideoTransfers.find(context, task.id));
            assertTrue(task.job.outputs.isEmpty());
        } finally {
            VideoTransfers.remove(task, false);
            controller.destroy();
        }
    }

    @Test
    public void serviceTimeoutProducesAnInterruptedResultAndKeepsItsNotification()
            throws Exception {
        VideoTransfers.Task task = task(null);
        var controller = Robolectric.buildService(VideoDownloadService.class).create();
        try {
            var service = controller.get();
            service.onStartCommand(
                    new Intent(context, VideoDownloadService.class)
                            .setAction(VideoDownloadService.START)
                            .putExtra(VideoDownloadActivity.TRANSFER, task.id),
                    0,
                    1);
            service.onTimeout(1, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            assertTrue(task.job.completed);
            assertEquals(context.getString(R.string.transfer_interrupted), task.job.error);
            assertNotNull(
                    shadowOf(context.getSystemService(NotificationManager.class))
                            .getNotification(task.notificationId));
        } finally {
            VideoTransfers.remove(task, false);
            controller.destroy();
        }
    }

    @Test
    public void persistedReadyStateDoesNotContainTheSourceUrlAndIsRecoverable() throws Exception {
        VideoTransfers.Task task = task(TARGET);
        try {
            complete(task);
            File metadata =
                    new File(context.getNoBackupFilesDir(), "video-transfers/" + task.id + ".json");
            String saved =
                    new String(
                            Files.readAllBytes(metadata.toPath()),
                            java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(saved.contains("example.org"));
            assertFalse(saved.contains("private=query"));
            // Remove only the in-memory entry to simulate a process boundary without deleting the
            // record.
            var field = VideoTransfers.class.getDeclaredField("tasks");
            field.setAccessible(true);
            ((java.util.Map<?, ?>) field.get(null)).clear();
            VideoTransfers.Task restored = VideoTransfers.find(context, task.id);
            assertNotNull(restored);
            assertTrue(restored.job.completed);
            assertNull(restored.job.error);
            assertEquals(TARGET, restored.destination);
            assertEquals(task.job.outputs.get(0).file, restored.job.outputs.get(0).file);
            VideoTransfers.remove(restored, false);
        } finally {
            VideoTransfers.remove(task, false);
        }
    }

    @Test
    public void oldAdvanceDestinationIsIgnoredWhenRestoringAReadyVideo() throws Exception {
        VideoTransfers.Task task = task(TARGET);
        try {
            complete(task);
            File file =
                    new File(context.getNoBackupFilesDir(), "video-transfers/" + task.id + ".json");
            var data =
                    new org.json.JSONObject(
                            new String(
                                    Files.readAllBytes(file.toPath()),
                                    java.nio.charset.StandardCharsets.UTF_8));
            data.remove("favorite");
            data.put("destination", TARGET.flattenToString());
            Files.write(
                    file.toPath(),
                    data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var field = VideoTransfers.class.getDeclaredField("tasks");
            field.setAccessible(true);
            ((java.util.Map<?, ?>) field.get(null)).clear();
            var restored = VideoTransfers.find(context, task.id);
            assertNotNull(restored);
            assertNull(restored.destination);
            assertNull(restored.job.error);
            VideoTransfers.remove(restored, false);
        } finally {
            VideoTransfers.remove(task, false);
        }
    }

    @Test
    public void runningMetadataRecoveredAfterProcessLossBecomesInterruptedInsteadOfRestarting()
            throws Exception {
        VideoTransfers.Task task = task(null);
        try {
            task.job.cancel();
            var field = VideoTransfers.class.getDeclaredField("tasks");
            field.setAccessible(true);
            ((java.util.Map<?, ?>) field.get(null)).clear();
            VideoTransfers.Task restored = VideoTransfers.find(context, task.id);
            assertNotNull(restored);
            assertTrue(restored.job.completed);
            assertEquals(context.getString(R.string.transfer_interrupted), restored.job.error);
            VideoTransfers.remove(restored, false);
        } finally {
            VideoTransfers.remove(task, false);
        }
    }

    @Test
    public void drawerStaysOpenUntilCompletionAndUsesNativeShareSheet() throws Exception {
        VideoTransfers.Task task = task(null);
        var controller =
                Robolectric.buildActivity(
                                VideoDownloadActivity.class,
                                new Intent(context, VideoDownloadActivity.class)
                                        .setAction(Intent.ACTION_SEND)
                                        .putExtra(VideoDownloadActivity.TRANSFER, task.id))
                        .setup();
        try {
            assertFalse(controller.get().isFinishing());
            assertNotNull(controller.get().findViewById(R.id.video_background));
            assertNull(shadowOf(controller.get()).getNextStartedActivity());
            controller.recreate();
            assertFalse(controller.get().isFinishing());
            complete(task);
            assertNativeShare(shadowOf(controller.get()).getNextStartedActivity());
            controller.pause().resume();
            assertNull(shadowOf(controller.get()).getNextStartedActivity());
            assertNotNull(VideoTransfers.find(context, task.id));
        } finally {
            controller.pause().stop().destroy();
            VideoTransfers.remove(task, false);
        }
    }

    @Test
    @Config(sdk = {28, 35})
    public void minimizedDownloadWaitsForFinishedTapAndNativeShareCanBeRetried() throws Exception {
        VideoTransfers.Task task = task(null);
        var serviceController = Robolectric.buildService(VideoDownloadService.class).create();
        serviceController
                .get()
                .onStartCommand(
                        new Intent(context, VideoDownloadService.class)
                                .setAction(VideoDownloadService.START)
                                .putExtra(VideoDownloadActivity.TRANSFER, task.id),
                        0,
                        1);
        var drawer =
                Robolectric.buildActivity(
                                VideoDownloadActivity.class,
                                new Intent(context, VideoDownloadActivity.class)
                                        .setAction(Intent.ACTION_SEND)
                                        .putExtra(VideoDownloadActivity.TRANSFER, task.id))
                        .setup();
        try {
            assertFalse(drawer.get().isFinishing());
            drawer.get().findViewById(R.id.video_background).performClick();
            assertTrue(drawer.get().isFinishing());
            drawer.pause().stop().destroy();
            complete(task);
            assertNull(shadowOf(drawer.get()).getNextStartedActivity());
            Notification ready =
                    shadowOf(context.getSystemService(NotificationManager.class))
                            .getNotification(task.notificationId);
            assertNotNull(ready);
            assertEquals(
                    context.getString(R.string.transfer_ready),
                    ready.extras.getString(Notification.EXTRA_TITLE));
            Intent tap = shadowOf(ready.contentIntent).getSavedIntent();
            // Restore only ready metadata, as if Android reclaimed the minimized process.
            var field = VideoTransfers.class.getDeclaredField("tasks");
            field.setAccessible(true);
            ((java.util.Map<?, ?>) field.get(null)).clear();
            for (int attempt = 0; attempt < 2; attempt++) {
                var share = Robolectric.buildActivity(VideoDownloadActivity.class, tap).setup();
                try {
                    assertNativeShare(shadowOf(share.get()).getNextStartedActivity());
                    share.pause();
                    share.get().onActivityResult(20, android.app.Activity.RESULT_CANCELED, null);
                    assertTrue(share.get().isFinishing());
                    assertNotNull(VideoTransfers.find(context, task.id));
                    assertNotNull(
                            shadowOf(context.getSystemService(NotificationManager.class))
                                    .getNotification(task.notificationId));
                    assertTrue(task.job.outputs.get(0).file.isFile());
                } finally {
                    share.stop().destroy();
                }
            }
            var sent = Robolectric.buildActivity(VideoDownloadActivity.class, tap).setup();
            try {
                Intent chooser = shadowOf(sent.get()).getNextStartedActivity();
                android.content.IntentSender chosen =
                        chooser.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT_INTENT_SENDER);
                // Android 15 can report the newer ChooserResult through a different extra.
                if (chosen == null)
                    chosen = chooser.getParcelableExtra(Intent.EXTRA_CHOOSER_RESULT_INTENT_SENDER);
                assertNotNull(chosen);
                // Exercise the matching PendingIntent: IntentSender's API 35 binder transport
                // is not implemented by this Robolectric version.
                var callback =
                        android.app.PendingIntent.getBroadcast(
                                context,
                                0,
                                new Intent(context, VideoShareReceiver.class)
                                        .setData(
                                                android.net.Uri.parse(
                                                        "middleshare://transfer/"
                                                                + task.id
                                                                + "/chosen")),
                                android.app.PendingIntent.FLAG_NO_CREATE
                                        | android.app.PendingIntent.FLAG_ONE_SHOT
                                        | android.app.PendingIntent.FLAG_IMMUTABLE);
                assertNotNull(callback);
                assertEquals(callback.getIntentSender(), chosen);
                assertTrue(shadowOf(callback).isImmutable());
                callback.send();
                shadowOf(Looper.getMainLooper()).idle();
                assertNull(VideoTransfers.find(context, task.id));
                assertNull(
                        shadowOf(context.getSystemService(NotificationManager.class))
                                .getNotification(task.notificationId));
                assertTrue(task.job.outputs.get(0).file.isFile());
                assertFalse(
                        context.getPackageManager()
                                .getReceiverInfo(
                                        new ComponentName(context, VideoShareReceiver.class), 0)
                                .exported);
            } finally {
                sent.pause().stop().destroy();
            }
        } finally {
            VideoTransfers.remove(task, false);
            serviceController.destroy();
        }
    }

    private static void assertNativeShare(Intent chooser) {
        assertNotNull(chooser);
        assertEquals(Intent.ACTION_CHOOSER, chooser.getAction());
        assertNull(chooser.getComponent());
        Intent payload = chooser.getParcelableExtra(Intent.EXTRA_INTENT);
        assertEquals(Intent.ACTION_SEND, payload.getAction());
        assertEquals("video/mp4", payload.getType());
        assertNull(payload.getComponent());
        assertNull(payload.getPackage());
        assertNotNull(payload.getParcelableExtra(Intent.EXTRA_STREAM));
        assertNotNull(payload.getClipData());
        assertTrue((payload.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
    }

    @Test
    public void deniedNotificationsStillAllowForegroundHandoff() throws Exception {
        VideoTransfers.Task task = task(TARGET);
        try {
            complete(task);
            shadowOf(RuntimeEnvironment.getApplication())
                    .denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
            var controller =
                    Robolectric.buildActivity(
                                    VideoDownloadActivity.class,
                                    new Intent(context, VideoDownloadActivity.class)
                                            .setAction(Intent.ACTION_SEND)
                                            .putExtra(VideoDownloadActivity.TRANSFER, task.id))
                            .setup();
            try {
                assertEquals(
                        TARGET, shadowOf(controller.get()).getNextStartedActivity().getComponent());
                assertTrue(task.job.dispatched);
            } finally {
                controller.pause().stop().destroy();
            }
        } finally {
            VideoTransfers.remove(task, false);
        }
    }

    @Test
    public void grantingPermissionAfterCompletionRepostsTheReadyNotification() throws Exception {
        shadowOf(RuntimeEnvironment.getApplication())
                .denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        context.getSharedPreferences("video-notifications", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        VideoTransfers.Task task = task(TARGET);
        var serviceController = Robolectric.buildService(VideoDownloadService.class).create();
        serviceController
                .get()
                .onStartCommand(
                        new Intent(context, VideoDownloadService.class)
                                .setAction(VideoDownloadService.START)
                                .putExtra(VideoDownloadActivity.TRANSFER, task.id),
                        0,
                        1);
        var controller =
                Robolectric.buildActivity(
                                VideoDownloadActivity.class,
                                new Intent(context, VideoDownloadActivity.class)
                                        .setAction(Intent.ACTION_SEND)
                                        .putExtra(VideoDownloadActivity.TRANSFER, task.id))
                        .setup();
        try {
            controller.pause();
            complete(task);
            assertNull(
                    shadowOf(context.getSystemService(NotificationManager.class))
                            .getNotification(task.notificationId));
            shadowOf(RuntimeEnvironment.getApplication())
                    .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
            controller
                    .get()
                    .onRequestPermissionsResult(
                            43,
                            new String[] {android.Manifest.permission.POST_NOTIFICATIONS},
                            new int[] {android.content.pm.PackageManager.PERMISSION_GRANTED});
            assertNotNull(
                    shadowOf(context.getSystemService(NotificationManager.class))
                            .getNotification(task.notificationId));
            assertFalse(task.job.dispatched);
        } finally {
            controller.stop().destroy();
            VideoTransfers.remove(task, false);
            serviceController.destroy();
        }
    }

    @Test
    public void pendingDownloadsAreBoundedUntilTheUserSendsOrDiscardsThem() throws Exception {
        var pending = new java.util.ArrayList<VideoTransfers.Task>();
        try {
            for (int i = 0; i < 20; i++) {
                gate = new CountDownLatch(0);
                VideoTransfers.Task saved = task(null);
                pending.add(saved);
                complete(saved);
            }
            assertThrows(java.io.IOException.class, () -> task(null));
            VideoTransfers.remove(pending.remove(0), false);
            VideoTransfers.Task allowed = task(null);
            pending.add(allowed);
            complete(allowed);
        } finally {
            for (VideoTransfers.Task saved : pending) VideoTransfers.remove(saved, false);
        }
    }

    @Test
    public void atomicBackupOnlyRecordIsRecoveredAfterAnInterruptedWrite() throws Exception {
        VideoTransfers.Task task = task(TARGET);
        try {
            complete(task);
            File file =
                    new File(context.getNoBackupFilesDir(), "video-transfers/" + task.id + ".json");
            Files.move(file.toPath(), new File(file.getPath() + ".bak").toPath());
            var field = VideoTransfers.class.getDeclaredField("tasks");
            field.setAccessible(true);
            ((java.util.Map<?, ?>) field.get(null)).clear();
            var restored = VideoTransfers.recover(context);
            assertEquals(1, restored.size());
            assertEquals(task.id, restored.get(0).id);
            assertNull(restored.get(0).job.error);
            VideoTransfers.remove(restored.get(0), false);
        } finally {
            VideoTransfers.remove(task, false);
        }
    }
}
