# Sokoban

A Sokoban puzzle game for Android, written in Kotlin with Jetpack Compose.
Fifty hand-designed levels in five worlds, every one verified solvable.

## Build

Requirements: Android SDK 37 and JDK 17 or newer.

```bash
./gradlew test            # engine and app unit tests
./gradlew assembleDebug   # app/build/outputs/apk/debug/
./gradlew :app:lintDebug
```

## Release

```bash
./gradlew bundleRelease   # app/build/outputs/bundle/release/app-release.aab (for Google Play)
./gradlew assembleRelease # app/build/outputs/apk/release/app-release.apk
```

Both are minified with R8. They are signed with your upload key when one is
configured, and with the debug key otherwise (fine for testing; Google Play
refuses it). Keys and their passwords never go into git.

Create an upload key once, and keep a backup of it:

```bash
keytool -genkeypair -keystore ~/keys/sokoban-upload.jks -alias upload \
    -keyalg RSA -keysize 4096 -validity 10000
```

Then either write `keystore.properties` at the project root (git-ignored):

```properties
storeFile=/home/you/keys/sokoban-upload.jks
storePassword=…
keyAlias=upload
keyPassword=…
```

or set `SOKOBAN_KEYSTORE`, `SOKOBAN_KEYSTORE_PASSWORD`, `SOKOBAN_KEY_ALIAS` and
`SOKOBAN_KEY_PASSWORD` (for a build server). A relative `storeFile` is resolved
from the project root. A partial configuration fails the build instead of
falling back to the debug key.

Raise `versionCode` in `app/build.gradle.kts` for every upload.

## Layout

| Module  | Contents |
|---------|----------|
| `:core` | Pure Kotlin/JVM, no Android dependency: rules (`GameEngine`), immutable `GameState` with unlimited undo, level parser and validator, deadlock detection, campaign progress rules, and the level pack. |
| `:app`  | The Compose app: Canvas-drawn board and character, D-pad, swipe, tap-to-walk and keyboard input, screens, synthesised sound, haptics, DataStore persistence. |

Data flows one way: `GameEngine` → `GameState` → `GameViewModel` (`StateFlow`) → Compose.

## Levels

Levels use the standard text format (`#` wall, `.` goal, `$` box, `@` player,
`*` box on goal, `+` player on goal) and live in
`core/src/main/kotlin/dev/stefan/sokoban/core/levels/`.

Each level has a stored solution in `core/src/test/resources/solutions.txt`.
`LevelPackTest` replays every solution through the engine and re-proves
solvability with the solver, so a broken level fails the build. A level's par
is the length of its stored solution.

Design tools, run on demand:

```bash
./gradlew :core:test --tests '*LevelWorkbench*' -Psokoban.report=/tmp/report.txt --rerun
./gradlew :core:test --tests '*LevelWorkbench*' -Psokoban.generate=/tmp/rooms.txt --rerun
```

## Sound

Every effect is synthesised at startup. To replace one with a recording, add a
WAV file named after it to `app/src/main/assets/sounds/` (`button`, `step`,
`push`, `goal`, `bump`, `undo`, `restart`, `star`, `unlock`, `victory`); no
code change is needed.

## Licences

The Nunito font is used under the SIL Open Font License
(`docs/licenses/Nunito-OFL.txt`).
