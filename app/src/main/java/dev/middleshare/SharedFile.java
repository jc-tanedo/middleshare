package dev.middleshare;

import android.content.Context;
import android.net.Uri;

import java.io.File;

final class SharedFile {
    final File file;
    final Uri uri;
    final String mime;

    SharedFile(Context context, File file, String mime) {
        this.file = file;
        this.uri = ShareProvider.uriFor(context, file);
        this.mime = mime;
    }
}
