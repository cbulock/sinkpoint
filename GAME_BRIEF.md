# Sinkpoint — first-release brief

## Product

- Android-only hidden-fleet strategy game for short, untimed sessions.
- App name: Sinkpoint. Application ID/namespace: `com.cbulock.sinkpoint`.
- Minimum API 26; target 36; compile 36.1; JVM 17.
- First release: AI, two-device Nearby multiplayer, custom rules, independent autosaves, tutorial, results, statistics, settings, and explicit backup/restore.
- Excluded: online play, pass-and-play, accounts, daily challenges, sharing cards, additional save slots, publishing, and release signing.

## Family appearance

Use the supplied `design-guide` documents and Minescape theme source as references. Preserve Device/Light/Dark selection, wallpaper colors on API 31+, fallback palette, default Material typography, rounded card geometry, safe insets, responsive reading widths, and accessible large text.

The emblem uses abstract connected fleet pieces, a target, and a dot on violet. Adaptive launcher resources include a monochrome layer. Boards use rounded cells and connected pieces; misses use dots, hits crosses, destroyed pieces outlines, and selection a visible outline. State never relies only on color.

## Rules and input

Default: 10×10, piece lengths 5/4/3/3/2, touching allowed, one shot per alternating turn. First player is chosen randomly. A hit does not award another turn. Destroy all opposing pieces to win.

Custom: square size 6–12, 1–10 pieces, lengths 2–6, touching on/off, and classic/salvo. No-touch forbids edge and corner adjacency. Both players use the same settings. Exact placement search runs off the UI thread, checks cancellation, and has a five-second caller deadline; a timeout is reported separately from a proven impossible layout. No silent configuration changes.

Salvo: one target per surviving piece at the beginning of the turn, capped by untried cells. Choose the whole volley and reveal together. Pieces may only be straight and horizontal/vertical.

Placement: drag from the tray or grab any segment of a placed piece to move it. Valid drops save immediately; invalid or canceled drops preserve the fleet. The rotate icon turns the selected piece. Tap-to-place and coordinate entry provide alternatives. Ready locks the complete fleet; Randomize, Remove, and Clear live in the menu.

Play: tap previews a target; Fire commits. Coordinate entry and TalkBack selection use the same action. Pan, pinch, crossing targets, holds, cancellation, or changing the input context cancel a pending tap. Zoom remains available after completion. No hints or undo. Nearby resignation waits for your turn and a completed exchange.

## Screens and progress

Home prioritizes resume cards and one-tap AI difficulty choices. Game options persist for future matches. Nearby uses Host/Join, a display name, pairing-code comparison, rule acceptance, and readiness. Phone gameplay switches between target/own fleet; wide layouts display both with compact side controls. Fire stays accessible on compact phones.

The tutorial includes an interactive B3 selection/firing example. Results show outcome, configuration, shots, hits, accuracy, View board, Play again, and New game. New game returns home without discarding progress; starting a replacement requires confirmation. Nearby rematches require both players.

AI levels: Easy searches randomly, Normal uses the original Hard placement scoring, and Hard models complete possible fleets, looks ahead through shot outcomes, and coordinates whole salvos. Small Classic endgames use bounded exact finishing search; larger fully enumerated endgames compare full-game rollouts with a public-information continuation policy. New Hard matches select defensive layouts tested against probability targeting and checkerboard hunting, with a simple-placement fallback for dense custom fleets. Shooting accepts only public knowledge and persists its generator state. Work is bounded and deterministic; uncertain alternatives retain the baseline. Backgrounding/navigation cancels AI work.

One AI and one nearby save survive process restart. Nearby continuation requires both original devices and their saves. Disconnection/backgrounding pauses rather than forfeits. Conflicting histories cannot continue and are preserved.

Record completed games once, including resignations. Abandoning unfinished games creates no result. Aggregate records separate mode, AI difficulty, and standard/custom rules; retain the latest 100 results. There is no timer or time-based score.

Settings save immediately. Backup includes settings, AI progress, and statistics; excludes Nearby identity and sessions. Validate and preview before replacement. No automatic cloud backup. Initial language is English, with UI copy in resources.

## Acceptance

Run unit rules, AI, serialization, failure/recovery, protocol, and Compose interaction tests; Android lint; debug and instrumentation APK assembly. Render home, placement, gameplay, results, and settings in fallback light/dark, landscape, tablet, and 200% text.

Hardware acceptance remains separate: real touch/zoom cancellation, TalkBack traversal/actions, actual dynamic palettes, disabled animations, document pickers, two-device offline discovery/play, radio interruptions, process restarts, and same-match reconnect. Report gaps explicitly.

