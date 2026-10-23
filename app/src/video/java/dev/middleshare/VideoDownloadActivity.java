package dev.middleshare;

/** Private entry point used only after the user confirms the download. */
public final class VideoDownloadActivity extends ShareActivity {
    @Override
    protected boolean isVideoDownloadConfirmed() {
        return true;
    }
}
