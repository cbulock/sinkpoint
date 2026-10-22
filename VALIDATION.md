# Sinkpoint validation

Latest verification: October 18, 2026. This file distinguishes local automated checks from physical-device acceptance; dated sections below retain earlier results.

### Tablet space usage — October 18, 2026

Tall tablets now prioritize a larger target board above a row containing the own fleet and firing controls. Wide tablets combine opponent progress and firing controls in one footer row. Short landscape windows retain side controls, and portrait placement expands beyond the former 540 dp cap.

Passed 24 targeted tests across `GameplayLayoutTest`, `LayoutTest`, `FoldPlacementTest`, and `TurnHapticsTest`, plus `lintDebug` and `assembleDebug`. Coverage includes tablet board dimensions and target selection, portrait and landscape, smaller tablets, phones, folds, and 200% text. Reviewed native renders `tablet-portrait-adaptive.png`, `tablet-landscape-adaptive.png`, and `tablet-light-placement.png` in `app/build/outputs/screenshots/`. Refreshed the debug APK checksum. Physical tablet testing was not performed.

### Sinkpoint rename — October 13, 2026

Renamed the UI and launcher label, application ID/namespace, production and test packages, themes, icon resource, Nearby service ID, save/export filenames, project name, and benchmark environment variables to Sinkpoint.

Passed **46 tests** across `HomeMenuTest`, `LayoutTest`, `GameplayLayoutTest`, `UiTest`, `SaveTest`, `ProtocolTest`, and `SessionIntegrationTest`. `lintDebug` passed with **0 errors and 29 warnings**. `assembleDebug` and `assembleDebugAndroidTest` succeeded. Inspection of the packaged APKs confirmed label `Sinkpoint`, application ID `com.cbulock.sinkpoint`, launcher activity `com.cbulock.sinkpoint.MainActivity`, and instrumentation application ID `com.cbulock.sinkpoint.test`. The app APK checksum was refreshed. Device installation and physical Nearby behavior were not tested.

### Main menu refresh — October 13, 2026

Solo and Multiplayer now have equally prominent mode cards, with side-by-side cards on wide screens. Solo opens a dismissible difficulty chooser. Unfinished games appear under Continue playing with mode, solo difficulty, and fleet-setup or shot status; completed boards appear separately under Recent games. Game options includes the current rules summary. The menu uses the existing dynamic/fallback theme colors, small mode glyphs, and a compact app-art header.

`HomeMenuTest`, `UiTest`, and all eight `LayoutTest` configurations passed **30 tests**. Coverage includes explicit difficulty selection and cancellation, unfinished-game replacement cancellation, independent solo/nearby resume navigation, multiplayer lobby access without replacing saves, finished-board separation, blocked-storage behavior, and reachable controls at 200% text. `lintDebug` passed with **0 errors and 29 warnings**; app and instrumentation debug APK assembly succeeded. Native-rendered phone, tablet, landscape, light/dark, saved-game, completed-game, and large-text previews were generated. Physical-device, TalkBack, dynamic wallpaper colors, and actual nearby-radio behavior were not tested in this update.

The final equal-height adjustment for wide mode cards passed all eight layout configurations again, along with lint and debug app assembly. The APK checksum was refreshed.

### Repeat firing after hits — October 13, 2026

Classic game options now include **Fire again after a hit**, enabled by default for new games. Both players retain the turn after hits and sinking shots; misses hand it over. Disabling the option preserves alternating shots, and Salvo continues alternating volleys. The AI schedules consecutive persisted shots, and nearby play waits for durable confirmation before another shot. Rule summaries disclose the option. Legacy saved matches and imported progress retain their original alternating rules; missing preference fields adopt the new default. Nearby protocol version 2 requires both devices to update.

