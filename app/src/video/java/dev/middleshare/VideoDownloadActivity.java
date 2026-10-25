package dev.middleshare;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

import java.io.IOException;
import java.util.Collections;

/** Private UI for a service-owned download or a notification's direct, user-initiated handoff. */
public class VideoDownloadActivity extends ShareActivity {
    static final String TRANSFER = "dev.middleshare.TRANSFER";
    static final String OPEN = "dev.middleshare.TRANSFER_OPEN";
    static final String SEND_READY = "dev.middleshare.TRANSFER_SEND";
    static final String PICK = "dev.middleshare.TRANSFER_PICK";
    private VideoTransfers.Task task;
    private boolean picked;

    @Override
    public void onCreate(Bundle state) {
        picked = state != null && state.getBoolean("picker_opened");
        if (state != null && state.getString(TRANSFER) != null)
            getIntent().putExtra(TRANSFER, state.getString(TRANSFER));
        super.onCreate(state);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("picker_opened", picked);
        if (task() != null) state.putString(TRANSFER, task.id);
        super.onSaveInstanceState(state);
    }

    private VideoTransfers.Task task() {
        if (task == null) task = VideoTransfers.find(this, getIntent().getStringExtra(TRANSFER));
        return task;
    }

    @Override
    protected boolean isVideoDownloadConfirmed() {
        return true;
    }

    @Override
    protected boolean backgroundVideo() {
        return true;
    }

    @Override
    protected boolean needsReadyDestination() {
        return true;
    }

    @Override
    protected boolean canDispatchVideo() {
        return !VideoNotifications.allowed(this)
                || OPEN.equals(getIntent().getAction())
                || SEND_READY.equals(getIntent().getAction())
                || (PICK.equals(getIntent().getAction()) && picked);
    }

    @Override
    protected ComponentName initialDestination() {
        VideoTransfers.Task saved = task();
        if (saved != null) return saved.destination;
        String raw = getIntent().getStringExtra(VideoAddon.DESTINATION);
        return raw == null ? null : ComponentName.unflattenFromString(raw);
    }

    @Override
    protected ConversionJob resumeVideoJob() {
        if (!getIntent().hasExtra(TRANSFER)) return null;
        VideoTransfers.Task saved = task();
        return saved == null
                ? ConversionJob.completedVideo(
                        Collections.emptyList(), getString(R.string.transfer_missing))
                : saved.job;
    }

    @Override
    ConversionJob newVideoJob(String link, VideoQuality quality, String format, boolean audio) {
        try {
            task =
                    VideoTransfers.create(
                            this, link, quality, format, audio, selectedDestination(), null);
            // Keep the transfer ID in the private activity's own intent for rotation and
            // notification re-entry.
            getIntent().putExtra(TRANSFER, task.id);
            Intent start =
                    new Intent(this, VideoDownloadService.class)
                            .setAction(VideoDownloadService.START)
                            .putExtra(TRANSFER, task.id);
            try {
                startForegroundService(start);
            } catch (RuntimeException error) {
                VideoTransfers.remove(task, false);
                throw error;
            }
            return task.job;
        } catch (IOException error) {
            return ConversionJob.completedVideo(Collections.emptyList(), error.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing() || currentJob() == null || currentJob().dispatched) return;
        if (!currentJob().completed && VideoNotifications.shouldRequest(this)) {
            VideoNotifications.markPrompted(this);
            requestPermissions(new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 43);
            return;
        }
        if (PICK.equals(getIntent().getAction()) && !picked) {
            // Defer completion until the destination picker has returned.
            picked = true;
            openDestinationPicker();
        } else if (Intent.ACTION_SEND.equals(getIntent().getAction())
                && currentJob().error == null) {
            if (VideoNotifications.allowed(this)) finish();
            else
                android.widget.Toast.makeText(
                                this,
                                R.string.transfer_notifications_disabled,
                                android.widget.Toast.LENGTH_LONG)
                        .show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 43 && VideoNotifications.allowed(this)) {
            if (task() != null && task.job.completed && !task.removed)
                VideoNotifications.publish(this, task);
            if (Intent.ACTION_SEND.equals(getIntent().getAction())
                    && currentJob() != null
                    && currentJob().error == null) finish();
        }
    }

    @Override
    protected void destinationChanged(ComponentName target) {
        if (task() != null) task.destination(target);
        if (currentJob() != null && currentJob().completed && target == null) finish();
        else if (currentJob() != null
                && !currentJob().completed
                && VideoNotifications.allowed(this))
            new android.os.Handler(getMainLooper())
                    .post(
                            () -> {
                                if (!isFinishing()
                                        && currentJob() != null
                                        && !currentJob().dispatched) finish();
                            });
    }

    @Override
    protected void shareDispatched() {
        VideoTransfers.remove(task(), true);
    }

    @Override
    protected void cancelShare() {
        if (task() != null) VideoTransfers.remove(task, false);
        else VideoTransfers.discard(this, getIntent().getStringExtra(TRANSFER));
        super.cancelShare();
    }
}
