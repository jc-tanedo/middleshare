package dev.middleshare;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;

import com.yausername.youtubedl_android.YoutubeDLRequest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, shadows = TestStorage.class)
public class VideoDownloaderTest {
    private Context context;

    @Before
    public void setup() {
        context = RuntimeEnvironment.getApplication();
        TestStorage.allocatable = 2L * 1024 * 1024 * 1024;
    }

    private File work() throws IOException {
        return Files.createTempDirectory(context.getCacheDir().toPath(), "video-test-").toFile();
    }

    @Test
    public void presetsAndCustomApplyToActualDownloadRequests() throws Exception {
        for (VideoQuality quality : VideoQuality.values()) {
            YoutubeDLRequest request =
                    VideoDownloader.request("https://example.com/v", work(), quality, "137", true);
            if (quality == VideoQuality.BEST) {
                assertFalse(request.hasOption("-f"));
                assertFalse(request.hasOption("-S"));
            } else assertEquals(quality.selector("137", true), request.getOption("-f"));
            assertEquals(quality.sort(), request.getOption("-S"));
            assertTrue(request.hasOption("--max-filesize"));
        }
    }

    @Test
    public void customLookupOnlyRequestsMetadataAndSupportsCancellation() throws Exception {
        VideoDownloader downloader =
                new VideoDownloader(
                        new VideoDownloader.Engine() {
                            public void init(Context app) {}

                            public void execute(
                                    YoutubeDLRequest request,
                                    String id,
                                    VideoDownloader.Progress progress) {
                                fail("Lookup downloaded media");
                            }

                            public String inspect(YoutubeDLRequest request, String id) {
                                assertTrue(request.hasOption("--skip-download"));
                                assertTrue(request.hasOption("--dump-single-json"));
                                assertTrue(request.hasOption("--ignore-config"));
                                assertTrue(request.hasOption("--no-playlist"));
                                assertFalse(request.hasOption("-o"));
                                return VideoFormatsTest.METADATA;
                            }

                            public void cancel(String id) {}
                        });
        assertEquals(2, downloader.formats(context, "https://example.com/v").size());
        downloader.cancel();
        assertThrows(
                InterruptedIOException.class,
                () -> downloader.formats(context, "https://example.com/v"));
    }

    @Test
    public void requestUsesOneVideoBoundedRetriesAndNoReencoding() throws Exception {
        YoutubeDLRequest request = VideoDownloader.request("https://example.com/watch", work());
        assertTrue(request.hasOption("--ignore-config"));
        assertTrue(request.hasOption("--no-playlist"));
        assertEquals("1", request.getOption("--playlist-items"));
        assertEquals("mp4/mkv", request.getOption("--merge-output-format"));
        assertEquals(
                String.valueOf(VideoDownloader.MAX_VIDEO_BYTES),
                request.getOption("--max-filesize"));
        assertFalse(request.hasOption("--recode-video"));
        assertFalse(request.hasOption("--no-check-certificates"));
        assertEquals(
                "https://example.com/watch",
                request.buildCommand().get(request.buildCommand().size() - 1));
    }

