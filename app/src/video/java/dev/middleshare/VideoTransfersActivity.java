package dev.middleshare;

import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pending downloads remain reachable when notifications are denied or dismissed. */
public final class VideoTransfersActivity extends ThemedActivity
        implements VideoTransfers.Observer {
    private final Map<String, TransferRow> rows = new LinkedHashMap<>();

    @Override
    protected void onResume() {
        super.onResume();
        LinearLayout page = Ui.screen(this);
        Ui.heading(
                this,
                page,
                getString(R.string.view_downloads),
                getString(R.string.transfer_channel_description));
        rows.clear();
        var tasks = VideoTransfers.recover(this);
        if (tasks.isEmpty())
            page.addView(Ui.text(this, getString(R.string.no_downloads), 16, Ui.MUTED, false));
        for (VideoTransfers.Task task : tasks) rows.put(task.id, new TransferRow(page, task));
        Button back = Ui.button(this, R.string.back, false);
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
        TransferRow row = rows.get(task.id);
        if (row != null) row.render(task);
    }

    private final class TransferRow {
        private final LinearLayout card;
        private final TextView status;
        private final Button open;

        TransferRow(LinearLayout page, VideoTransfers.Task task) {
            card = Ui.card(VideoTransfersActivity.this, page);
            status = Ui.text(VideoTransfersActivity.this, task.status, 18, Ui.INK, true);
            card.addView(status);
            open = Ui.button(VideoTransfersActivity.this, R.string.view_downloads, true);
            open.setOnClickListener(
                    v ->
                            startActivity(
                                    new Intent(
                                                    VideoTransfersActivity.this,
                                                    VideoDownloadActivity.class)
                                            .setAction(
                                                    task.job.completed
                                                            ? VideoDownloadActivity.SEND_READY
                                                            : VideoDownloadActivity.OPEN)
                                            .putExtra(VideoDownloadActivity.TRANSFER, task.id)));
            card.addView(open);
            Ui.gap(VideoTransfersActivity.this, card, 8);
            Button discard =
                    Ui.button(VideoTransfersActivity.this, R.string.transfer_discard, false);
            discard.setOnClickListener(v -> VideoTransfers.remove(task, false));
            card.addView(discard);
            render(task);
        }

        void render(VideoTransfers.Task task) {
            status.setText(task.status);
            open.setText(task.ready() ? R.string.transfer_send : R.string.view_downloads);
            card.setVisibility(task.removed ? View.GONE : View.VISIBLE);
        }
    }
}
