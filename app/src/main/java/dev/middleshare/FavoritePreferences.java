package dev.middleshare;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/** User-owned favorite preference, kept separate from caller-controlled share extras. */
final class FavoritePreferences {
    static void addCard(Activity activity, LinearLayout page) {
        LinearLayout card = Ui.card(activity, page);
        Ui.heading(
                activity,
                card,
                activity.getString(R.string.favorite_title),
                activity.getString(R.string.favorite_description));
        TextView current = Ui.text(activity, "", 15, Ui.ACCENT, true);
        current.setId(R.id.favorite_status);
        card.addView(current);
        Ui.gap(activity, card, 12);
        Button choose = Ui.button(activity, R.string.favorite_choose, false);
        choose.setId(R.id.favorite_choose);
        card.addView(choose, new LinearLayout.LayoutParams(-1, -2));
        Ui.gap(activity, card, 8);
        Button clear = Ui.button(activity, R.string.favorite_clear, false);
        clear.setId(R.id.favorite_clear);
        card.addView(clear, new LinearLayout.LayoutParams(-1, -2));
        Runnable refresh =
                () -> {
                    ComponentName favorite = Settings.getFavorite(activity);
                    current.setText(
                            favorite == null
                                    ? activity.getString(R.string.favorite_none)
                                    : ShareDestinations.label(activity, favorite));
                    clear.setVisibility(
                            favorite == null ? android.view.View.GONE : android.view.View.VISIBLE);
                };
        refresh.run();
        choose.setOnClickListener(v -> showPicker(activity, refresh));
        clear.setOnClickListener(
                v -> {
                    Settings.setFavorite(activity, null);
                    refresh.run();
                });
    }

    static void showPicker(Activity activity, Runnable changed) {
        var pm = activity.getPackageManager();
        var candidates = new LinkedHashMap<ComponentName, ResolveInfo>();
        for (String mime : new String[] {"image/*", "video/*"}) {
            for (ResolveInfo info :
                    pm.queryIntentActivities(
                            new Intent(Intent.ACTION_SEND).setType(mime),
                            PackageManager.MATCH_DEFAULT_ONLY)) {
                if (!ShareDestinations.receiverAllowed(activity, info.activityInfo)) continue;
                var target =
                        new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
                candidates.put(target, info);
            }
        }
        var apps = new ArrayList<>(candidates.values());
        apps.sort(
                (a, b) ->
                        a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));
        AlertDialog.Builder dialog =
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.favorite_choose)
                        .setNegativeButton(R.string.cancel, null);
        if (apps.isEmpty()) dialog.setMessage(R.string.favorite_no_apps);
        else
            dialog.setItems(
                    apps.stream().map(app -> app.loadLabel(pm).toString()).toArray(String[]::new),
                    (d, which) -> {
                        var app = apps.get(which).activityInfo;
                        Settings.setFavorite(
                                activity, new ComponentName(app.packageName, app.name));
                        changed.run();
                    });
        Ui.styleDialog(activity, dialog.show());
    }
}
