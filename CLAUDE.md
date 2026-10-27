# CLAUDE.md

Gem Drop is a native Android game (Kotlin, no third-party libraries) in a single `:app` module.

## Owner preferences

- **Always merge work into `main`, then build and publish the APK to GitHub.** Pushing a
  `claude/**` branch triggers `.github/workflows/build-release.yml`, which builds, runs the unit
  tests, merges the branch into `main` and dispatches a release run on `main` that publishes
  `GemDrop.apk`. After pushing, check that both runs succeeded and the release exists. If the merge job cannot push to `main`,
  open a pull request and merge it instead (that push to `main` also releases).
- Keep the 80s/90s arcade style: neon colours, pixel font, chiptune audio, big arcade buttons.

## Building

- CI is the build system of record (`./gradlew testDebugUnitTest assembleRelease`).
- Cloud sandboxes usually cannot reach `dl.google.com`, so the Android SDK and AGP may not
  resolve locally. The game rules in `game/` have no Android dependencies and can be unit tested
  on a plain JVM.
- `versionCode` comes from `-PversionCode=<run number>`; keep it monotonic so updates install.
- Never commit signing keys or passwords. Release signing uses the `SIGNING_*` repository secrets
  when set, otherwise a key generated once by CI and kept in the Actions cache
  (`gem-drop-signing-key-v1`). Changing the cache key or the secrets changes the APK signature and
  forces players to reinstall, losing their high scores.
- `ScreenshotTest` (Robolectric, native graphics) renders every screen; CI publishes the PNGs to
  the `ci-screenshots` branch. The release build only runs the `game.*` tests.

## Code map

- `ArcadeApp` owns services and the current `Scene`; `GameView` drives the frame loop from
  `onDraw` (hardware canvas, so avoid `BlurMaskFilter` and shape shadow layers at draw time —
  pre-render those into bitmaps as `GemSprites` does).
- Everything is drawn in a 720-unit-wide virtual space; height follows the screen aspect.
- `GemDropGame` is the rules state machine; scenes listen through `GameEvents`.
