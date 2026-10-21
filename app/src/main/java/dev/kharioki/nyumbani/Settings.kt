package dev.kharioki.nyumbani

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class GreetingStyle { TIME_OF_DAY, CUSTOM }

enum class ClockMode { FOLLOW_SYSTEM, ON, OFF }

/** Everything the person can change from the TV. Defaults live in [LauncherConfig]. */
data class LauncherSettings(
    val name: String = "",
    val greetingStyle: GreetingStyle = GreetingStyle.TIME_OF_DAY,
    val customGreeting: String = LauncherConfig.DEFAULT_CUSTOM_GREETING,
    val location: String = "",
    /** 24-hour clock: follow the TV, always on, or always off. */
    val use24Hour: ClockMode = ClockMode.FOLLOW_SYSTEM,
    /** Package names in dock order. Empty until the dock has been seeded on first launch. */
    val dockPackages: List<String> = emptyList(),
    val wallpaperId: String = LauncherConfig.DEFAULT_WALLPAPER_ID,
    /** False until the first-run setup has been finished or skipped. */
    val setupDone: Boolean = false,
    /** Newest release tag seen by the update check, or null. */
    val latestVersionSeen: String? = null,
    val lastUpdateCheck: Long = 0L,
) {
    val dockSeeded: Boolean get() = dockPackages.isNotEmpty()
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val NAME = stringPreferencesKey("name")
        val GREETING_STYLE = stringPreferencesKey("greeting_style")
        val CUSTOM_GREETING = stringPreferencesKey("custom_greeting")
        val LOCATION = stringPreferencesKey("location")
        val USE_24_HOUR = stringPreferencesKey("use_24_hour")
        // Stored as one string: preference string sets don't keep their order.
        val DOCK = stringPreferencesKey("dock_packages")
        val WALLPAPER = stringPreferencesKey("wallpaper_id")
        val SETUP_DONE = booleanPreferencesKey("setup_done")
        val OWNER_SEEDED = booleanPreferencesKey("owner_seeded")
        val LATEST_SEEN = stringPreferencesKey("latest_version_seen")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
    }

    val settings: Flow<LauncherSettings> = store.data.map { p ->
        LauncherSettings(
            name = p[Keys.NAME] ?: "",
            greetingStyle = enumOr(p[Keys.GREETING_STYLE], GreetingStyle.TIME_OF_DAY),
            customGreeting = p[Keys.CUSTOM_GREETING] ?: LauncherConfig.DEFAULT_CUSTOM_GREETING,
            location = p[Keys.LOCATION] ?: "",
            use24Hour = enumOr(p[Keys.USE_24_HOUR], ClockMode.FOLLOW_SYSTEM),
            dockPackages = p[Keys.DOCK]?.split(DOCK_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty(),
            wallpaperId = p[Keys.WALLPAPER] ?: LauncherConfig.DEFAULT_WALLPAPER_ID,
            setupDone = p[Keys.SETUP_DONE] ?: false,
            latestVersionSeen = p[Keys.LATEST_SEEN],
            lastUpdateCheck = p[Keys.LAST_UPDATE_CHECK] ?: 0L,
        )
    }

    /**
     * Builds made with `-PnyumbaniSeedOwner=true` pre-fill the owner's details the first time they
     * run, so updating the original TV keeps its greeting. Public builds start blank.
     */
    suspend fun seedOwnerIfNeeded() {
        if (!BuildConfig.DEBUG_SEED_OWNER) return
        store.edit { p ->
            if (p[Keys.OWNER_SEEDED] == true) return@edit
            p[Keys.OWNER_SEEDED] = true
            if (p[Keys.NAME] == null) p[Keys.NAME] = LauncherConfig.OWNER_NAME
            if (p[Keys.GREETING_STYLE] == null) {
                p[Keys.GREETING_STYLE] = GreetingStyle.CUSTOM.name
                p[Keys.CUSTOM_GREETING] = LauncherConfig.OWNER_GREETING
            }
            if (p[Keys.LOCATION] == null) p[Keys.LOCATION] = LauncherConfig.OWNER_LOCATION
            p[Keys.SETUP_DONE] = true
        }
    }

    /** First launch: fill the dock once from [LauncherConfig.FAVORITES], or the first few apps. */
    suspend fun seedDockIfNeeded(allApps: List<AppEntry>) {
        if (allApps.isEmpty()) return
        store.edit { p ->
            if (!p[Keys.DOCK].isNullOrBlank()) return@edit
            p[Keys.DOCK] = defaultDock(allApps).joinToString(DOCK_SEPARATOR)
        }
    }

    suspend fun setName(value: String) = store.edit { it[Keys.NAME] = value.trim() }
    suspend fun setGreetingStyle(value: GreetingStyle) = store.edit { it[Keys.GREETING_STYLE] = value.name }
    suspend fun setCustomGreeting(value: String) = store.edit { it[Keys.CUSTOM_GREETING] = value.trim() }
    suspend fun setLocation(value: String) = store.edit { it[Keys.LOCATION] = value.trim() }
    suspend fun setClock(value: ClockMode) = store.edit { it[Keys.USE_24_HOUR] = value.name }
    suspend fun setWallpaper(id: String) = store.edit { it[Keys.WALLPAPER] = id }
    suspend fun setSetupDone() = store.edit { it[Keys.SETUP_DONE] = true }

    suspend fun setDock(packages: List<String>) {
        val clean = packages.distinct().take(LauncherConfig.MAX_DOCK_APPS)
        if (clean.isEmpty()) return
        store.edit { it[Keys.DOCK] = clean.joinToString(DOCK_SEPARATOR) }
    }

    suspend fun setUpdateCheck(latestTag: String?, at: Long) = store.edit { p ->
        if (latestTag != null) p[Keys.LATEST_SEEN] = latestTag
        p[Keys.LAST_UPDATE_CHECK] = at
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: fallback

    companion object {
        private const val DOCK_SEPARATOR = ","

        fun defaultDock(allApps: List<AppEntry>): List<String> {
            val favorites = LauncherConfig.FAVORITES.mapNotNull { fav ->
                val byPackage = fav.packages.firstNotNullOfOrNull { pkg -> allApps.firstOrNull { it.packageName == pkg } }
                val byLabel = allApps.firstOrNull { it.label.equals(fav.label, ignoreCase = true) }
                (byPackage ?: byLabel)?.packageName
            }.distinct()
            return favorites.ifEmpty { allApps.take(3).map { it.packageName } }
        }
    }
}
