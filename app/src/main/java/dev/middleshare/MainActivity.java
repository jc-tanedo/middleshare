package dev.middleshare;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final VideoQuality[] SITE_QUALITIES = java.util.Arrays.stream(VideoQuality.values())
            .filter(q -> q != VideoQuality.CUSTOM).toArray(VideoQuality[]::new);

    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Ui.themedContext(base));
    }
    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        LinearLayout page = Ui.screen(this);
        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(android.view.Gravity.CENTER_VERTICAL);
        android.widget.ImageView mark = new android.widget.ImageView(this);
        mark.setImageResource(R.drawable.ic_brand_mark);
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44));
        markParams.setMarginEnd(Ui.dp(this, 12));
        brand.addView(mark, markParams);
        brand.addView(Ui.text(this, BuildConfig.VIDEO_TOOLS ? "MiddleShare Video" : "MiddleShare", 26, Ui.INK, true));
        page.addView(brand);
        Ui.gap(this, page, 12);
        page.addView(Ui.text(this, "A smoother way to share.", 16, Ui.MUTED, false));
        Ui.gap(this, page, 28);
        if (BuildConfig.VIDEO_TOOLS) { showVideoAddon(page); return; }

        LinearLayout images = Ui.card(this, page);
        Ui.heading(this, images, "Images", "Turn WebP into a format your destination app accepts.");
        TextView rule = Ui.text(this, "", 14, Ui.GREEN, true);
        images.addView(rule);
        Ui.gap(this, images, 12);
        RadioGroup formats = new RadioGroup(this);
        formats.addView(option("JPEG\nSmaller files · widest compatibility", 1));
        formats.addView(option("PNG\nLossless · keeps transparency", 2));
        images.addView(formats);
        Settings.Format current = Settings.getFormat(this);
        formats.check(current == Settings.Format.JPEG ? 1 : 2);
        rule.setText(getString(R.string.processor_rule, current.name()));
        formats.setOnCheckedChangeListener((group, checked) -> {
            Settings.Format format = checked == 1 ? Settings.Format.JPEG : Settings.Format.PNG;
            Settings.setFormat(this, format);
            rule.setText(getString(R.string.processor_rule, format.name()));
            rule.announceForAccessibility("Default saved: " + format.name());
        });
        Ui.gap(this, images, 10);
        Button pick = Ui.button(this, R.string.try_image, true);
        pick.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
            try { startActivityForResult(intent, 10); }
            catch (android.content.ActivityNotFoundException e) {
                new AlertDialog.Builder(this).setMessage("No image picker is available on this device.").setPositiveButton("OK", null).show();
            }
        });
        images.addView(pick, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout videos = Ui.card(this, page);
        Ui.heading(this, videos, "Video links", "Download a video, then send it straight to another app.");
        videos.addView(Ui.text(this, VideoAddon.available(this) ? "Video add-on installed" : "Optional video add-on", 13, Ui.GREEN, true));
        Ui.gap(this, videos, 16);
        videos.addView(Ui.text(this, "Default quality", 14, Ui.MUTED, true));
        Spinner quality = picker(java.util.Arrays.stream(VideoQuality.values()).map(q -> q.label).toArray(String[]::new), "Default video quality");
        quality.setId(R.id.video_quality);
        quality.setSelection(Settings.getVideoQuality(this).ordinal());
        videos.addView(quality);
        quality.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                Settings.setVideoQuality(MainActivity.this, VideoQuality.values()[position]);
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        Ui.gap(this, videos, 10);
        videos.addView(Ui.text(this, "Best lets yt-dlp choose. Custom loads available formats when you share a link. Up to 256 MB per video.", 13, Ui.MUTED, false));
        Ui.gap(this, videos, 16);
        Button sites = Ui.button(this, R.string.site_preferences, false);
        sites.setOnClickListener(v -> showSites());
        videos.addView(sites, new LinearLayout.LayoutParams(-1, -2));
        if (!VideoAddon.available(this)) {
            Ui.gap(this, videos, 8);
            Button releases = Ui.button(this, R.string.video_releases, false);
            releases.setOnClickListener(v -> VideoAddon.releases(this));
            videos.addView(releases, new LinearLayout.LayoutParams(-1, -2));
        }

        LinearLayout appearance = Ui.card(this, page);
        Ui.heading(this, appearance, "Appearance", "Follow your device, or make it your own.");
        Spinner theme = picker(new String[]{"Use device theme", "Light", "Dark · Nord"}, "Color theme");
        theme.setSelection(Settings.getTheme(this).ordinal());
        appearance.addView(theme);
        theme.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                Settings.Theme selected = Settings.Theme.values()[position];
                if (selected != Settings.getTheme(MainActivity.this)) { Settings.setTheme(MainActivity.this, selected); recreate(); }
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        LinearLayout help = Ui.card(this, page);
        Ui.heading(this, help, "Share from anywhere", "Share an image or link → MiddleShare → choose your destination.");
        help.addView(Ui.text(this, "Images are processed on your device. Video downloads connect to the source site. Other image formats pass through unchanged.", 13, Ui.MUTED, false));
        addAbout(page);
    }
    private Spinner picker(String[] labels, String description) {
        Spinner picker = new Spinner(this);
        picker.setContentDescription(description);
        picker.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        Ui.field(this, picker);
        return picker;
    }
    private void showSites() {
        java.util.List<String> hosts = Settings.videoQualityHosts(this);
        String[] labels = hosts.stream().map(h -> h + "\n" + Settings.getVideoQuality(this, h).label).toArray(String[]::new);
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(R.string.site_preferences);
        if (hosts.isEmpty()) builder.setMessage(R.string.no_site_preferences);
        else builder.setItems(labels, (dialog, which) -> editSite(hosts.get(which)));
        Ui.styleDialog(this, builder.setPositiveButton(R.string.add_site, (dialog, which) -> editSite(null))
                .setNegativeButton(R.string.close, null).show());
    }
    private void editSite(String existingHost) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(this, 24), Ui.dp(this, 12), Ui.dp(this, 24), Ui.dp(this, 8));
        form.addView(Ui.text(this, "This site's quality overrides your default. Other sites keep using the default.", 14, Ui.MUTED, false));
        Ui.gap(this, form, 16);
        EditText domain = new EditText(this);
        domain.setId(R.id.video_domain);
        domain.setSingleLine(true);
        domain.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        domain.setHint(R.string.domain_hint);
        domain.setContentDescription("Website domain");
        Ui.field(this, domain);
        if (existingHost != null) domain.setText(existingHost);
        form.addView(domain);
        Ui.gap(this, form, 12);
        Spinner quality = picker(java.util.Arrays.stream(SITE_QUALITIES).map(q -> q.label).toArray(String[]::new), "Site video quality");
        quality.setId(R.id.video_domain_quality);
        if (existingHost != null) {
            VideoQuality saved = Settings.getVideoQuality(this, existingHost);
            for (int i = 0; i < SITE_QUALITIES.length; i++) if (SITE_QUALITIES[i] == saved) quality.setSelection(i);
        }
        form.addView(quality);
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(existingHost == null ? R.string.add_site : R.string.edit_site)
                .setView(form).setPositiveButton(R.string.save, null).setNegativeButton(R.string.cancel, null);
        if (existingHost != null) builder.setNeutralButton(R.string.remove,
                (d, which) -> Settings.clearVideoQuality(this, existingHost));
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Ui.styleDialog(this, dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String input = domain.getText().toString().trim();
                String host = Settings.videoHost(input.contains("://") ? input : "https://" + input);
                if (host == null || host.isBlank()) { domain.setError(getString(R.string.invalid_domain)); return; }
                if (existingHost != null && !existingHost.equals(host)) Settings.clearVideoQuality(this, existingHost);
                Settings.setVideoQuality(this, host, SITE_QUALITIES[quality.getSelectedItemPosition()]);
                dialog.dismiss();
            });
        });
        dialog.show();
    }
    private void addAbout(LinearLayout page) {
        Button about = Ui.button(this, R.string.about_button, false);
        about.setId(R.id.about_button);
        about.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        Ui.gap(this, page, 8);
        page.addView(about, new LinearLayout.LayoutParams(-1, -2));
    }
    private void showVideoAddon(LinearLayout page) {
        LinearLayout card = Ui.card(this, page);
        Ui.heading(this, card, "Ready for video", "Your video tools are installed.");
        card.addView(Ui.text(this, "Share a video link to the main MiddleShare app to download it and choose a destination. Keep both apps installed.", 16, Ui.MUTED, false));
        addAbout(page);
    }
    private RadioButton option(String label, int id) {
        RadioButton button = new RadioButton(this);
        button.setId(id);
        button.setText(label);
        Ui.choice(this, button);
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
