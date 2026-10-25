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
    private VideoTransfers.Task task;

    @Override
    public void onCreate(Bundle state) {
        if (state != null && state.getString(TRANSFER) != null)
            getIntent().putExtra(TRANSFER, state.getString(TRANSFER));
        super.onCreate(state);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
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
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 43 && VideoNotifications.allowed(this)) {
            if (task() != null && task.job.completed && !task.removed)
                VideoNotifications.publish(this, task);
        }
    }

    @Override
    protected Intent shareChooser(ConversionJob result) {
        return ShareIntents.chooser(
                this,
                result.outputs,
                VideoShareReceiver.callback(this, getIntent().getStringExtra(TRANSFER)));
    }

    @Override
    protected void shareDispatched() {
        // Direct favorite handoff is observable; the native chooser's final Send is not.
        if (currentJob() != null && currentJob().dispatched) VideoTransfers.remove(task(), true);
    }

    @Override
    protected void cancelShare() {
        if (task() != null) VideoTransfers.remove(task, false);
        else VideoTransfers.discard(this, getIntent().getStringExtra(TRANSFER));
        super.cancelShare();
    }
}
