package dev.middleshare;

import android.content.ComponentName;
import android.content.Intent;

/**
 * Public shortcut uses only the favorite saved locally, never caller-supplied destination extras.
 */
public final class FavoriteShareActivity extends ShareActivity {
    @Override
    protected ComponentName initialDestination() {
        ComponentName favorite = Settings.getFavorite(this);
        if (favorite == null)
            android.widget.Toast.makeText(
                            this, R.string.favorite_not_set, android.widget.Toast.LENGTH_LONG)
                    .show();
        return favorite;
    }

    @Override
    protected Intent videoHandoff(String link) {
        ComponentName favorite = Settings.getFavorite(this);
        return favorite == null
                ? super.videoHandoff(link)
                : VideoAddon.favoriteShare(this, link, favorite);
    }
}
