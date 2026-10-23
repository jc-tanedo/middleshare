package dev.middleshare;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class VideoFormatsTest {
    static final String METADATA =
            "{\"formats\":["
                + "{\"format_id\":\"18\",\"vcodec\":\"avc1\",\"acodec\":\"aac\",\"ext\":\"mp4\",\"height\":360},"
                + "{\"format_id\":\"137\",\"vcodec\":\"avc1\",\"acodec\":\"none\",\"ext\":\"mp4\",\"height\":1080,\"fps\":60,\"filesize\":10485760},"
                + "{\"format_id\":\"audio\",\"vcodec\":\"none\",\"acodec\":\"aac\",\"ext\":\"m4a\"},"
                + "{\"format_id\":\"drm\",\"vcodec\":\"avc1\",\"ext\":\"mp4\",\"has_drm\":true},"
                + "{\"format_id\":\"x/best\",\"vcodec\":\"avc1\",\"ext\":\"mp4\"},"
                + "{\"format_id\":\"storyboard\",\"vcodec\":\"images\",\"ext\":\"mhtml\"}]}";

    @Test
    public void formatsExcludeAudioOnlyDrmAndInvalidIdsAndSortByResolution() throws Exception {
        var formats = VideoFormat.parse(METADATA);
        assertEquals(2, formats.size());
        assertEquals("137", formats.get(0).id);
        assertTrue(formats.get(0).needsAudio);
        assertTrue(formats.get(0).label.contains("1080p"));
        assertTrue(formats.get(0).label.contains("60 fps"));
        assertTrue(formats.get(0).label.contains("video + audio"));
        assertEquals("18", formats.get(1).id);
        assertFalse(formats.get(1).needsAudio);
    }

    @Test
    public void malformedEmptyAndLiveMetadataAreRejected() {
        for (String json :
                new String[] {
                    "broken", "{}", "{\"formats\":[]}", "{\"is_live\":true,\"formats\":[]}"
                }) {
            assertThrows(IOException.class, () -> VideoFormat.parse(json));
        }
    }

    @Test
    public void playlistMetadataUsesTheFirstRequestedVideo() throws Exception {
        assertEquals(2, VideoFormat.parse("{\"entries\":[" + METADATA + "]}").size());
    }
}
