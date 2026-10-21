# Nyumbani — a minimal Android TV launcher

A one-screen home for an Android TV (built for a TCL Smart TV Pro on Android TV 11). `preview.png` shows the layout.

- full-screen 4K wallpaper (a savanna dusk is bundled; swap in your own any time)
- top left: a frosted-glass widget with **Hey Tony**, **Nairobi**, the time and the date
- bottom: **Apple TV, Netflix, YouTube** in one glass dock (the glass takes its colours from whatever wallpaper is set)
- focus an app → its top 3 **Continue watching** items appear above the dock; select one to resume
- two small buttons at the end of the dock: **All apps** and **Settings**, so nothing is out of reach

Kotlin + Jetpack Compose for TV. No ads, no tracking; the only network use is loading artwork for the Continue watching cards.

---

## 1. Build the APK

**Option A: GitHub Actions (nothing to install)**
1. Push this folder to a new GitHub repo (private is fine).
2. Open the repo's **Actions** tab. The *Build APK* workflow runs on every push.
3. When it finishes, download the **nyumbani-launcher** artifact and unzip it to get `app-release.apk`.

**Option B: Android Studio / command line**
1. Open the folder in Android Studio (Ladybug or newer) and let it sync, or run:
   ```bash
   ./gradlew assembleRelease
   ```
2. The APK is at `app/build/outputs/apk/release/app-release.apk`.

The release build is minified (smoother on TV hardware) and signed with the debug key, so it installs without any keystore setup.

## 2. Install it

```bash
adb connect 192.168.100.9:5555
adb install -r app-release.apk
```

## 3. Check "Continue watching"

The launcher reads the shared Watch Next list that Netflix, YouTube and other apps publish. Whether it can do that depends on how your TV defines the permission. Check whether the launcher has it:

```bash
adb shell dumpsys package dev.kharioki.nyumbani | grep READ_EPG
```

`granted=true` means Continue watching works. If not, see how the TV protects the permission:

```bash
adb shell dumpsys package permission com.android.providers.tv.permission.READ_EPG_DATA | grep -i prot
```

- `prot=normal`: the permission is granted automatically at install, so there's nothing to do. `pm grant` isn't needed (it fails with "not a changeable permission type").
- `prot=signature|privileged`: only system apps can read the list. The Continue watching row just stays empty and the wallpaper shows through.

If an app shows "Nothing to continue" even with the permission granted, that app isn't publishing Watch Next entries on your TV. Apple TV in particular may not publish one.

## 4. Make it your Home screen

Turn off Projectivy's **Override current launcher** first (or uninstall Projectivy), so the two don't fight over the Home button.

```bash
# Set Nyumbani as Home
adb shell cmd package set-home-activity dev.kharioki.nyumbani/.MainActivity

# Press Home on the remote. If the stock launcher still comes back, disable it:
adb shell pm list packages | grep -i launcher
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

If a "Choose Home app" prompt appears, pick **Nyumbani** → **Always**.

Your remote's Source/Input button and the TV's quick settings menu keep working as before; the gear on the dock opens full Android Settings.

## 5. Use your own wallpaper (optional)

Any JPG, PNG or WebP works; 3840×2160 or 1920×1080 is ideal.

```bash
adb push my-wallpaper.jpg /sdcard/Android/data/dev.kharioki.nyumbani/files/wallpaper.jpg
```

Press Home and it switches over. Delete that file to go back to the bundled one. The bundled wallpaper is in `wallpaper/`, along with the script that generated it.

Note: most Android TVs draw the home screen at 1920×1080 even on a 4K panel (video plays at 4K on a separate layer). The launcher decodes the 4K image at exactly the size the screen uses, so it stays pixel-sharp without wasting memory.

## 6. Customise

Edit `app/src/main/java/dev/kharioki/nyumbani/LauncherConfig.kt`:

```kotlin
const val NAME = "Tony"
const val GREETING = "Hey"
const val LOCATION = "Nairobi"
const val WATCH_NEXT_LIMIT = 3
val FAVORITES = listOf(...)   // the dock apps, in order
```

Rebuild and reinstall with `adb install -r app-release.apk`. Colours and sizes are at the top of `HomeScreen.kt`.

The clock follows the TV's time zone and its 12/24-hour setting (Settings → Device Preferences → Date & time).

## Undo everything

```bash
adb shell pm enable com.google.android.tvlauncher
adb uninstall dev.kharioki.nyumbani
```

## Credits

Inter typeface © The Inter Project Authors, SIL Open Font License 1.1 (see `FONT-LICENSE-Inter.txt`).
