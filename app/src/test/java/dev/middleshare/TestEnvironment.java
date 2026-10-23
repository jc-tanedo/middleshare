package dev.middleshare;

import android.webkit.MimeTypeMap;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowMimeTypeMap;

final class TestEnvironment {
    static void installMimeMappings() {
        // Robolectric's MIME database starts empty; provide the platform entries used by fixtures.
        ShadowMimeTypeMap mime = Shadow.extract(MimeTypeMap.getSingleton());
        mime.addExtensionMimeTypeMapping("jpg", "image/jpeg");
        mime.addExtensionMimeTypeMapping("png", "image/png");
        mime.addExtensionMimeTypeMapping("webp", "image/webp");
    }
}
