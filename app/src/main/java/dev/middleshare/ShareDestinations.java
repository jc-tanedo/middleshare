package dev.middleshare;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.List;

/** Validates explicit favorite receivers against the finished payload. */
final class ShareDestinations {
    static boolean available(Context context, ComponentName target, Intent payload) {
        if (target == null) return false;
        if (target.getPackageName().equals("dev.middleshare")
                || target.getPackageName().equals(VideoAddon.PACKAGE)) return false;
        for (ResolveInfo candidate :
                context.getPackageManager()
                        .queryIntentActivities(payload, PackageManager.MATCH_DEFAULT_ONLY)) {
            var activity = candidate.activityInfo;
            if (receiverAllowed(context, activity)
                    && target.equals(new ComponentName(activity.packageName, activity.name)))
                return true;
        }
        return false;
    }

    static boolean receiverAllowed(Context context, android.content.pm.ActivityInfo activity) {
        return activity != null
                && activity.name != null
                && activity.packageName != null
                && !activity.packageName.equals("dev.middleshare")
                && !activity.packageName.equals(VideoAddon.PACKAGE)
                && activity.enabled
                && activity.exported
                && (activity.applicationInfo == null || activity.applicationInfo.enabled)
                && (activity.permission == null
                        || context.checkSelfPermission(activity.permission)
                                == PackageManager.PERMISSION_GRANTED);
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
        if (!available(context, target, payload)) {
            // Some media receivers require a content scheme as well as a MIME type.
            payload.setDataAndType(files.get(0).uri, payload.getType());
            if (!available(context, target, payload)) return null;
        }
        return payload.setComponent(target);
    }
}