Verified all **76 tests across two runs**: the full suite passed 68 non-layout tests; its eight layout failures came from a shared fixture assuming alternating hit turns. After making that fixture explicitly alternate, all eight layout checks passed. Coverage includes player and AI hit streaks, misses, sinking/winning, disabled Classic and Salvo rules, target rejection, option persistence/reset, legacy disk/backup migration, nearby confirmations/restarts, and existing AI benchmarks. `lintDebug` passed with 0 errors and 30 warnings, and `assembleDebug` succeeded; the APK checksum was refreshed. Physical-device and actual nearby-radio testing were not performed.

## Build results

`gradlew.bat testDebugUnitTest lintDebug assembleDebug` completed successfully after the drag-and-drop placement update. Instrumentation APK assembly passed in the earlier baseline run.

- **39 tests passed**, no failures: 9 rules/AI, 5 protocol, 4 save, 12 interaction, 8 layout, and 1 complete controller integration test.
- **Android lint: 0 errors**, with non-blocking warnings for dependency updates, resource/localization suggestions, SDK attributes, and Compose conventions.
- Debug app and instrumentation APKs built successfully. Instrumentation was compiled, not executed on hardware. The app APK includes a sibling `.sha256` checksum file.
- **40 rendered images**: home, placement, gameplay, settings, and results across phone, landscape, tablet, and 200% text, each in light and dark.
- At the time of this earlier run, the merged manifest confirmed application ID `com.cbulock.tideplot`, min SDK 26, target SDK 36, disabled automatic backup, explicit cloud/device-transfer exclusions, and no INTERNET permission.

## Automated coverage

- Rules: overlap, bounds, adjacency, placement across board sizes and turn modes, impossible fleets, cancellation, repeated targets, salvo fleet reduction, victory, and malformed results.
- AI: identical choices after restored RNG state, custom salvo uniqueness, and relative strength over 80 fixed standard fleets. The initial passing run used 7,640 / 4,104 / 3,301 total shots for Easy / Normal / Hard (averages 95.5 / 51.3 / 41.3).
- Saves: JSON round trips, private/public state and RNG restoration, independent nearby slot during import, corrupt/unknown backup rejection, exactly-once results, and a failed repository write followed by retry.
- Protocol: a paired deterministic message queue exercises loss at each shot/result/ACK/CONFIRM stage, serialization/restart of both peers, duplicate delivery, wrong player/version/match, conflicting histories, pending-move input locks, and out-of-order bounded frame reassembly.
- Full controller integration: host/join negotiation, explicit rule acceptance, placement/readiness, a confirmed move, pause/reconnect, resignation, and mutual rematch. This test found and verified the fix for a stale guest synchronization flag on rematch. Late packets after returning home cannot reopen gameplay.
- Compose: accessible coordinate selection, custom rule persistence, replacement cancellation, reachable controls, and screenshot rendering of home/game/placement/settings/results on phone, landscape, tablet, and 200% text in light/dark.
- Gameplay interaction: selecting a winning target does not fire until confirmation; finished-game restoration does not reopen results or duplicate records. Long holds and cross-cell drags do not select targets. Placement supports tray drops, dragging from a tail segment, immediate rotation, invalid-drop preservation, cancellation, and drops outside the grid. Compact-phone placement keeps the grid and Rotate/Ready available; landscape puts the tray beside the grid; firing remains visible separately from scrolling content.

Rendered screenshots use Robolectric's native graphics on API 30 with the fixed fallback palette. They do not prove actual wallpaper colors or touch/radio performance.

### Gameplay feedback update

Hits now use animated flame silhouettes; misses use an X, with a matching board legend and tutorial copy. The latest incoming volley has a persistent double outline, accessible state descriptions, and a coordinate/result summary that opens the own-fleet board on compact layouts. Victory results show a finite trophy/confetti animation that follows the system animation scale.

Validated with `GameplayFeedbackTest`, `UiTest`, and all eight `LayoutTest` configurations, plus `lintDebug` and `assembleDebug`. The new tests cover Salvo highlights, preservation after a local reply, replacement by the next incoming volley, compact board switching, and victory-only celebration. Native-rendered screenshots include `own-latest-volley.png` and `victory-confetti.png`. Physical-device animation and TalkBack behavior remain unverified.

