package dev.middleshare;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pending work remains reachable when notification permission is denied or a notification is
 * dismissed.
 */
public final class VideoTransfersActivity extends Activity implements VideoTransfers.Observer {
    private final Map<String, TextView> statuses = new LinkedHashMap<>();

    private final Map<String, android.widget.Button> buttons = new LinkedHashMap<>();
    private final Map<String, LinearLayout> cards = new LinkedHashMap<>();

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Ui.themedContext(base));
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LinearLayout page = Ui.screen(this);
        Ui.heading(
                this,
                page,
                getString(R.string.view_downloads),
                getString(R.string.transfer_channel_description));
        statuses.clear();
        buttons.clear();
        cards.clear();
        var tasks = VideoTransfers.recover(this);
        if (tasks.isEmpty())
            page.addView(Ui.text(this, getString(R.string.no_downloads), 16, Ui.MUTED, false));
        for (VideoTransfers.Task task : tasks) {
            LinearLayout card = Ui.card(this, page);
            cards.put(task.id, card);
            TextView status = Ui.text(this, task.status, 18, Ui.INK, true);
            statuses.put(task.id, status);
            card.addView(status);
            var open =
                    Ui.button(
                            this,
                            task.job.completed && task.job.error == null
                                    ? R.string.transfer_send
                                    : R.string.view_downloads,
                            true);
            buttons.put(task.id, open);
            open.setOnClickListener(
                    v ->
                            startActivity(
                                    new Intent(this, VideoDownloadActivity.class)
                                            .setAction(
                                                    task.job.completed
                                                            ? VideoDownloadActivity.SEND_READY
                                                            : VideoDownloadActivity.OPEN)
                                            .putExtra(VideoDownloadActivity.TRANSFER, task.id)));
            card.addView(open);
            Ui.gap(this, card, 8);
            var discard = Ui.button(this, R.string.transfer_discard, false);
            discard.setOnClickListener(
                    v -> {
                        VideoTransfers.remove(task, false);
                        card.setVisibility(android.view.View.GONE);
                    });
            card.addView(discard);
        }
        var back = Ui.button(this, R.string.back, false);
        back.setOnClickListener(v -> finish());
        page.addView(back);
        VideoTransfers.observe(this);
    }

    @Override
    protected void onPause() {
        VideoTransfers.forget(this);
        super.onPause();
    }

    @Override
    public void changed(VideoTransfers.Task task) {
        TextView status = statuses.get(task.id);
        if (status != null) status.setText(task.status);
        if (task.removed && cards.containsKey(task.id))
            cards.get(task.id).setVisibility(android.view.View.GONE);
        if (buttons.containsKey(task.id))
            buttons.get(task.id)
                    .setText(
                            task.job.completed && task.job.error == null
                                    ? R.string.transfer_send
                                    : R.string.view_downloads);
    }
}
