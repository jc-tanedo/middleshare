package dev.middleshare;

import android.content.Intent;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SharedLink {
    private static final Pattern URL = Pattern.compile("(?i)https?://[^\\s<>\"']+");

    static String read(Intent intent) throws IOException {
        if (!Intent.ACTION_SEND.equals(intent.getAction())) return null;
        // An attachment takes priority over a caption containing a URL.
        if (intent.hasExtra(Intent.EXTRA_STREAM)) return null;
        if (intent.getClipData() != null) {
            for (int i = 0; i < intent.getClipData().getItemCount(); i++) {
                if (intent.getClipData().getItemAt(i).getUri() != null) return null;
            }
        }
        CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (text == null
                && intent.getClipData() != null
                && intent.getClipData().getItemCount() == 1) {
            text = intent.getClipData().getItemAt(0).getText();
        }
        if (text == null) return null;
        if (text.length() > 16_384)
            throw new IOException("This shared text is too long. Share one video link.");
        Matcher matcher = URL.matcher(text);
        if (!matcher.find())
            throw new IOException("No video link was found. Share an HTTP or HTTPS link.");
        String url = matcher.group();
        if (matcher.find()) throw new IOException("Share one video link at a time.");
        while (!url.isEmpty() && ".,!?;:".indexOf(url.charAt(url.length() - 1)) >= 0)
            url = url.substring(0, url.length() - 1);
        for (String pair : new String[] {"()", "[]", "{}"}) {
            char open = pair.charAt(0), close = pair.charAt(1);
            while (url.endsWith(String.valueOf(close)) && count(url, close) > count(url, open))
                url = url.substring(0, url.length() - 1);
        }
        try {
            URI uri = new URI(url);
            if (uri.getHost() == null || uri.getRawUserInfo() != null)
                throw new URISyntaxException(url, "Invalid host");
            return uri.toASCIIString();
        } catch (URISyntaxException e) {
            throw new IOException("This video link is not valid.");
        }
    }

    private static long count(String text, char character) {
        return text.chars().filter(value -> value == character).count();
    }
}
