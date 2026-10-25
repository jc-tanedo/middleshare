package dev.middleshare;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.List;

/** Selects an app without sharing an unfinished file or launching the receiver. */
final class ShareDestinations {
    private static Intent videoIntent() {
        return new Intent(Intent.ACTION_SEND).setType("video/*");
    }

    static Intent picker(Context context) {
        return new Intent(Intent.ACTION_PICK_ACTIVITY)
                .putExtra(Intent.EXTRA_INTENT, videoIntent())
                .putExtra(Intent.EXTRA_TITLE, context.getString(R.string.choose_destination));
    }

    static boolean available(Context context, ComponentName target, Intent payload) {
        if (target == null) return false;
        if (target.getPackageName().equals("dev.middleshare")
                || target.getPackageName().equals(VideoAddon.PACKAGE)) return false;
        for (ResolveInfo candidate :
                context.getPackageManager()
                        .queryIntentActivities(payload, PackageManager.MATCH_DEFAULT_ONLY)) {
            var activity = candidate.activityInfo;
            if (activity != null
                    && activity.enabled
                    && activity.exported
                    && (activity.applicationInfo == null || activity.applicationInfo.enabled)
                    && target.equals(new ComponentName(activity.packageName, activity.name))
                    && (activity.permission == null
                            || context.checkSelfPermission(activity.permission)
                                    == PackageManager.PERMISSION_GRANTED)) return true;
        }
        return false;
    }

    static boolean available(Context context, ComponentName target) {
        return available(context, target, videoIntent());
    }

    static String label(Context context, ComponentName target) {
        try {
            return context.getPackageManager()
                    .getActivityInfo(target, 0)
                    .loadLabel(context.getPackageManager())
                    .toString();
        } catch (PackageManager.NameNotFoundException ignored) {
            return target.getPackageName();
        }
    }

    static Intent outgoing(Context context, List<SharedFile> files, ComponentName target) {
        Intent payload = ShareIntents.outgoing(files);
        // The finished MIME type may differ from the general video type used by the picker.
        if (!available(context, target, payload)) return null;
        return payload.setComponent(target);
    }
}
