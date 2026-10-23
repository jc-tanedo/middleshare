package dev.middleshare;

/** Presets need no metadata request; custom format IDs are scoped to one shared link. */
enum VideoQuality {
    MOBILE("Mobile · up to 480p", 480), STANDARD("Standard · up to 1080p", 1080),
    FULL("Full · best available", 0), CUSTOM("Custom · choose for each link", 0);
    final String label;
    final int height;
    VideoQuality(String label, int height) { this.label = label; this.height = height; }
    static VideoQuality parse(String value) {
        try { return valueOf(value); }
        catch (IllegalArgumentException | NullPointerException ignored) { return STANDARD; }
    }
    String selector(String id, boolean needsAudio) {
        if (this == CUSTOM) {
            if (id == null || !id.matches("[A-Za-z0-9_.-]{1,128}"))
                throw new IllegalArgumentException("Choose a video format before downloading.");
            return needsAudio ? id + "+ba/" + id : id;
        }
        String limit = height == 0 ? "" : "[height<=?" + height + "]";
        return "bv*" + limit + "+ba/b" + limit;
    }
    String sort() {
        return (height == 0 ? "res," : "res:" + height + ",") + "vcodec:h264,acodec:aac,ext:mp4:m4a";
    }
}
