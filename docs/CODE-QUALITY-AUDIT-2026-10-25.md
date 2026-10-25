# Code quality audit — 25 October 2026

Baseline: `ea64efe` (0.8.4). This report accompanies the cleanup commit before release.

## Outcome

The native app is small enough to maintain with its current flavor split and framework UI. The review found useful refactoring opportunities and one release-certificate check that needed updating. Those findings are addressed below. No remaining code-quality release blocker was identified within the reviewed scope.

The review covered maintained Java app code, settings and transfer state, manifest/build boundaries, signing/release scripts, and CI workflows. The credits generator and its integrity contract were inspected; bundled third-party sources, notices, and binaries remain unchanged.

## Findings addressed

| Priority | Finding | Resolution |
| --- | --- | --- |
| P1 | Release collection recognized the standard Android debug certificate but not the newly introduced MiddleShare Debug certificate. | Reject both known debug certificate subjects, validate a complete SHA-256 fingerprint, and cover rejection and malformed output with Python tests. |
| P2 | Activities repeated theme attachment and share drawers repeated sizing lifecycle code. | Add package-private `ThemedActivity` and `SheetActivity`; keep exported activity class names and lifecycle timing unchanged. |
| P2 | Main screen creation mixed all settings cards into a long `onCreate`. | Compose named card-building methods from a short `onCreate`. |
| P2 | Favorite discovery lived inside UI preferences even though receiver eligibility and dispatch lived in `ShareDestinations`. | Put discovery, alias eligibility, and finished-payload validation together in `ShareDestinations`. The picker remains responsible for presentation and saving the selection. |
| P2 | The download list kept status views, buttons, and cards in three parallel maps. | Give each transfer one `TransferRow` that owns and renders its views. |
| P2 | Core and video stubs repeated recursive cache deletion; yt-dlp download and format requests repeated common flags. | Introduce shared `CacheFiles` cleanup and one base yt-dlp request builder. Preserve per-operation retries, limits, and format options. |
| P2 | Debug and release scripts independently decoded keystores and created private files. | Share `scripts/signing.py`, with explicit overwrite behavior and owner-only permissions, while retaining separate keys and secret names. |
| P3 | Repeated picker construction, redundant button setters, literal request codes, and repeated transfer/persistence constants obscured intent. | Reuse `Ui.picker` and the existing button factory; name activity request codes, host-quality prefixes, pending-transfer limits, and notification-ID calculation. |
| P3 | Release script formatting and CLI errors made operational code harder to read. | Expand compound expressions, add standard CLI help/argument validation, and report invalid certificate output explicitly. |

The refactor adds no runtime libraries, copied third-party code, or assets. Credits and dependency hashes continue to pass their existing verification guard.

## Structure and conventions

- `app/src/main` holds shared UI, preferences, image processing, share contracts, and framework helpers. Internal implementation classes remain package-private where possible.
- `app/src/video` holds the foreground download service, transfer persistence, notifications, format discovery, and native runtime integration. `app/src/core` provides the lightweight video boundary stub. Core APKs continue to contain no video native runtime or Internet permission.
- Keeping public share activity names stable protects Android intent entry points and cross-APK handoff. A package-tree migration would need compatibility planning. With roughly 4,000 lines of app Java, the existing package remains practical; more feature packages can be introduced as the app grows.
- Theme and drawer inheritance now share only framework lifecycle mechanics. Image conversions, format lookup, and service-owned transfers keep distinct cancellation and retention behavior. Their similar worker patterns do not justify a generic job framework yet.
- Resource-boundary validation remains explicit: parsed links, incoming content URIs, cached filenames, and receiver eligibility have different trust constraints. Similar-looking checks at those boundaries are intentional.
- Java follows the existing AOSP formatting style and `.editorconfig`. UI construction reuses `Ui` helpers; release/signing tooling uses only Python's standard library.

## Non-blocking follow-ups

1. **Localization:** some user-facing English prose remains in Java rather than `strings.xml`. Consolidate it before adding translations so localized messages and accessibility labels stay consistent.
2. **Favorite-picker profiling:** discovery still runs synchronously when the grid opens. It performs multiple PackageManager queries to find content-URI, MIME-specific, and batch receivers. Profile on devices with large app inventories before introducing asynchronous loading or caching; a cache must still respect dynamically enabled/disabled aliases.
3. **Credits-generator readability:** `scripts/update_credits.py` is denser than the runtime code. Reformat and decompose it alongside a reviewed inventory update. Its source hash is part of the provenance guard, so an edit requires regenerating and reviewing the catalog rather than bypassing that guard.

## Verification

- **108 Android JVM tests passed:** 43 core and 65 video, including theme overrides, About/credits, favorite aliases, debug isolation, image conversion, Custom formats, cancellation, restored transfers, and native-chooser handoff contracts.
- **8 Python tests passed:** both debug-certificate rejections, complete/malformed fingerprints, version matching, Base64 decoding, private permissions, explicit overwrite, and partial debug-secret configuration.
- Both debug lint reports: **No issues found**.
- Built debug and optimized release APKs for both flavors. Local release APKs remain unsigned; production signing stays in the release workflow.
- Verified core APK runtime isolation, the four package IDs, provider/favorite-permission separation, Java formatting, workflow syntax, and whitespace checks.
- The third-party credits guard passed without inventory or notice changes.

These checks exercise host-side behavior and packaging. A device smoke test of the refactored screens, rotation, background downloads, and sharing remains appropriate before tagging; live website/native subprocess behavior and OS background restrictions are outside JVM test coverage.
