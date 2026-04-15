# HyperIcons

One-screen Android app that turns an icon pack (Arcticons or any CandyBar-based
pack) into a HyperOS/MIUI `.mtz` theme and hands it straight to the system theme
manager.

## Why

Applying an icon pack on HyperOS today means two extra apps: **Peafowl** to
build the theme, **MTZ Tester** to apply it. HyperIcons collapses that into a
single step — pick icons, tap **Apply**, done.

## How it works

1. Discovers installed icon packs and parses their `appfilter.xml`.
2. Lists launchable apps and share-sheet targets, auto-matches by component.
3. Unmatched apps get a **Pick** button that opens the icon pack's own picker —
   search/browse, tap the icon you want.
4. Choices persist in a local Room DB, so on the next run only *new* apps need
   attention.
5. **Apply** builds an icons-only `.mtz` (activity-qualified PNGs plus a
   per-package fallback so Quick Share / Print / Xiaomi Share resolve) and fires
   the theme manager's `ApplyThemeForScreenshot` entry point, with a
   Downloads-visible copy as the fallback path.
6. **Wallpaper** FAB deep-links to HyperOS's super wallpaper list
   (`theme://zhuti.xiaomi.com/superwallpaperlist`) so you can pick Saturn /
   Faraway Rings / etc. right after applying.

## Build

JDK 17+ and the Android SDK with platform 34.

```bash
# one-time: generate the gradle wrapper (needs a system gradle 8.x)
gradle wrapper --gradle-version 8.9

# build + install debug
./gradlew installDebug
```

Or open the project in Android Studio and hit Run.

## Install + run

1. Install an icon pack on the device (Arcticons from F-Droid or Play).
2. `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. Launch **HyperIcons**, tap **Pick** for any app marked *unmatched*, then tap
   **Apply**.

## Known rough edges

- A handful of share-sheet targets declare their own `android:icon` and bypass
  MIUI's theme hook — those stay unthemed regardless of `.mtz` content.
- No preview thumbnails in the `.mtz`. Theme manager will show a blank tile.
- The per-wallpaper detail activity is non-exported, so the Wallpaper FAB lands
  on the list — one more tap to pick a specific super wallpaper.
