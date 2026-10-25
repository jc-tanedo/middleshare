package dev.middleshare;

/** Gives every share drawer the same stable size before its first frame. */
abstract class SheetActivity extends ThemedActivity {
    @Override
    protected void onStart() {
        super.onStart();
        Ui.layoutSheet(this);
    }
}
