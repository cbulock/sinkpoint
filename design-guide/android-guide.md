# Android implementation and reuse guide

## Starting point

Use Kotlin, Jetpack Compose, and Material 3 for the closest match. Minescape currently targets SDK 36, compiles against 36.1, supports API 26+, and uses JVM 17. Its build uses AGP 8.13.2, Kotlin 2.2.20, Compose BOM 2026.06.01, and Material 3 1.4.0. These are a reference snapshot, not a requirement to pin future apps indefinitely. Start from the repository's [app build file](../../../app/build.gradle.kts) and [root build file](../../../build.gradle.kts), then check compatibility when upgrading.

Build a new application with its own application ID, namespace, name, resources, signing setup, and save format. Minescape's namespace (`com.camer.minesweeper`) differs from its application ID (`com.cbulock.minescape`); neither belongs in a sibling app. Keep new project files outside this game's production source tree unless intentionally adding a module.

## What to carry over

| Source | Reuse approach | Replace or review |
| --- | --- | --- |
| [Theme.kt](../../../app/src/main/java/com/camer/minesweeper/ui/Theme.kt) | Copy theme policy and fallback schemes; rename theme wrapper | Package and local ThemeMode dependency |
| [MainActivity.kt](../../../app/src/main/java/com/camer/minesweeper/MainActivity.kt) | Follow edge-to-edge, lifecycle state collection, system-bar luminance handling | ViewModel/application classes |
| [HomeScreen.kt](../../../app/src/main/java/com/camer/minesweeper/ui/HomeScreen.kt) | Adapt grouped list, resume card, options and responsive layout | BoardConfig, difficulty, glyphs, generation/practice options |
| [SettingsScreen.kt](../../../app/src/main/java/com/camer/minesweeper/ui/SettingsScreen.kt) | Adapt appearance, toggle rows, chip wrapping, backup UI | Preferences and game-specific controls |
| [ResultDetails.kt](../../../app/src/main/java/com/camer/minesweeper/ui/ResultDetails.kt) | Adapt result shell, badges, metrics, action hierarchy | Game/result types, progress meaning, retry and daily rules |
| [StatisticsScreen.kt](../../../app/src/main/java/com/camer/minesweeper/ui/StatisticsScreen.kt) | Adapt overview, records, filter and history layouts | Metrics and eligibility |
| [Board.kt](../../../app/src/main/java/com/camer/minesweeper/ui/Board.kt) | Study shared geometry, semantic overlay and gesture cancellation | Renderer, coordinate model and all rules |
| [ResultEffects.kt](../../../app/src/main/java/com/camer/minesweeper/ui/ResultEffects.kt) | Follow fresh-transition and reduced-motion handling | Outcome-specific visuals |
| [DailyShare.kt](../../../app/src/main/java/com/camer/minesweeper/ui/DailyShare.kt) | Adapt preview/export flow and fresh PNG URI handling | Branding, data, dimensions and spoiler policy |
| [App.kt](../../../app/src/main/java/com/camer/minesweeper/ui/App.kt) | Study Back behavior and replacement confirmations | Navigation destinations and game commands |

These screens are not drop-in independent components: several are `internal`, helper functions are private, and signatures depend on Minesweeper models. Copy and adapt them for the first sibling. Extract a shared module once both games expose real common requirements; there is no shared family module today.

For an eventual shared Compose library, good candidates are the theme, centered page frame, section card, difficulty row, labeled metric, result surface, and appearance settings. Give these data/callback interfaces and keep game engines, score eligibility, daily puzzle catalogs, storage schemas, and canvas renderers in their apps.

## Suggested new-app structure

```text
game/                 Immutable state, rules, commands, game-specific geometry
data/                 Preferences, versioned saves, migration, records, backups
ui/theme/             Family theme and optional layout constants
ui/components/        Cards, rows, metrics, result shell
ui/screens/           Home, game, tutorial, results, settings, statistics
GameViewModel.kt      Commands, state, timer and background jobs
MainActivity.kt       Edge-to-edge Compose host
```

This is a recommended structure for siblings; Minescape currently keeps its theme and screen components together in `ui/`.

## Theme integration

Copy `Theme.kt`, rename `MinesweeperTheme` to a name appropriate to the new app, update its package, and supply a local `ThemeMode` enum with SYSTEM/LIGHT/DARK. Preserve the API 31 guard around dynamic color selection.

Inside components, read `MaterialTheme.colorScheme` and `MaterialTheme.typography`. Specify the component radii from the design guide explicitly; setting MaterialTheme alone does not reproduce Minescape's card geometry. Prefer corresponding on-container roles for tinted cards. Keep fixed game-symbol colors separate from the UI theme.

