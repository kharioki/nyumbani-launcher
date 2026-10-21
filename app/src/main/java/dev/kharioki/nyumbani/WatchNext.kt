package dev.kharioki.nyumbani

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.util.Log
import androidx.core.content.ContextCompat

data class WatchNextItem(
    val id: Long,
    val title: String,
    val subtitle: String?,
    val imageUri: String?,
    /** 0..1, or null when the app doesn't report playback position. */
    val progress: Float?,
    val intentUri: String?,
    val packageName: String,
)

sealed interface WatchNextState {
    data object Loading : WatchNextState
    data object NoPermission : WatchNextState
    data class Loaded(val items: List<WatchNextItem>) : WatchNextState
    data class Failed(val reason: String) : WatchNextState
}

/**
 * Reads the system-wide "Watch Next" (Continue watching) list that streaming apps publish to
 * Android TV, filtered to one app. Column names are the documented TvContract ones, written out
 * as strings so they don't depend on which interface the constant is declared on.
 */
object WatchNextRepository {
    const val PERMISSION = "com.android.providers.tv.permission.READ_EPG_DATA"
    private const val TAG = "Nyumbani"

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Call off the main thread. */
    fun load(context: Context, packageName: String, limit: Int): WatchNextState {
        if (!hasPermission(context)) return WatchNextState.NoPermission

        val projection = arrayOf(
            "_id", "title", "episode_title", "season_display_number", "episode_display_number",
            "poster_art_uri", "thumbnail_uri", "intent_uri",
            "last_playback_position_millis", "duration_millis", "package_name",
        )
        return try {
            val cursor = context.contentResolver.query(
                TvContract.WatchNextPrograms.CONTENT_URI,
                projection,
                "package_name=?",
                arrayOf(packageName),
                "last_engagement_time_utc_millis DESC",
            ) ?: return WatchNextState.Failed("TV provider not available")

            val items = ArrayList<WatchNextItem>()
            val titles = HashSet<String>()
            cursor.use { c ->
                fun str(col: String) = c.getColumnIndex(col).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getString(it) }
                fun long(col: String) = c.getColumnIndex(col).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getLong(it) }

                while (c.moveToNext() && items.size < limit) {
                    val title = str("title")?.takeIf { it.isNotBlank() } ?: continue
                    // A series can have several episodes queued; show it once.
                    if (!titles.add(title)) continue

                    val season = str("season_display_number")
                    val episode = str("episode_display_number")
                    val episodeTitle = str("episode_title")
                    val subtitle = buildString {
                        if (season != null && episode != null) append("S$season E$episode")
                        if (!episodeTitle.isNullOrBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(episodeTitle)
                        }
                    }.ifBlank { null }

                    val position = long("last_playback_position_millis") ?: 0L
                    val duration = long("duration_millis") ?: 0L
                    val progress = if (position > 0 && duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else null

                    items += WatchNextItem(
                        id = long("_id") ?: items.size.toLong(),
                        title = title,
                        subtitle = subtitle,
                        // Thumbnails are usually landscape, which fits the cards better than posters.
                        imageUri = str("thumbnail_uri") ?: str("poster_art_uri"),
                        progress = progress,
                        intentUri = str("intent_uri"),
                        packageName = str("package_name") ?: packageName,
                    )
                }
            }
            WatchNextState.Loaded(items)
        } catch (e: SecurityException) {
            Log.w(TAG, "Watch Next access denied", e)
            WatchNextState.NoPermission
        } catch (e: Exception) {
            Log.w(TAG, "Watch Next query failed", e)
            WatchNextState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** The deep link that resumes playback, or null to fall back to opening the app. */
    fun intentFor(item: WatchNextItem): Intent? {
        val intent = item.intentUri
            ?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() }
            ?: return null
        // The URI comes from another app's data: don't let it piggyback a selector or URI grants.
        intent.selector = null
        intent.flags = intent.flags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            ).inv()
        // Keep the deep link inside the app that published it.
        if (intent.component == null && intent.`package` == null) intent.setPackage(item.packageName)
        return intent
    }
}
