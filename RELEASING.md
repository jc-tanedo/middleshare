# Releasing MiddleShare

Version tags such as `v0.8.5` run `.github/workflows/release.yml`. The workflow tests both APK variants, checks lint, builds signed release APKs, checks ZIP handler constructors in the optimized video APK, verifies signatures, writes SHA-256 checksums, and publishes a GitHub Release. Ordinary trusted builds upload debug APKs to private SeaweedFS storage, with download links in the workflow summary.

The **Artifact retention** workflow keeps the two newest completed versions of each private artifact type: core debug, video debug, and release archives. It runs after Android/Release workflows finish and can be run manually. Active builds and other bucket contents are protected. Incomplete objects and multipart uploads from completed runs are removed. Existing GitHub Actions artifacts retain their previous two-version cleanup policy; new uploads no longer use GitHub's artifact quota. Published GitHub Release assets are never deleted by either cleanup.

The release job's private S3 archive is optional: a storage outage does not bypass validation or block the required direct upload to GitHub Releases.

## Private CI artifact storage

The shared `.github/actions/s3-artifacts` action defaults to endpoint `https://bucket.s3.jczr.dev`, bucket `gh-artifact-store`, region `us-east-1`, and path-style S3 requests. Artifacts live only under `ci-artifacts/OWNER/REPO/TYPE/RUN/ATTEMPT/`. Change the common action inputs to change these settings.

Configure repository **variable** `S3_ACCESS_KEY_ID` and repository **secret** `S3_SECRET_ACCESS_KEY`. The key ID must not also remain a secret: GitHub would redact it inside presigned URLs and break the links. The secret key is never included in links or logs. The storage identity needs listing, read, write, delete, and multipart permissions within the repository's artifact prefix. TLS verification stays enabled; the uploader does not make the bucket public.

Each run summary contains individual APK downloads and a manifest with SHA-256 checksums. Links expire after 24 hours and can stop working earlier if their build is pruned. To renew them without rebuilding, open **Actions → Artifact retention → Run workflow → download-links**. Choose **test-storage** to test listing, a 9 MiB multipart upload, presigned download/checksum, anonymous-access denial, and deletion of the test object. The default **cleanup** operation keeps the newest two complete builds of each type.

The uploader requires Python 3.10+. Its hash-pinned, pure-Python Boto3 SDK is cached in the runner's tool cache without pip, sudo, or system Python changes. These dependencies and their bundled notices appear in About as development-only tools; their code is not included in either APK. Fork/Dependabot pull requests still run build checks but do not receive storage credentials or upload private artifacts.

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
