package dev.middleshare;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Read-only, temporary grants to individual files; never exposes source URIs or paths. */
public final class ShareProvider extends ContentProvider {
    static File directory(android.content.Context context) {
        return new File(context.getCacheDir(), "shared-images");
    }
    static Uri uriFor(android.content.Context context, File file) {
        return new Uri.Builder().scheme("content").authority(context.getPackageName() + ".files")
                .appendPath(file.getName()).build();
    }
    private File resolve(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (uri.getPathSegments().size() != 1 || name == null
                || !name.matches("[a-f0-9\\-]{36}\\.[a-z0-9]{1,10}")) {
            throw new FileNotFoundException("Invalid file URI");
        }
        try {
            File root = directory(getContext()).getCanonicalFile();
            File file = new File(root, name).getCanonicalFile();
            if (!root.equals(file.getParentFile()) || !file.isFile()) {
                throw new FileNotFoundException("File is no longer available");
            }
            return file;
        } catch (IOException e) { throw new FileNotFoundException("File is no longer available"); }
    }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) {
        String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        String video = VideoDownloader.mimeFor(uri.getLastPathSegment() == null ? "" : uri.getLastPathSegment());
        if (video != null) return video;
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mime != null && mime.startsWith("image/") ? mime : "application/octet-stream";
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only file");
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        File file;
        try { file = resolve(uri); } catch (FileNotFoundException e) { return null; }
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = file.getName();
            if (OpenableColumns.SIZE.equals(columns[i])) row[i] = file.length();
        }
        cursor.addRow(row);
        return cursor;
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read only"); }
    @Override public int update(Uri uri, ContentValues values, String s, String[] a) { throw new UnsupportedOperationException("Read only"); }
    @Override public int delete(Uri uri, String s, String[] a) { throw new UnsupportedOperationException("Read only"); }
}
