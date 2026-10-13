# New Android game brief

Copy this file into the new game's repository and fill in the brackets. Attach the family design and Android guides, plus selected Minescape source references, when handing the brief to a developer or coding assistant. Relative source links in the guides refer to the Minescape checkout.

## Product

- App name: [name]
- Game: [rules in a few sentences; link any precise rules reference]
- Intended player/session length: [audience; short sessions or longer play]
- Application ID and namespace: [unique identifiers]
- Minimum Android version: [API 26 to match Minescape, or explain another choice]
- First release scope: [required features]
- Later features: [explicitly deferred features]

## Family appearance

Use Kotlin, Jetpack Compose, and Material 3. Follow the Minescape family design guide for typography, semantic colors, rounded surfaces, padding, responsive widths, results, and accessibility. Offer Device/Light/Dark themes with dynamic wallpaper colors on API 31+ and the family fallback palette on older devices.

- Launcher emblem: [game-specific pieces/symbols; rounded, readable at 48 px]
- Playfield: [grid, cards, path, or another layout]
- Meaningful game colors/symbols: [state and how it is distinguishable without color]
- Intentional visual deviations: [none, or specify and explain]

## Screens

| Screen | Game-specific content |
| --- | --- |
| Home | [presets/difficulty details; resume summary; optional game settings] |
| Gameplay | [counters; primary input; secondary controls; small-screen strategy] |
| Learn to play | [short interactive steps and completion behavior] |
| Result | [outcome wording; metrics; primary action; replay/retry behavior] |
| Settings | [Appearance; game-specific Controls; backup policy] |
| Statistics | [records; filters; eligibility; useful empty state] |
| Optional daily challenge | [omit or define date, official attempt, practice and sharing rules] |

## Rules and input contract

- Starting state and legal moves: [define]
- Win/loss/end conditions: [define]
- Tap, hold, drag, multi-touch: [define only the gestures this game needs]
- Cancellation: [what invalidates a pending action]
- Small targets: [zoom, alternate controls, or another safe interaction]
- Accessibility: [labels, states, named actions, and focus behavior]
- Undo/hints/practice: [availability, limitations, and effect on records]
- New game vs Play again vs retry: [what configuration/layout is retained]

## Progress and records

- Automatic save: [what must survive recreation/process restart]
- Timer or score: [definition and pause rules]
- Record eligibility: [normal, assisted, replay, custom, daily]
- Stored history limits: [define]
- Backup/export/import: [scope, validation, preview, replacement confirmation]
- Network/accounts/ads/analytics: [default: none; specify any deliberate additions]
- Localization: [initial languages; keep UI strings in resources]

## Acceptance

Use the Android guide's validation checklist. Include screenshots of home, gameplay, result, and settings in light/dark themes, landscape, tablet width, and 200% text. Record the Android version and wallpaper palette. Demonstrate save/restore, safe gesture cancellation, Back behavior, reduced motion, and TalkBack actions. Report device checks separately from local tests.

## Copyable coding-assistant request

> Build [APP NAME], an Android [GAME TYPE] game, using the attached completed brief and Minescape family design/Android guides. Preserve the family theme policy, default Material 3 typography, rounded component geometry, calm feedback, automatic saving, and responsive accessibility behavior. Adapt Minescape's screen patterns to this game's rules and input. Use a unique application ID, new game-specific state/save schema, and distinct launcher emblem. Implement the first-release scope above, run appropriate rules and UI checks, and report any device checks that remain unverified. Start by reviewing the provided Minescape source references and completed brief.
