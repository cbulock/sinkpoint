# Sinkpoint — Google Play listing

English (United States) store package, prepared October 15, 2026.

## Listing text

Paste the contents of these files into the matching Play Console fields:

| Field | File |
| --- | --- |
| App name | [title.txt](title.txt) |
| Short description | [short-description.txt](short-description.txt) |
| Full description | [full-description.txt](full-description.txt) |

Suggested category: **Game → Board**. Package: `com.cbulock.sinkpoint`.

Website: https://cbulock.github.io/sinkpoint/

Privacy policy: https://cbulock.github.io/sinkpoint/privacy.html

Add your developer support email in Play Console. Content rating, target audience, Data safety, pricing, and release-track settings require their own Console entries. In particular, account for the Google Nearby SDK when completing Data safety; the listing does not claim that every included SDK collects no data.

## Graphics

| Asset | Size | Upload location |
| --- | --- | --- |
| `app-icon-512.png` | 512 × 512 | App icon |
| `featured-graphic-1024x500.png` | 1024 × 500 | Feature graphic |
| `phone-screenshots/` | 1080 × 1920 | Phone, eight portrait images |
| `phone-landscape-screenshots/` | 1920 × 1080 | Phone alternatives, four landscape images |
| `tablet-screenshots/portrait/` | 1440 × 2560 | Tablet portrait, four images |
| `tablet-screenshots/landscape/` | 2560 × 1440 | Tablet landscape, four images |

Play accepts up to eight screenshots per device type. Use the eight portrait images for a complete phone set, or replace some with landscape alternatives. For each 7-inch or 10-inch tablet gallery, choose four portrait and four landscape images, or a smaller selection of at least four. These represent responsive tablet layouts, not captures from specific tablet hardware. Do not upload all twelve phone images into one gallery.

The icon reuses the existing app artwork. The feature graphic is original code-drawn artwork using Sinkpoint's violet, mint, and coral palette and abstract fleet shapes. Screenshots contain only app UI, with no promotional overlays, device frames, stretching, or cropping. The four core screens appear in every orientation; the phone portrait set also includes the home screen, nearby lobby, guide, and settings.

## Alt text

Use the corresponding description for each orientation; each is below 140 characters.

| File | Alt text |
| --- | --- |
| App icon | Mint fleet piece over a violet grid with a coral shot marker. |
| Feature graphic | Sinkpoint with an abstract fleet grid, mint pieces, coral hits, and the words Plan your next shot. |
| `01-classic-gameplay.png` | Classic match with shot history, fleet status, and the Fire control. |
| `02-fleet-placement.png` | Arrange your fleet on the grid using Rotate, Random, and Ready. |
| `03-dark-theme.png` | A match in dark theme with violet controls and visible hit and miss markers. |
| `04-game-options.png` | Game options for board size, fleet lengths, touching rules, and Classic or Salvo turns. |
| `05-main-menu.png` | Sinkpoint home screen with Solo, Multiplayer, Game options, Learn to play, and Statistics. |
| `06-nearby-play.png` | Nearby multiplayer setup with a display name and options to host or join. |
| `07-learn-to-play.png` | Built-in guide explaining fleet placement and firing. |
| `08-settings-and-backup.png` | Settings for themes, haptics, and exporting or importing a backup. |

## Reproduce and validate

From the repository root, with the Android SDK and JDK configured:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest --tests com.cbulock.sinkpoint.StoreListingCaptureTest
python scripts/build-store-listing.py
```

The Python step requires Pillow. It exports graphics, validates the native screenshot sizes and listing character limits, flattens screenshots to RGB PNG without resampling, writes `validation.json`, and creates `../store-listing-package.zip`.

Captures use the current native Compose UI through Robolectric SDK 30 with fixed sample match data. They are local renders, not physical-device or Nearby radio validation. The light/dark palette is the app's fallback palette; Android 12 and later can use wallpaper colors. Only store text and assets are prepared here; nothing is uploaded or published.

Requirements checked against Google's [preview asset guidelines](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en): 512-square PNG icon under 1 MB, opaque 1024 × 500 feature graphic, opaque screenshots with 9:16 or 16:9 proportions, and short description within 80 characters. Validation also checks the 30-character title and 4,000-character full description limits.