Selection coordinates now occupy a persistent one-line summary beside Fire on phones and in the action column on wide layouts. Selecting, adding, and clearing targets preserve both grid and Fire-button bounds, verified on phone and landscape. The turn banner uses a solid primary color while input is available, with a matching highlighted Fire area and a polite accessibility announcement. Waiting, synchronization, and paused states use the inactive treatment. Regression coverage checks the turn labels and input availability through these transitions; screenshots include `your-turn-banner.png`.

### Fold inner-screen placement

Side-by-side placement reserves a 240 dp tray plus padding and gap before sizing the square board. This prevents near-square inner screens from squeezing the title into a vertical column. Narrow screens and large text retain the stacked, scrollable layout; Rotate and Ready remain in the bottom action bar.

`FoldPlacementTest` passed five native-rendered configurations: 673 × 700, 600 × 650, rotated 700 × 600, taller 673 × 840, and 673 × 700 at 200% text. Assertions check readable heading bounds, all five tray pieces, non-overlapping board/tray, and visible Rotate/Ready controls. The full board is checked at standard text size. All eight existing layout checks and 12 UI interaction tests also passed, along with `lintDebug` and `assembleDebug`. Reviewed `fold-inner-placement.png` and `fold-large-text-placement.png`; these checks do not establish physical fold-device behavior.

### Visible random placement

Random placement is now an outlined, labeled shuffle button beside Rotate in the persistent placement action bar. Ready remains the filled primary action; controls wrap for large text. Random clears stale drag/error feedback and uses the existing guarded, persisted fleet generator. Remove and Clear stay in the overflow menu.

Validated with all 13 `UiTest` interactions, five `FoldPlacementTest` configurations, and eight `LayoutTest` configurations, plus `lintDebug` and `assembleDebug`. The new interaction check verifies a partial fleet becomes a valid complete fleet without marking it Ready, then clears and generates again. Layout checks assert Random remains displayed and enabled. Reviewed native screenshots for phone dark, large-text light, and fold inner placement. Physical-device touch and TalkBack behavior remain unverified.

### Gameplay layout and fleet status

Tall wide screens use two larger boards with turn, latest-move, and firing controls below them. Short landscape screens retain a fixed-width action column. Board sizing reserves room for Fire and the status/zoom controls. Each grid has a compact scrollable ship-status row: ship length is represented by segments, own damage by crossed hit segments, and sunk ships by a muted strike-through. Opponent icons only show afloat or sunk; unresolved opponent hits are not assigned to hidden ships. The aggregate “left” text is removed.

Validated with three new `GameplayLayoutTest` checks, all five `GameplayFeedbackTest` checks, eight `LayoutTest` configurations, and 13 `UiTest` interactions, plus `lintDebug` and `assembleDebug`. Tests cover near-square board sizing, visible per-ship status, duplicate-length ship identity, own damaged-to-sunk updates, opponent information boundaries, stable selection/Fire bounds, both zoom buttons, and repeated AI turns. Native screenshots include `fold-inner-game-status.png` and refreshed layout game captures. Physical-device layout, gestures, and TalkBack remain unverified.

### Battle screen refresh

The battle screen now uses a quieter turn indicator, flatter board controls and fleet chips, a filled target selection, and a large crosshair Fire action. Tall wide layouts keep both grids and a fixed bottom firing area; short landscape retains side actions. Phones keep an opponent summary outside board scrolling, showing incoming hit and sunk-ship totals plus the latest volley. That summary opens the own-fleet tab, with a persistent return-to-targeting action. Phone turn status lives alongside Fire to preserve board space. Large text stacks the firing controls and keeps the board area scrollable.

