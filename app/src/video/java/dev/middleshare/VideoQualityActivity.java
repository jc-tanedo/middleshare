package dev.middleshare;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

/** The format lookup starts only when Custom becomes the selected option. */
public class VideoQualityActivity extends Activity implements VideoFormatsJob.Listener {
    private String link;
    private VideoQuality quality = VideoQuality.BEST;
    private VideoFormatsJob lookup;
    private TextView status;
    private Spinner formats;
    private Button download;
    private String host;
    private int selectedFormat;
    private static final class Retained {
        final VideoFormatsJob lookup;
        final VideoQuality quality;
        final int selected;
        Retained(VideoFormatsJob lookup, VideoQuality quality, int selected) {
            this.lookup = lookup; this.quality = quality; this.selected = selected;
        }
    }
    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Ui.themedContext(base));
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (getWindow() != null) {
            getWindow().setGravity(android.view.Gravity.BOTTOM);
            getWindow().setLayout(-1, -2);
        }
        link = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        if (link == null || !link.startsWith("https://") && !link.startsWith("http://")) { finish(); return; }
        host = Settings.videoHost(link);
        quality = Settings.getVideoQuality(this, host);
        quality = VideoQuality.parse(getIntent().getStringExtra(VideoAddon.QUALITY));
        if (state != null) {
            quality = VideoQuality.parse(state.getString("quality"));
            selectedFormat = state.getInt("selected");
        }
        if (getLastNonConfigurationInstance() instanceof Retained) {
            Retained retained = (Retained) getLastNonConfigurationInstance();
            lookup = retained.lookup; quality = retained.quality; selectedFormat = retained.selected;
        }
        LinearLayout page = Ui.screen(this);
        page.addView(Ui.text(this, "Share video", 26, Ui.INK, true));
        page.addView(Ui.text(this, host == null ? link : host, 14, Ui.MUTED, false));
        page.addView(Ui.text(this, "Choose for this share. MP4 is preferred; the 256 MB file limit still applies.", 16, Ui.MUTED, false));
        RadioGroup choices = new RadioGroup(this);
        for (VideoQuality option : VideoQuality.values()) {
            RadioButton button = new RadioButton(this);
            button.setId(option == VideoQuality.CUSTOM ? R.id.video_custom : View.generateViewId());
            button.setTag(option);
            button.setText(option.label);
            button.setMinHeight(Ui.dp(this, 52));
            choices.addView(button);
            if (option == quality) choices.check(button.getId());
        }
        page.addView(choices);
        status = Ui.text(this, "", 14, Ui.MUTED, false);
        status.setId(R.id.video_format_status);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        page.addView(status);
        formats = new Spinner(this);
        formats.setId(R.id.video_formats);
        formats.setContentDescription("Available video formats");
        formats.setMinimumHeight(Ui.dp(this, 52));
        formats.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { selectedFormat = position; }
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        page.addView(formats);
        download = new Button(this);
        download.setId(R.id.video_download);
        download.setText(R.string.download_video);
        download.setOnClickListener(v -> download());
        page.addView(download);
        Button cancel = new Button(this);
        cancel.setText(R.string.cancel);
        cancel.setOnClickListener(v -> finish());
        page.addView(cancel);
        choices.setOnCheckedChangeListener((group, id) -> {
            RadioButton selected = group.findViewById(id);
            if (selected != null) { quality = (VideoQuality) selected.getTag(); selectQuality(); }
        });
        // A failed Custom lookup can be retried even while its radio button stays checked.
        choices.findViewById(R.id.video_custom).setOnClickListener(v -> {
            if (lookup != null && lookup.completed && lookup.error != null) { lookup.cancel(); lookup = null; selectQuality(); }
        });
        selectQuality();
    }
    private void selectQuality() {
        boolean custom = quality == VideoQuality.CUSTOM;
        if (!custom && lookup != null && !lookup.completed) { lookup.cancel(); lookup = null; }
        formats.setVisibility(custom ? View.VISIBLE : View.GONE);
        download.setEnabled(!custom);
        status.setText(custom ? R.string.video_format_loading : R.string.video_preset_hint);
        if (custom) {
            if (lookup == null) lookup = newLookup();
            lookup.attach(this);
        }
    }
    VideoFormatsJob newLookup() { return new VideoFormatsJob(getApplicationContext(), link); }
    @Override public void ready(VideoFormatsJob result) {
        if (isFinishing() || quality != VideoQuality.CUSTOM) return;
        if (result.error != null || result.formats == null || result.formats.isEmpty()) {
            status.setText(result.error == null ? getString(R.string.video_formats_empty) : result.error);
            formats.setVisibility(View.GONE);
            download.setEnabled(false);
            return;
        }
        int selected = Math.min(selectedFormat, result.formats.size() - 1);
        formats.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, result.formats));
        formats.setSelection(Math.max(0, selected));
        formats.setVisibility(View.VISIBLE);
        status.setText(R.string.video_format_hint);
        download.setEnabled(true);
    }
    private void download() {
        if (isFinishing()) return;
        Intent next = new Intent(this, ShareActivity.class).setAction(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, link).putExtra(VideoAddon.QUALITY, quality.name())
                .putExtra(VideoAddon.CONFIRMED, true);
        if (quality == VideoQuality.CUSTOM) {
            if (lookup == null || !lookup.completed || lookup.formats == null || lookup.error != null) return;
            int position = formats.getSelectedItemPosition();
            if (position < 0 || position >= lookup.formats.size()) return;
            VideoFormat format = lookup.formats.get(position);
            next.putExtra(VideoAddon.FORMAT, format.id).putExtra(VideoAddon.AUDIO, format.needsAudio);
        }
        download.setEnabled(false);
        startActivity(next);
        finish();
    }
    @Override protected void onResume() { super.onResume(); if (lookup != null) lookup.attach(this); }
    @Override protected void onPause() { if (lookup != null) lookup.detach(); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("quality", quality.name()); state.putInt("selected", selectedPosition());
        super.onSaveInstanceState(state);
    }
    private int selectedPosition() {
        return formats != null && formats.getSelectedItemPosition() >= 0 ? formats.getSelectedItemPosition() : selectedFormat;
    }
    @Override public Object onRetainNonConfigurationInstance() { return new Retained(lookup, quality, selectedPosition()); }
    @Override protected void onDestroy() {
        if (lookup != null) { lookup.detach(); if (!isChangingConfigurations()) lookup.cancel(); }
        super.onDestroy();
    }
}
