package dev.middleshare;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Metadata only; no direct media URLs or format IDs are persisted between shares. */
final class VideoFormat {
    final String id;
    final boolean needsAudio;
    final int height;
    final String label;

    private VideoFormat(String id, boolean needsAudio, int height, String label) {
        this.id = id;
        this.needsAudio = needsAudio;
        this.height = height;
        this.label = label;
    }

    static List<VideoFormat> parse(String json) throws IOException {
        if (json == null || json.length() > 16 * 1024 * 1024)
            throw new IOException("Format information is too large. Try a preset.");
        try {
            JSONObject root = new JSONObject(json);
            JSONArray entries = root.optJSONArray("entries");
            if (entries != null && entries.length() > 0 && entries.optJSONObject(0) != null)
                root = entries.getJSONObject(0);
            if (root.optBoolean("is_live") || root.optBoolean("is_upcoming"))
                throw new IOException("Live and upcoming videos are not supported.");
            JSONArray formats = root.optJSONArray("formats");
            ArrayList<VideoFormat> result = new ArrayList<>();
            HashSet<String> ids = new HashSet<>();
            if (formats != null)
                for (int i = 0; i < formats.length(); i++) {
                    JSONObject f = formats.optJSONObject(i);
                    if (f == null) continue;
                    String id = f.optString("format_id", "");
                    String codec = f.optString("vcodec", "none");
                    String ext = f.optString("ext", "");
                    if (!id.matches("[A-Za-z0-9_.-]{1,128}")
                            || codec.equals("none")
                            || codec.isEmpty()
                            || f.optBoolean("has_drm")
                            || VideoDownloader.mimeFor("video." + ext) == null
                            || !ids.add(id)) continue;
                    int height = Math.max(0, f.optInt("height"));
                    boolean needsAudio = "none".equals(f.optString("acodec", "none"));
                    String label =
                            (height > 0 ? height + "p" : "Unknown resolution")
                                    + " · "
                                    + ext
                                    + " · "
                                    + codec;
                    double fps = f.optDouble("fps", 0);
                    if (fps > 0) label += String.format(Locale.getDefault(), " · %.0f fps", fps);
                    double bytes = f.optDouble("filesize", 0);
                    boolean approximate = false;
                    if (bytes <= 0) {
                        bytes = f.optDouble("filesize_approx", 0);
                        approximate = true;
                    }
                    if (Double.isFinite(bytes) && bytes > 0)
                        label +=
                                String.format(
                                        Locale.getDefault(),
                                        " · %s%.1f MB%s",
                                        approximate ? "~" : "",
                                        bytes / (1024 * 1024),
                                        needsAudio ? " video" : "");
                    label += needsAudio ? " + audio" : " · includes audio";
                    result.add(new VideoFormat(id, needsAudio, height, label));
                }
            result.sort(Comparator.comparingInt((VideoFormat f) -> f.height).reversed());
            if (result.isEmpty())
                throw new IOException(
                        "No selectable video formats were found. Try a preset or another link.");
            return result;
        } catch (JSONException e) {
            throw new IOException("Couldn’t read video formats. Try a preset or another link.");
        }
    }

    @Override
    public String toString() {
        return label;
    }
}
