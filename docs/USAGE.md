# Usage and limits

- **Images:** Share an image attachment → MiddleShare → your destination app. JPEG is the default (quality 92, white background for transparency); select PNG in settings to preserve transparency. Other supported image formats pass through byte-for-byte. Batches support up to 20 images.
- **Video links:** Share one HTTP/HTTPS link as text → MiddleShare → wait for the download → choose the receiving app. Captions containing one link work. Image attachments take precedence over caption links. yt-dlp determines whether a link is supported; no separate double-fetch probe is needed.
- **Appearance:** Uses the device theme by default, including automatic light/dark changes. Dark uses a Nord-inspired slate and frost palette; Light keeps the softer neutral palette. Choose **Light** or **Dark** to override it.
- **Settings:** Image, Video, and Appearance cards group the defaults. **Site preferences** opens a list where you can add, edit, or remove host overrides. The shared button and selection styles use native Android views, with no added UI dependency.
- **Icon:** Original share-node M artwork, with a dotted split, adaptive launcher masks, and Android 13+ monochrome themed-icon support.
- **Try an image:** The home screen can open the system document picker.

The optional video add-on contains yt-dlp with Python, QuickJS, and FFmpeg. The main APK contains none of these runtimes. The tools initialize only for video links. Best leaves format selection to yt-dlp. Mobile and Standard cap the resolution at 480p and 1080p; Full has no resolution cap. Separate streams are merged without re-encoding. WebM or MKV may be used when an MP4-compatible source is unavailable. No conversion server is used. Link downloads contact the original site and consume your internet connection; image conversion stays local.

## Video quality

Choose the default or add per-domain overrides in the main app’s **Video links** settings. Sharing a link opens a compact bottom drawer where you can change quality for that download. A saved host preference overrides the global default for links from that host.

| Quality | Selection |
| --- | --- |
| Mobile | Best available video up to 480p; smaller downloads |
| Best (default) | Let yt-dlp choose its default format without extra format sorting |
| Standard | Best available video up to 1080p |
| Full | Best available resolution; potentially much larger |
| Custom | Fetch this link’s formats on demand, then choose an exact video format |

Presets start without a separate format-list lookup. Merely opening settings or the quality drawer with a preset selected never requests format metadata. Selecting **Custom** performs a cancellable metadata lookup (60-second deadline), then shows resolution, container, codec, frame rate, and known/estimated size. Video-only formats are paired with available audio. Sizes for these formats exclude added audio. DRM, audio-only, and storyboard formats are excluded.

A Custom default prompts for a new choice on each shared link. Exact format IDs are specific to that link and are never saved in preferences. Results are reused within the share, including across rotation; leaving Custom cancels an unfinished lookup. If loading fails, tap Custom again to retry or use a preset. Resolution caps apply when the source reports dimensions; sources with unknown resolution may pass through. All choices retain the 256 MiB final-file, temporary-storage, and download-time limits below. Full is not an unlimited file-size setting.

Use matching main/add-on APKs from release 0.6.1 or later to include the download-confirmation security fix. The main app rejects older add-ons.

## Choose a destination while downloading

Tap **Download video**, then **Choose destination** in the progress drawer. Android lets you pick an app while the download continues; the app receives the file only after it is complete. You can change the destination before completion. Canceling the picker keeps any previous selection, or uses the normal share sheet if no app has been selected.

The selection is scoped to the current share and survives rotation. If you leave MiddleShare or keep the picker open, handing over waits until you return and finish choosing. If the chosen app is unavailable or cannot receive the finished format, the usual share sheet opens instead. Choosing a destination is optional.

This selects an **app**, not a contact or conversation. Finish choosing the recipient and sending inside that app. No incomplete file or temporary read grant is sent while choosing.

## Download behavior

- Downloads one video; playlist links are limited to their first entry. Live/upcoming streams are excluded.
- Shows progress and supports cancellation. Rotation retains the job; the share sheet opens once the activity is visible again.
- Keep the app open while downloading. There is no foreground service or durable background queue; Android can stop a background process.
- Maximum final video: 256 MiB. Temporary files are monitored with a 768 MiB cap and a 10-minute deadline. Network retries and socket waits are bounded. At least 512 MiB of allocatable storage is required before starting. yt-dlp skips sources known to exceed its size limit; the final output is checked again.
- Cancelling or failing removes partial files. Successful outputs live in private cache and are pruned on the next share after 24 hours. Android may evict cached files earlier. Old interrupted download directories are pruned as well.
- Read-only content URIs, MIME types, filenames, sizes, `EXTRA_STREAM`, and `ClipData` read grants let receiving apps read the files. MiddleShare excludes itself from the outgoing chooser.

WebP is detected from contents rather than the supplied MIME type or extension. Animated WebP becomes a still image. WebP metadata is not copied; decoding applies supported orientation and sRGB conversion. Images have a 64 MiB input limit and a decoding pixel budget of the smaller of 16 million pixels and `maxHeapBytes / 16`. Oversized images are rejected instead of silently resized.

Some links require sign-in or site-specific access, or may stop working when a site changes. This app does not provide a login/cookie flow. Image URLs are not downloaded as images by the video processor. Updated extractors ship in new app builds; see [THIRD_PARTY.md](../THIRD_PARTY.md) for the pinned yt-dlp release and checksum.
