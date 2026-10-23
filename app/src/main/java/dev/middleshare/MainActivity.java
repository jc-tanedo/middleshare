package dev.middleshare;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Ui.themedContext(base));
    }
    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        if (BuildConfig.VIDEO_TOOLS) { showVideoAddon(); return; }
        LinearLayout page = Ui.screen(this);
        page.addView(Ui.text(this, "MIDDLESHARE  /  SHARE TOOLS", 12, Ui.GREEN, true));
        Ui.gap(this, page, 24);
        page.addView(Ui.text(this, "A little change.\nA smoother share.", 34, Ui.INK, true));
        Ui.gap(this, page, 12);
        page.addView(Ui.text(this, "Convert WebP images or turn a supported video link into a shareable file.", 16, Ui.MUTED, false));
        Ui.gap(this, page, 28);

        LinearLayout settings = Ui.card(this, page);
        settings.addView(Ui.text(this, "YOUR DEFAULT PROCESSOR", 11, Ui.GREEN, true));
        Ui.gap(this, settings, 12);
        TextView rule = Ui.text(this, "", 26, Ui.INK, true);
        settings.addView(rule);
        Ui.gap(this, settings, 8);
        settings.addView(Ui.text(this, "Choose the format for incoming WebP images. Other image formats pass through unchanged.", 14, Ui.MUTED, false));
        Ui.gap(this, settings, 16);
        RadioGroup formats = new RadioGroup(this);
        formats.setOrientation(RadioGroup.VERTICAL);
        RadioButton jpeg = option("JPEG · widest compatibility\n92% quality · white background", 1);
        RadioButton png = option("PNG · keep transparency\nLossless · usually a larger file", 2);
        formats.addView(jpeg);
        formats.addView(png);
        settings.addView(formats);
        Settings.Format current = Settings.getFormat(this);
        formats.check(current == Settings.Format.JPEG ? 1 : 2);
        rule.setText(getString(R.string.processor_rule, current.name()));
        formats.setOnCheckedChangeListener((group, checked) -> {
            Settings.Format format = checked == 1 ? Settings.Format.JPEG : Settings.Format.PNG;
            Settings.setFormat(this, format);
            rule.setText(getString(R.string.processor_rule, format.name()));
            rule.announceForAccessibility("Default saved: " + format.name());
        });
        Ui.gap(this, page, 24);
        LinearLayout appearance = Ui.card(this, page);
        appearance.addView(Ui.text(this, "APPEARANCE", 11, Ui.GREEN, true));
        Ui.gap(this, appearance, 8);
        android.widget.Spinner themePicker = new android.widget.Spinner(this);
        themePicker.setContentDescription("Color theme");
        themePicker.setMinimumHeight(Ui.dp(this, 48));
        themePicker.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Use device theme", "Light", "Dark"}));
        themePicker.setSelection(Settings.getTheme(this).ordinal());
        appearance.addView(themePicker);
        themePicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                Settings.Theme selected = Settings.Theme.values()[position];
                if (selected != Settings.getTheme(MainActivity.this)) {
                    Settings.setTheme(MainActivity.this, selected);
                    recreate();
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        Ui.gap(this, page, 24);
        LinearLayout videos = Ui.card(this, page);
        videos.addView(Ui.text(this, "VIDEO LINKS  /  YT-DLP", 11, Ui.GREEN, true));
        Ui.gap(this, videos, 12);
        videos.addView(Ui.text(this, "Share a supported link to download one video, then send it to another app. Choose a default quality below; you can change it for each share. Downloads use your internet connection and can be cancelled.", 14, Ui.MUTED, false));
        android.widget.Spinner qualityPicker = new android.widget.Spinner(this);
        qualityPicker.setId(R.id.video_quality);
        qualityPicker.setContentDescription("Default video quality");
        qualityPicker.setMinimumHeight(Ui.dp(this, 48));
        qualityPicker.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                java.util.Arrays.stream(VideoQuality.values()).map(q -> q.label).toArray(String[]::new)));
        qualityPicker.setSelection(Settings.getVideoQuality(this).ordinal());
        videos.addView(qualityPicker);
        videos.addView(Ui.text(this, "Custom loads formats only for the link you share, when selected. Full uses the best available resolution. All qualities keep the 256 MB file limit.", 14, Ui.MUTED, false));
        qualityPicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                Settings.setVideoQuality(MainActivity.this, VideoQuality.values()[position]);
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        Ui.gap(this, videos, 12);
        videos.addView(Ui.text(this, "PER-SITE QUALITY", 11, Ui.GREEN, true));
        videos.addView(Ui.text(this, "Set a saved quality for a host. It overrides the default for links from that site.", 14, Ui.MUTED, false));
        android.widget.EditText domain = new android.widget.EditText(this);
        domain.setId(R.id.video_domain);
        domain.setSingleLine(true);
        domain.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        domain.setHint(R.string.domain_hint);
        domain.setContentDescription("Website domain");
        videos.addView(domain);
        android.widget.Spinner domainQuality = new android.widget.Spinner(this);
        domainQuality.setId(R.id.video_domain_quality);
        domainQuality.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                java.util.Arrays.stream(VideoQuality.values()).filter(q -> q != VideoQuality.CUSTOM).map(q -> q.label).toArray(String[]::new)));
        videos.addView(domainQuality);
        Button saveDomain = new Button(this);
        saveDomain.setText(R.string.save_site_quality);
        saveDomain.setAllCaps(false);
        videos.addView(saveDomain);
        Button clearDomain = new Button(this);
        clearDomain.setText(R.string.clear_site_quality);
        clearDomain.setAllCaps(false);
        videos.addView(clearDomain);
        TextView savedDomains = Ui.text(this, "", 14, Ui.MUTED, false);
        videos.addView(savedDomains);
        Runnable refreshDomains = () -> {
            java.util.List<String> hosts = Settings.videoQualityHosts(this);
            savedDomains.setText(hosts.isEmpty() ? getString(R.string.no_site_preferences)
                    : getString(R.string.saved_sites, String.join(", ", hosts)));
        };
        refreshDomains.run();
        saveDomain.setOnClickListener(v -> {
            String host = Settings.videoHost(domain.getText().toString().contains("://")
                    ? domain.getText().toString() : "https://" + domain.getText().toString());
            if (host == null || host.isBlank()) {
                domain.setError(getString(R.string.invalid_domain));
                return;
            }
            VideoQuality selected = java.util.Arrays.stream(VideoQuality.values()).filter(q -> q != VideoQuality.CUSTOM)
                    .toArray(VideoQuality[]::new)[domainQuality.getSelectedItemPosition()];
            Settings.setVideoQuality(this, host, selected);
            domain.setText("");
            refreshDomains.run();
        });
        clearDomain.setOnClickListener(v -> {
            String host = Settings.videoHost(domain.getText().toString().contains("://")
                    ? domain.getText().toString() : "https://" + domain.getText().toString());
            if (host == null || host.isBlank()) {
                domain.setError(getString(R.string.clear_domain_prompt));
                return;
            }
            Settings.clearVideoQuality(this, host);
            domain.setText("");
            refreshDomains.run();
        });
        videos.addView(Ui.text(this, "Video downloads need the optional MiddleShare Video add-on. Install it once from Releases; images need no add-on.", 14, Ui.MUTED, false));
        Button releases = new Button(this);
        releases.setText(R.string.video_releases);
        releases.setAllCaps(false);
        releases.setOnClickListener(v -> VideoAddon.releases(this));
        videos.addView(releases);
        Ui.gap(this, page, 24);
        Button pick = new Button(this);
        pick.setText(R.string.try_image);
        pick.setAllCaps(false);
        pick.setMinHeight(Ui.dp(this, 52));
        pick.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                    .addCategory(Intent.CATEGORY_OPENABLE);
            try { startActivityForResult(intent, 10); }
            catch (android.content.ActivityNotFoundException e) {
                new android.app.AlertDialog.Builder(this).setMessage("No image picker is available on this device.")
                        .setPositiveButton("OK", null).show();
            }
        });
        page.addView(pick, new LinearLayout.LayoutParams(-1, -2));
        Ui.gap(this, page, 24);
        page.addView(Ui.text(this, "FROM ANY APP", 11, Ui.GREEN, true));
        Ui.gap(this, page, 12);
        page.addView(Ui.text(this, "1   Share an image or video link to MiddleShare.\n2   We convert the image or download the video.\n3   Choose where to send the file.", 16, Ui.INK, false));
        Ui.gap(this, page, 24);
        page.addView(Ui.text(this, "Images stay on your device. Video links contact the source site using yt-dlp. No account or broad storage permission is needed. Animated WebP becomes a still image.", 13, Ui.MUTED, false));
        Ui.gap(this, page, 24);
        Button about = new Button(this);
        about.setId(R.id.about_button);
        about.setText(R.string.about_button);
        about.setAllCaps(false);
        about.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        page.addView(about, new LinearLayout.LayoutParams(-1, -2));
    }
    private void showVideoAddon() {
        LinearLayout page = Ui.screen(this);
        page.addView(Ui.text(this, "MiddleShare Video", 30, Ui.INK, true));
        Ui.gap(this, page, 20);
        page.addView(Ui.text(this, "Video tools are installed. Share a video link to the main MiddleShare app to download it and open the share sheet. Keep both apps installed. Images use only the lightweight main app.", 16, Ui.MUTED, false));
        Button about = new Button(this);
        about.setId(R.id.about_button);
        about.setText(R.string.about_button);
        about.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        page.addView(about);
    }
    private RadioButton option(String label, int id) {
        RadioButton button = new RadioButton(this);
        button.setId(id);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(getColor(Ui.INK));
        button.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        button.setMinHeight(Ui.dp(this, 64));
        return button;
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 10 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Intent share = new Intent(this, ShareActivity.class).setAction(Intent.ACTION_SEND).setType("image/*")
                    .putExtra(Intent.EXTRA_STREAM, data.getData()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share.setClipData(android.content.ClipData.newRawUri("Image", data.getData()));
            startActivity(share);
        }
    }
}
