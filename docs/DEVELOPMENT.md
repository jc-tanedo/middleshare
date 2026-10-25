# Development

## Build and test

Use Android Studio or a full JDK 17/21, Android SDK platform 35, build-tools 35.0.0, and platform-tools. Set `ANDROID_HOME`, or `sdk.dir` in ignored `local.properties`.

```sh
./gradlew testCoreDebugUnitTest testVideoDebugUnitTest lintCoreDebug lintVideoDebug assembleCoreDebug assembleVideoDebug assembleCoreRelease assembleVideoRelease
adb install -r app/build/outputs/apk/core/debug/app-core-universal-debug.apk
# Optional; both debug APKs must come from the same build:
adb install -r app/build/outputs/apk/video/debug/app-video-arm64-v8a-debug.apk
```

The Gradle wrapper pins and verifies Gradle 8.11.1. The `core` flavor keeps the original `dev.middleshare` package; `video` uses `dev.middleshare.video` and includes the heavy native dependencies. Each flavor produces ARM64, ARMv7, x86_64, and universal APKs; all core APKs contain the same architecture-independent code, so distribution uses its universal APK. Debug builds from one run share a development signing key. Local release APKs are optimized and unsigned unless signing environment variables are supplied. Tagged CI releases use persistent signing secrets; see [RELEASING.md](../RELEASING.md).

The image/UI implementation remains Java with framework views and native image codecs. Only the video flavor adds the youtubedl-android and FFmpeg libraries. The core dependency graph contains no third-party runtime libraries. ABI-specific add-on packages reduce download size. The bundled extractor is SHA-256 checked before use, including when replacing an older extracted copy. TLS verification remains enabled.

Tests cover add-on signature/version checks, missing-add-on prompts and retry, native image codecs on Android 14, Android 15 intent handling, device/default/override themes, link parsing, output MIME/URI permissions, download request policy, cancellation, partial cleanup, size limits, storage preflight, and bundled extractor integrity. The downloader engine and Android storage service are replaced with fixtures in JVM tests; these tests do not establish that live sites or Android native subprocesses work. Use the emulator/device smoke test for integration checks.

## Source layout

| Location | Responsibility |
| --- | --- |
| `app/src/main/` | Shared UI, settings, image processing, share intents, and the read-only provider |
| `app/src/core/` | Lightweight app manifest and unavailable-video stub |
| `app/src/video/` | Private download/quality activities, yt-dlp integration, native runtime resources |
| `app/src/test/` | Tests shared by both flavors |
| `app/src/testCore/`, `app/src/testVideo/` | Flavor-specific tests |
| `scripts/` | Release packaging and third-party attribution tooling |
| `third_party/`, `gradle/third-party.gradle` | Provenance registries and build-time attribution checks |

The small single-module project uses Android source sets to keep the video runtime out of the core APK. `SitePreferences` owns the site editor instead of adding more dialog logic to `MainActivity`. Activities handle UI and dispatch; workers retain only an application context. Keep these boundaries when adding features.

The exported `ShareActivity` accepts incoming shares but cannot treat extras as proof of user confirmation. Only `VideoDownloadActivity`, declared `exported=false`, starts a confirmed video download. New handoff changes must preserve this boundary and the add-on package/version/signature checks.

The video progress drawer can open Android's activity picker with a MIME-only share intent. `ShareDestinations` validates the returned component against exported, enabled video receivers and rechecks the finished MIME type before dispatch. Selection lives in activity state, not incoming share extras or saved preferences. Completed jobs defer dispatch while the picker is open or the activity is paused.

## Formatting

Java follows Google Java Format 1.24.0 in AOSP mode (four-space indentation). Use the formatter's `--aosp` option when formatting Java files. `.editorconfig` defines the shared whitespace conventions. Do not reformat vendored payloads, upstream license notices, or generated attribution files by hand.

## Device smoke test

1. Toggle device light/dark mode while **Use device theme** is selected; check settings, buttons, processing and error screens, dialogs, and system bars. Test explicit Light/Dark overrides.
2. Share a transparent WebP → MiddleShare → a messaging app. Check JPEG white background; repeat with PNG transparency.
3. Install the matching video add-on, then share a public video link (e.g. YouTube, Vimeo, or a direct MP4) → MiddleShare → a video-capable receiving app. Check video playback and audio.
4. Rotate during download; confirm the job continues and only one chooser appears. Cancel an active download; confirm no partial output is shared.
5. Try an unsupported URL, an unavailable video, and a source requiring sign-in. Check that errors return control to the user.
6. Test an image batch, Android 9, a recent Android version, large text, and gesture navigation.

## Cloud workspace

Use the existing `/workspace/middleshare` checkout. Tasks are already isolated; do not create another Git worktree unless requested.

```sh
source /workspace/tools/middleshare-env.sh
cd /workspace/middleshare
./gradlew -I /workspace/tools/android-test.init.gradle testCoreDebugUnitTest testVideoDebugUnitTest lintCoreDebug lintVideoDebug assembleCoreDebug assembleVideoDebug assembleCoreRelease assembleVideoRelease
```

The retained SDK, JDK, Gradle cache, proxy, system CA trust, and writable Robolectric cache are configured outside the checkout. No development server is needed. An optional Android 15 x86_64 AVD named `middleshare-api35` is available under `/workspace/android-avd` for native integration checks; it uses software emulation when hardware acceleration is unavailable.

## About and open-source credits

Open **About & open-source licenses** from either app’s home screen. Both apps include offline notices; video dependencies are explicitly marked **Optional video add-on** and are not claimed to be bundled in the core APK. The page follows the selected theme, lists runtime libraries and bundled code, and provides offline license/copyright notices plus upstream source links. Build/test tools are identified separately from software included in the app.

The checked-in catalog at `app/src/main/assets/third_party.json` includes the union of both flavors’ resolved Android dependency sets and the Python, FFmpeg, codec, certificate, and JavaScript components found inside the shipped runtime bundles. Exact versions are shown where verified; otherwise entries identify the wrapper bundle they came from.

Before adding or updating third-party code:

```sh
./gradlew :app:exportThirdPartyArtifacts
python3 scripts/update_credits.py
./gradlew :app:verifyThirdPartyCredits
```

Normal builds run the verification automatically. It rejects changed dependencies, vendored payloads, registry inputs, or notice files with stale credits. Review new native components in `third_party/native-components.json` and preserve their upstream license notices in `app/src/main/assets/licenses`. `third_party/license-sources.json` records the origins of the downloaded standard/upstream notice texts. The generator also extracts license/NOTICE files from resolved artifacts and credits the dependencies named in the bundled EJS JavaScript notices.

Record copied snippets, vendored sources, or third-party assets in `third_party/additional-components.json` with `id`, `name`, `version`, `category`, `license`, `source`, `licenseUrls`, `noticeAssets`, and repository-relative `files`. Their file hashes are checked too. This inventory requires human review for copied code that dependency resolution cannot discover; see [AGENTS.md](../AGENTS.md).
