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
            assertEquals(2, progress.actions.length);
            task.status = "Downloading video… 50%";
            assertEquals(50, service.notification(task).extras.getInt(Notification.EXTRA_PROGRESS));
            Intent cancel = shadowOf(progress.actions[1].actionIntent).getSavedIntent();
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
    public void cancelingReadyAppSelectionKeepsTheVideoAndNotification() throws Exception {
        VideoTransfers.Task task = task(null);
        var serviceController = Robolectric.buildService(VideoDownloadService.class).create();
        try {
            serviceController
                    .get()
                    .onStartCommand(
                            new Intent(context, VideoDownloadService.class)
                                    .setAction(VideoDownloadService.START)
                                    .putExtra(VideoDownloadActivity.TRANSFER, task.id),
                            0,
                            1);
            complete(task);
            var controller =
                    Robolectric.buildActivity(
                                    VideoDownloadActivity.class,
                                    new Intent(context, VideoDownloadActivity.class)
                                            .setAction(VideoDownloadActivity.SEND_READY)
                                            .putExtra(VideoDownloadActivity.TRANSFER, task.id))
                            .setup();
            try {
                assertEquals(
                        Intent.ACTION_PICK_ACTIVITY,
                        shadowOf(controller.get()).getNextStartedActivity().getAction());
                controller
                        .get()
                        .onActivityResult(
                                ShareActivity.PICK_DESTINATION,
                                android.app.Activity.RESULT_CANCELED,
                                null);
                assertTrue(controller.get().isFinishing());
                assertFalse(task.job.dispatched);
                assertNotNull(VideoTransfers.find(context, task.id));
                assertNotNull(
                        shadowOf(context.getSystemService(NotificationManager.class))
                                .getNotification(task.notificationId));
            } finally {
                controller.pause().stop().destroy();
            }
        } finally {
            VideoTransfers.remove(task, false);
            serviceController.destroy();
        }
    }

    @Test
    public void readyVideoSelectionReturnsFromPrivatePickerAndHandsOffAfterResume()
            throws Exception {
        VideoTransfers.Task task = task(null);
        try {
            complete(task);
            // Also exercise the saved-ready path used when Android has reclaimed the process.
            var field = VideoTransfers.class.getDeclaredField("tasks");
            field.setAccessible(true);
            ((java.util.Map<?, ?>) field.get(null)).clear();
            VideoTransfers.Task restored = VideoTransfers.find(context, task.id);
            VideoNotifications.publish(context, restored);
            var controller =
                    Robolectric.buildActivity(
                                    VideoDownloadActivity.class,
                                    new Intent(context, VideoDownloadActivity.class)
                                            .setAction(VideoDownloadActivity.SEND_READY)
                                            .putExtra(VideoDownloadActivity.TRANSFER, task.id))
                            .setup();
            try {
                Intent pickerIntent = shadowOf(controller.get()).getNextStartedActivity();
                assertEquals(
                        DestinationPickerActivity.class.getName(),
                        pickerIntent.getComponent().getClassName());
                Intent prototype = pickerIntent.getParcelableExtra(Intent.EXTRA_INTENT);
                assertEquals("video/mp4", prototype.getType());
                assertNull(prototype.getClipData());
                controller.pause();
                var picker =
                        Robolectric.buildActivity(DestinationPickerActivity.class, pickerIntent)
                                .setup();
                try {
                    android.widget.Button receiver =
                            findButton(picker.get().getWindow().getDecorView(), "Receiver");
                    assertNotNull(receiver);
                    receiver.performClick();
                    assertTrue(picker.get().isFinishing());
                    assertEquals(
                            android.app.Activity.RESULT_OK, shadowOf(picker.get()).getResultCode());
                    controller
                            .get()
                            .onActivityResult(
                                    ShareActivity.PICK_DESTINATION,
                                    shadowOf(picker.get()).getResultCode(),
                                    shadowOf(picker.get()).getResultIntent());
                    assertFalse(controller.get().isFinishing());
                    assertFalse(restored.job.dispatched);
                    controller.resume();
                    Intent outgoing = shadowOf(controller.get()).getNextStartedActivity();
                    assertNotNull(outgoing);
                    assertEquals(TARGET, outgoing.getComponent());
                    assertEquals("video/mp4", outgoing.getType());
                    assertNotNull(outgoing.getParcelableExtra(Intent.EXTRA_STREAM));
                    assertNotNull(outgoing.getClipData());
                    assertTrue((outgoing.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
                    assertNull(VideoTransfers.find(context, task.id));
                    assertNull(
                            shadowOf(context.getSystemService(NotificationManager.class))
                                    .getNotification(task.notificationId));
                    assertTrue(restored.job.outputs.get(0).file.isFile());
                } finally {
                    picker.pause().stop().destroy();
                }
            } finally {
                controller.pause().stop().destroy();
                VideoTransfers.remove(restored, false);
            }
        } finally {
            VideoTransfers.remove(task, false);
        }
    }

    private static android.widget.Button findButton(android.view.View view, String label) {
        if (view instanceof android.widget.Button
                && label.contentEquals(((android.widget.Button) view).getText()))
            return (android.widget.Button) view;
        if (view instanceof android.view.ViewGroup) {
            var group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                var found = findButton(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
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
