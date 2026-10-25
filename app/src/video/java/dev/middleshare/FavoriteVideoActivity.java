package dev.middleshare;

import android.content.ComponentName;

/** Signature-protected handoff from the main app's favorite share receiver. */
public final class FavoriteVideoActivity extends VideoDownloadActivity {
    @Override
    protected boolean isVideoDownloadConfirmed() {
        return VideoQuality.parse(getIntent().getStringExtra(VideoAddon.QUALITY))
                != VideoQuality.CUSTOM;
    }

    @Override
    protected ComponentName trustedVideoDestination() {
        return initialDestination();
    }
}
