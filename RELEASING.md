# Releasing MiddleShare

Version tags such as `v0.7.0` run `.github/workflows/release.yml`. The workflow tests both APK variants, checks lint, builds signed release APKs, verifies their signatures, writes SHA-256 checksums, and publishes a GitHub Release. Ordinary branch pushes and pull requests upload debug APKs as Actions artifacts only.

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
2. Tag that commit with the exact matching version (stable `vMAJOR.MINOR.PATCH` tags are supported):

   ```sh
   git tag -a v0.7.0 -m "MiddleShare 0.7.0"
   git push origin v0.7.0
   ```

3. Wait for **Actions → Release**. The published release includes the small main APK, four video add-on APKs (three ABIs plus universal), checksums, install instructions, and GitHub's source archives. The app links to `https://github.com/jc-tanedo/middleshare/releases/latest`.

Tags that do not match the committed app version fail before building. Use a new version/tag after publication; do not overwrite published tags or replace their APKs. A failed run before publication can be rerun after fixing repository secrets. If publication was partially completed, inspect the existing release before retrying; the workflow intentionally does not overwrite releases.

The first public release uses a different certificate from development/debug builds, so those builds must be uninstalled first (settings reset). Release-signed main and add-on installations subsequently update in place. Debug artifacts from different CI runs may use different debug keys; install both from the same run.

## Local signed build

Set `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`, then run `./gradlew assembleCoreRelease assembleVideoRelease`. Without signing variables, local release builds remain unsigned. Do not publish unsigned APKs. `RELEASE_TAG=v0.7.0 python3 scripts/release.py collect` verifies and stages signed APKs under ignored `release-assets/` when the Android SDK is configured.

## Device checks before tagging

- Install the lightweight main APK alone: convert WebP in both themes; share a link and check the Releases/install prompt.
- Install the matching video add-on: return to the pending share, tap **Try again**, download a public video, and share playable video/audio to another app.
- Check Mobile (480p), Standard (1080p), Full, and the Custom format picker. Presets must not perform a separate format lookup; selecting Custom should load once, survive rotation, and cancel when leaving it.
- Choose a video destination while downloading; check rotation, canceling the picker, and a receiver that becomes unavailable. Confirm the receiving app opens once with readable video/audio.
- Cancel a download, rotate during it, and test an unavailable link. Images should keep working without the add-on or network.
- An add-on signed with another key must be rejected; update both release APKs in place to check signing continuity. Check the About/licenses page in both apps.

The repository can stay private during development, but external users cannot access its Releases until it is public. No tag or public release is created by committing this configuration.
