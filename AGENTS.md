# Contributor instructions

Keep the in-app **About & open-source licenses** page accurate whenever adding or updating third-party libraries, copied snippets, vendored code, assets, native binaries, or their transitive dependencies. Include names, verified versions (or identify the bundle when exact versions are unavailable), license terms, source links, and supplied copyright/license notices. Do not guess a license or omit a component to make the checks pass.

Runtime dependency credits are maintained with:

```sh
./gradlew :app:exportThirdPartyArtifacts
python3 scripts/update_credits.py
./gradlew :app:verifyThirdPartyCredits
```

The generator audits all ABI bundles and the vendored yt-dlp zipapp. New native libraries or Python packages require reviewed entries in `third_party/native-components.json`. Include applicable license texts in `app/src/main/assets/licenses`, preserve supplied notices, and document their source URLs. Add explicit credits for copied source snippets and assets too; dependency resolution cannot discover their provenance automatically.

`verifyThirdPartyCredits` runs before builds and checks exact artifact hashes, vendored payloads, registry inputs, and notices. Preserve this guard. Check the About page in both themes and keep the relevant tests and README instructions current.

Cloud tasks use the existing checkout; do not create a Git worktree unless requested. Source `/workspace/tools/middleshare-env.sh` and pass `-I /workspace/tools/android-test.init.gradle` for JVM tests in this managed environment.
