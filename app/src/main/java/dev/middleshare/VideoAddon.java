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
    static final String CORE_PACKAGE = "dev.middleshare" + (BuildConfig.DEBUG ? ".debug" : "");
    static final String PACKAGE = "dev.middleshare.video" + (BuildConfig.DEBUG ? ".debug" : "");
    static final String FAVORITE_PERMISSION = CORE_PACKAGE + ".permission.SHARE_FAVORITE";
    private static final String REPOSITORY = "https://github.com/jc-tanedo/middleshare";
    static final String RELEASES = REPOSITORY + "/releases/latest";
    static final String DOWNLOADS = downloadsUrl(BuildConfig.DEBUG, BuildConfig.VERSION_NAME);
    // Earlier release add-ons can crash while unpacking the native runtime.
    static final int MIN_VERSION = 15;
    static final String DESTINATION = "dev.middleshare.DESTINATION";
    static final String QUALITY = "dev.middleshare.QUALITY";
    static final String FORMAT = "dev.middleshare.FORMAT";
    static final String AUDIO = "dev.middleshare.AUDIO";

    static String downloadsUrl(boolean debug, String version) {
        if (debug) return REPOSITORY + "/actions/workflows/android.yml";
        return version.contains("-") ? REPOSITORY + "/releases/tag/v" + version : RELEASES;
    }

    static boolean available(Context context) {
        try {
            var pm = context.getPackageManager();
            var info = pm.getPackageInfo(PACKAGE, 0);
            var activity =
                    pm.getActivityInfo(
                            new ComponentName(PACKAGE, ShareActivity.class.getName()), 0);
            return info.getLongVersionCode() >= MIN_VERSION
                    && pm.checkSignatures(context.getPackageName(), PACKAGE)
                            == PackageManager.SIGNATURE_MATCH
                    && activity.enabled
                    && activity.exported
                    && pm.getApplicationInfo(PACKAGE, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static Intent share(Context context, String link) {
        return new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .setComponent(new ComponentName(PACKAGE, ShareActivity.class.getName()))
                .putExtra(Intent.EXTRA_TEXT, link)
                .putExtra(
                        QUALITY,
                        Settings.getVideoQuality(context, Settings.videoHost(link)).name());
    }

    static Intent favoriteShare(Context context, String link, ComponentName favorite) {
        return share(context, link)
                .setComponent(new ComponentName(PACKAGE, "dev.middleshare.FavoriteVideoActivity"))
                .putExtra(DESTINATION, favorite.flattenToString());
    }

    static void downloads(Activity activity) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOADS)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.no_browser, Toast.LENGTH_LONG).show();
        }
    }

    static void prompt(Activity activity, Runnable retry) {
        new AlertDialog.Builder(activity)
                .setTitle("Add video downloads")
                .setMessage(
                        BuildConfig.DEBUG
                                ? "Install MiddleShare Video Debug from the same Actions build as"
                                        + " this main app. Use the download links in that run's"
                                        + " summary and choose the APK for your device (usually"
                                        + " arm64-v8a). Then return and tap Try again. A release"
                                        + " add-on cannot pair with this debug installation."
                                : "Install the optional MiddleShare Video APK from the latest"
                                        + " release. Choose the APK for your device (usually"
                                        + " arm64-v8a), or universal if unsure.\n\n"
                                        + "It is a one-time installation until an update is needed."
                                        + " Image conversion works without it. After installing,"
                                        + " return here and tap Try again. Use the main app and"
                                        + " add-on from the same release.")
                .setPositiveButton(
                        BuildConfig.DEBUG ? "Open debug builds" : "Open Releases",
                        (dialog, which) -> downloads(activity))
                .setNeutralButton("Try again", (dialog, which) -> retry.run())
                .setNegativeButton("Cancel", (dialog, which) -> activity.finish())
                .show();
    }
}
