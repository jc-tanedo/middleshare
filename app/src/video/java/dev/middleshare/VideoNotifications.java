package dev.middleshare;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class VideoNotifications {
    static boolean shouldRequest(Context context) {
        return Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED
                && !context.getSharedPreferences("video-notifications", Context.MODE_PRIVATE)
                        .getBoolean("prompted", false);
    }

    static void markPrompted(Context context) {
        context.getSharedPreferences("video-notifications", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("prompted", true)
                .apply();
    }

    static boolean allowed(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (!manager.areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) return false;
        var channel = manager.getNotificationChannel(VideoDownloadService.CHANNEL);
        return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    private static final Pattern PERCENT = Pattern.compile("([0-9]+(?:[.][0-9]+)?)%");

    static void createChannel(Context context) {
        NotificationChannel channel =
                new NotificationChannel(
                        VideoDownloadService.CHANNEL,
                        context.getString(R.string.transfer_channel),
                        NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(context.getString(R.string.transfer_channel_description));
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    static void publish(Context context, VideoTransfers.Task task) {
        if (!allowed(context)) return;
        createChannel(context);
        try {
            context.getSystemService(NotificationManager.class)
                    .notify(task.notificationId, notification(context, task));
        } catch (SecurityException ignored) {
            /* The UI and saved download list remain usable without notifications. */
        }
    }

    private static PendingIntent open(Context context, VideoTransfers.Task task, String action) {
        Intent intent =
                new Intent(context, VideoDownloadActivity.class)
                        .setAction(action)
                        .setData(Uri.parse("middleshare://transfer/" + task.id + "/" + action))
                        .putExtra(VideoDownloadActivity.TRANSFER, task.id);
        return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent cancel(Context context, VideoTransfers.Task task) {
        Intent intent =
                new Intent(context, VideoDownloadService.class)
                        .setAction(VideoDownloadService.CANCEL)
                        .setData(Uri.parse("middleshare://transfer/" + task.id + "/cancel"))
                        .putExtra(VideoDownloadActivity.TRANSFER, task.id);
        return PendingIntent.getService(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static Notification notification(Context context, VideoTransfers.Task task) {
        boolean done = task.job.completed;
        Notification.Builder builder =
                new Notification.Builder(context, VideoDownloadService.CHANNEL)
                        .setSmallIcon(R.drawable.ic_download_notification)
                        .setOnlyAlertOnce(true)
                        .setVisibility(Notification.VISIBILITY_PRIVATE)
                        .setOngoing(true)
                        .setCategory(
                                done
                                        ? Notification.CATEGORY_STATUS
                                        : Notification.CATEGORY_PROGRESS)
                        .setContentTitle(
                                context.getString(
                                        done
                                                ? (task.ready()
                                                        ? R.string.transfer_ready
                                                        : R.string.transfer_failed)
                                                : R.string.preparing_video))
                        .setContentText(
                                done
                                        ? (task.ready()
                                                ? context.getString(R.string.transfer_tap_send)
                                                : task.job.error)
                                        : task.status)
                        .setContentIntent(
                                open(
                                        context,
                                        task,
                                        done
                                                ? VideoDownloadActivity.SEND_READY
                                                : VideoDownloadActivity.OPEN));
        if (task.destination != null)
            builder.setSubText(ShareDestinations.label(context, task.destination));
        if (done) {
            if (task.ready())
                builder.addAction(
                        new Notification.Action.Builder(
                                        null,
                                        context.getString(R.string.transfer_send),
                                        open(context, task, VideoDownloadActivity.SEND_READY))
                                .build());
            builder.addAction(
                    new Notification.Action.Builder(
                                    null,
                                    context.getString(R.string.transfer_discard),
                                    cancel(context, task))
                            .build());
            builder.setDeleteIntent(cancel(context, task));
        } else {
            Matcher percent = PERCENT.matcher(task.status);
            if (percent.find())
                builder.setProgress(
                        100, Math.min(100, (int) Float.parseFloat(percent.group(1))), false);
            else builder.setProgress(0, 0, true);
            builder.addAction(
                    new Notification.Action.Builder(
                                    null, context.getString(R.string.cancel), cancel(context, task))
                            .build());
        }
        return builder.build();
    }
}
