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
        if (isMiddleShare(target.getPackageName())) return false;
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
                && !isMiddleShare(activity.packageName)
                && componentEnabled(context, activity)
                && activity.exported
                && applicationEnabled(context, activity)
                && (activity.permission == null
                        || context.checkSelfPermission(activity.permission)
                                == PackageManager.PERMISSION_GRANTED);
    }

    private static boolean isMiddleShare(String name) {
        return "dev.middleshare".equals(name)
                || "dev.middleshare.video".equals(name)
                || "dev.middleshare.debug".equals(name)
                || "dev.middleshare.video.debug".equals(name);
    }

    private static boolean enabled(int setting, boolean manifestDefault) {
        return setting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                || (setting == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && manifestDefault);
    }

    private static boolean componentEnabled(Context context, android.content.pm.ActivityInfo info) {
        try {
            // An enabled alias can still report enabled=false from its manifest. This override
            // is also important when a receiver is disabled after the grid has been opened.
            return enabled(
                    context.getPackageManager()
                            .getComponentEnabledSetting(
                                    new ComponentName(info.packageName, info.name)),
                    info.enabled);
        } catch (IllegalArgumentException missing) {
            return false;
        }
    }

    private static boolean applicationEnabled(
            Context context, android.content.pm.ActivityInfo info) {
        try {
            return enabled(
                    context.getPackageManager().getApplicationEnabledSetting(info.packageName),
                    info.applicationInfo == null || info.applicationInfo.enabled);
        } catch (IllegalArgumentException missing) {
            return false;
        }
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
