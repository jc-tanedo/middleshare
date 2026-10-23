package dev.middleshare;

import static org.junit.Assert.*;

import org.junit.Test;

public class VideoQualityTest {
    @Test
    public void presetsUseTheirResolutionLimitsAndFullHasNoResolutionCap() {
        assertEquals(
                "bv*[height<=?480]+ba/b[height<=?480]", VideoQuality.MOBILE.selector(null, false));
        assertEquals(
                "bv*[height<=?1080]+ba/b[height<=?1080]",
                VideoQuality.STANDARD.selector(null, false));
        assertEquals("bv*+ba/b", VideoQuality.FULL.selector(null, false));
        assertNull(VideoQuality.BEST.sort());
        assertTrue(VideoQuality.FULL.sort().startsWith("res,"));
        assertEquals(VideoQuality.BEST, VideoQuality.parse("invalid"));
        assertEquals(VideoQuality.BEST, VideoQuality.parse(null));
    }

    @Test
    public void customUsesExactFormatAndPairsVideoOnlyWithAudio() {
        assertEquals("137+ba/137", VideoQuality.CUSTOM.selector("137", true));
        assertEquals("http-720", VideoQuality.CUSTOM.selector("http-720", false));
        for (String invalid :
                new String[] {null, "", "137/best", "137;exec", "a b", "http://example.org"}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> VideoQuality.CUSTOM.selector(invalid, true));
        }
    }
}
