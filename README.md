# MiddleShare

Native Android share middleware: convert WebP images or download a video from a supported link, then open Android’s share sheet with the resulting file. Android 9+.

## Use

- **Images:** Share an image attachment → MiddleShare → your destination app. JPEG is the default (quality 92, white background for transparency); select PNG in settings to preserve transparency. Other supported image formats pass through byte-for-byte. Batches support up to 20 images.
- **Video links:** Share one HTTP/HTTPS link as text → MiddleShare → wait for the download → choose the receiving app. Captions containing one link work. Image attachments take precedence over caption links. yt-dlp determines whether a link is supported; no separate double-fetch probe is needed.
- **Appearance:** Uses the device theme by default, including automatic light/dark changes. Choose **Light** or **Dark** to override it.
- **Try an image:** The home screen can open the system document picker.

Videos use yt-dlp with Python, QuickJS, and FFmpeg bundled on-device. The tools initialize only for video links. MP4/H.264/AAC and resolutions at or below 1080p are preferred; separate streams are merged without re-encoding. WebM or MKV may be used when an MP4-compatible source is unavailable. No conversion server is used. Link downloads contact the original site and consume your internet connection; image conversion stays local.

## Download behavior

- Downloads one video; playlist links are limited to their first entry. Live/upcoming streams are excluded.
- Shows progress and supports cancellation. Rotation retains the job; the share sheet opens once the activity is visible again.
- Keep the app open while downloading. There is no foreground service or durable background queue; Android can stop a background process.
- Maximum final video: 256 MiB. Temporary files are monitored with a 768 MiB cap and a 10-minute deadline. Network retries and socket waits are bounded. At least 512 MiB of allocatable storage is required before starting. yt-dlp skips sources known to exceed its size limit; the final output is checked again.
- Cancelling or failing removes partial files. Successful outputs live in private cache and are pruned on the next share after 24 hours. Android may evict cached files earlier. Old interrupted download directories are pruned as well.
- Read-only content URIs, MIME types, filenames, sizes, `EXTRA_STREAM`, and `ClipData` read grants let receiving apps read the files. MiddleShare excludes itself from the outgoing chooser.

WebP is detected from contents rather than the supplied MIME type or extension. Animated WebP becomes a still image. WebP metadata is not copied; decoding applies supported orientation and sRGB conversion. Images have a 64 MiB input limit and a decoding pixel budget of the smaller of 16 million pixels and `maxHeapBytes / 16`. Oversized images are rejected instead of silently resized.

Some links require sign-in or site-specific access, or may stop working when a site changes. This app does not provide a login/cookie flow. Image URLs are not downloaded as images by the video processor. Updated extractors ship in new app builds; see [THIRD_PARTY.md](THIRD_PARTY.md) for the pinned yt-dlp release and checksum.

## Build and test

Use Android Studio or a full JDK 17/21, Android SDK platform 35, build-tools 35.0.0, and platform-tools. Set `ANDROID_HOME`, or `sdk.dir` in ignored `local.properties`.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

The Gradle wrapper pins and verifies Gradle 8.11.1. Builds produce ARM64, ARMv7, x86_64, and universal APKs. Use `app-universal-debug.apk` if unsure of your device architecture. Debug builds use the existing development signing key so they can update v0.1. Release APKs are optimized and unsigned; use your own signing key for distribution.

The image/UI implementation remains Java with framework views and native image codecs. Video support adds the youtubedl-android and FFmpeg libraries, so APKs are substantially larger than the image-only release. ABI-specific packages reduce download size. The bundled extractor is SHA-256 checked before use, including when replacing an older extracted copy. TLS verification remains enabled.

Tests cover native image codecs on Android 14, Android 15 intent handling, device/default/override themes, link parsing, output MIME/URI permissions, download request policy, cancellation, partial cleanup, size limits, storage preflight, and bundled extractor integrity. The downloader engine and Android storage service are replaced with fixtures in JVM tests; these tests do not establish that live sites or Android native subprocesses work. Use the emulator/device smoke test for integration checks.

## Device smoke test

1. Toggle device light/dark mode while **Use device theme** is selected; check settings, buttons, processing and error screens, dialogs, and system bars. Test explicit Light/Dark overrides.
2. Share a transparent WebP → MiddleShare → a messaging app. Check JPEG white background; repeat with PNG transparency.
3. Share a public video link (e.g. YouTube, Vimeo, or a direct MP4) → MiddleShare → a video-capable receiving app. Check video playback and audio.
4. Rotate during download; confirm the job continues and only one chooser appears. Cancel an active download; confirm no partial output is shared.
5. Try an unsupported URL, an unavailable video, and a source requiring sign-in. Check that errors return control to the user.
6. Test an image batch, Android 9, a recent Android version, large text, and gesture navigation.

## Cloud workspace

Use the existing `/workspace/middleshare` checkout. Tasks are already isolated; do not create another Git worktree unless requested.

```sh
source /workspace/tools/middleshare-env.sh
cd /workspace/middleshare
./gradlew -I /workspace/tools/android-test.init.gradle testDebugUnitTest lintDebug assembleDebug assembleRelease
```

The retained SDK, JDK, Gradle cache, proxy, system CA trust, and writable Robolectric cache are configured outside the checkout. No development server is needed. An optional Android 15 x86_64 AVD named `middleshare-api35` is available under `/workspace/android-avd` for native integration checks; it uses software emulation when hardware acceleration is unavailable.

## About and open-source credits

Open **About & open-source licenses** from the home screen. The page follows the selected theme, lists runtime libraries and bundled code, and provides offline license/copyright notices plus upstream source links. Build/test tools are identified separately from software included in the app.

The checked-in catalog at `app/src/main/assets/third_party.json` includes the complete resolved Android dependency set and the Python, FFmpeg, codec, certificate, and JavaScript components found inside the shipped runtime bundles. Exact versions are shown where verified; otherwise entries identify the wrapper bundle they came from.

Before adding or updating third-party code:

```sh
./gradlew :app:exportThirdPartyArtifacts
python3 scripts/update_credits.py
./gradlew :app:verifyThirdPartyCredits
```

Normal builds run the verification automatically. It rejects changed dependencies, vendored payloads, registry inputs, or notice files with stale credits. Review new native components in `third_party/native-components.json` and preserve their upstream license notices in `app/src/main/assets/licenses`. `third_party/license-sources.json` records the origins of the downloaded standard/upstream notice texts. The generator also extracts license/NOTICE files from resolved artifacts and credits the dependencies named in the bundled EJS JavaScript notices.

Record copied snippets, vendored sources, or third-party assets in `third_party/additional-components.json` with `id`, `name`, `version`, `category`, `license`, `source`, `licenseUrls`, `noticeAssets`, and repository-relative `files`. Their file hashes are checked too. This inventory requires human review for copied code that dependency resolution cannot discover; see `AGENTS.md`.
