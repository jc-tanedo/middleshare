package dev.middleshare;

import android.content.ComponentName;
import android.content.Context;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Main-thread transfer state; no source URLs or Activity references are persisted. */
final class VideoTransfers {
    interface Observer {
        void changed(Task task);
    }

    private static final Map<String, Task> tasks = new LinkedHashMap<>();
    private static final Set<Observer> observers = new LinkedHashSet<>();

    static final class Task implements ConversionJob.Listener {
        final Context app;
        final String id;
        final int notificationId;
        ConversionJob job;
        ComponentName destination;
        String status;
        boolean removed;
        long lastNotification;

        Task(Context context, String id, int notificationId) {
            app = context.getApplicationContext();
            this.id = id;
            this.notificationId = notificationId;
            status = app.getString(R.string.transfer_starting);
        }

        private File metadata() {
            return new File(directory(app), id + ".json");
        }

        void persist() throws IOException {
            AtomicFile file = new AtomicFile(metadata());
            FileOutputStream stream = null;
            try {
                JSONObject data =
                        new JSONObject()
                                .put("id", id)
                                .put("notification", notificationId)
                                .put("complete", job != null && job.completed)
                                .put(
                                        "destination",
                                        destination == null
                                                ? JSONObject.NULL
                                                : destination.flattenToString())
                                .put(
                                        "error",
                                        job == null || job.error == null
                                                ? JSONObject.NULL
                                                : job.error);
                JSONArray files = new JSONArray();
                if (job != null)
                    for (SharedFile output : job.outputs) files.put(output.file.getName());
                data.put("files", files);
                stream = file.startWrite();
                stream.write(data.toString().getBytes(StandardCharsets.UTF_8));
                file.finishWrite(stream);
            } catch (Exception error) {
                if (stream != null) file.failWrite(stream);
                throw new IOException("Cannot save download state.", error);
            }
        }

        void destination(ComponentName target) {
            destination = target;
            saveAfterUpdate();
            notifyObservers(this);
        }

        private void saveAfterUpdate() {
            try {
                persist();
            } catch (IOException ignored) {
                status = app.getString(R.string.transfer_state_warning);
            }
        }

        @Override
        public void onProgress(int current, int total) {}

        @Override
        public void onStatus(String message) {
            if (removed || job.completed) return;
            status = message;
            notifyObservers(this);
        }

        @Override
        public void onComplete(ConversionJob result) {
            if (removed) return;
            status =
                    app.getString(
                            result.error == null
                                    ? R.string.transfer_ready
                                    : R.string.transfer_failed);
            saveAfterUpdate();
            notifyObservers(this);
        }
    }

    private static File directory(Context context) {
        File root = new File(context.getNoBackupFilesDir(), "video-transfers");
        if (!root.isDirectory()) root.mkdirs();
        return root;
    }

    static Task create(
            Context context,
            String url,
            VideoQuality quality,
            String format,
            boolean audio,
            ComponentName destination,
            VideoDownloader supplied)
            throws IOException {
        recover(context);
        long pending =
                tasks.values().stream().filter(task -> !task.removed && task.job.completed).count();
        if (pending >= 20)
            throw new IOException(context.getString(R.string.transfer_pending_limit));
        for (Task running : tasks.values())
            if (!running.removed && !running.job.completed)
                throw new IOException(context.getString(R.string.transfer_already_running));
        String id = UUID.randomUUID().toString();
        int notification = 2000 + (id.hashCode() & 0x3fffffff);
        Task task = new Task(context, id, notification);
        task.destination = destination;
        task.persist();
        task.job =
                supplied == null
                        ? new ConversionJob(context, url, quality, format, audio)
                        : new ConversionJob(context, url, quality, format, audio, supplied);
        tasks.put(id, task);
        task.job.attach(task);
        return task;
    }

    static Task find(Context context, String id) {
        if (id == null || !id.matches("[a-f0-9-]{36}")) return null;
        if (tasks.containsKey(id)) return tasks.get(id);
        File file = new File(directory(context), id + ".json");
        if (!file.isFile() && !new File(file.getPath() + ".bak").isFile()) return null;
        try {
            JSONObject data =
                    new JSONObject(
                            new String(new AtomicFile(file).readFully(), StandardCharsets.UTF_8));
            if (!id.equals(data.getString("id"))) return null;
            Task task = new Task(context, id, data.getInt("notification"));
            String target = data.isNull("destination") ? null : data.getString("destination");
            task.destination = target == null ? null : ComponentName.unflattenFromString(target);
            String error = data.isNull("error") ? null : data.getString("error");
            if (!data.getBoolean("complete"))
                error = context.getString(R.string.transfer_interrupted);
            List<SharedFile> outputs = new ArrayList<>();
            JSONArray files = data.getJSONArray("files");
            for (int i = 0; i < files.length(); i++) {
                String name = files.getString(i);
                if (!name.matches("[a-f0-9-]{36}\\.[a-z0-9]{1,10}"))
                    throw new IOException("Invalid cached file");
                File output = new File(ShareProvider.directory(context), name);
                String mime = VideoDownloader.mimeFor(name);
                if (!output.isFile() || mime == null)
                    error = context.getString(R.string.transfer_missing);
                else outputs.add(new SharedFile(context, output, mime));
            }
            if (error == null && outputs.isEmpty())
                error = context.getString(R.string.transfer_missing);
            task.job = ConversionJob.completedVideo(outputs, error);
            tasks.put(id, task);
            task.job.attach(task);
            return task;
        } catch (Exception ignored) {
            return null;
        }
    }

    static void discard(Context context, String id) {
        Task task = find(context, id);
        if (task != null) {
            remove(task, false);
            return;
        }
        if (id == null || !id.matches("[a-f0-9-]{36}")) return;
        new AtomicFile(new File(directory(context), id + ".json")).delete();
        context.getSystemService(android.app.NotificationManager.class)
                .cancel(2000 + (id.hashCode() & 0x3fffffff));
    }

    static void observe(Observer observer) {
        observers.add(observer);
    }

    static void forget(Observer observer) {
        observers.remove(observer);
    }

    private static void notifyObservers(Task task) {
        for (Observer observer : new ArrayList<>(observers)) observer.changed(task);
    }

    static void remove(Task task, boolean handedOff) {
        if (task == null || task.removed) return;
        task.removed = true;
        task.job.detach(task);
        if (handedOff) task.job.dispatched = true;
        else task.job.cancel();
        new AtomicFile(task.metadata()).delete();
        tasks.remove(task.id);
        task.app
                .getSystemService(android.app.NotificationManager.class)
                .cancel(task.notificationId);
        notifyObservers(task);
    }

    // A service/process restart cannot resume a native subprocess; metadata becomes an interrupted
    // result.
    static List<Task> recover(Context context) {
        List<Task> recovered = new ArrayList<>();
        File[] files =
                directory(context)
                        .listFiles(
                                (dir, name) ->
                                        name.endsWith(".json") || name.endsWith(".json.bak"));
        Set<String> seen = new LinkedHashSet<>();
        if (files != null)
            for (File file : files) {
                long savedAt = file.lastModified();
                String id = file.getName().substring(0, file.getName().indexOf(".json"));
                if (!seen.add(id)) continue;
                Task task = find(context, id);
                if (task != null) {
                    if (task.job.completed
                            && System.currentTimeMillis() - savedAt
                                    > ImageProcessor.CACHE_LIFETIME_MS) remove(task, false);
                    else recovered.add(task);
                }
            }
        return recovered;
    }
}
