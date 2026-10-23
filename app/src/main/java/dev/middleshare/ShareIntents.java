package dev.middleshare;

import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcelable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class ShareIntents {
    static final int MAX_IMAGES = 20;
    @SuppressWarnings("deprecation")
    static ArrayList<Uri> read(Intent intent) throws IOException {
        ArrayList<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Object uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) add(uris, uri);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            ArrayList<?> streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (streams != null) for (Object uri : streams) add(uris, uri);
        } else {
            throw new IOException("Choose an image in another app and share it to MiddleShare.");
        }
        if (uris.isEmpty() && intent.getClipData() != null) {
            ClipData clip = intent.getClipData();
            for (int i = 0; i < clip.getItemCount(); i++) add(uris, clip.getItemAt(i).getUri());
        }
        if (uris.isEmpty()) throw new IOException("No image file was shared. Share the image itself, rather than its link.");
        return uris;
    }
    private static void add(ArrayList<Uri> uris, Object value) throws IOException {
        if (!(value instanceof Uri) || !"content".equals(((Uri) value).getScheme())) {
            throw new IOException("The sending app did not provide a readable image file.");
        }
        if (uris.size() >= MAX_IMAGES) throw new IOException("Share up to 20 images at a time.");
        uris.add((Uri) value);
    }
    static Intent outgoing(List<SharedFile> outputs) {
        if (outputs.isEmpty()) throw new IllegalArgumentException("No images");
        String mime = outputs.get(0).mime;
        ArrayList<Uri> streams = new ArrayList<>();
        for (SharedFile output : outputs) {
            streams.add(output.uri);
            if (!mime.equals(output.mime)) {
                String type = mime.substring(0, mime.indexOf('/'));
                mime = output.mime.startsWith(type + "/") ? type + "/*" : "*/*";
            }
        }
        Intent intent = new Intent(outputs.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
        intent.setType(mime);
        if (outputs.size() == 1) intent.putExtra(Intent.EXTRA_STREAM, streams.get(0));
        else intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, streams);
        ClipData clip = new ClipData("Shared files", new String[]{mime}, new ClipData.Item(streams.get(0)));
        for (int i = 1; i < streams.size(); i++) clip.addItem(new ClipData.Item(streams.get(i)));
        intent.setClipData(clip);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }
    static Intent chooser(Context context, List<SharedFile> outputs) {
        Intent outgoing = outgoing(outputs);
        String label = outgoing.getType().startsWith("video/") ? "Share video"
                : "Share image" + (outputs.size() > 1 ? "s" : "");
        Intent chooser = Intent.createChooser(outgoing, label);
        chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS,
                new Parcelable[]{new ComponentName(context, ShareActivity.class)});
        chooser.setClipData(outgoing.getClipData());
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return chooser;
    }
}
