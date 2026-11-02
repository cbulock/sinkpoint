# Quick Play, Daily Fleet Hunt, and personal bests Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement task by task. Track completion below.

**Goal:** Deliver the three approved additions without complicating standard combat.
**Architecture:** Pure serializable daily state and record models; repository-owned atomic result updates; ViewModel projections hide the daily fleet; Compose reuses the existing board.
**Tech Stack:** Kotlin, Compose, DataStore, JUnit, Robolectric.
**Spec:** `docs/superpowers/specs/2026-11-02-quick-play-daily-bests-design.md` (approved in chat).

## Global constraints
- Presets: Skirmish 6x6 / 3,2,2; Classic and Salvo 10x10 / 5,4,3,3,2; touching allowed.
- Daily: 8x8 / 4,3,2, fixed versioned generation, local date, first attempt only.
- Preserve old statistics and independent solo/nearby saves. No deployment.

## Review focus
- Failed writes and retried completion must not duplicate records or expose unsaved fleets (tasks 1, 2).
- Date rollover and clock rollback must preserve attempts and score eligibility (tasks 1, 2).
- Old backups and already-recorded finished saves must not invent personal bests (task 1).
- Exact fractions, equivalent custom fleets, and inactive salvo options must compare fairly (task 1).
- Small landscape screens and 200% text must keep Fire/setup actions reachable (task 3).

### Task 1: Durable models and statistics
Files: create `game/QuickPlay.kt`, `game/DailyHunt.kt`, `data/PersonalBests.kt`; modify `data/GameRepository.kt`; create `FeaturesTest.kt` (under the existing source/test package roots).
Interfaces: `QuickPreset.config(custom: GameConfig): GameConfig`; `DailyHunt.create(date: String): DailyHunt`, `DailyHunt.fire(cell: Int): DailyHunt`; `DailyProgress.start(date: String, replace: Boolean): DailyProgress`, `fire(cell: Int)`, `abandon()`; `BestCategory`, `PersonalBest.record(match: MatchState)`; repository adds daily progress and category bests.
- [x] Write tests for presets, deterministic fleet fixtures, scored/practice transitions, rollover, fractions, category normalization, streaks, migration and backup round trips.
- [x] Run `gradlew.bat testDebugUnitTest --tests '*FeaturesTest'`; observe missing interfaces/failing assertions.
- [x] Implement pure rules and atomic repository updates, with validated serialization and result eligibility.
- [x] Run focused tests and existing SaveTest; expect no failures.

### Task 2: ViewModel actions and private state
Files: modify `GameViewModel.kt`; create `DailyViewModelTest.kt`.
Interfaces: `UiState.daily` is public-only `DailyUi`; `startQuick(preset, difficulty)`, `startDaily(replace=false)`, `resumeDaily()`, `fireDaily(cell)`, `abandonDaily()`; clock injected as `() -> LocalDate`.
- [x] Write tests for independent saves, hidden fleet, write failure/retry, midnight and once-only results.
- [x] Run focused tests, observe failures, then implement commands and foreground/date refresh.
- [x] Run `DailyViewModelTest`, `BattleViewModelTest`, `FeaturesTest`, `SaveTest`; expect no failures.

### Task 3: Setup, daily gameplay, results and statistics
Files: modify `ui/Home.kt`, `ui/App.kt`, strings; create `ui/Daily.kt`, `ui/PersonalBests.kt`; update HomeMenuTest, create DailyUiTest.
Interfaces: Home provides preset/difficulty start callback and daily navigation; daily screen receives only public state and ViewModel commands; Statistics renders persisted best categories and separate daily scores.
- [x] Update home interaction tests for explicit Start and remembered setup; add daily rendering, first/practice result, confirmation, and large-text tests.
- [x] Observe failures, implement Compose and resources; retain current haptics and board semantics.
- [x] Run focused UI tests; inspect captured phone, landscape, tablet and large-text PNGs.
- [x] Run functional `testDebugUnitTest lintDebug assembleDebug` with the documented benchmark exclusions; read results, fix regressions, update README/validation notes.
- [x] Fresh whole-change review; fix important findings and run affected checks. Leave changes on a feature branch without publishing.

## Execution ledger
- User approved design and explicitly requested implementation; proceed inline without another execution-method approval.
- Work in the clean existing checkout on a new feature branch to retain the configured SDK/build cache and keep changes directly available to the user.
- Pre-flight: tasks 2 and 3 depend on the task 1 data contracts; only public daily projections may reach Compose.
- Task 1: feature/save tests observed RED on missing interfaces, then GREEN.
- Task 2: ViewModel tests observed RED, then GREEN after distinguishing replacement validation from disk failure.
- Task 3: UI tests observed RED on missing screen, then integrated feature/save/battle/UI checks GREEN. Landscape H8 visibility test observed RED, followed by side-by-side layout correction; final captured full grid visually verified.
- Fresh review: no actionable findings. Preserved existing pinch-only battle zoom, home/foreground date refresh policy, and Continue-after-retry behavior; none loses saved progress or changes the approved design.
- Verification scope: the full-suite attempt was deliberately stopped after nearly 13 minutes in the unchanged 800-fleet AI strength benchmark. Final verification excludes `HardAiTest.strongerAcrossIndependentFleets`, `HardStrengthTest`, and `HardMatchTest`, while retaining the other functional/AI correctness tests, all UI/save/protocol tests, lint, and debug assembly. The long AI benchmarks are not claimed complete.
- Follow-up regression: queued duplicate daily Fire taps showed an error (RED). The ViewModel now ignores already-fired targets and shots queued after completion; durable recording remains in the pure daily model.
- Final verification: 141 functional tests passed, zero failures/errors/skips; the duplicate-tap regression is GREEN. Lint and debug assembly passed. No physical-device testing or publication.
