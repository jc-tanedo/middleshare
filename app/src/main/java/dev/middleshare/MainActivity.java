package dev.middleshare;

import android.app.Activity;
import android.app.AlertDialog;
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

public final class MainActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Ui.themedContext(base));
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        LinearLayout page = Ui.screen(this);
        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(android.view.Gravity.CENTER_VERTICAL);
        android.widget.ImageView mark = new android.widget.ImageView(this);
        mark.setImageResource(R.drawable.ic_brand_mark);
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams markParams =
                new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44));
        markParams.setMarginEnd(Ui.dp(this, 12));
        brand.addView(mark, markParams);
        brand.addView(
                Ui.text(
                        this,
                        BuildConfig.VIDEO_TOOLS ? "MiddleShare Video" : "MiddleShare",
                        26,
                        Ui.INK,
                        true));
        page.addView(brand);
        Ui.gap(this, page, 12);
        page.addView(Ui.text(this, "A smoother way to share.", 16, Ui.MUTED, false));
        Ui.gap(this, page, 28);
        if (BuildConfig.VIDEO_TOOLS) {
            showVideoAddon(page);
            return;
        }

        FavoritePreferences.addCard(this, page);
        LinearLayout images = Ui.card(this, page);
        Ui.heading(this, images, "Images", "Turn WebP into a format your destination app accepts.");
        TextView rule = Ui.text(this, "", 14, Ui.ACCENT, true);
        images.addView(rule);
        Ui.gap(this, images, 12);
        RadioGroup formats = new RadioGroup(this);
        formats.addView(option("JPEG\nSmaller files · widest compatibility", 1));
        formats.addView(option("PNG\nLossless · keeps transparency", 2));
        images.addView(formats);
        Settings.Format current = Settings.getFormat(this);
        formats.check(current == Settings.Format.JPEG ? 1 : 2);
        rule.setText(getString(R.string.processor_rule, current.name()));
        formats.setOnCheckedChangeListener(
                (group, checked) -> {
                    Settings.Format format =
                            checked == 1 ? Settings.Format.JPEG : Settings.Format.PNG;
                    Settings.setFormat(this, format);
                    rule.setText(getString(R.string.processor_rule, format.name()));
                    rule.announceForAccessibility("Default saved: " + format.name());
                });
        Ui.gap(this, images, 10);
        Button pick = Ui.button(this, R.string.try_image, true);
        pick.setOnClickListener(
                v -> {
                    Intent intent =
                            new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                    .setType("image/*")
                                    .addCategory(Intent.CATEGORY_OPENABLE);
                    try {
                        startActivityForResult(intent, 10);
                    } catch (android.content.ActivityNotFoundException e) {
                        new AlertDialog.Builder(this)
                                .setMessage("No image picker is available on this device.")
                                .setPositiveButton("OK", null)
                                .show();
                    }
                });
        images.addView(pick, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout videos = Ui.card(this, page);
        Ui.heading(
                this,
                videos,
                "Video links",
                "Download a video, then send it straight to another app.");
        videos.addView(
                Ui.text(
                        this,
                        VideoAddon.available(this)
                                ? "Video add-on installed"
                                : "Optional video add-on",
                        13,
                        Ui.ACCENT,
                        true));
        Ui.gap(this, videos, 16);
        videos.addView(Ui.text(this, "Default quality", 14, Ui.MUTED, true));
        Spinner quality =
                picker(
                        java.util.Arrays.stream(VideoQuality.values())
                                .map(q -> q.label)
                                .toArray(String[]::new),
                        "Default video quality");
        quality.setId(R.id.video_quality);
        quality.setSelection(Settings.getVideoQuality(this).ordinal());
        videos.addView(quality);
        quality.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            View view,
                            int position,
                            long id) {
                        Settings.setVideoQuality(
                                MainActivity.this, VideoQuality.values()[position]);
                    }

                    public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                });
        Ui.gap(this, videos, 10);
        videos.addView(
                Ui.text(
                        this,
                        "Best lets yt-dlp choose. Custom loads available formats when you share a"
                                + " link. Up to 256 MB per video.",
                        13,
                        Ui.MUTED,
                        false));
        Ui.gap(this, videos, 16);
        Button sites = Ui.button(this, R.string.site_preferences, false);
        sites.setOnClickListener(v -> SitePreferences.show(this));
        videos.addView(sites, new LinearLayout.LayoutParams(-1, -2));
        if (VideoAddon.available(this)) {
            Ui.gap(this, videos, 8);
            Button tools = Ui.button(this, R.string.video_tools, false);
            tools.setOnClickListener(
                    v -> {
                        Intent launch =
                                getPackageManager().getLaunchIntentForPackage(VideoAddon.PACKAGE);
                        if (launch != null) startActivity(launch);
                        else VideoAddon.releases(this);
                    });
            videos.addView(tools, new LinearLayout.LayoutParams(-1, -2));
        } else {
            Ui.gap(this, videos, 8);
            Button releases = Ui.button(this, R.string.video_releases, false);
            releases.setOnClickListener(v -> VideoAddon.releases(this));
            videos.addView(releases, new LinearLayout.LayoutParams(-1, -2));
        }

        LinearLayout appearance = Ui.card(this, page);
        Ui.heading(this, appearance, "Appearance", "Follow your device, or make it your own.");
        Spinner theme =
                picker(new String[] {"Use device theme", "Light", "Dark · Nord"}, "Color theme");
        theme.setSelection(Settings.getTheme(this).ordinal());
        appearance.addView(theme);
        theme.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            View view,
                            int position,
                            long id) {
                        Settings.Theme selected = Settings.Theme.values()[position];
                        if (selected != Settings.getTheme(MainActivity.this)) {
                            Settings.setTheme(MainActivity.this, selected);
                            recreate();
                        }
                    }

                    public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                });
        LinearLayout help = Ui.card(this, page);
        Ui.heading(
                this,
                help,
                "Share from anywhere",
                "Share an image or link → MiddleShare → choose your destination.");
        help.addView(
                Ui.text(
                        this,
                        "Images are processed on your device. Video downloads connect to the source"
                                + " site. Other image formats pass through unchanged.",
                        13,
                        Ui.MUTED,
                        false));
        addAbout(page);
    }

    private Spinner picker(String[] labels, String description) {
        Spinner picker = new Spinner(this);
        picker.setContentDescription(description);
        picker.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        Ui.field(this, picker);
        return picker;
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
        card.addView(
                Ui.text(
                        this,
                        "Share a video link to the main MiddleShare app to download it and choose a"
                                + " destination. Keep both apps installed.",
                        16,
                        Ui.MUTED,
                        false));
        Button downloads = Ui.button(this, R.string.view_downloads, true);
        downloads.setOnClickListener(
                v ->
                        startActivity(
                                new Intent()
                                        .setClassName(
                                                this, "dev.middleshare.VideoTransfersActivity")));
        page.addView(downloads);
        Ui.gap(this, page, 8);
        Button notifications = Ui.button(this, R.string.transfer_notification_settings, false);
        notifications.setOnClickListener(
                v ->
                        startActivity(
                                new Intent(
                                                android.provider.Settings
                                                        .ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(
                                                android.provider.Settings.EXTRA_APP_PACKAGE,
                                                getPackageName())));
        page.addView(notifications);
        addAbout(page);
    }

    private RadioButton option(String label, int id) {
        RadioButton button = new RadioButton(this);
        button.setId(id);
        button.setText(label);
        Ui.choice(this, button);
        return button;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 10
                && resultCode == RESULT_OK
                && data != null
                && data.getData() != null) {
            Intent share =
                    new Intent(this, ShareActivity.class)
                            .setAction(Intent.ACTION_SEND)
                            .setType("image/*")
                            .putExtra(Intent.EXTRA_STREAM, data.getData())
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share.setClipData(android.content.ClipData.newRawUri("Image", data.getData()));
            startActivity(share);
        }
    }
}
