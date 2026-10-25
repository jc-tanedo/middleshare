package dev.middleshare;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.widget.Button;
import android.widget.GridView;
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

    static ArrayList<ResolveInfo> receivers(android.content.Context context) {
        var pm = context.getPackageManager();
        var candidates = new LinkedHashMap<ComponentName, ResolveInfo>();
        for (String mime :
                new String[] {
                    "image/*",
                    "image/jpeg",
                    "image/png",
                    "image/webp",
                    "video/*",
                    "video/mp4",
                    "video/webm"
                }) {
            for (String action : new String[] {Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE}) {
                if (mime.startsWith("video/") && Intent.ACTION_SEND_MULTIPLE.equals(action))
                    continue;
                for (boolean content : new boolean[] {false, true}) {
                    Intent query = new Intent(action);
                    if (content)
                        query.setDataAndType(
                                Uri.parse(
                                        "content://"
                                                + context.getPackageName()
                                                + ".files/favorite-preview"),
                                mime);
                    else query.setType(mime);
                    for (ResolveInfo info :
                            pm.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY)) {
                        if (!ShareDestinations.receiverAllowed(context, info.activityInfo))
                            continue;
                        var target =
                                new ComponentName(
                                        info.activityInfo.packageName, info.activityInfo.name);
                        candidates.put(target, info);
                    }
                }
            }
        }
        var apps = new ArrayList<>(candidates.values());
        apps.sort(
                java.util.Comparator.comparing(
                                (ResolveInfo app) ->
                                        app.activityInfo.applicationInfo.loadLabel(pm).toString(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(
                                app -> app.loadLabel(pm).toString(),
                                String.CASE_INSENSITIVE_ORDER));
        return apps;
    }

    static void showPicker(Activity activity, Runnable changed) {
        var apps = receivers(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                Ui.dp(activity, 20), Ui.dp(activity, 20), Ui.dp(activity, 20), Ui.dp(activity, 8));
        content.addView(
                Ui.text(activity, activity.getString(R.string.favorite_choose), 24, Ui.INK, true));
        Ui.gap(activity, content, 16);
        GridView grid = new GridView(activity);
        grid.setId(R.id.favorite_grid);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(
                Ui.dp(
                        activity,
                        activity.getResources().getConfiguration().fontScale > 1.2f ? 128 : 96));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(Ui.dp(activity, 8));
        grid.setVerticalSpacing(Ui.dp(activity, 8));
        grid.setClipToPadding(false);
        grid.setAdapter(new FavoriteAppsAdapter(activity, apps));
        int height =
                Math.min(
                        Ui.dp(activity, 480),
                        Math.max(
                                Ui.dp(activity, 96),
                                activity.getResources().getDisplayMetrics().heightPixels
                                        - Ui.dp(activity, 200)));
        if (apps.isEmpty())
            content.addView(
                    Ui.text(
                            activity,
                            activity.getString(R.string.favorite_no_apps),
                            16,
                            Ui.MUTED,
                            false));
        else content.addView(grid, new LinearLayout.LayoutParams(-1, height));
        AlertDialog.Builder dialog =
                new AlertDialog.Builder(activity)
                        .setView(content)
                        .setNegativeButton(R.string.cancel, null);
        AlertDialog shown = dialog.create();
        grid.setOnItemClickListener(
                (parent, view, which, id) -> {
                    var app = apps.get(which).activityInfo;
                    if (!ShareDestinations.receiverAllowed(activity, app)) return;
                    Settings.setFavorite(activity, new ComponentName(app.packageName, app.name));
                    changed.run();
                    shown.dismiss();
                });
        shown.show();
        shown.getWindow().setBackgroundDrawable(Ui.shape(activity, R.color.surface, 28));
        shown.getWindow()
                .setLayout(
                        Math.min(
                                activity.getResources().getDisplayMetrics().widthPixels
                                        - Ui.dp(activity, 32),
                                Ui.dp(activity, 720)),
                        -2);
        Ui.styleDialog(activity, shown);
    }
}
