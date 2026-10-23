package dev.middleshare;

import static org.junit.Assert.*;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ShareIntentsTest {
    private final Uri uri = Uri.parse("content://images/photo/1");
    @Test public void readsSingleStreamAndClipDataFallback() throws Exception {
        Intent single = new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri);
        assertEquals(List.of(uri), ShareIntents.read(single));
        Intent clip = new Intent(Intent.ACTION_SEND);
        clip.setClipData(ClipData.newRawUri("Image", uri));
        assertEquals(List.of(uri), ShareIntents.read(clip));
    }
    @Test public void rejectsEmptyLinkOnlyInvalidActionAndHugeBatches() {
        assertThrows(IOException.class, () -> ShareIntents.read(new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "https://example.com/x.webp")));
        assertThrows(IOException.class, () -> ShareIntents.read(new Intent(Intent.ACTION_VIEW)));
        ArrayList<Uri> batch = new ArrayList<>();
        for (int i = 0; i <= ShareIntents.MAX_IMAGES; i++) batch.add(uri);
        assertThrows(IOException.class, () -> ShareIntents.read(new Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, batch)));
    }
    private SharedFile output(String extension, String mime) {
        Context context = RuntimeEnvironment.getApplication();
        return new SharedFile(context, new File(ShareProvider.directory(context), "00000000-0000-0000-0000-000000000000." + extension), mime);
    }
    @Test public void singleOutputHasCorrectMimeStreamAndReadGrant() {
        SharedFile image = output("jpg", "image/jpeg");
        Intent share = ShareIntents.outgoing(List.of(image));
        assertEquals(Intent.ACTION_SEND, share.getAction());
        assertEquals("image/jpeg", share.getType());
        assertEquals(image.uri, share.getParcelableExtra(Intent.EXTRA_STREAM));
        assertEquals(image.uri, share.getClipData().getItemAt(0).getUri());
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, share.getFlags());
    }
    @Test public void batchUsesMultipleStreamsAndEveryUriGetsClipDataGrant() {
        Intent share = ShareIntents.outgoing(List.of(output("jpg", "image/jpeg"), output("png", "image/png")));
        assertEquals(Intent.ACTION_SEND_MULTIPLE, share.getAction());
        assertEquals("image/*", share.getType());
        assertEquals(2, share.getParcelableArrayListExtra(Intent.EXTRA_STREAM).size());
        assertEquals(2, share.getClipData().getItemCount());
        assertTrue((share.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
    }
    @Test public void chooserExcludesMiddleShareAndRetainsReadGrants() {
        Intent chooser = ShareIntents.chooser(RuntimeEnvironment.getApplication(), List.of(output("png", "image/png")));
        assertEquals(Intent.ACTION_CHOOSER, chooser.getAction());
        assertEquals(1, chooser.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS).length);
        assertNotNull(chooser.getClipData());
        assertTrue((chooser.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
    }
}
