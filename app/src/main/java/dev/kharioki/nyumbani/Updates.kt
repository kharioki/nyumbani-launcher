package dev.kharioki.nyumbani

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Update check against this repo's GitHub Releases. This and the Continue watching artwork are the
 * only network requests Nyumbani makes; nothing is sent beyond the request itself.
 */
object Updates {
    private const val TAG = "Nyumbani"
    private const val APK_NAME = "nyumbani.apk"
    private const val CHECKSUMS_NAME = "SHA256SUMS.txt"
    const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    private const val RETRY_AFTER_FAILURE_MS = 60L * 60 * 1000

    class Release(
        val tag: String,
        val notes: String,
        val pageUrl: String,
        val apkUrl: String?,
        val checksumsUrl: String?,
    ) {
        val version: String get() = tag.removePrefix("v")
    }

    /** Local builds without a tag are 0.0.0-dev; they don't nag about updates on their own. */
    val isDevBuild: Boolean get() = BuildConfig.VERSION_NAME.endsWith("-dev")

    fun isNewer(tag: String?, current: String = BuildConfig.VERSION_NAME): Boolean =
        tag != null && compareVersions(tag, current) > 0

    /** The newest full release (GitHub leaves pre-releases out of "latest"). Call off the main thread. */
    fun latest(): Release {
        val json = JSONObject(get("https://api.github.com/repos/${LauncherConfig.GITHUB_REPO}/releases/latest").decodeToString())
        val assets = json.optJSONArray("assets")
        fun asset(name: String): String? {
            if (assets == null) return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name") == name) return a.optString("browser_download_url").takeIf { it.startsWith("https://") }
            }
            return null
        }
        return Release(
            tag = json.getString("tag_name"),
            notes = json.optString("body").takeUnless { it == "null" }.orEmpty(),
            pageUrl = json.optString("html_url", LauncherConfig.RELEASES_URL),
            apkUrl = asset(APK_NAME),
            checksumsUrl = asset(CHECKSUMS_NAME),
        )
    }

    /**
     * Downloads the release APK to the cache and checks it against the release's SHA256SUMS.txt
     * (when it has one). Call off the main thread.
     */
    fun download(context: Context, release: Release, onProgress: (Float) -> Unit): File {
        val url = release.apkUrl ?: throw IOException("This release has no $APK_NAME to install.")
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val apk = File(dir, APK_NAME)

        val connection = open(url)
        try {
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                apk.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }

        release.checksumsUrl?.let { sumsUrl ->
            val expected = get(sumsUrl).decodeToString().lineSequence()
                .map { it.trim().split(Regex("\\s+")) }
                .firstOrNull { it.size >= 2 && it[1].removePrefix("*") == APK_NAME }
                ?.get(0)?.lowercase()
            if (expected != null && expected != sha256(apk)) {
                apk.delete()
                throw IOException("The download was damaged (checksum mismatch). Please try again.")
            }
        }
        val info = context.packageManager.getPackageArchiveInfo(apk.path, 0)
        if (info?.packageName != context.packageName) {
            apk.delete()
            throw IOException("The download isn't a Nyumbani APK.")
        }
        return apk
    }

    /** Android only lets Nyumbani install the update once "Install unknown apps" is allowed for it. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun unknownAppsSettings(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    private val _installError = MutableStateFlow<String?>(null)
    /** Set when the system reports a failed install. */
    val installError: StateFlow<String?> = _installError

    /**
     * Hands the APK to the system installer. Android asks for confirmation, then replaces this app
     * and restarts it. Call off the main thread.
     */
    fun install(context: Context, apk: File) {
        _installError.value = null
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite(APK_NAME, 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = Intent(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, sessionId, callback, flags).intentSender)
        }
    }

    internal fun reportInstallError(message: String) {
        _installError.value = message
    }

    /** Semantic-version comparison: 1.10.0 > 1.9.2, and 1.3.0 > 1.3.0-beta.2 > 1.3.0-beta.1. */
    fun compareVersions(a: String, b: String): Int {
        fun split(v: String): Pair<List<Int>, List<String>> {
            val clean = v.trim().removePrefix("v").substringBefore('+')
            val core = clean.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val pre = clean.substringAfter('-', "").split('.').filter { it.isNotEmpty() }
            return core to pre
        }
        val (coreA, preA) = split(a)
        val (coreB, preB) = split(b)
        for (i in 0 until maxOf(coreA.size, coreB.size, 3)) {
            val c = (coreA.getOrNull(i) ?: 0).compareTo(coreB.getOrNull(i) ?: 0)
            if (c != 0) return c
        }
        if (preA.isEmpty() || preB.isEmpty()) return preB.size.coerceAtMost(1) - preA.size.coerceAtMost(1)
        for (i in 0 until minOf(preA.size, preB.size)) {
            val x = preA[i]
            val y = preB[i]
            val nx = x.toIntOrNull()
            val ny = y.toIntOrNull()
            val c = when {
                nx != null && ny != null -> nx.compareTo(ny)
                nx != null -> -1
                ny != null -> 1
                else -> x.compareTo(y)
            }
            if (c != 0) return c
        }
        return preA.size.compareTo(preB.size)
    }

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Nyumbani/${BuildConfig.VERSION_NAME}")
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            throw IOException(if (code == 404) "No releases published yet." else "GitHub answered $code.")
        }
        return connection
    }

    private fun get(url: String): ByteArray {
        val connection = open(url)
        return try {
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Quiet daily check from the home screen: records the newest tag so the gear can show a dot. */
    suspend fun checkQuietly(repo: SettingsRepository, settings: LauncherSettings) {
        if (isDevBuild) return
        val now = System.currentTimeMillis()
        if (now - settings.lastUpdateCheck in 0 until CHECK_INTERVAL_MS) return
        // Two resumes in quick succession would otherwise both ask before either has saved.
        if (!quietCheckRunning.compareAndSet(false, true)) return
        try {
            val tag = withContext(Dispatchers.IO) { runCatching { latest().tag } }
                .onFailure { Log.i(TAG, "Update check failed: $it") }
                .getOrNull()
            // Offline or rate-limited: try again in an hour rather than tomorrow.
            repo.setUpdateCheck(tag, if (tag != null) now else now - CHECK_INTERVAL_MS + RETRY_AFTER_FAILURE_MS)
        } finally {
            quietCheckRunning.set(false)
        }
    }

    private val quietCheckRunning = AtomicBoolean(false)
}

/** Receives the system installer's progress for an update started from About. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { Updates.reportInstallError("The TV couldn't show the install prompt.") }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // We're replaced and restarted.
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.w("Nyumbani", "Update install failed ($status): $message")
                Updates.reportInstallError(
                    if (status == PackageInstaller.STATUS_FAILURE_CONFLICT || status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE) {
                        "This update is signed differently from the installed app. Uninstall Nyumbani once, then install the new version."
                    } else {
                        "The update wasn't installed${message?.let { ": $it" } ?: "."}"
                    },
                )
            }
        }
    }
}
