package dev.middleshare;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/** Returns only a verified component; never launches an app with an unfinished payload. */
public final class DestinationPickerActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Ui.themedContext(base));
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setGravity(android.view.Gravity.BOTTOM);
        getWindow().setLayout(-1, -2);
        setResult(RESULT_CANCELED);
        Intent prototype = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
        String mime = prototype == null ? null : prototype.getType();
        if (mime == null || !mime.startsWith("video/")) {
            finish();
            return;
        }
        // Rebuild the query rather than forwarding extras, flags or URI grants from a caller.
        Intent query = new Intent(Intent.ACTION_SEND).setType(mime);
        var pm = getPackageManager();
        var candidates = new LinkedHashMap<ComponentName, ResolveInfo>();
        for (ResolveInfo info :
                pm.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY)) {
            if (!ShareDestinations.receiverAllowed(this, info.activityInfo)) continue;
            candidates.put(
                    new ComponentName(info.activityInfo.packageName, info.activityInfo.name), info);
        }
        var apps = new ArrayList<>(candidates.entrySet());
        apps.sort(
                (a, b) ->
                        a.getValue()
                                .loadLabel(pm)
                                .toString()
                                .compareToIgnoreCase(b.getValue().loadLabel(pm).toString()));
        LinearLayout page = Ui.screen(this);
        Ui.handle(this, page);
        Ui.heading(this, page, getString(R.string.choose_destination), "");
        if (apps.isEmpty())
            page.addView(
                    Ui.text(this, getString(R.string.destination_no_apps), 16, Ui.MUTED, false));
        for (var app : apps) {
            var button = Ui.button(this, R.string.choose_destination, false);
            button.setText(app.getValue().loadLabel(pm));
            var icon = app.getValue().loadIcon(pm);
            int size = Ui.dp(this, 28);
            icon.setBounds(0, 0, size, size);
            button.setCompoundDrawablesRelative(icon, null, null, null);
            button.setCompoundDrawablePadding(Ui.dp(this, 12));
            button.setOnClickListener(
                    v -> {
                        if (!ShareDestinations.available(this, app.getKey(), query)) {
                            android.widget.Toast.makeText(
                                            this,
                                            R.string.destination_unavailable,
                                            android.widget.Toast.LENGTH_LONG)
                                    .show();
                            return;
                        }
                        setResult(RESULT_OK, new Intent().setComponent(app.getKey()));
                        finish();
                    });
            page.addView(button, new LinearLayout.LayoutParams(-1, -2));
            Ui.gap(this, page, 8);
        }
        var cancel = Ui.button(this, R.string.cancel, false);
        cancel.setOnClickListener(v -> finish());
        page.addView(cancel, new LinearLayout.LayoutParams(-1, -2));
    }
}
