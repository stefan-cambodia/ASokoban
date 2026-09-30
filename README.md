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

`assembleRelease` builds a minified APK signed with the debug key; configure a
real signing config in `app/build.gradle.kts` before publishing.

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
