package dev.middleshare;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

/** Owns the site preference dialogs; persistent settings remain in Settings. */
final class SitePreferences {
    private static final VideoQuality[] SITE_QUALITIES =
            java.util.Arrays.stream(VideoQuality.values())
                    .filter(q -> q != VideoQuality.CUSTOM)
                    .toArray(VideoQuality[]::new);

    private final Activity activity;

    private SitePreferences(Activity activity) {
        this.activity = activity;
    }

    static void show(Activity activity) {
        new SitePreferences(activity).showSites();
    }

    private void showSites() {
        java.util.List<String> hosts = Settings.videoQualityHosts(activity);
        String[] labels =
                hosts.stream()
                        .map(h -> h + "\n" + Settings.getVideoQuality(activity, h).label)
                        .toArray(String[]::new);
        AlertDialog.Builder builder =
                new AlertDialog.Builder(activity).setTitle(R.string.site_preferences);
        if (hosts.isEmpty()) builder.setMessage(R.string.no_site_preferences);
        else builder.setItems(labels, (dialog, which) -> editSite(hosts.get(which)));
        Ui.styleDialog(
                activity,
                builder.setPositiveButton(R.string.add_site, (dialog, which) -> editSite(null))
                        .setNegativeButton(R.string.close, null)
                        .show());
    }

    private void editSite(String existingHost) {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(
                Ui.dp(activity, 24), Ui.dp(activity, 12), Ui.dp(activity, 24), Ui.dp(activity, 8));
        form.addView(
                Ui.text(
                        activity,
                        "This site's quality overrides your default. Other sites keep using the"
                                + " default.",
                        14,
                        Ui.MUTED,
                        false));
        Ui.gap(activity, form, 16);
        EditText domain = new EditText(activity);
        domain.setId(R.id.video_domain);
        domain.setSingleLine(true);
        domain.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        domain.setHint(R.string.domain_hint);
        domain.setContentDescription("Website domain");
        Ui.field(activity, domain);
        if (existingHost != null) domain.setText(existingHost);
        form.addView(domain);
        Ui.gap(activity, form, 12);
        Spinner quality =
                Ui.picker(
                        activity,
                        java.util.Arrays.stream(SITE_QUALITIES)
                                .map(q -> q.label)
                                .toArray(String[]::new),
                        "Site video quality");
        quality.setId(R.id.video_domain_quality);
        if (existingHost != null) {
            VideoQuality saved = Settings.getVideoQuality(activity, existingHost);
            for (int i = 0; i < SITE_QUALITIES.length; i++)
                if (SITE_QUALITIES[i] == saved) quality.setSelection(i);
        }
        form.addView(quality);
        AlertDialog.Builder builder =
                new AlertDialog.Builder(activity)
                        .setTitle(existingHost == null ? R.string.add_site : R.string.edit_site)
                        .setView(form)
                        .setPositiveButton(R.string.save, null)
                        .setNegativeButton(R.string.cancel, null);
        if (existingHost != null)
            builder.setNeutralButton(
                    R.string.remove,
                    (d, which) -> Settings.clearVideoQuality(activity, existingHost));
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(
                d -> {
                    Ui.styleDialog(activity, dialog);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                            .setOnClickListener(
                                    v -> {
                                        String input = domain.getText().toString().trim();
                                        String host =
                                                Settings.videoHost(
                                                        input.contains("://")
                                                                ? input
                                                                : "https://" + input);
                                        if (host == null || host.isBlank()) {
                                            domain.setError(
                                                    activity.getString(R.string.invalid_domain));
                                            return;
                                        }
                                        if (existingHost != null && !existingHost.equals(host))
                                            Settings.clearVideoQuality(activity, existingHost);
                                        Settings.setVideoQuality(
                                                activity,
                                                host,
                                                SITE_QUALITIES[quality.getSelectedItemPosition()]);
                                        dialog.dismiss();
                                    });
                });
        dialog.show();
    }
}
