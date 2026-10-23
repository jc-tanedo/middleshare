# Video runtime

MiddleShare retains the repository's GPL-3.0 license. The optional **MiddleShare Video** APK includes these upstream components. They are excluded from the lightweight main APK:

- [youtubedl-android](https://github.com/yausername/youtubedl-android), Maven artifacts `io.github.junkfood02.youtubedl-android:library:0.18.1` and `ffmpeg:0.18.1` (GPL-3.0). They supply the Android Python 3.12, QuickJS, and FFmpeg runtimes and their transitive dependencies. Upstream source, build scripts, and component licenses are available in that repository.
- [yt-dlp](https://github.com/yt-dlp/yt-dlp), release **2026.10.21** (Unlicense for its original source; see upstream for bundled dependency notices).

`app/src/video/res/raw/ytdlp` is the unmodified official zipapp from:

https://github.com/yt-dlp/yt-dlp/releases/download/2026.10.21/yt-dlp

SHA-256, verified against the official release's `SHA2-256SUMS` over HTTPS:

```
b7af77f686e95f7845ce8d09d21957718febb8b48b30555adb9dd2292ad7bf14
```

The app verifies this same hash before executing its extracted copy. `BundledYtDlp.VERSION` and `SHA256` pin the bundled release. To update, obtain the official zipapp and checksum manifest over verified HTTPS, verify the download, replace the raw resource and constants, update this notice, bump the app version, and rerun the tests. Do not disable signature, checksum, or TLS verification. The extractor executable and its runtimes ship only with the video add-on APK; the app does not automatically update them over the network. Site scripts may be retrieved as part of normal video extraction.

## Complete in-app inventory

The **About & open-source licenses** screen now includes the resolved direct and transitive Android libraries, all registered components of the Python/FFmpeg bundles, yt-dlp/EJS, and the JavaScript dependencies credited in EJS's own notices. License texts and supplied copyright notices are readable offline.

The authoritative generated catalog is `app/src/main/assets/third_party.json`. Native component provenance is maintained in `third_party/native-components.json`; additional copied code/assets belong in `third_party/additional-components.json`. The build's `verifyThirdPartyCredits` check prevents changed dependency artifacts or vendored payloads from silently retaining stale credits. See the [development guide](docs/DEVELOPMENT.md#about-and-open-source-credits) and [AGENTS.md](AGENTS.md) for the update workflow.
