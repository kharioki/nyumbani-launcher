package dev.kharioki.nyumbani

/**
 * Built-in defaults. Everything a person is likely to change (name, greeting, city, dock apps,
 * wallpaper) is set from the TV in Nyumbani settings and stored on the device.
 */
object LauncherConfig {
    const val DEFAULT_CUSTOM_GREETING = "Hey"
    const val DEFAULT_WALLPAPER_ID = "savanna-dusk"

    /** Filled in on first run only by builds made with `-PnyumbaniSeedOwner=true`. */
    const val OWNER_NAME = "Tony"
    const val OWNER_GREETING = "Hey"
    const val OWNER_LOCATION = "Nairobi"

    /** How many "Continue watching" items to show for the focused app. */
    const val WATCH_NEXT_LIMIT = 3

    const val MAX_DOCK_APPS = 5

    /** Where releases are published; used by About and the update check. */
    const val GITHUB_REPO = "kharioki/nyumbani-launcher"
    const val RELEASES_URL = "https://github.com/$GITHUB_REPO/releases"

    /**
     * The first-run dock, in order, if these apps are installed. Each entry lists the package
     * names the app is known to use on Android TV; the first one installed wins. If none match,
     * the launcher falls back to matching the app's visible name.
     */
    val FAVORITES = listOf(
        Favorite(
            label = "Apple TV",
            packages = listOf("com.apple.atve.androidtv.appletv", "com.apple.atve.amazon.appletv"),
        ),
        Favorite(
            label = "Netflix",
            packages = listOf("com.netflix.ninja", "com.netflix.mediaclient"),
        ),
        Favorite(
            label = "YouTube",
            packages = listOf("com.google.android.youtube.tv", "com.google.android.youtube.googletv"),
        ),
    )
}

data class Favorite(val label: String, val packages: List<String>)
