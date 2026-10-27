# Releasing MiddleShare

Version tags such as `v0.8.5` run `.github/workflows/release.yml`. The workflow tests both APK variants, checks lint, builds signed release APKs, checks ZIP handler constructors in the optimized video APK, verifies signatures, writes SHA-256 checksums, and publishes a GitHub Release. Ordinary branch pushes and pull requests upload debug APKs as Actions artifacts only.

The **Artifact retention** workflow keeps the two newest completed versions of each Actions artifact type: core debug, video debug, and release archives. It runs after Android/Release workflows finish and can be run manually. Older combined `middleshare-debug` artifacts count toward the core history. Active runs and unrelated artifact names are protected. This cleanup never deletes published GitHub Release assets; GitHub's repository-wide artifact expiration limit still applies.

The release job's duplicate Actions archive is optional: hitting the Actions storage quota does not bypass validation or block the required direct upload to GitHub Releases. Debug artifact uploads still fail if storage is full. GitHub can take 6–12 hours to recalculate usage after cleanup.

## One-time signing setup

Create and **securely back up** one release signing key. Both the main app and video add-on must always use this key, including future updates. Losing it prevents updates to existing installations. Do not commit the keystore or passwords.

```sh
keytool -genkeypair -keystore middleshare-release.jks -alias middleshare \
  -keyalg RSA -keysize 4096 -validity 10000
```

In GitHub → repository Settings → Secrets and variables → Actions, add these **repository secrets**:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded keystore contents (`base64 -w 0 middleshare-release.jks` on Linux) |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | `middleshare`, or your chosen alias |
| `ANDROID_KEY_PASSWORD` | Key password |

The workflow refuses to publish without these secrets. It uses the job's built-in GitHub token (`contents: write`); no personal access token is required. Repository/organization policy must permit workflow releases. The key is decoded only in the runner's temporary directory and removed in an always-run cleanup step. The signing secrets must remain unchanged across releases.

## Cut a release

1. Update `versionName` and increment `versionCode` in `app/build.gradle`. Commit and push the change to `main`.
2. Tag that commit with the exact matching version. Stable `vMAJOR.MINOR.PATCH` tags and preview suffixes `-alpha.N`, `-beta.N`, and `-rc.N` (starting at 1) are supported:

   ```sh
   git tag -a v0.8.5 -m "MiddleShare 0.8.5"
   git push origin v0.8.5
   ```

3. Wait for **Actions → Release**. The published release includes the small main APK, four video add-on APKs (three ABIs plus universal), checksums, install instructions, and GitHub's source archives. The app links to `https://github.com/jc-tanedo/middleshare/releases/latest`.

Preview tags such as `v0.8.5-rc.1` require the matching `versionName '0.8.5-rc.1'` and are automatically published as GitHub prereleases. They do not replace the latest stable release. Install them from their specific release page. Increase `versionCode` again when building the final stable version so it can update preview installations.

Tags that do not match the committed app version fail before building. Use a new version/tag after publication; do not overwrite published tags or replace their APKs. A failed run before publication can be rerun after fixing repository secrets. If publication was partially completed, inspect the existing release before retrying; the workflow intentionally does not overwrite releases.

Debug APKs now use `dev.middleshare.debug` and `dev.middleshare.video.debug`, with separate launcher names, provider authorities, and favorite signature permissions. They can coexist with release APKs. Install the matching debug main/add-on pair. Debug installs from before 0.8.4 used the release IDs; remove those old debug installs once if you need to install release-signed APKs under those IDs. Settings stay private to each installation.

## Persistent debug signing

Create and back up a **separate debug keystore**, never the release keystore. Use PKCS12 (the keytool default), alias `middleshare-debug`, and the same password for the store and key:

```sh
keytool -genkeypair -keystore middleshare-debug.jks -alias middleshare-debug \
  -keyalg RSA -keysize 3072 -validity 10000
```

Add two repository secrets:

| Secret | Value |
| --- | --- |
| `ANDROID_DEBUG_KEYSTORE_BASE64` | Base64-encoded debug keystore (`base64 -w 0 middleshare-debug.jks` on Linux) |
| `ANDROID_DEBUG_KEYSTORE_PASSWORD` | Debug keystore/key password |

Push builds use this identity when configured, so future debug APKs update in place. The key is decoded into the runner's temporary directory with owner-only permissions and removed after the build. Release secrets are never used by this step. Keep both debug secrets unchanged between builds and do not commit the key or password.

Without these secrets, CI continues using its temporary Android debug identity; pull-request builds also use a temporary identity. Use push-build artifacts with the persistent key for installable updates. Switching an existing debug installation to a different key requires a one-time uninstall.

For local builds, set `ANDROID_DEBUG_KEYSTORE_PATH` and `ANDROID_DEBUG_KEYSTORE_PASSWORD` to use the same debug identity; otherwise Gradle uses the local SDK debug keystore.

## Local signed build

Set `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`, then run `./gradlew assembleCoreRelease assembleVideoRelease verifyVideoReleaseZipHandlers`. Without signing variables, local release builds remain unsigned. Do not publish unsigned APKs. `RELEASE_TAG=v0.8.5 python3 scripts/release.py collect` verifies and stages signed APKs under ignored `release-assets/` when the Android SDK is configured.

## Device checks before tagging

- Install the lightweight main APK alone: convert WebP in both themes; share a link and check the Releases/install prompt.
- Install the matching video add-on: return to the pending share, tap **Try again**, download a public video, and share playable video/audio to another app.
- Check Mobile (480p), Standard (1080p), Full, and the Custom format picker. Presets must not perform a separate format lookup; selecting Custom should load once, survive rotation, and cancel when leaving it.
- Allow/deny notification permission on Android 13+; check foreground progress, Cancel, screen-off/background behavior, and ready Send/Discard. Open View downloads after notifications are disabled or dismissed. Force-stop/reopen: interrupted work must not restart automatically.
- Choose a favorite; share an image, batch, and link to MiddleShare Favorite. Check incompatible/missing favorites, per-domain quality, Custom lookup, matching signatures, and the protected favorite handoff.
- Minimize a video download, wait for Finished, and tap Share. Cancel the native chooser and retry; selecting a target should clear the notification while retaining a readable video/audio file. Check enabled/disabled favorite aliases and debug/release coexistence.
- Cancel a download, rotate during it, and test an unavailable link. Images should keep working without the add-on or network.
- An add-on signed with another key must be rejected; update both release APKs in place to check signing continuity. Check the About/licenses page in both apps.

The repository can stay private during development, but external users cannot access its Releases until it is public. No tag or public release is created by committing this configuration.
