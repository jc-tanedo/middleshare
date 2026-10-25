package dev.middleshare;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;

/**
 * Android's native chooser reports app selection, allowing cancellation without losing the file.
 */
public final class VideoShareReceiver extends BroadcastReceiver {
    static IntentSender callback(Context context, String id) {
        Intent intent =
                new Intent(context, VideoShareReceiver.class)
                        .setData(Uri.parse("middleshare://transfer/" + id + "/chosen"))
                        .putExtra(VideoDownloadActivity.TRANSFER, id);
        // Only our transfer ID is needed; ignore any fill-in extras from the chooser.
        return PendingIntent.getBroadcast(
                        context,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_ONE_SHOT
                                | PendingIntent.FLAG_IMMUTABLE)
                .getIntentSender();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        VideoTransfers.Task task =
                VideoTransfers.find(context, intent.getStringExtra(VideoDownloadActivity.TRANSFER));
        if (task != null && task.ready()) VideoTransfers.remove(task, true);
    }
}