Validated October 12, 2026 with `testDebugUnitTest` filtered to `GameplayLayoutTest`, `GameplayFeedbackTest`, `LayoutTest`, and `UiTest`: **31 tests passed**. `lintDebug` completed with **0 errors and 30 warnings**, and `assembleDebug` succeeded. Coverage includes opponent summary updates after damage/sinking, the pre-first-shot state, persistent summary bounds during scrolling, board switching, selection/Fire stability, waiting/paused states, gesture cancellation, and repeated actual taps across AI turns. Native-rendered phone, fold, landscape, tablet, light/dark, and 200% text layouts were generated; phone, fold, landscape, and large-text captures were visually reviewed. The APK checksum was refreshed. This is local Robolectric/build validation; physical-device and dynamic wallpaper palette acceptance remain unverified.

### Zoom toolbar follow-up

Removed the visible Fit/zoom controls from both battle boards, while preserving pinch zoom and pan. Board sizing reclaims the toolbar height, including compact phone and short landscape layouts. `GameplayLayoutTest`, `GameplayFeedbackTest`, and all eight `LayoutTest` configurations passed (**18 tests**), and `assembleDebug` succeeded. Layout checks assert the toolbar is absent and Fire remains visible; opponent-summary scrolling coverage now scrolls to Resign. Reviewed refreshed phone and fold screenshots and updated the APK checksum. No physical-device run was performed.

### Hard AI tuning — October 13, 2026

Hard now excludes placements inconsistent with no-touch hits or the last remaining ship, prioritizes provable hits, and evaluates compatible complete arrangements when two or three ships remain. The joint search is capped at 25,000 candidate combinations, honors coroutine cancellation, and reuses scores within a salvo. Easy and Normal are unchanged; the AI still accepts public shot knowledge only.

Validated with 18 passing tests across `HardAiTest`, `RulesTest`, `SaveTest`, and `SessionIntegrationTest`, plus `lintDebug` and `assembleDebug`. New coverage checks guaranteed hits, no-touch deductions, complete classic/salvo games on 6/8/10/12 boards, unique shots, and resumed RNG choices.

The paired comparison against the frozen previous Hard strategy uses 400 fleets per touching rule (seeds 1000–1399). Total shots changed from 17,696 to 17,685 with touching allowed and from 15,578 to 15,572 without touching. The original 80-fleet sample changed from 3,301 to 3,374; Easy/Normal stayed at 7,640/4,104. The measured gain is very small and sample-dependent, not evidence of a large difficulty or human win-rate change. No physical-device gameplay run was performed.

### Original Hard promoted to Normal — October 13, 2026

Normal now uses the exact original Hard placement-scoring strategy; Easy and the updated Hard retain their behavior. The middle level remains named Normal. Its persisted `NORMAL` identifier and statistics keys are unchanged.

The strategy parity test compares every shot and RNG state against the frozen original Hard across complete classic/salvo games, touching on/off, and 6/8/10/12 boards. AI/rules/save tests passed (19 tests), all eight layout checks passed, and all 14 UI tests passed after correcting the new asynchronous setup test's wait. The final Normal-label follow-up passed the home-action/statistics test and all eight layout checks again; `assembleDebug` succeeded. Lint passed with 0 errors and 30 warnings before the final label-only follow-up. The phone home rendering was inspected during the label update. The APK checksum was refreshed. No physical-device run was performed.

### Whole-fleet planning, look-ahead, and coordinated salvos — October 13, 2026

Hard now models complete legal fleets throughout the game, compares possible observations and follow-up moves, and revisits earlier salvo targets after evaluating the whole volley. It uses exact enumeration for small spaces and bounded sampling otherwise. A conservative uncertainty margin retains Normal's choice when a sampled single-shot alternative lacks enough estimated support. Normal still exactly matches the original Hard, including its RNG sequence. The planner receives public knowledge only and uses a separate deterministic inference stream.

The full `testDebugUnitTest lintDebug assembleDebug` run passed: **70 tests, 0 failures; lint 0 errors and 30 warnings**. Coverage includes complete-fleet consistency, no-touch spacing, identical-length ships, high cells on 12×12 boards, informative joint salvos, a position where look-ahead breaks an immediate-hit tie, budget fallback, cancellation, deterministic replay, custom complete games, and Normal parity. The APK and sibling checksum were rebuilt.

