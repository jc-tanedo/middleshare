package dev.middleshare;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

public final class AboutActivity extends ThemedActivity {
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(Ui.dp(this, 12), Ui.dp(this, 24), Ui.dp(this, 12), Ui.dp(this, 16));
        // The list recycles component rows, including the large native runtime inventory.
        ListView list = new ListView(this);
        list.setId(R.id.credits_list);
        list.setDividerHeight(0);
        list.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 24));
        Ui.installScreen(this, list);
        Button back = Ui.button(this, R.string.back, false);
        back.setOnClickListener(v -> finish());
        page.addView(back);
        Ui.gap(this, page, 16);
        page.addView(Ui.text(this, getString(R.string.about_title), 30, Ui.INK, true));
        String version;
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            version = "";
        }
        page.addView(Ui.text(this, getString(R.string.app_version, version), 15, Ui.MUTED, false));
        Ui.gap(this, page, 12);
        page.addView(Ui.text(this, getString(R.string.about_intro), 15, Ui.MUTED, false));
        Button projectLicense = Ui.button(this, R.string.app_license, false);
        projectLicense.setOnClickListener(
                v ->
                        showLicenses(
                                getString(R.string.app_name),
                                new JSONArray().put("licenses/MiddleShare.txt")));
        page.addView(projectLicense);
        Ui.gap(this, page, 8);
        Button projectSource = Ui.button(this, R.string.project_source, false);
        projectSource.setOnClickListener(
                v -> openSource("https://github.com/jc-tanedo/middleshare"));
        page.addView(projectSource);
        Ui.gap(this, page, 16);
        list.addHeaderView(page, null, false);
        try {
            List<JSONObject> entries = CreditsCatalog.load(this);
            list.setAdapter(new CreditAdapter(entries));
            list.setOnItemClickListener(
                    (parent, view, position, id) -> {
                        int index = position - list.getHeaderViewsCount();
                        if (index >= 0 && index < entries.size()) showComponent(entries.get(index));
                    });
        } catch (IOException e) {
            TextView error =
                    Ui.text(this, getString(R.string.credits_unavailable), 16, Ui.INK, false);
            list.addFooterView(error, null, false);
            list.setAdapter(new CreditAdapter(java.util.Collections.emptyList()));
        }
    }

    private void showComponent(JSONObject entry) {
        String details =
                entry.optString("version")
                        + "\n\n"
                        + entry.optString("license")
                        + "\n\n"
                        + entry.optString("id");
        if (entry.optString("category").equals("Build and test tools"))
            details += "\n\n" + getString(R.string.development_only);
        new AlertDialog.Builder(this)
                .setTitle(entry.optString("name"))
                .setMessage(details)
                .setPositiveButton(
                        R.string.license_texts,
                        (dialog, which) ->
                                showLicenses(
                                        entry.optString("name"),
                                        entry.optJSONArray("noticeAssets")))
                .setNeutralButton(
                        R.string.source, (dialog, which) -> openSource(entry.optString("source")))
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void showLicenses(String title, JSONArray assets) {
        StringBuilder contents = new StringBuilder();
        try {
            if (assets == null || assets.length() == 0) throw new IOException("No license text");
            for (int i = 0; i < assets.length(); i++) {
                if (i > 0) contents.append("\n\n────────────────\n\n");
                contents.append(CreditsCatalog.read(this, assets.optString(i)));
            }
        } catch (IOException e) {
            contents.append(getString(R.string.credits_unavailable));
        }
        TextView text = Ui.text(this, contents.toString(), 14, Ui.INK, false);
        text.setTextIsSelectable(true);
        text.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 12));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private void openSource(String source) {
        Uri uri = Uri.parse(source);
        if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    private final class CreditAdapter extends BaseAdapter {
        private final List<JSONObject> entries;

        CreditAdapter(List<JSONObject> entries) {
            this.entries = entries;
        }

        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public JSONObject getItem(int position) {
            return entries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View recycled, ViewGroup parent) {
            LinearLayout row;
            if (recycled instanceof LinearLayout) row = (LinearLayout) recycled;
            else {
                row = new LinearLayout(AboutActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(
                        Ui.dp(AboutActivity.this, 12),
                        Ui.dp(AboutActivity.this, 14),
                        Ui.dp(AboutActivity.this, 12),
                        Ui.dp(AboutActivity.this, 14));
                row.addView(Ui.text(AboutActivity.this, "", 16, Ui.INK, true));
                row.addView(Ui.text(AboutActivity.this, "", 12, Ui.MUTED, false));
            }
            JSONObject entry = entries.get(position);
            ((TextView) row.getChildAt(0)).setText(entry.optString("name"));
            String subtitle = entry.optString("version") + " · " + entry.optString("license");
            if (entry.optString("category").equals("Build and test tools"))
                subtitle = getString(R.string.development_only) + "\n" + subtitle;
            ((TextView) row.getChildAt(1)).setText(subtitle);
            return row;
        }
    }
}
