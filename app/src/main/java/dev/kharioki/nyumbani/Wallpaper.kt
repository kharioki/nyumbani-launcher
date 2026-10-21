package dev.kharioki.nyumbani

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.InputStream

/**
 * Wallpaper source, in order:
 *  1. A file you push to the app's own folder (no storage permission needed):
 *     /sdcard/Android/data/dev.kharioki.nyumbani/files/wallpaper.jpg  (or .png / .webp)
 *  2. The 4K wallpaper bundled in the APK.
 */
object Wallpaper {
    private const val TAG = "Nyumbani"
    private val CUSTOM_NAMES = listOf("wallpaper.jpg", "wallpaper.jpeg", "wallpaper.png", "wallpaper.webp")

    fun customFile(context: Context): File? {
        val dir = context.getExternalFilesDir(null) ?: return null
        return CUSTOM_NAMES.map { File(dir, it) }.firstOrNull { it.isFile && it.length() > 0 }
    }

    /** Changes whenever the custom wallpaper is added, replaced or removed. */
    fun signature(context: Context): String =
        customFile(context)?.let { "${it.name}:${it.length()}:${it.lastModified()}" } ?: "bundled"

    /**
     * Decodes at the smallest power-of-two downscale that still covers [targetW]x[targetH].
     * Android TV usually draws its interface at 1920x1080 even on a 4K panel, so a 4K image is
     * decoded at half size there: still pixel-sharp, and a quarter of the memory.
     * Call off the main thread.
     */
    fun load(context: Context, targetW: Int, targetH: Int): ImageBitmap? {
        val custom = customFile(context)
        fun open(): InputStream = custom?.inputStream() ?: context.assets.open("wallpaper.jpg")

        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open().use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("Unreadable image")

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetW && bounds.outHeight / (sample * 2) >= targetH) sample *= 2

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            open().use { BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
        } catch (e: Exception) {
            Log.w(TAG, "Wallpaper failed to load from ${custom ?: "assets"}", e)
            // A broken custom file shouldn't leave you with a black screen.
            if (custom != null) {
                val opts = BitmapFactory.Options().apply { inSampleSize = if (targetW <= 1920) 2 else 1 }
                runCatching {
                    context.assets.open("wallpaper.jpg").use { BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
                }.getOrNull()
            } else {
                null
            }
        }
    }
}
