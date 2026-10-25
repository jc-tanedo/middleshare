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

Use matching main/add-on APKs from release 0.8.1 or later for foreground downloads and the favorite shortcut. The main app rejects older add-ons.

## Background downloads and destinations

After **Download video**, an Android foreground service owns the download. With notifications enabled, the drawer closes and an ongoing notification shows progress, **Choose destination**, and **Cancel**. You can switch apps or close the drawer. On Android 13+, the video add-on asks for notification permission; if you decline or disable the channel, a progress drawer remains available instead. The add-on's home screen also has **View downloads** and notification settings.

Choose a destination through the ongoing notification. No unfinished file or URI read grant is sent to that app. When the video is complete, the notification becomes **Video ready to send**. Tap **Send** to open your chosen app, or pick a compatible app if no destination was selected or it is no longer available. Android restricts automatic app launches from the background, so completion does not pop another app over what you are doing.

Ready notifications remain until handoff or **Discard**. Handoff clears the notification but retains the cached file for the receiving app to read. This selects an **app**, not a contact or conversation: finish choosing the recipient and sending inside that app. MiddleShare cannot observe the final Send action in another app.

The selected destination and ready-file metadata are saved privately without the source URL. Rotation and closing the drawer do not restart a download. A force stop, reboot, service timeout, or process loss can interrupt it; interrupted records explain that you need to share the link again. There is no automatic restart or resumable download queue. Android may allow users to dismiss ongoing notifications; pending work remains accessible in **View downloads**.

## Favorite share shortcut

In the main app, choose **Favorite app**. The app picker includes compatible image/video receivers and excludes MiddleShare itself. You can change or clear the favorite at any time.

Share to **MiddleShare Favorite** to process images and open the configured app without another destination selection. Video links use the configured site's quality (or global fallback) and saved favorite automatically; Custom still opens the per-link format picker. Video downloads finish through the ready notification as above. If no favorite is set, the shortcut uses the ordinary share flow. If the favorite cannot receive a particular image/batch/video format, choose another compatible receiver.

The favorite is a local app/activity preference. Sender-supplied destination extras are ignored by public receivers. The main app passes its saved favorite to the video add-on through a signature-permission-protected activity; use APKs from the same signed release.

## Download behavior

- Downloads one video; playlist links are limited to their first entry. Live/upcoming streams are excluded.
- Shows progress and supports cancellation. One active video download is allowed at a time; up to 20 completed/interrupted records can wait for handoff or discard.
- A `dataSync` foreground service and bounded CPU wake lock keep a download independent of the drawer. Android 15 foreground-service timeouts are handled as interruptions. A foreground service does not make the process immune to force stops or system termination.
- Maximum final video: 256 MiB. Temporary files are monitored with a 768 MiB cap and a 10-minute deadline. Network retries and socket waits are bounded. At least 512 MiB of allocatable storage is required before starting. yt-dlp skips sources known to exceed its size limit; the final output is checked again.
- Cancelling or failing removes partial files. Successful outputs live in private cache and are pruned on the next share after 24 hours. Android may evict cached files earlier. Old interrupted download directories are pruned as well.
- Read-only content URIs, MIME types, filenames, sizes, `EXTRA_STREAM`, and `ClipData` read grants let receiving apps read the files. MiddleShare excludes itself from the outgoing chooser.

WebP is detected from contents rather than the supplied MIME type or extension. Animated WebP becomes a still image. WebP metadata is not copied; decoding applies supported orientation and sRGB conversion. Images have a 64 MiB input limit and a decoding pixel budget of the smaller of 16 million pixels and `maxHeapBytes / 16`. Oversized images are rejected instead of silently resized.

Some links require sign-in or site-specific access, or may stop working when a site changes. This app does not provide a login/cookie flow. Image URLs are not downloaded as images by the video processor. Updated extractors ship in new app builds; see [THIRD_PARTY.md](../THIRD_PARTY.md) for the pinned yt-dlp release and checksum.
