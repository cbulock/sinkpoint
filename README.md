# Sinkpoint

An Android-only hidden-fleet game built with Kotlin, Jetpack Compose, and Material 3. The abstract pieces, rounded surfaces, dynamic colors, and screen patterns follow the supplied Minescape family design guide.

## Download

[Get it on Google Play](https://play.google.com/store/apps/details?id=com.cbulock.sinkpoint) · [Website](https://cbulock.github.io/sinkpoint/) · [Privacy](https://cbulock.github.io/sinkpoint/privacy.html)

Requires Android 8.0 or later; nearby multiplayer also needs Google Play services.

<details>
<summary>Other download options</summary>

[Download the latest preview APK](https://github.com/cbulock/sinkpoint/releases/latest/download/sinkpoint.apk) · [Preview release notes](https://github.com/cbulock/sinkpoint/releases/latest) · [SHA-256 checksum](https://github.com/cbulock/sinkpoint/releases/latest/download/sinkpoint.apk.sha256)

Open the APK on your phone and allow installation from your browser or file manager when prompted. Published APKs are debug-signed previews with a stable signing certificate, so later downloads update an existing preview installation.

</details>

## Play

- **Main menu:** Solo opens the difficulty chooser; Multiplayer opens nearby hosting and joining. Both modes stay prominent at the top. Continue playing lists unfinished saves with their mode and setup or shot status; completed boards appear separately in Recent games. Game options, Learn to play, and Statistics remain directly accessible.
- **AI:** Easy searches randomly, Normal uses the original Hard placement-scoring strategy, and Hard models complete possible fleets, coordinates whole salvos, and simulates finishing sequences. Small Classic endgames are solved exactly within a fixed work budget. Hard also tests its own fleet placements against different attack styles before choosing a layout. All shooting strategies use only public shot history and preserve deterministic save/resume behavior.
- **Rules:** standard 10×10 with lengths 5/4/3/3/2, or custom 6–12 square boards, 1–10 pieces of lengths 2–6, touching rules, and classic/salvo turns. Classic enables **Fire again after a hit** by default for both players (including AI); a miss ends the turn. Disable it in Game options to alternate after every shot. Salvo always alternates after each volley. Options apply to new games; existing saves retain their original rules.
- **Placement:** drag pieces from the tray onto the board, or grab any segment to move them. Valid drops save immediately; an icon rotates the selected piece. Tap-to-place is also available. The outlined Random button beside Rotate arranges the whole fleet; tap again for another layout or adjust pieces before Ready. Remove and Clear remain in the menu until Ready.
- **Shots:** tap a cell, then press the prominent crosshair Fire button. In salvo, select one target per surviving piece. No undo after firing. Ship icons below each grid show afloat/sunk status, with damaged segments on your own ships. Portrait tablets give targeting a large upper board, with your fleet and firing controls together below. Landscape tablets show two large boards with a fixed firing area below; short landscape screens retain side controls. Placement boards expand to the available tablet width. Phones keep the opponent's hit/sunk totals and latest volley above the board tabs, with turn status and Fire in the bottom action area. Tap the opponent summary to inspect your fleet, then use Back to targeting to return.
- **Saves:** one AI match and one nearby match, saved independently. Back preserves progress. Replacing unfinished progress requires confirmation.
- **Nearby:** one player hosts and the other joins. Compare the pairing code, accept the rules, and place fleets. Both devices keep their own hidden fleet. A dropped connection pauses play; reconnect using the same two saved matches.
- **Accessibility:** 48 dp ordinary controls, grid semantics, pinch-to-zoom/pan, large-text layouts, and device/light/dark themes. Battle screens omit the zoom toolbar to leave more room for the boards; pinch inward to return to the full board. Canvas coordinates scale with tiles; TalkBack provides cell state and selection actions.

There are no accounts, ads, app analytics, backend services, or online play. Nearby needs Google Play services and Bluetooth/Wi-Fi permissions, but no existing router connection or internet access. Google documents its own Nearby diagnostics in [Data Collection](https://developers.google.com/nearby/connections/overview#data_collection); this is separate from Sinkpoint's data storage.

## Build on Windows

Install Android SDK platform **36.1**, the compatible build tools, and a JDK supporting Java 17. Set `JAVA_HOME` and create an untracked `local.properties` containing `sdk.dir=C:/path/to/Android/Sdk` (forward slashes work on Windows).

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Outputs:

- App: `app/build/outputs/apk/debug/app-debug.apk`
- Device tests: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- Unit/UI reports: `app/build/reports/tests/testDebugUnitTest/index.html`
- Lint report: `app/build/reports/lint-results-debug.html`
- Rendered layout checks: `app/build/outputs/screenshots/`

The local debug APK uses the standard local Android debug certificate.

### Automatic public APK builds

`.github/workflows/android-apk.yml` runs unit/UI tests and Android lint, builds the debug APK, verifies its signature, and publishes `sinkpoint.apk` plus its SHA-256 checksum to GitHub Releases on successful pushes to `main` or manual runs on `main`. Pull requests run the checks and upload temporary build artifacts without access to the publishing key or creating releases.

Publishing requires the repository secret `ANDROID_DEBUG_KEYSTORE_BASE64`, containing the stable Android debug keystore (alias `androiddebugkey`, standard Android debug passwords). Keep the key private and preserve it across builds. The workflow explicitly signs the APK and verifies the certificate against that key before publishing. CI sets an increasing version code using `1000 + GITHUB_RUN_NUMBER`; local builds retain version code 1. The stable `/releases/latest/download/sinkpoint.apk` URL always points to the latest successful published build.

The static website lives in `docs/`; GitHub Pages publishes `main` → `/docs`. Google Play is the primary download source; preview APKs remain available through GitHub Releases. Automated Play Store and AAB publication are not configured. Prepared Play Store descriptions, graphics, phone/tablet screenshots, and upload instructions are in [store-listing/store-listing.md](store-listing/store-listing.md); reproduce the package with `StoreListingCaptureTest` and `scripts/build-store-listing.py`.

### Toolchain compatibility

Gradle 8.13, AGP 8.13.2, Compose BOM 2026.06.01, and Material 3 1.4.0 retain the family project's baseline. **Kotlin is 2.4.21**, because `play-services-nearby:19.5.1` contains Kotlin 2.4 metadata and fails compilation with the initially planned Kotlin 2.2.20. R8/D8 is explicitly pinned to Google's available stable **9.5.23** to support those class files. JVM bytecode targets 17.

References: [Kotlin compatibility matrix](https://kotlinlang.org/docs/gradle-configure-project.html), [Android Kotlin support](https://developer.android.com/build/kotlin-support), [R8 overrides](https://r8.googlesource.com/r8/+/refs/heads/main/README.md#replacing-r8-in-android-gradle-plugin).

## Implementation map

- `game`: serializable immutable match models, exact cancellable placement search, rules, public shot knowledge, and deterministic AI strategies.
- `data`: validated versioned DataStore persistence, result aggregates, capped recent history, and explicit backup format.
- `nearby`: Google Nearby adapter, bounded message framing, and pure move-exchange/reconciliation state machine.
- `GameViewModel`: serialized actions, durable writes before wire effects, AI jobs, discovery/setup, import previews, and UI projections.
- `ui`: native Material screens and a Canvas board with a shared transform for rendering, touch, and accessibility.

`UiState` never exposes the saved AI fleet. Backup previews contain counts only. Network messages never include either private fleet.

### Hard strategy

Hard builds complete candidate fleets consistent with misses, unresolved hits, sunk pieces, and the touching rule. Small candidate spaces are enumerated exactly; larger spaces use 256 sampled fleets, single/pair ship updates, and independent restarts. Placement averages reduce sampling noise. A 60,000-node initialization budget falls back to Normal if a difficult custom position cannot be modeled within the limit.

The planner compares possible hit/miss/sunk outcomes and the best follow-up shot or volley. In search mode it considers the next shot after a miss; while pursuing hits it favors finishing damaged ships. Salvos are evaluated as complete sets, then earlier choices are revisited to replace redundant shots. No result is assumed known before the whole volley is fired. For sampled single-shot decisions, a conservative uncertainty margin keeps Normal's stable choice unless the alternative has enough estimated support.

Inference uses a separate random stream derived from the saved RNG state. Work limits depend on counts, not wall-clock deadlines, so restoring the same history and RNG state produces the same targets. Planning runs on the existing background dispatcher and checks cancellation during search, sampling, and volley evaluation. The strategy receives only `Knowledge`; the actual defending fleet is consulted afterward by the rules engine to resolve the chosen shots.

When at most two ships remain, Hard also attempts bounded enumeration even if independent placement counts are large. Enumeration is capped at 256 fleet arrangements; reaching the cap discards the incomplete enumeration and uses sampling. Exact Classic planning minimizes expected remaining shots under the uniform legal-fleet model for complete beliefs with at most 32 arrangements and 18 relevant cells, capped at 25,000 recursive calls. It branches on actual hit/miss/sunk observations and memoizes shared continuations. Equally good decisions preserve the baseline shot and RNG sequence. Exceeding the search budget discards the incomplete result and keeps the bounded planner.

For larger fully enumerated endgames with at most three ships, rollout planning compares the existing shot or volley with up to four alternatives. Each is played to completion against up to 128 identical sampled possibilities and matched random seeds, using a fast public-information implementation of Normal as the continuation policy. The simulated fleet is consulted only after choosing a shot, to produce its result. A conservative paired margin is required to change the baseline. Approximate fleet samples do not drive these rollouts: exploratory comparisons found their rankings too noisy. Salvo rollouts retain the current quota; actual matches recalculate it after losses.

Hard's defensive setup evaluates 12 layouts with four simulated attacks each, split between probability targeting and checkerboard hunting. It randomly chooses among the top three, favoring both average survival and the weakest trial. Dense custom configurations (more than six ships or over one-third occupied) retain the first valid generated layout to bound setup work. The player's placement is never consulted. Defensive selection is applied only when starting a new Hard match; saved fleets remain intact.

## Recovery protocol

Each shot has a unique action ID and turn number. The attacker saves the shot before sending it. The defender validates it, resolves against its own fleet, and saves the result before replying. The attacker saves the event before ACK; the defender saves the event before CONFIRM. Pending exchanges disable another shot. Duplicate packets return the original result/acknowledgment.

Nearby protocol version 2 carries the repeat-hit rule; both devices need this version to play together. Existing nearby saves keep their rules and can reconnect after both apps are updated.

On reconnect, both peers compare exact shared history prefixes and resend pending exchanges. A peer can advance only from a locally persisted matching result; it cannot overwrite its fleet or invent missing history. Conflicts preserve both saves and stop continuation. Nearby endpoint IDs are transient; the saved player and match IDs determine resumption. Pairing codes must be confirmed on every connection.

Messages are split into 8 KiB frames below Nearby's 32 KiB byte-payload limit, with bounded message size, pending-frame count, and expiry. Version mismatches or malformed payloads stop the session.

Resignation is available on your turn in nearby play, after any move exchange completes. Rematches require both players; the host proposes the retained rules and the opponent accepts them. The starting player alternates for nearby rematches.

## Data and backups

Sinkpoint uses application ID `com.cbulock.sinkpoint`, so Android installs it separately from the former Tideplot app. To transfer preferences, solo progress, and statistics, export a backup from the old app and import it in Sinkpoint. Nearby progress and device identity cannot be transferred through backups; both players need Sinkpoint for nearby play.

App-private `files/sinkpoint-v1.json` stores version 1 data atomically through DataStore. Placement, AI random state, move histories, pending network exchanges, and preferences persist. Finished matches contribute once to aggregates; recent history keeps 100 results.

Export/import uses Android's document picker and covers preferences, AI progress, and statistics. Nearby progress and device identity are excluded and remain untouched by imports. Import validates before presenting a replacement confirmation. An invalid import or failed write preserves the existing state. Android automatic cloud backup is disabled.

An unreadable save is deliberately not replaced. Recover file access or preserve the original file before considering a reset. A storage error pauses changes and allows retry. If it interrupts nearby play, reconnect after retry so the protocol can reconcile the saved exchange.

## Verification

See [VALIDATION.md](VALIDATION.md) for checks actually run and device limitations. Use a dedicated emulator/device install for instrumentation:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Real Nearby acceptance requires **two physical Android devices**. Emulator or fake-transport success does not establish radio behavior.

