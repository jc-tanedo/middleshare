package dev.middleshare;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.file.Files;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class BundledYtDlpTest {
    @Test
    public void payloadMatchesOfficialChecksumAndInstallRepairsOlderCopy() throws Exception {
        var context = RuntimeEnvironment.getApplication();
        try (var input = context.getResources().openRawResource(R.raw.ytdlp)) {
            assertEquals(BundledYtDlp.SHA256, BundledYtDlp.digest(input));
        }
        BundledYtDlp.install(context);
        File target = new File(context.getNoBackupFilesDir(), "youtubedl-android/yt-dlp/yt-dlp");
        byte[] expected = Files.readAllBytes(target.toPath());
        long modified = target.lastModified();
        BundledYtDlp.install(context);
        assertEquals(modified, target.lastModified());
        Files.write(target.toPath(), new byte[] {0});
        BundledYtDlp.install(context);
        assertArrayEquals(expected, Files.readAllBytes(target.toPath()));
    }
}
