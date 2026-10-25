package dev.middleshare;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Retained across configuration changes; only the application context reaches the worker. */
final class ConversionJob {
    interface Listener {
        void onProgress(int current, int total);

        void onComplete(ConversionJob job);

        default void onStatus(String message) {}
    }

    final ArrayList<SharedFile> outputs = new ArrayList<>();
    String error;
    boolean completed;
    boolean dispatched;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean cancelled;
    private final java.util.Set<Listener> listeners = new java.util.LinkedHashSet<>();
    final boolean video;
    private final VideoDownloader downloader;
    private String status;
    private int current = 1;
    private final int total;

    ConversionJob(Context context, List<Uri> inputs, Settings.Format format) {
        this(context, inputs, format, null, VideoQuality.BEST, null, false, null);
    }

    ConversionJob(
            Context context,
            String link,
            VideoQuality quality,
            String customId,
            boolean needsAudio) {
        this(
                context,
                java.util.Collections.emptyList(),
                Settings.Format.JPEG,
                link,
                quality,
                customId,
                needsAudio,
                null);
    }

    ConversionJob(
            Context context,
            String link,
            VideoQuality quality,
            String customId,
            boolean needsAudio,
            VideoDownloader downloader) {
        this(
                context,
                java.util.Collections.emptyList(),
                Settings.Format.JPEG,
                link,
                quality,
                customId,
                needsAudio,
                downloader);
    }

    private ConversionJob(
            Context context,
            List<Uri> inputs,
            Settings.Format format,
            String link,
            VideoQuality quality,
            String customId,
            boolean needsAudio,
            VideoDownloader suppliedDownloader) {
        Context app = context.getApplicationContext();
        video = link != null;
        downloader =
                video
                        ? (suppliedDownloader == null ? new VideoDownloader() : suppliedDownloader)
                        : null;
        total = video ? 1 : inputs.size();
        executor.execute(
                () -> {
                    ArrayList<SharedFile> results = new ArrayList<>();
                    String failure = null;
                    try {
                        ImageProcessor.cleanExpired(app);
                        if (video) {
                            results.add(
                                    downloader.download(
                                            app,
                                            link,
                                            quality,
                                            customId,
                                            needsAudio,
                                            message ->
                                                    main.post(
                                                            () -> {
                                                                status = message;
                                                                for (Listener observer :
                                                                        new ArrayList<>(listeners))
                                                                    observer.onStatus(message);
                                                            })));
                        }
                        for (int i = 0; i < inputs.size(); i++) {
                            ImageProcessor.checkCancelled();
                            if (cancelled) break;
                            int progress = i + 1;
                            main.post(
                                    () -> {
                                        current = progress;
                                        for (Listener observer : new ArrayList<>(listeners))
                                            observer.onProgress(current, total);
                                    });
                            results.add(ImageProcessor.process(app, inputs.get(i), format));
                        }
                    } catch (SecurityException e) {
                        failure =
                                "The sending app did not grant access to this image. Try sharing it"
                                        + " again.";
                    } catch (android.graphics.ImageDecoder.DecodeException e) {
                        failure =
                                "This WebP image could not be decoded. It may be damaged or"
                                        + " incomplete.";
                    } catch (IOException e) {
                        failure =
                                e.getMessage() == null
                                        ? "The image could not be read or saved."
                                        : e.getMessage();
                    } catch (RuntimeException | OutOfMemoryError e) {
                        failure =
                                "This share could not be processed safely. Try a smaller file or a"
                                        + " different link.";
                    }
                    if (failure != null || cancelled) {
                        delete(results);
                        results.clear();
                    }
                    String finalFailure = failure;
                    main.post(
                            () -> {
                                if (cancelled) {
                                    delete(results);
                                    return;
                                }
                                outputs.addAll(results);
                                error = finalFailure;
                                completed = true;
                                deliver();
                            });
                    executor.shutdown();
                });
    }

    private ConversionJob() {
        video = true;
        downloader = null;
        total = 1;
        completed = true;
        executor.shutdown();
    }

    static ConversionJob completedVideo(List<SharedFile> files, String failure) {
        ConversionJob job = new ConversionJob();
        job.outputs.addAll(files);
        job.error = failure;
        return job;
    }

    void attach(Listener listener) {
        listeners.add(listener);
        listener.onProgress(current, total);
        if (status != null) listener.onStatus(status);
        if (completed) listener.onComplete(this);
    }

    void detach(Listener listener) {
        listeners.remove(listener);
    }

    private void detach() {
        listeners.clear();
    }

    private void deliver() {
        if (completed)
            for (Listener observer : new ArrayList<>(listeners)) observer.onComplete(this);
    }

    void interrupt(String message) {
        if (completed) return;
        cancelled = true;
        if (downloader != null) downloader.cancel();
        executor.shutdownNow();
        delete(outputs);
        outputs.clear();
        error = message;
        completed = true;
        deliver();
    }

    void cancel() {
        cancelled = true;
        if (downloader != null) downloader.cancel();
        executor.shutdownNow();
        if (!dispatched) delete(outputs);
        detach();
    }

    private static void delete(List<SharedFile> outputs) {
        for (SharedFile output : outputs) output.file.delete();
    }
}
