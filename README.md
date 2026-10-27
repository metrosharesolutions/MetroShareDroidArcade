# MetroShare Droid Arcade — GEM DROP

A retro arcade puzzle game for Android, inspired by the falling-jewel puzzlers of the late 80s
and early 90s. Neon synthwave visuals, chiptune music and sound effects generated in code, big
on-screen arcade buttons, and levels that never end.

## Download

**Latest APK:** https://github.com/metrosharesolutions/MetroShareDroidArcade/releases/latest/download/GemDrop.apk

Open the link on your Android phone (Android 8.0 or newer), allow installs from your browser
when asked, and tap the downloaded file. New builds install over the old one and keep your
high scores and settings.

## How to play

- A column of three gems falls into the well. Line up **3 or more gems of the same colour**
  horizontally, vertically or diagonally to clear them.
- Gems above fall into the gaps and can set off **chain reactions** — each step multiplies
  the points.
- **ROTATE** cycles the order of the three falling gems.
- The rainbow **magic gem** destroys every gem of the colour it lands on.
- Clear enough gems to reach the next level. Every level is faster, brings a new colour scheme,
  and eventually more gem colours. There is no last level.
- Clearing the whole board earns an **ALL CLEAR** bonus.

### Controls

| Button | Action |
| --- | --- |
| ◀ ▶ (yellow) | Move left / right (hold to slide) |
| ▼ (green) | Fast fall (soft drop, +1 point per row) |
| ROTATE (red) | Cycle the gem order |
| DROP (blue) | Slam the piece down (hard drop, +2 per row) |
| ❚❚ | Pause |

You can also swipe on the board: drag to move, tap to rotate, flick down to drop.
Bluetooth gamepads and keyboards work too (D-pad / WASD, A = rotate, B or Space = drop,
Start / P = pause).

## Features

- Unlimited levels with rising speed and six rotating neon themes
- Top-10 high score table with classic three-letter initials entry
- Settings: music and sound-effect volume, vibration, difficulty (Easy / Normal / Hard),
  starting level, ghost piece, swipe controls, left-handed button layout, CRT scanline filter,
  and high score reset
- Colour-blind friendly: every gem colour has its own shape
- Tiny APK with no third-party libraries — all graphics and audio are generated at runtime

## Building

Requirements: JDK 17+ and the Android SDK (platform 35).

```sh
./gradlew testDebugUnitTest assembleRelease
```

The signed APK lands in `app/build/outputs/apk/release/`.

### Continuous delivery

`.github/workflows/build-release.yml` runs on every push:

1. Builds the app and runs the unit tests.
2. For `claude/**` branches, merges the branch into `main` once the build is green and starts a
   release run on `main`.
3. On `main`, publishes a GitHub Release (`v1.0.<run number>`) with `GemDrop.apk` attached.

`.github/workflows/screenshots.yml` renders every screen with Robolectric and publishes the PNGs
to the `ci-screenshots` branch.

### Signing

No signing key is stored in this repository. On its first release the workflow generates a key
and keeps it in the GitHub Actions cache (a weekly scheduled run stops it from expiring), so every
build has the same signature and updates install in place.

For a permanent key of your own, add these repository secrets and the workflow will use them
instead: `SIGNING_KEYSTORE_BASE64` (keystore file, base64-encoded), `SIGNING_STORE_PASSWORD`,
`SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`. Switching keys means uninstalling the old build
once.

## Project layout

```
app/src/main/java/com/metroshare/droidarcade/
  game/     pure-Kotlin rules: board, matching, chains, scoring, levels (unit tested)
  gfx/      gem sprites, neon text, synthwave background, particles
  audio/    chiptune synthesiser, sound effects and music
  ui/       arcade buttons and neon menu buttons
  scenes/   title, game, settings, high scores, initials entry, how to play
  data/     settings, leaderboard, haptics
```

Font: [Press Start 2P](https://fonts.google.com/specimen/Press+Start+2P) by CodeMan38,
SIL Open Font License (see `app/src/main/assets/fonts/OFL.txt`).
