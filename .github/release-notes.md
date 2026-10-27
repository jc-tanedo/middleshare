**Prerelease:** MiddleShare 0.8.5-rc.1 fixes the video add-on crashing when starting a download in optimized release builds.

- Preserve the ZIP handler constructors needed to unpack the bundled Python and FFmpeg tools. This fixes the `ExceptionInInitializerError` / “not a concrete class” crash before video extraction begins.
- Handle runtime class-loading failures as visible errors instead of closing the app.
- Check the actual optimized APK in CI and before release publication so missing ZIP handler constructors block the build.

Install **middleshare-0.8.5-rc.1.apk** for lightweight, offline image conversion.

For optional video downloads, also install **middleshare-video-0.8.5-rc.1-arm64-v8a.apk** (most modern phones), **armeabi-v7a** (older 32-bit phones), or **x86_64** (compatible emulators/devices). Choose **universal** if unsure; it is much larger. Keep both apps installed, then share video links to the main MiddleShare app.

Update **MiddleShare Video** as well as the main app: the crash fix is in the video add-on, and the main app now requires version 0.8.5-rc.1 or later. Release installations update in place using the existing signing key. Debug builds install separately and require a matching debug video add-on.

SHA256SUMS lists checksums for every APK. Third-party licenses and source links are available in each app's About page. Source archives for this tag appear below.
