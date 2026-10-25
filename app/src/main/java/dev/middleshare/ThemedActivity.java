package dev.middleshare;

import android.app.Activity;
import android.content.Context;

/** Applies the saved theme before any Activity resources or views are created. */
abstract class ThemedActivity extends Activity {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.themedContext(base));
    }
}