The final paired strength benchmark used **2,000 fresh fleets**, 500 per configuration, with base seed 190000 and a 10000 offset per configuration. Both strategies attacked the same fleets. These results supersede the smaller exploratory samples:

| Configuration | Normal total shots | Hard total shots | Hard uses fewer shots |
| --- | ---: | ---: | ---: |
| Classic, touching allowed | 22,241 | 22,457 | −0.97% |
| Classic, no touching | 19,393 | 19,457 | −0.33% |
| Salvo, touching allowed | 28,425 | 28,515 | −0.32% |
| Salvo, no touching | 27,655 | 24,860 | 10.11% |

Negative percentages mean Hard used more shots. The aggregate improvement was 2.48%, driven by no-touch Salvo. The approximate paired 95% intervals for shots saved per game were −0.432 ± 0.569, −0.128 ± 0.351, −0.180 ± 1.303, and 5.590 ± 0.910 respectively. Thus the run supports a substantial improvement in no-touch Salvo, but does not establish a general Classic or touching-Salvo advantage. The proposed 10% improvement across configurations was **not achieved**. The older 800-fleet Classic regression sample also passed, at 17,579 / 15,527 Hard shots versus 17,696 / 15,578 Normal shots, illustrating the small, sample-dependent Classic difference.

Salvo strength was measured with five shots per volley throughout the attack, assuming all five attacking ships remain afloat; this is not a head-to-head match or human win-rate measurement. Desktop planning p95 was approximately 20–21 ms for Classic and 68–72 ms for Salvo; maximum observed time was 314 ms. Separate maximum-custom-board checks (12×12, ten length-six ships, ten-shot volleys) took 492 ms on an empty history and 132 ms after 48 shots. These are local JVM measurements, not Android device performance. No physical-device run was performed.

To reproduce the larger benchmark, set `SINKPOINT_AI_BENCHMARK_GAMES=500` and `SINKPOINT_AI_BENCHMARK_SEED=190000` before running `testDebugUnitTest --tests '*HardStrengthTest'`. Without these overrides, the strength test uses 100 fleets per configuration.

### Rollout shooting, exact endgames, and defensive placement — October 13, 2026

Hard now compares full-game continuations in fully enumerated endgames, solves small single-shot endgames exactly under its legal-fleet model, and selects defensive layouts tested against probability targeting and checkerboard hunting. All rollout choices use simulated public history; only the result resolver sees the assumed fleet. Normal is unchanged. Existing saved fleets are preserved; defensive selection applies to new Hard games.

Exploratory rollouts from approximate fleet samples were unreliable, so the final implementation limits rollouts to complete enumerations. Enumeration stops at 256 possibilities and discards incomplete results. Exact search is limited to 32 fleet arrangements, 18 relevant cells, and 25,000 calls, preserving the baseline on equal expected cost. These limits also prevent a large enumerated belief from overloading the existing look-ahead planner. Larger or uncertain positions retain bounded planning.

The existing 800-fleet Classic regression passed on the final implementation: Normal used 17,696 shots versus Hard's 17,647 with touching enabled, and 15,578 versus 15,559 with touching disabled. These are small gains over Normal; the preceding Hard implementation used 17,579 and 15,527 on the same fixtures, so the new shooting logic does not establish an across-the-board improvement over previous Hard.

Complete-match comparisons against Normal used 12 fleet seeds (490000–490011) and both starting players, **24 matches per version per configuration**. The reference is the frozen pre-rollout Hard with ordinary random defense; the new version includes both shooting changes and selected defense. Salvo quotas shrink after losses, and Classic follows the configured hit-repeat rule.

| Rules | Touching | Previous Hard wins | New Hard wins |
| --- | --- | ---: | ---: |
| Classic, fire again on hit | Yes | 17/24 | 18/24 |
| Classic, alternating | Yes | 17/24 | 18/24 |
| Classic, fire again on hit | No | 12/24 | 20/24 |
| Classic, alternating | No | 12/24 | 20/24 |
| Salvo | Yes | 14/24 | 15/24 |
| Salvo | No | 18/24 | 18/24 |

