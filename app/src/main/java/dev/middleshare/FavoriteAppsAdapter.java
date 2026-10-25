package dev.middleshare;

import android.app.Activity;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Small recycled framework grid, with activity icons and the owning app's name. */
final class FavoriteAppsAdapter extends BaseAdapter {
    private final Activity activity;
    private final List<Entry> entries = new ArrayList<>();

    private static final class Entry {
        final ResolveInfo info;
        final String receiver;
        final String app;
        Drawable icon;

        Entry(Activity activity, ResolveInfo info) {
            this.info = info;
            var pm = activity.getPackageManager();
            receiver = info.loadLabel(pm).toString();
            app = info.activityInfo.applicationInfo.loadLabel(pm).toString();
        }
    }

    FavoriteAppsAdapter(Activity activity, List<ResolveInfo> apps) {
        this.activity = activity;
        for (ResolveInfo info : apps) entries.add(new Entry(activity, info));
    }

    public int getCount() {
        return entries.size();
    }

    public ResolveInfo getItem(int position) {
        return entries.get(position).info;
    }

    public long getItemId(int position) {
        return position;
    }

    public View getView(int position, View recycled, ViewGroup parent) {
        Cell cell = recycled instanceof Cell ? (Cell) recycled : new Cell(activity);
        Entry entry = entries.get(position);
        if (entry.icon == null) entry.icon = entry.info.loadIcon(activity.getPackageManager());
        cell.icon.setImageDrawable(entry.icon);
        cell.title.setText(entry.receiver);
        cell.subtitle.setText(entry.app);
        cell.setContentDescription(entry.app + ", " + entry.receiver);
        return cell;
    }

    private static final class Cell extends LinearLayout {
        final ImageView icon;
        final TextView title;
        final TextView subtitle;

        Cell(Activity activity) {
            super(activity);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            setPadding(
                    Ui.dp(activity, 8),
                    Ui.dp(activity, 12),
                    Ui.dp(activity, 8),
                    Ui.dp(activity, 12));
            setBackground(
                    new android.graphics.drawable.RippleDrawable(
                            android.content.res.ColorStateList.valueOf(
                                    activity.getColor(R.color.ripple)),
                            Ui.shape(activity, R.color.surface_variant, 20),
                            null));
            icon = new ImageView(activity);
            icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(icon, new LayoutParams(Ui.dp(activity, 40), Ui.dp(activity, 40)));
            Ui.gap(activity, this, 8);
            title = Ui.text(activity, "", 13, Ui.INK, true);
            title.setGravity(Gravity.CENTER);
            title.setMinLines(2);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            addView(title, new LayoutParams(-1, -2));
            subtitle = Ui.text(activity, "", 12, Ui.MUTED, false);
            subtitle.setGravity(Gravity.CENTER);
            subtitle.setMinLines(1);
            subtitle.setMaxLines(1);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            addView(subtitle, new LayoutParams(-1, -2));
        }
    }
}
