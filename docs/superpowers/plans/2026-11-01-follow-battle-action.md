# Follow the Action Implementation Plan

> Execute inline with the executing-plans, test-driven-development, and verification-before-completion skills.

**Goal:** Follow turn ownership on phones while showing every live shot result for 1,000 ms and giving CPU turns at least 900 ms before firing.

**Architecture:** A coroutine-backed presentation controller consumes authoritative match history and publishes a displayed prefix, stage, suggested board, and focus revision. GameViewModel owns its lifecycle, guards firing, and paces AI without blocking persistence or nearby exchanges. Compose uses the displayed match and retains manual browsing until the next focus revision.

**Tech Stack:** Kotlin, StateFlow, coroutines, Jetpack Compose, JUnit/Robolectric.

**Spec:** User-approved “Follow the action during battle” plan in this chat.

## Constraints

- Results: 1,000 ms; CPU thinking: minimum 900 ms, with computation overlapping the delay.
- Classic hit streaks retain the board; salvo targets appear together.
- Network/save formats and deterministic AI choices stay unchanged.
- Navigation, backgrounding, disconnects, and storage failures cancel presentation; resume skips historical replay.
- Existing uncommitted board reveal and feedback edits must be preserved.

## Tasks

- [x] 1. Add failing phone follow-turn tests; run them and verify the baseline.
- [x] 2. Add BattlePresentation controller/state with ordered live result playback, cancellation, deduplication, and timing tests using virtual time.
- [x] 3. Integrate authoritative/presented state in GameViewModel, guard fire, overlap CPU computation/thinking, and defer shot-driven results until playback completes. Test actual saves, AI pacing, and nearby confirmation/recovery.
- [x] 4. Follow focus revisions in GameScreen, keep tabs usable, highlight both sides, and gate status/haptics/actions while showing results. Extend phone/tablet/large-text UI coverage.
- [x] 5. Run unit/UI tests, lint, debug and device-test builds; inspect rendered layouts and available device tooling; review final diff. Validation limitations are recorded below.

## Review Focus

Fast nearby streaks must not collapse; duplicate packets must not restart holds; repeated fire calls must not bypass presentation; finished fleets must not leak before their displayed result; resume must never reapply a shot.

## Execution Ledger

- Ruling: Work in the supplied checkout to preserve the existing uncommitted changes in overlapping files; do not move or commit the user's edits. The user explicitly requested implementation of the approved plan, so no additional design-approval gate is needed.
- Baseline: eight existing GameplayFeedbackTest tests passed; the new opponent-first phone test failed before implementation.
- Controller and ViewModel timing, firing, final-result, retry, and cancellation tests passed with controlled clocks. Phone/manual browsing, large-text, and nearby rapid-move/reconnect tests passed.
- Independent review: no actionable defects; added coverage for interrupting CPU thinking and a CPU-hit hold before resuming.
- Device validation limitation: no physical device connected. The existing small_phone emulator cannot boot because this host lacks hardware virtualization support. Phone/tablet rendered layouts inspected instead.
- Final verification: 121 functional unit/UI tests passed (19 suites, zero failures/errors/skips), including both added CPU interruption tests. Android lint passed with zero errors and 30 warnings; assembleDebug and assembleDebugAndroidTest succeeded. git diff --check passed.
- Full-suite limitation: stopped the unrestricted run during the unchanged 800-fleet AI strength regression after approximately nine minutes. The final run excluded only HardAiTest.strongerAcrossIndependentFleets, HardStrengthTest.benchmarkClassicAndJointSalvos, and NextHardTest.pairedAttackAndDefenseBenchmark using an ignored build-directory Gradle init script; all remaining tests ran.
- Store-listing capture tests regenerated tracked promotional screenshots. Restored those generated files to their initially clean versions, preserving the existing source changes. No deployment performed during validation.
- Endgame follow-up: moved View board to the fixed results-dialog footer beside Main menu. All 12 targeted layout/result tests, lint, and the rebuilt debug APK passed; the action remains visible while scrolling results on phones, tablets, and large-text layouts.
