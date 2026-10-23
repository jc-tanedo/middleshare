package dev.middleshare;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A cancellable lookup retained across rotation; never holds an Activity on the worker. */
final class VideoFormatsJob {
    interface Loader {
        List<VideoFormat> load() throws Exception;
    }

    interface Listener {
        void ready(VideoFormatsJob job);
    }

    List<VideoFormat> formats;
    String error;
    boolean completed;
    private boolean cancelled;
    private Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Runnable stop;

    VideoFormatsJob(Context context, String link) {
        this(new VideoDownloader(), context.getApplicationContext(), link);
    }

    private VideoFormatsJob(VideoDownloader downloader, Context app, String link) {
        this(() -> downloader.formats(app, link), downloader::cancel);
    }

    VideoFormatsJob(Loader loader, Runnable stop) {
        this.stop = stop;
        executor.execute(
                () -> {
                    List<VideoFormat> result = null;
                    String failure = null;
                    try {
                        result = loader.load();
                    } catch (Exception | OutOfMemoryError e) {
                        failure = "Couldn’t load formats. Select Custom to retry, or use a preset.";
                    }
                    List<VideoFormat> loaded = result;
                    String failed = failure;
                    main.post(
                            () -> {
                                if (cancelled) return;
                                formats = loaded;
                                error = failed;
                                completed = true;
                                deliver();
                            });
                    executor.shutdown();
                });
    }

    void attach(Listener listener) {
        this.listener = listener;
        deliver();
    }

    void detach() {
        listener = null;
    }

    private void deliver() {
        if (completed && listener != null) listener.ready(this);
    }

    void cancel() {
        cancelled = true;
        detach();
        stop.run();
        executor.shutdownNow();
    }
}
