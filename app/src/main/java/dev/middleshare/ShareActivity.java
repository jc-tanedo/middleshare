package dev.middleshare;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.IOException;

public final class ShareActivity extends Activity implements ConversionJob.Listener {
    private ConversionJob job;
    private TextView progress;
    private LinearLayout page;
    private boolean showingError;

    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Ui.themedContext(base));
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        page = Ui.screen(this);
        page.addView(Ui.text(this, "MIDDLESHARE", 12, Ui.GREEN, true));
        Ui.gap(this, page, 48);
        progress = Ui.text(this, "Preparing your share…", 30, Ui.INK, true);
        progress.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        page.addView(progress);
        Ui.gap(this, page, 20);
        page.addView(new ProgressBar(this));
        Ui.gap(this, page, 20);
        page.addView(Ui.text(this, "Your share sheet opens when the file is ready. Images convert locally; video links download from the source site.", 16, Ui.MUTED, false));
        Button cancel = new Button(this);
        cancel.setText(R.string.cancel);
        cancel.setAllCaps(false);
        cancel.setOnClickListener(v -> finish());
        page.addView(cancel);
        if (state != null && state.getBoolean("dispatched")) { finish(); return; }
        Object retained = getLastNonConfigurationInstance();
        if (retained instanceof ConversionJob) {
            job = (ConversionJob) retained;
        } else {
            try {
                String link = SharedLink.read(getIntent());
                job = link != null ? new ConversionJob(getApplicationContext(), link)
                        : new ConversionJob(getApplicationContext(), ShareIntents.read(getIntent()), Settings.getFormat(this));
            } catch (IOException e) { showError(e.getMessage()); }
            catch (RuntimeException e) { showError("The shared payload is invalid. Please try sharing the image or video link again."); }
        }
    }
    @Override protected void onResume() { super.onResume(); if (job != null) job.attach(this); }
    @Override protected void onPause() { if (job != null) job.detach(); super.onPause(); }
    @Override public Object onRetainNonConfigurationInstance() { return job; }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("dispatched", job != null && job.dispatched);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        if (job != null) {
            job.detach();
            if (!isChangingConfigurations()) job.cancel();
        }
        super.onDestroy();
    }
    @Override public void onProgress(int current, int total) {
        if (job != null && job.video) { progress.setText(R.string.preparing_video); return; }
        progress.setText(total > 1 ? getString(R.string.preparing_batch, current, total) : getString(R.string.preparing_image));
    }
    @Override public void onStatus(String message) { progress.setText(message); }
    @Override public void onComplete(ConversionJob result) {
        if (result.dispatched || isFinishing()) return;
        if (result.error != null) { showError(result.error); return; }
        if (result.outputs.isEmpty()) { showError("There are no files to share."); return; }
        try {
            startActivityForResult(ShareIntents.chooser(this, result.outputs), 20);
            result.dispatched = true;
        } catch (ActivityNotFoundException e) { showError("No app is available to receive this file."); }
    }
    private void showError(String message) {
        if (showingError) return;
        showingError = true;
        page.removeAllViews();
        page.addView(Ui.text(this, "Couldn’t prepare this share", 28, Ui.INK, true));
        Ui.gap(this, page, 16);
        page.addView(Ui.text(this, message, 16, Ui.MUTED, false));
        Ui.gap(this, page, 24);
        Button close = new Button(this);
        close.setText(R.string.close);
        close.setAllCaps(false);
        close.setOnClickListener(v -> finish());
        page.addView(close);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 20) finish();
    }
}
