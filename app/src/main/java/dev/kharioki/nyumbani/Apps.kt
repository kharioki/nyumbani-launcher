package dev.kharioki.nyumbani

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(
    val label: String,
    val packageName: String,
    /** 16:9 TV banner, if the app ships one. */
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
)

/** A dock slot: the configured favourite plus the installed app it resolved to (null = not installed). */
data class DockSlot(val favorite: Favorite, val app: AppEntry?)

class AppRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    /** All apps that can be opened from a TV launcher, sorted by name. Call off the main thread. */
    fun loadAllApps(): List<AppEntry> {
        val seen = HashSet<String>()
        val result = ArrayList<AppEntry>()

        fun collect(category: String) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(intent, 0)) {
                val ai = info.activityInfo ?: continue
                val pkg = ai.packageName
                if (pkg == context.packageName || !seen.add(pkg)) continue
                val banner = runCatching { ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm) }.getOrNull()
                val icon = runCatching { ai.loadIcon(pm) }.getOrNull()
                result += AppEntry(
                    label = info.loadLabel(pm).toString(),
                    packageName = pkg,
                    banner = banner?.toImage(defaultW = 320, defaultH = 180),
                    icon = icon?.toImage(defaultW = 128, defaultH = 128),
                )
            }
        }

        collect(Intent.CATEGORY_LEANBACK_LAUNCHER)
        // Phone-only apps sideloaded onto the TV show up too, after the TV apps.
        collect(Intent.CATEGORY_LAUNCHER)
        return result.sortedBy { it.label.lowercase() }
    }

    fun resolveDock(allApps: List<AppEntry>): List<DockSlot> =
        LauncherConfig.FAVORITES.map { fav ->
            val byPackage = fav.packages.firstNotNullOfOrNull { pkg -> allApps.firstOrNull { it.packageName == pkg } }
            val byLabel = allApps.firstOrNull { it.label.equals(fav.label, ignoreCase = true) }
            DockSlot(fav, byPackage ?: byLabel)
        }

    fun launch(packageName: String) {
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
            ?: return
        start(intent)
    }

    fun openSettings() = start(Intent(Settings.ACTION_SETTINGS))

    fun start(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w(TAG, "Could not start $intent", e)
        }
    }

    private fun Drawable.toImage(defaultW: Int, defaultH: Int): ImageBitmap {
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) return toBitmap(defaultW, defaultH).asImageBitmap()
        // Keep the aspect ratio, but cap the size so a dozen banners don't eat memory.
        val scale = minOf(1f, defaultW * 2f / intrinsicWidth, defaultH * 2f / intrinsicHeight)
        val w = (intrinsicWidth * scale).toInt().coerceAtLeast(1)
        val h = (intrinsicHeight * scale).toInt().coerceAtLeast(1)
        return toBitmap(w, h).asImageBitmap()
    }

    companion object {
        private const val TAG = "Nyumbani"
    }
}