    @Test
    public void successfulDownloadIsSharedAsVideoWithReadGrantAndCleanedWorkDirectory()
            throws Exception {
        ArrayList<String> updates = new ArrayList<>();
        VideoDownloader.Engine engine =
                new VideoDownloader.Engine() {
                    public void init(Context app) {
                        assertSame(context, app);
                    }

                    public void execute(
                            YoutubeDLRequest request, String id, VideoDownloader.Progress progress)
                            throws Exception {
                        File output = new File(request.getOption("-o").replace("%(ext)s", "mp4"));
                        Files.write(output.toPath(), new byte[] {0, 1, 2, 3});
                        Files.write(
                                new File(output.getParentFile(), "scratch.part").toPath(),
                                new byte[] {4});
                        progress.update("Downloading video… 100%");
                    }

                    public void cancel(String id) {}
                };
        SharedFile output =
                new VideoDownloader(engine)
                        .download(context, "https://example.com/v", updates::add);
        assertEquals("video/mp4", output.mime);
        assertTrue(output.file.exists());
        Intent share = ShareIntents.outgoing(List.of(output));
        assertEquals(Intent.ACTION_SEND, share.getAction());
        assertEquals("video/mp4", share.getType());
        assertEquals(output.uri, share.getClipData().getItemAt(0).getUri());
        assertTrue((share.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        ShareProvider provider =
                Robolectric.setupContentProvider(
                        ShareProvider.class, context.getPackageName() + ".files");
        assertEquals("video/mp4", provider.getType(output.uri));
        try (var pfd = provider.openFile(output.uri, "r")) {
            assertEquals(4, pfd.getStatSize());
        }
        assertTrue(updates.contains("Downloading video… 100%"));
        assertEquals(
                0,
                context.getCacheDir().listFiles((dir, name) -> name.startsWith("video-")).length);
    }

    @Test
    public void rejectsMissingEmptyMultipleAndOversizedVideoOutputs() throws Exception {
        File work = work();
        assertThrows(IOException.class, () -> VideoDownloader.prepareOutput(context, work));
        File video = new File(work, "video.mp4");
        Files.write(video.toPath(), new byte[0]);
        assertThrows(IOException.class, () -> VideoDownloader.prepareOutput(context, work));
        try (RandomAccessFile file = new RandomAccessFile(video, "rw")) {
            file.setLength(VideoDownloader.MAX_VIDEO_BYTES + 1);
        }
        assertThrows(IOException.class, () -> VideoDownloader.prepareOutput(context, work));
        Files.write(new File(work, "video.webm").toPath(), new byte[] {0});
        assertThrows(IOException.class, () -> VideoDownloader.prepareOutput(context, work));
    }

    @Test
    public void cancelBeforeStartupDoesNotInitializeNativeToolsAndRemovesTemporaryFiles() {
        VideoDownloader downloader =
                new VideoDownloader(
                        new VideoDownloader.Engine() {
                            public void init(Context app) {
                                fail("Cancelled job initialized native tools");
                            }

                            public void execute(
                                    YoutubeDLRequest request,
                                    String id,
                                    VideoDownloader.Progress progress) {
                                fail("Cancelled job started a download");
                            }

                            public void cancel(String id) {
                                throw new UnsatisfiedLinkError("Runtime unavailable");
                            }
                        });
        downloader.cancel();
        assertThrows(
                InterruptedIOException.class,
                () -> downloader.download(context, "https://example.com/v", message -> {}));
        assertEquals(
                0,
                context.getCacheDir().listFiles((dir, name) -> name.startsWith("video-")).length);
    }

    @Test
    public void insufficientStorageStopsBeforeNativeStartup() {
        TestStorage.allocatable = 0;
        VideoDownloader downloader =
                new VideoDownloader(
                        new VideoDownloader.Engine() {
                            public void init(Context app) {
                                fail("Started tools without enough storage");
                            }

                            public void execute(
                                    YoutubeDLRequest request,
                                    String id,
                                    VideoDownloader.Progress progress) {
                                fail("Started download without enough storage");
                            }

                            public void cancel(String id) {}
                        });
        IOException error =
                assertThrows(
                        IOException.class,
                        () -> downloader.download(context, "https://example.com/v", message -> {}));
        assertTrue(error.getMessage().contains("512 MB"));
    }

    @Test
    public void failedDownloadRemovesPartialsAndDoesNotExposeBackendLogs() {
        VideoDownloader downloader =
                new VideoDownloader(
                        new VideoDownloader.Engine() {
                            public void init(Context app) {}

                            public void execute(
                                    YoutubeDLRequest request,
                                    String id,
                                    VideoDownloader.Progress progress)
                                    throws Exception {
                                Files.write(
                                        new File(request.getOption("-o"))
                                                .getParentFile()
                                                .toPath()
                                                .resolve("video.part"),
                                        new byte[] {1});
                                throw new Exception("Internal backend detail");
                            }

                            public void cancel(String id) {}
                        });
        IOException error =
                assertThrows(
                        IOException.class,
                        () -> downloader.download(context, "https://example.com/v", message -> {}));
        assertFalse(error.getMessage().contains("Internal backend detail"));
        assertEquals(
                0,
                context.getCacheDir().listFiles((dir, name) -> name.startsWith("video-")).length);
    }

    @Test
    public void runtimeLinkageFailuresBecomeErrorsForBothDownloadsAndFormatLookup() {
        for (boolean failDuringInit : new boolean[] {true, false}) {
            LinkageError failure = new NoClassDefFoundError("Missing runtime class");
            VideoDownloader downloader =
                    new VideoDownloader(
                            new VideoDownloader.Engine() {
                                public void init(Context app) {
                                    if (failDuringInit) throw failure;
                                }

                                public void execute(
                                        YoutubeDLRequest request,
                                        String id,
                                        VideoDownloader.Progress progress) {
                                    throw failure;
                                }

                                public String inspect(YoutubeDLRequest request, String id) {
                                    throw failure;
                                }

                                public void cancel(String id) {
                                    throw failure;
                                }
                            });
            IOException download =
                    assertThrows(
                            IOException.class,
                            () ->
                                    downloader.download(
                                            context, "https://example.com/v", message -> {}));
            IOException lookup =
                    assertThrows(
                            IOException.class,
                            () -> downloader.formats(context, "https://example.com/v"));
            assertSame(failure, download.getCause());
            assertSame(failure, lookup.getCause());
            assertTrue(download.getMessage().contains("Video tools could not run"));
            assertEquals(
                    0,
                    context.getCacheDir()
                            .listFiles((dir, name) -> name.startsWith("video-"))
                            .length);
        }
    }
}
