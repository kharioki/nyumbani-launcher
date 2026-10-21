package dev.kharioki.nyumbani

/**
 * Everything you are likely to want to tweak lives here.
 * Change a value, rebuild, reinstall.
 */
object LauncherConfig {
    const val NAME = "Tony"
    const val GREETING = "Hey"
    const val LOCATION = "Nairobi"

    /** How many "Continue watching" items to show for the focused app. */
    const val WATCH_NEXT_LIMIT = 3

    /**
     * The apps in the dock, in order. Each entry lists the package names the app is known
     * to use on Android TV; the first one installed wins. If none match, the launcher falls
     * back to matching the app's visible name.
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
