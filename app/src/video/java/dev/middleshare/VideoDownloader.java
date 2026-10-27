package dev.middleshare;

import android.content.Context;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Video tools are loaded only when a link is shared, never on the image path. */
final class VideoDownloader {
    static final long MAX_VIDEO_BYTES = 256L * 1024 * 1024;
    static final long MAX_WORK_BYTES = 3 * MAX_VIDEO_BYTES;
    static final long MAX_DURATION_MS = 10 * 60 * 1000L;
    private static final String RUNTIME_FAILURE =
            "Video tools could not run. Reinstall the matching MiddleShare Video APK for your"
                    + " device, then try again.";

    interface Progress {
        void update(String message);
    }

    interface Engine {
        void init(Context context) throws Exception;

        void execute(YoutubeDLRequest request, String id, Progress progress) throws Exception;

        void cancel(String id);

        default String inspect(YoutubeDLRequest request, String id) throws Exception {
            throw new UnsupportedOperationException("Format lookup unavailable.");
        }
    }

    private static final class NativeEngine implements Engine {
        @Override
        public void init(Context context) throws Exception {
            // FFmpeg's upstream initializer is not synchronized; parallel share tasks must not
            // extract or replace the same runtime files at the same time.
            synchronized (NativeEngine.class) {
                YoutubeDL.getInstance().init(context);
                BundledYtDlp.install(context);
                FFmpeg.getInstance().init(context);
            }
        }

        @Override
        public void execute(YoutubeDLRequest request, String id, Progress progress)
                throws Exception {
            YoutubeDL.getInstance()
                    .execute(
                            request,
                            id,
                            (percent, eta, line) -> {
                                progress.update(
                                        percent >= 0 && percent <= 100
                                                ? String.format(
                                                        Locale.getDefault(),
                                                        "Downloading video… %.0f%%",
                                                        percent)
                                                : "Downloading or preparing video…");
                                return kotlin.Unit.INSTANCE;
                            });
        }

        @Override
        public void cancel(String id) {
            YoutubeDL.getInstance().destroyProcessById(id);
        }

        @Override
        public String inspect(YoutubeDLRequest request, String id) throws Exception {
            return YoutubeDL.getInstance().execute(request, id, null).getOut();
        }
    }

    private final Engine engine;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    VideoDownloader() {
        this(new NativeEngine());
    }

    VideoDownloader(Engine engine) {
        this.engine = engine;
    }

    void cancel() {
        cancelled.set(true);
    }

    private static YoutubeDLRequest baseRequest(String url) {
        YoutubeDLRequest request = new YoutubeDLRequest(url);
        request.addOption("--ignore-config");
        request.addOption("--no-playlist");
        request.addOption("--playlist-items", "1");
        request.addOption("--no-cache-dir");
        request.addOption("--socket-timeout", 20);
        request.addOption("--extractor-retries", 1);
        return request;
    }

    static YoutubeDLRequest request(String url, File work) {
        return request(url, work, VideoQuality.BEST, null, false);
    }

    static YoutubeDLRequest request(
            String url, File work, VideoQuality quality, String customId, boolean needsAudio) {
        YoutubeDLRequest request = baseRequest(url);
        request.addOption("--no-mtime");
        request.addOption("--no-write-thumbnail");
        request.addOption("--no-write-info-json");
        request.addOption("--retries", 2);
        request.addOption("--fragment-retries", 2);
        request.addOption("--concurrent-fragments", 2);
        request.addOption("--max-filesize", MAX_VIDEO_BYTES);
        request.addOption("--match-filter", "!is_live & !is_upcoming");
        if (quality != VideoQuality.BEST) {
            request.addOption("-f", quality.selector(customId, needsAudio));
            if (quality.sort() != null) request.addOption("-S", quality.sort());
        }
        request.addOption("--merge-output-format", "mp4/mkv");
        request.addOption("-o", new File(work, "video.%(ext)s").getAbsolutePath());
        return request;
    }

    SharedFile download(Context context, String url, Progress progress) throws IOException {
        return download(context, url, VideoQuality.BEST, null, false, progress);
    }

