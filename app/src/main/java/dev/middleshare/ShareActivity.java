package dev.middleshare;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.IOException;

public class ShareActivity extends Activity implements ConversionJob.Listener {
    private ConversionJob job;
    private TextView progress;
    private LinearLayout page;
    private boolean showingError;
    private String pendingLink;
    private ComponentName destination;
    private boolean resumed;
    private boolean sharing;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Ui.themedContext(base));
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (getWindow() != null) {
            getWindow().setGravity(android.view.Gravity.BOTTOM);
            getWindow().setLayout(-1, -2);
        }
        if (state != null) {
            String saved = state.getString("favorite");
            destination = saved == null ? null : ComponentName.unflattenFromString(saved);
        }
        if (state == null) destination = initialDestination();
        page = Ui.screen(this);
        page.setMinimumWidth(Ui.dp(this, 320));
        page.setMinimumHeight(Ui.dp(this, 260));
        Ui.handle(this, page);
        page.addView(Ui.text(this, "MiddleShare", 14, Ui.ACCENT, true));
        Ui.gap(this, page, 16);
        progress = Ui.text(this, "Preparing your share…", 26, Ui.INK, true);
        progress.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        page.addView(progress);
        Ui.gap(this, page, 20);
        ProgressBar spinner = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
        spinner.setIndeterminate(true);
        spinner.setContentDescription(getString(R.string.preparing_share_file));
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(-2, -2);
        spinnerParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        page.addView(spinner, spinnerParams);
        Ui.gap(this, page, 20);
        page.addView(Ui.text(this, getString(R.string.processing_details), 16, Ui.MUTED, false));
        Button cancel = Ui.button(this, R.string.cancel, false);
        cancel.setText(R.string.cancel);
        cancel.setAllCaps(false);
        cancel.setOnClickListener(v -> cancelShare());
        page.addView(cancel, new LinearLayout.LayoutParams(-1, -2));
        if (state != null && state.getBoolean("dispatched")) {
            finish();
            return;
        }
        Object retained = getLastNonConfigurationInstance();
        if (retained instanceof ConversionJob) {
            job = (ConversionJob) retained;
        } else if ((job = resumeVideoJob()) == null) {
            try {
                String link = SharedLink.read(getIntent());
                if (link != null && !BuildConfig.VIDEO_TOOLS) {
                    pendingLink = link;
                    handOffVideo();
                    return;
                }
                VideoQuality quality =
                        VideoQuality.parse(getIntent().getStringExtra(VideoAddon.QUALITY));
                if (link != null && BuildConfig.VIDEO_TOOLS && !isVideoDownloadConfirmed()) {
                    Intent next =
                            new Intent()
                                    .setClassName(this, "dev.middleshare.VideoQualityActivity")
                                    .putExtra(Intent.EXTRA_TEXT, link)
                                    .putExtra(VideoAddon.QUALITY, quality.name());
                    ComponentName trusted = trustedVideoDestination();
                    if (trusted != null)
                        next.putExtra(VideoAddon.DESTINATION, trusted.flattenToString());
                    startActivity(next);
                    finish();
                    return;
                }
                job =
                        link != null
                                ? newVideoJob(
                                        link,
                                        quality,
                                        getIntent().getStringExtra(VideoAddon.FORMAT),
                                        getIntent().getBooleanExtra(VideoAddon.AUDIO, false))
                                : new ConversionJob(
                                        getApplicationContext(),
                                        ShareIntents.read(getIntent()),
                                        Settings.getFormat(this));
            } catch (IOException e) {
                showError(e.getMessage());
            } catch (RuntimeException e) {
                showError(
                        "The shared payload is invalid. Please try sharing the image or video link"
                                + " again.");
            }
        }
        if (job != null && job.video && backgroundVideo()) {
            Button hide = Ui.button(this, R.string.minimize_download, false);
            hide.setId(R.id.video_background);
            hide.setOnClickListener(v -> finish());
            page.addView(hide, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    protected ConversionJob currentJob() {
        return job;
    }

    protected ComponentName selectedDestination() {
        return destination;
    }

    protected ComponentName initialDestination() {
        return null;
    }

    protected ComponentName trustedVideoDestination() {
        return null;
    }

    protected ConversionJob resumeVideoJob() {
        return null;
    }

    protected boolean backgroundVideo() {
        return false;
    }

    protected void shareDispatched() {}

    protected Intent shareChooser(ConversionJob result) {
        return ShareIntents.chooser(this, result.outputs);
    }

    protected void cancelShare() {
        if (job != null) job.cancel();
        finish();
    }

    protected Intent videoHandoff(String link) {
        return VideoAddon.share(this, link);
    }

    ConversionJob newVideoJob(String link, VideoQuality quality, String format, boolean audio) {
        return new ConversionJob(getApplicationContext(), link, quality, format, audio);
    }

    /** Only the non-exported video download activity can bypass quality confirmation. */
    protected boolean isVideoDownloadConfirmed() {
        return false;
    }

    private void handOffVideo() {
        if (VideoAddon.available(this)) {
            try {
                startActivity(videoHandoff(pendingLink));
                finish();
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                /* Offer reinstall. */
            }
        }
        page.removeAllViews();
        page.addView(Ui.text(this, "Video add-on needed", 28, Ui.INK, true));
        page.addView(
                Ui.text(
                        this,
                        "Install MiddleShare Video, then return here to continue sharing your"
                                + " link.",
                        16,
                        Ui.MUTED,
                        false));
        Button retry = Ui.button(this, R.string.try_again, true);
        retry.setId(R.id.video_retry);
        retry.setText(R.string.try_again);
        retry.setOnClickListener(v -> handOffVideo());
        page.addView(retry);
        VideoAddon.prompt(this, this::handOffVideo);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (job != null) job.attach(this);
    }

    @Override
    protected void onPause() {
        resumed = false;
        if (job != null) job.detach(this);
        super.onPause();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return job;
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("dispatched", sharing || (job != null && job.dispatched));
        state.putString("favorite", destination == null ? null : destination.flattenToString());
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        if (job != null) {
            job.detach(this);
            if (!isChangingConfigurations() && !backgroundVideo()) job.cancel();
        }
        super.onDestroy();
    }

    @Override
    public void onProgress(int current, int total) {
        if (job != null && job.video) {
            progress.setText(R.string.preparing_video);
            return;
        }
        progress.setText(
                total > 1
                        ? getString(R.string.preparing_batch, current, total)
                        : getString(R.string.preparing_image));
    }

    @Override
    public void onStatus(String message) {
        progress.setText(message);
    }

    @Override
    public void onComplete(ConversionJob result) {
        if (result.dispatched || sharing || isFinishing() || !resumed) return;
        if (result.error != null) {
            showError(result.error);
            return;
        }
        if (result.outputs.isEmpty()) {
            showError("There are no files to share.");
            return;
        }
        if (destination != null) {
            Intent targeted = ShareDestinations.outgoing(this, result.outputs, destination);
            if (targeted != null) {
                try {
                    startActivityForResult(targeted, 20);
                    result.dispatched = true;
                    shareDispatched();
                    return;
                } catch (ActivityNotFoundException | SecurityException ignored) {
                    // The app may have changed since the user selected it.
                }
            }
            destination = null;
            android.widget.Toast.makeText(
                            this,
                            R.string.destination_unavailable,
                            android.widget.Toast.LENGTH_LONG)
                    .show();
        }
        try {
            startActivityForResult(shareChooser(result), 20);
            sharing = true;
            // A system chooser can be canceled. Keep service-owned files available for retry.
            if (!backgroundVideo()) result.dispatched = true;
            shareDispatched();
        } catch (ActivityNotFoundException | SecurityException e) {
            showError("No app is available to receive this file.");
        }
    }

    private void showError(String message) {
        if (showingError) return;
        showingError = true;
        page.removeAllViews();
        page.addView(Ui.text(this, "Couldn’t prepare this share", 28, Ui.INK, true));
        Ui.gap(this, page, 16);
        page.addView(Ui.text(this, message, 16, Ui.MUTED, false));
        Ui.gap(this, page, 24);
        Button close = Ui.button(this, R.string.close, false);
        close.setText(R.string.close);
        close.setAllCaps(false);
        close.setOnClickListener(v -> cancelShare());
        page.addView(close, new LinearLayout.LayoutParams(-1, -2));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 20) finish();
    }
}
