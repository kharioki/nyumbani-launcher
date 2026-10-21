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

    /** The sharp wallpaper plus a heavily blurred copy used as the "glass" behind the widgets. */
    class Images(val sharp: ImageBitmap, val frosted: ImageBitmap)

    /**
     * Decodes at the smallest power-of-two downscale that still covers [targetW]x[targetH].
     * Android TV usually draws its interface at 1920x1080 even on a 4K panel, so a 4K image is
     * decoded at half size there: still pixel-sharp, and a quarter of the memory.
     * Call off the main thread.
     */
    fun load(context: Context, targetW: Int, targetH: Int): Images? {
        val custom = customFile(context)
        var bitmap = decode(context, custom, targetW, targetH)
        // A broken custom file shouldn't leave you with a black screen: fall back to the bundled one.
        if (bitmap == null && custom != null) bitmap = decode(context, null, targetW, targetH)
        if (bitmap == null) return null
        return Images(sharp = bitmap.asImageBitmap(), frosted = frost(bitmap).asImageBitmap())
    }

    private fun decode(context: Context, file: File?, targetW: Int, targetH: Int): Bitmap? {
        fun open(): InputStream = file?.inputStream() ?: context.assets.open("wallpaper.jpg")
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
            open().use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            Log.w(TAG, "Wallpaper failed to load from ${file ?: "assets"}", e)
            null
        }
    }

    /**
     * Android 11 has no live blur, so we blur once per wallpaper: shrink to ~1/8 size, then run a
     * few box-blur passes (together roughly a Gaussian). Drawn back up with bilinear filtering it
     * reads as frosted glass, at a cost of a few milliseconds and ~150 KB.
     */
    private fun frost(source: Bitmap): Bitmap {
        val w = 240
        val h = (w * source.height / source.width).coerceAtLeast(1)
        val mid = Bitmap.createScaledBitmap(source, w * 3, h * 3, true)
        val small = Bitmap.createScaledBitmap(mid, w, h, true)
        if (mid !== source) mid.recycle()

        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) {
            boxBlur(px, w, h, radius = 4, horizontal = true)
            boxBlur(px, w, h, radius = 4, horizontal = false)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        if (small !== source) small.recycle()
        return out
    }

    /** One running-sum box blur pass over opaque ARGB pixels, clamping at the edges. */
    private fun boxBlur(px: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val window = radius * 2 + 1
        val r = IntArray(len); val g = IntArray(len); val b = IntArray(len)
        fun index(line: Int, i: Int) = if (horizontal) line * w + i else i * w + line

        for (line in 0 until lines) {
            for (i in 0 until len) {
                val c = px[index(line, i)]
                r[i] = (c shr 16) and 0xFF; g[i] = (c shr 8) and 0xFF; b[i] = c and 0xFF
            }
            var sr = 0; var sg = 0; var sb = 0
            for (k in -radius..radius) {
                val j = k.coerceIn(0, len - 1)
                sr += r[j]; sg += g[j]; sb += b[j]
            }
            for (i in 0 until len) {
                px[index(line, i)] = (0xFF shl 24) or ((sr / window) shl 16) or ((sg / window) shl 8) or (sb / window)
                val add = (i + radius + 1).coerceAtMost(len - 1)
                val drop = (i - radius).coerceAtLeast(0)
                sr += r[add] - r[drop]; sg += g[add] - g[drop]; sb += b[add] - b[drop]
            }
        }
    }
}
