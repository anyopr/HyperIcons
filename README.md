# icon-pack-to-mui

One-screen Android app that turns Arcticons (or any CandyBar-based icon pack) into
a HyperOS/MIUI `.mtz` theme and asks the system theme manager to apply it — no
Peafowl, no MTZ Tester in the happy path.

## How it works

1. Reads Arcticons' `appfilter.xml` out of its installed APK.
2. Lists every launchable app and auto-matches by `ComponentName`.
3. Unmatched apps get a **Pick** button that opens Arcticons' own icon picker —
   you search/browse there and tap the icon you want.
4. Choices persist in a local Room DB, so on the next run only *new* apps need
   attention.
5. **Apply** builds an icons-only `.mtz` and fires `Intent.ACTION_VIEW` at
   `com.android.thememanager`. If HyperOS 3 rejects the direct intent, the app
   falls back to a generic chooser (lets MTZ Tester handle it) and, failing that,
   exports the `.mtz` to `Android/data/de.bitbin.iconpacker/files/exports/`.

## Build

You need a JDK 17+ and the Android SDK with platform 34 installed.

```bash
# one-time: generate the gradle wrapper (needs a system gradle 8.x)
gradle wrapper --gradle-version 8.9

# build + install debug
./gradlew installDebug
```

Or open the project in Android Studio and hit Run.

## Install + run

1. Install Arcticons on the device (F-Droid or Play).
2. `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. Launch **Icon Packer**, tap **Pick** for any app marked *unmatched*, then tap
   **Apply** at the bottom.

## Known rough edges

- The MTZ layout uses `<package>.png` inside the nested `icons` zip. If HyperOS 3
  ignores those, swap `MtzBuilder.nameFor()` to use `<package>_<activity>.png`
  or the MIUI md5-of-component scheme.
- Only Arcticons is wired up (`ARCTICONS_PKG` in `MainViewModel.kt`). Any
  CandyBar pack should work — just change the constant.
- No preview thumbnails in the `.mtz`. Theme manager will show a blank tile.