Use a top-centered `Box` and `Column(Modifier.widthIn(max = 560.dp).fillMaxWidth())` for a home reading column. Add horizontal 20 dp padding, scroll support, and 16 dp spacing. For settings/history use 640 dp; for a result surface use 440 dp with safe-area padding and scrolling details. Follow each original screen's modifier ordering when matching its exact width behavior.

## State and behavior to preserve

- Keep game rules independent of Compose. Serialize commands through a ViewModel and expose observable immutable state.
- Save progress automatically and restore unfinished games. Persist preferences immediately; use saveable UI state for transient choices where appropriate.
- Use a monotonic clock for elapsed play time. Pause it for menus, dialogs, backgrounding, and expensive setup; persist elapsed duration rather than a running wall-clock timestamp.
- Confirm before replacing unfinished progress. Back should dismiss the foremost overlay, then return to the originating page without losing the saved board.
- Make expensive generation/hint work cancellable and keep the UI responsive. Explain failure and provide retry or an explicit fallback where relevant.
- Record a finished game once. Define record eligibility for assisted play, replays, custom games, and official challenges before implementing statistics.
- Version saves from the first release. Validate imports before preview/confirmation, and preserve existing progress on parse or write failure.

Minescape's [GameRepository.kt](../../../app/src/main/java/com/camer/minesweeper/data/GameRepository.kt), [AppData.kt](../../../app/src/main/java/com/camer/minesweeper/data/AppData.kt), and [Backup.kt](../../../app/src/main/java/com/camer/minesweeper/data/Backup.kt) are implementation references. Define a new game-specific schema rather than reusing Minescape's version numbers or importing its saves.

Daily play is optional. If offered, freeze puzzle identities after distribution, define date/timezone behavior, keep daily and regular progress independent, and explain official versus practice attempts. Do not reuse Minescape's frozen catalog as a new game's data.

Minescape is offline with no accounts, ads, analytics, backend, or internet permission. Preserve that product baseline for siblings unless the new brief deliberately calls for something else. Configure backups intentionally: this app disables Android automatic backup and provides explicit export/import.

For sharing, use an app-specific non-exported FileProvider, a narrowly scoped cache path, and temporary read grants. Change provider authorities with the application ID. Never copy broad file access or include puzzle solutions in an official daily card.

## Build in this order

1. Fill the new-game brief and define the rules, state model, scoring eligibility, and input intents.
2. Set up the new app identity, build configuration, theme, and launcher resources.
3. Build home, settings, and an inert playfield to review the visual match in both themes.
4. Implement rules, safe input, ViewModel commands, automatic save/restore, and timer behavior.
5. Add results, replay, tutorial, and meaningful statistics. Add daily play and sharing only if in scope.
6. Review phone/tablet, landscape, large text, TalkBack, and reduced motion before publishing.

## Validation checklist for each sibling

- [ ] Light and dark fallback schemes match on API 26–30; API 31+ follows wallpaper colors with readable foregrounds.
- [ ] Home, gameplay, results, settings, and statistics work on a compact phone, landscape, a tablet, and at 200% text.
- [ ] Labels wrap, choices stack, detail panels scroll, and important actions remain reachable.
- [ ] TalkBack announces useful game state and actions; decorative artwork is skipped.
- [ ] Gestures cannot accidentally activate another target after a drag, pinch, mode change, or cancellation.
- [ ] Finishing disables moves but keeps useful inspection; reduced motion gives immediate usable results.
- [ ] Recreation and process restart restore progress; returning to a completed game does not replay celebrations or double-count a result.
- [ ] Back, replacement confirmation, import cancellation, and failed imports preserve progress.
- [ ] Practice/replay eligibility agrees across results, statistics, daily history, and share cards.
- [ ] Launcher artwork remains legible at 48 px under adaptive masks and in monochrome.

Use pure unit tests for rules and serialization, Compose interaction/layout tests for meaningful user behavior, and device checks for touch latency, TalkBack, animation smoothness, document pickers, and the share chooser. Minescape's tests in `app/src/test` and `app/src/androidTest` provide examples; game-specific assertions need replacement.

From the new project root, typical Gradle checks are:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
# Requires a booted emulator or connected test device:
./gradlew connectedDebugAndroidTest
```

Use `gradlew.bat` on Windows. Do not run data-resetting instrumented tests against a device whose saved progress you want to retain. A documentation-only change to Minescape does not require these Android builds.
