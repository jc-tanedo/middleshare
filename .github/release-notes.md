MiddleShare 0.8.4 adds background video downloads and a favorite-app shortcut.

- The download drawer stays open by default. Tap **Minimize** to continue through a progress notification, then tap **Finished** to open Android's native share sheet.
- Choose a **Favorite app** from an icon grid and use **MiddleShare Favorite** to send processed images or videos to it. Dynamically enabled share receivers are supported.
- **View downloads** keeps pending files accessible, including when notifications are disabled. Canceling the native share sheet lets you retry.
- Improved drawer transitions, clearer download controls, and shared code cleanup keep the app lightweight. Debug builds now have separate application IDs and can coexist with release builds.

Install **middleshare-0.8.4.apk** for lightweight, offline image conversion.

For optional video downloads, also install **middleshare-video-0.8.4-arm64-v8a.apk** (most modern phones), **armeabi-v7a** (older 32-bit phones), or **x86_64** (compatible emulators/devices). Choose **universal** if unsure; it is much larger. Keep both apps installed, then share video links to the main MiddleShare app.

Download matching main and video APKs from this release. Both use the existing release signing key, so release installations update in place. Android may ask you to allow installation from your browser/files app.

Debug builds from 0.8.4 onward install separately. Older debug builds used the release application IDs and a different signing key; uninstall those old debug installations before replacing them with release APKs. Their settings will reset.

Validation: 108 Android tests, 8 release-tool tests, both flavors' lint and APK builds passed, followed by device testing.

SHA256SUMS lists checksums for every APK. Third-party licenses and source links are available in each app's About page. Source archives for this tag appear below.