These are small paired automated samples with reused fleet seeds and correlated rule variants, not independent human win-rate estimates. Defense is the clearest measured benefit: an expanded separate placement comparison used 160 held-out attacks (probability targeting and checkerboard hunting, with different seeds from layout selection). Ordinary layouts required 7,621 total shots to sink; selected layouts required 8,002, **5.0% more**. That placement code was unchanged by the later shooting tie and enumeration-limit fixes.

The final fresh-seed comparison used 40 fleets per touching rule (780000–780039), mixing ordinary random, edge-biased, and compact placements. Previous Hard / expanded enumeration plus exact search / expanded enumeration plus rollouts / all changes used **1,863 / 1,811 / 1,811 / 1,811** shots with touching and **1,572 / 1,586 / 1,587 / 1,587** without touching. This small sample does not isolate a consistent benefit from rollouts or exact search beyond the common enumeration change. A fresh 80-attack defensive comparison required **3,918 / 3,983** shots for ordinary/selected layouts, a **1.7%** increase. Shooting results remain mixed; defensive gains also vary by sample.

The final standard-fleet comparison against Normal used 100 fleets per configuration: Normal/Hard totals were **4,472/4,463** for touching Classic, **3,906/3,873** for no-touch Classic, **5,730/5,650** for touching Salvo, and **5,650/4,940** for no-touch Salvo. These attack-only Salvo measurements retain the fixed initial quota. Desktop planning p95 was 23–24 ms for Classic and 72 ms for Salvo; the largest observed standard-board time was 692 ms. Maximum-custom-board checks took 969 ms with an empty history and 166 ms after 48 shots. Device timing remains unverified.

All **82 tests passed across the final verification runs**, including simulation parity with Normal and public rules, an independent exhaustive endgame oracle, deterministic/diverse legal defense, cancellation, saved-state replay, complete matches, and existing UI/layout/rules/save/protocol coverage. The original combined invocation lost compiled test files while sharing Gradle task history with another build; the affected suites were rebuilt with both output and project cache isolated. One UI test then timed out during a long host pause; all 17 UI tests passed on an unchanged rerun. Successful suite XMLs are retained under `.rollout/build/passed-earlier/`.

`lintDebug` and `assembleDebug` completed successfully after those reruns. The new APK and SHA-256 file are under `.rollout/build/app/outputs/apk/debug/`. Build commands used `--project-cache-dir .rollout/build/project-cache -I .gradle/rollout.init.gradle` to avoid collisions with the standard build. No physical-device gameplay or timing run was performed.

## Hardware limitation

ADB reported no connected physical devices. An isolated QA emulator was created using the installed Android 36.1 Play Store image, without modifying the existing `small_phone` emulator. Boot failed because the host does not expose virtualization extensions required for x86_64 acceleration. Therefore instrumentation execution and actual emulator screenshots were unavailable.

## Required physical-device acceptance

Use two dedicated Android devices with Google Play services and the same APK:

1. Deny/regrant nearby permissions; test disabled radios and older Android location-service requirements. AI must remain available.
2. Disconnect from routers and turn off mobile data, leaving Bluetooth and Wi-Fi radios enabled. Host, discover, compare codes, and accept rules.
3. Complete classic and custom salvo games. Verify no hidden fleet appears on the other device.
4. Interrupt each shot exchange with radio loss; reconnect. Check identical turns and histories, no duplicated shots, and no premature additional turn.
5. Background, force-stop, and reopen either device. Resume using the original saves. Test an unrelated device and a missing peer save.
6. Verify both-player rematch approval, resignation, completed-game inspection, and independent AI progress.
7. Verify real taps, drags, long holds, pinch cancellation, TalkBack actions, large text, landscape, wallpaper palettes, system animation disabling, and export/import document pickers.

No release signing, store upload, or distribution was performed.

