<p align="center">
  <img src="docs/assets/icon.svg" width="96" height="96" alt="MiddleShare icon">
</p>
<h1 align="center">MiddleShare</h1>
<p align="center">A little change. A smoother share.</p>
<p align="center">
  <a href="https://github.com/jc-tanedo/middleshare/actions/workflows/android.yml"><img src="https://github.com/jc-tanedo/middleshare/actions/workflows/android.yml/badge.svg" alt="Android build"></a>
  <img src="https://img.shields.io/badge/Android-9%2B-88C0D0" alt="Android 9 or newer">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-81A1C1" alt="GPL 3.0 license"></a>
</p>
<p align="center">
  <a href="#download">Download</a> · <a href="#how-it-works">How it works</a> · <a href="#documentation">Documentation</a>
</p>

MiddleShare sits between Android's share sheet and your destination app. Convert WebP images to JPEG or PNG, or download a video from a supported link, then share the resulting file.

- **Small by default.** Native Java, Android image codecs, and no third-party runtime libraries in the main app.
- **Video when you need it.** An optional add-on brings yt-dlp and FFmpeg, with quality presets and per-site preferences.
- **Fits your phone.** Compact share drawers, a Nord-inspired dark theme, and a light theme that follow your device settings.

## Download

Get signed APKs from **[the latest release](https://github.com/jc-tanedo/middleshare/releases/latest)**. Requires Android 9 or newer.

| APK | Install it for |
| --- | --- |
| `middleshare-VERSION.apk` | Offline WebP conversion. Start here. |
| `middleshare-video-VERSION-arm64-v8a.apk` | Optional video downloads on most modern phones. |

Other video builds: **armeabi-v7a** for older 32-bit devices, **x86_64** for compatible devices/emulators, or **universal** if unsure. Universal is much larger. Keep the main app installed and use both APKs from the same release.

Android may ask you to allow installation from your browser or file manager. `SHA256SUMS` accompanies every release. Existing debug builds must be uninstalled before installing release builds because they use a different signing key.

## How it works

1. Share an image or one supported video link to **MiddleShare**.
2. WebP converts using your saved format. For a video, choose a quality and tap **Download video**.
3. Pick the app that should receive the file.

Open MiddleShare to choose **JPEG or PNG**, set video quality, manage **Site preferences**, or change the theme. Video defaults to **Best**, letting yt-dlp choose. **Mobile**, **Standard**, **Full**, and **Custom** are also available; Custom loads formats only when selected.

The main app works offline and requests no internet permission. Video downloads contact the source site and need the optional add-on. Sharing a video link without it offers the Releases page so you can install it once and retry.

**Good to know:** animated WebP becomes a still image, other image formats pass through unchanged, videos have a 256 MiB final-file limit, and downloads need the app to remain open. Support varies by site; there is no sign-in/cookie flow. See [usage and limits](docs/USAGE.md) for details.

## Documentation

| Guide | Contents |
| --- | --- |
| [Usage and limits](docs/USAGE.md) | Quality presets, supported payloads, privacy, and storage behavior |
| [Development](docs/DEVELOPMENT.md) | Build commands, source layout, formatting, tests, and device checks |
| [Releasing](RELEASING.md) | Signing setup and automated version-tag releases |
| [Third-party software](THIRD_PARTY.md) | Video runtime provenance and attribution |
| [Security](SECURITY.md) | Reporting vulnerabilities and the repository audit |

## Contributing and license

Bug reports and focused pull requests are welcome. Read the [development guide](docs/DEVELOPMENT.md) and [contributor instructions](AGENTS.md) before changing dependencies or bundled code.

MiddleShare is licensed under [GPL-3.0](LICENSE). Both apps include an **About & open-source licenses** page with offline notices and upstream source links.
