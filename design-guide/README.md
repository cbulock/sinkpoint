# Building games with the Minescape look and feel

Use this pack when starting another Android game that should feel like a sibling of Minescape. It captures the current app implementation, with recommendations for carrying it into new projects. It does not create a shared library or change Minescape's behavior.

1. Read the [design guide](design-guide.md) for colors, typography, components, motion, and accessibility.
2. Follow the [Android implementation guide](android-guide.md) to reuse the relevant code and structure.
3. Copy the [new-game brief](new-game-brief.md), fill in the game-specific decisions, and give it to your developer or coding assistant with this pack.

## What makes the games a family

Keep the rounded Material 3 surfaces, generous spacing, clear headings, compact gameplay controls, immediate access to play, automatic saving, and friendly results. Use the same light/dark/device theme policy, with wallpaper colors on Android 12 and later.

Give each game its own name, launcher emblem, rules, playfield, tutorial, and meaningful statistics. A Sudoku sibling could use the same home difficulty list and result card while using pencil marks and a number pad in place of flags and zoom controls. A solitaire sibling could preserve the same surfaces and spacing around its cards, with game-specific gestures and scoring.

## Sources and visual references

The Kotlin implementation is the authority for current Android behavior:

| Reference | Purpose |
| --- | --- |
| [Theme.kt](../../../app/src/main/java/com/camer/minesweeper/ui/Theme.kt) | Fallback colors and dynamic color selection |
| [HomeScreen.kt](../../../app/src/main/java/com/camer/minesweeper/ui/HomeScreen.kt) | Grouped difficulty list, resume card, options |
| [SettingsScreen.kt](../../../app/src/main/java/com/camer/minesweeper/ui/SettingsScreen.kt) | Appearance, controls, backup, large-text behavior |
| [ResultDetails.kt](../../../app/src/main/java/com/camer/minesweeper/ui/ResultDetails.kt) | Result hierarchy and responsive dialog |
| [Board.kt](../../../app/src/main/java/com/camer/minesweeper/ui/Board.kt) | Tile drawing, gestures, accessibility |
| [launcher-icon.md](../launcher-icon.md) | Existing icon artwork and generation prompt |
| [Website home image](../../assets/home.png) | Older visual reference for palette and softness |
| [Website dark board image](../../assets/game-dark.png) | Dark gameplay visual reference |
| [Website share image](../../assets/daily-share.png) | Share-card visual reference |

The website images are marketing references, not a specification of the latest screen layouts. In particular, the current home screen groups presets and puts Practice/No-guess in Game options. Website CSS also uses its own palette and typography; use `Theme.kt` for Android colors.

Before implementing a sibling, capture the current app in light and dark themes, on a phone and at 200% text. Include home, active gameplay, results, and settings. Record the Android version and wallpaper palette so color comparisons are reproducible.
