package dev.middleshare;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

/** Explicit, signed handoff; links never reach an unverified add-on. */
final class VideoAddon {
    static final String PACKAGE = "dev.middleshare.video";
    static final String RELEASES = "https://github.com/jc-tanedo/middleshare/releases/latest";
    static final int MIN_VERSION = 5;
    static final String QUALITY = "dev.middleshare.QUALITY";
    static final String CONFIRMED = "dev.middleshare.CONFIRMED";
    static final String FORMAT = "dev.middleshare.FORMAT";
    static final String AUDIO = "dev.middleshare.AUDIO";

    static boolean available(Context context) {
        try {
            var pm = context.getPackageManager();
            var info = pm.getPackageInfo(PACKAGE, 0);
            var activity = pm.getActivityInfo(new ComponentName(PACKAGE, ShareActivity.class.getName()), 0);
            return info.getLongVersionCode() >= MIN_VERSION
                    && pm.checkSignatures(context.getPackageName(), PACKAGE) == PackageManager.SIGNATURE_MATCH
                    && activity.enabled && activity.exported
                    && pm.getApplicationInfo(PACKAGE, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) { return false; }
    }

    static Intent share(Context context, String link) {
        return new Intent(Intent.ACTION_SEND).setType("text/plain")
                .setComponent(new ComponentName(PACKAGE, ShareActivity.class.getName()))
                .putExtra(Intent.EXTRA_TEXT, link)
                .putExtra(QUALITY, Settings.getVideoQuality(context).name());
    }

    static void releases(Activity activity) {
        try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES))); }
        catch (ActivityNotFoundException e) { Toast.makeText(activity, R.string.no_browser, Toast.LENGTH_LONG).show(); }
    }

    static void prompt(Activity activity, Runnable retry) {
        new AlertDialog.Builder(activity).setTitle("Add video downloads")
                .setMessage("Install the optional MiddleShare Video APK from the latest release. Choose the APK for your device (usually arm64-v8a), or universal if unsure.\n\nIt is a one-time installation until an update is needed. Image conversion works without it. After installing, return here and tap Try again. Use the main app and add-on from the same release.")
                .setPositiveButton("Open Releases", (dialog, which) -> releases(activity))
                .setNeutralButton("Try again", (dialog, which) -> retry.run())
                .setNegativeButton("Cancel", (dialog, which) -> activity.finish()).show();
    }
}
