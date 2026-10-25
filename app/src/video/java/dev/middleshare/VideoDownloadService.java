package dev.middleshare;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/** Owns the running download; completion actions directly open an Activity (no trampoline). */
public final class VideoDownloadService extends Service implements VideoTransfers.Observer {
    static final String START = "dev.middleshare.DOWNLOAD_START";
    static final String CANCEL = "dev.middleshare.DOWNLOAD_CANCEL";
    static final String CHANNEL = "video-downloads";
    private VideoTransfers.Task active;
    private PowerManager.WakeLock wakeLock;
    private boolean foreground;
    private int lastStartId;

    @Override
    public void onCreate() {
        super.onCreate();
        VideoNotifications.createChannel(this);
        for (VideoTransfers.Task recovered : VideoTransfers.recover(this))
            if (recovered.job.completed) notifySafely(recovered);
        VideoTransfers.observe(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        lastStartId = startId;
        if (intent == null) {
            stopSelf(lastStartId);
            return START_NOT_STICKY;
        }
        VideoTransfers.Task task =
                VideoTransfers.find(this, intent.getStringExtra(VideoDownloadActivity.TRANSFER));
        if (CANCEL.equals(intent.getAction())) {
            VideoTransfers.discard(this, intent.getStringExtra(VideoDownloadActivity.TRANSFER));
            if (active == null || active.removed || active.job.completed) stopSelf(lastStartId);
            return START_NOT_STICKY;
        }
        if (task == null || task.removed) {
            stopSelf(lastStartId);
            return START_NOT_STICKY;
        }
        active = task;
        Notification notification = notification(task);
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(
                    task.notificationId,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(task.notificationId, notification);
        foreground = true;
        if (!task.job.completed) {
            wakeLock =
                    getSystemService(PowerManager.class)
                            .newWakeLock(
                                    PowerManager.PARTIAL_WAKE_LOCK, "MiddleShare:video-download");
            wakeLock.acquire(VideoDownloader.MAX_DURATION_MS + 60_000);
        }
        changed(task);
        return START_NOT_STICKY;
    }

    Notification notification(VideoTransfers.Task task) {
        return VideoNotifications.notification(this, task);
    }

    @Override
    public void changed(VideoTransfers.Task task) {
        if (task != active) return;
        if (task.removed) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            foreground = false;
            releaseWakeLock();
            stopSelf(lastStartId);
            return;
        }
        if (task.job.completed) {
            if (foreground) stopForeground(STOP_FOREGROUND_REMOVE);
            foreground = false;
            releaseWakeLock();
            notifySafely(task);
            stopSelf(lastStartId);
        } else if (android.os.SystemClock.elapsedRealtime() - task.lastNotification >= 1000) {
            task.lastNotification = android.os.SystemClock.elapsedRealtime();
            notifySafely(task);
        }
    }

    private void notifySafely(VideoTransfers.Task task) {
        VideoNotifications.publish(this, task);
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
    }

    @Override
    public void onTimeout(int startId, int foregroundServiceType) {
        if (active != null && !active.job.completed)
            active.job.interrupt(getString(R.string.transfer_interrupted));
        stopSelf(lastStartId);
    }

    @Override
    public void onDestroy() {
        VideoTransfers.forget(this);
        if (active != null && !active.removed && !active.job.completed) {
            active.job.interrupt(getString(R.string.transfer_interrupted));
            notifySafely(active);
        }
        releaseWakeLock();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