    SharedFile download(
            Context context,
            String url,
            VideoQuality quality,
            String customId,
            boolean needsAudio,
            Progress progress)
            throws IOException {
        File work = new File(context.getCacheDir(), "video-" + UUID.randomUUID());
        if (!work.mkdirs()) throw new IOException("Cannot create temporary video storage.");
        String id = UUID.randomUUID().toString();
        AtomicReference<String> stopped = new AtomicReference<>();
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        long started = android.os.SystemClock.elapsedRealtime();
        try {
            checkCancelled();
            android.os.storage.StorageManager storage =
                    context.getSystemService(android.os.storage.StorageManager.class);
            if (storage.getAllocatableBytes(storage.getUuidForPath(work)) < 2 * MAX_VIDEO_BYTES)
                throw new IOException(
                        "Free at least 512 MB of storage before downloading a video.");
            progress.update("Starting video tools…");
            engine.init(context.getApplicationContext());
            checkCancelled();
            monitor.scheduleWithFixedDelay(
                    () -> {
                        if (cancelled.get()) stopped.compareAndSet(null, "Download cancelled.");
                        if (android.os.SystemClock.elapsedRealtime() - started > MAX_DURATION_MS)
                            stopped.compareAndSet(
                                    null, "This download exceeded the 10 minute limit.");
                        if (size(work) > MAX_WORK_BYTES)
                            stopped.compareAndSet(
                                    null, "This video exceeds the temporary storage limit.");
                        if (stopped.get() != null) stop(id);
                    },
                    0,
                    250,
                    TimeUnit.MILLISECONDS);
            progress.update("Finding a downloadable video…");
            engine.execute(request(url, work, quality, customId, needsAudio), id, progress);
            checkCancelled();
            if (stopped.get() != null) throw new IOException(stopped.get());
            SharedFile output = prepareOutput(context, work);
            if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                output.file.delete();
                throw new InterruptedIOException("Download cancelled.");
            }
            return output;
        } catch (IOException e) {
            throw e;
        } catch (LinkageError e) {
            checkCancelled();
            throw new IOException(RUNTIME_FAILURE, e);
        } catch (Exception e) {
            if (cancelled.get() || Thread.currentThread().isInterrupted())
                throw new InterruptedIOException("Download cancelled.");
            if (stopped.get() != null) throw new IOException(stopped.get());
            throw new IOException(
                    "No downloadable video was found. This link may be unsupported, require a"
                            + " sign-in, or be unavailable. Check your connection and try again.");
        } finally {
            if (cancelled.get() || stopped.get() != null) stop(id);
            monitor.shutdownNow();
            CacheFiles.deleteTree(work);
        }
    }

    static YoutubeDLRequest formatsRequest(String url) {
        YoutubeDLRequest request = baseRequest(url);
        request.addOption("--skip-download");
        request.addOption("--dump-single-json");
        request.addOption("--no-warnings");
        request.addOption("--retries", 1);
        return request;
    }

    java.util.List<VideoFormat> formats(Context context, String url) throws IOException {
        String id = UUID.randomUUID().toString();
        long start = android.os.SystemClock.elapsedRealtime();
        AtomicBoolean timedOut = new AtomicBoolean();
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        try {
            checkCancelled();
            monitor.scheduleWithFixedDelay(
                    () -> {
                        if (android.os.SystemClock.elapsedRealtime() - start > 60_000)
                            timedOut.set(true);
                        if (cancelled.get() || timedOut.get()) stop(id);
                    },
                    0,
                    250,
                    TimeUnit.MILLISECONDS);
            engine.init(context.getApplicationContext());
            checkCancelled();
            if (timedOut.get())
                throw new IOException("Format lookup timed out. Try a preset or retry Custom.");
            String json = engine.inspect(formatsRequest(url), id);
            checkCancelled();
            if (timedOut.get())
                throw new IOException("Format lookup timed out. Try a preset or retry Custom.");
            return VideoFormat.parse(json);
        } catch (IOException e) {
            throw e;
        } catch (LinkageError e) {
            checkCancelled();
            throw new IOException(RUNTIME_FAILURE, e);
        } catch (Exception e) {
            checkCancelled();
            throw new IOException(
                    "Couldn’t load formats. Check your connection, try a preset, or select Custom"
                            + " to retry.");
        } finally {
            monitor.shutdownNow();
            if (cancelled.get() || timedOut.get()) stop(id);
        }
    }

    private void stop(String id) {
        try {
            engine.cancel(id);
        } catch (RuntimeException | LinkageError ignored) {
            /* Retry on the next monitor tick. */
        }
    }

    private void checkCancelled() throws InterruptedIOException {
        if (cancelled.get() || Thread.currentThread().isInterrupted())
            throw new InterruptedIOException("Download cancelled.");
    }

    static SharedFile prepareOutput(Context context, File work) throws IOException {
        File[] files =
                work.listFiles((dir, name) -> name.startsWith("video.") && mimeFor(name) != null);
        if (files == null || files.length != 1)
            throw new IOException(
                    "No single video was produced. This link may not contain a supported video, or"
                            + " the video exceeds the size limit.");
        File file = files[0];
        if (!file.isFile() || file.length() == 0 || file.length() > MAX_VIDEO_BYTES)
            throw new IOException("The downloaded video is empty or exceeds the 256 MB limit.");
        String extension =
                file.getName().substring(file.getName().lastIndexOf('.')).toLowerCase(Locale.ROOT);
        File directory = ShareProvider.directory(context);
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Cannot prepare temporary video storage.");
        File target = new File(directory, UUID.randomUUID() + extension);
        if (!file.renameTo(target)) throw new IOException("Cannot prepare the video for sharing.");
        return new SharedFile(context, target, mimeFor(target.getName()));
    }

    static String mimeFor(String name) {
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        switch (extension) {
            case "mp4":
            case "m4v":
                return "video/mp4";
            case "mkv":
                return "video/x-matroska";
            case "webm":
                return "video/webm";
            case "mov":
                return "video/quicktime";
            case "avi":
                return "video/x-msvideo";
            case "flv":
                return "video/x-flv";
            case "ts":
                return "video/mp2t";
            case "ogv":
                return "video/ogg";
            default:
                return null;
        }
    }

    static long size(File file) {
        if (!file.isDirectory()) return file.length();
        long total = 0;
        File[] files = file.listFiles();
        if (files != null) for (File child : files) total += size(child);
        return total;
    }
}
