package dev.middleshare;

import static org.junit.Assert.*;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SharedLinkTest {
    private Intent text(String value) { return new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, value); }
    @Test public void acceptsPlainAndCaptionedLinks() throws Exception {
        assertEquals("https://youtu.be/abc?t=2", SharedLink.read(text("Watch this: https://youtu.be/abc?t=2")));
        assertEquals("https://example.com/watch?id=3", SharedLink.read(text("(https://example.com/watch?id=3).")));
    }
    @Test public void acceptsClipTextAndPreservesBalancedParentheses() throws Exception {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setClipData(ClipData.newPlainText("Link", "https://example.com/video_(one)"));
        assertEquals("https://example.com/video_(one)", SharedLink.read(intent));
    }
    @Test public void rejectsAmbiguousMalformedAndNonWebLinks() {
        assertThrows(IOException.class, () -> SharedLink.read(text("https://a.example/v https://b.example/v")));
        assertThrows(IOException.class, () -> SharedLink.read(text("file:///some/video.mp4")));
        assertThrows(IOException.class, () -> SharedLink.read(text("https://user:password@example.com/video")));
        assertThrows(IOException.class, () -> SharedLink.read(text("https:///missing-host")));
    }
    @Test public void attachmentWinsOverCaptionLink() throws Exception {
        Intent image = text("https://example.com/video").putExtra(Intent.EXTRA_STREAM, Uri.parse("content://images/1"));
        assertNull(SharedLink.read(image));
        image.removeExtra(Intent.EXTRA_STREAM);
        image.setClipData(ClipData.newRawUri("Image", Uri.parse("content://images/1")));
        assertNull(SharedLink.read(image));
    }
}
