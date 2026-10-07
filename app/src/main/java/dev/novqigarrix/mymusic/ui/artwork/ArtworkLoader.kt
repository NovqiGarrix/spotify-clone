package dev.novqigarrix.mymusic.ui.artwork

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

enum class ArtworkSize(val px: Int) { Small(320), Large(1080) }

private const val BYTES_PER_KB = 1024
private const val MAX_IO_PARALLELISM = 4
private const val PALETTE_COLORS = 16
private const val MAX_LIGHTNESS = 0.38f
private const val MAX_SATURATION = 0.75f

/**
 * Loads embedded album art through MediaStore thumbnails (no network, no third-party image
 * library) and extracts Spotify-style background tints with Palette.
 */
class ArtworkLoader(context: Context) {

    private val resolver = context.contentResolver
    private val io = Dispatchers.IO.limitedParallelism(MAX_IO_PARALLELISM)

    private val bitmaps = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / BYTES_PER_KB / 8).toInt(),
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4 / BYTES_PER_KB
    }
    private val misses: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val colors = ConcurrentHashMap<Uri, Color>()

    fun cached(uri: Uri, size: ArtworkSize): ImageBitmap? = bitmaps.get(key(uri, size))

    fun cachedColor(uri: Uri): Color? = colors[uri]

    suspend fun load(uri: Uri, size: ArtworkSize): ImageBitmap? {
        val key = key(uri, size)
        bitmaps.get(key)?.let { return it }
        if (key in misses) return null
        return withContext(io) {
            try {
                resolver.loadThumbnail(uri, Size(size.px, size.px), null).asImageBitmap()
                    .also { bitmaps.put(key, it) }
            } catch (_: IOException) {
                misses += key
                null
            } catch (_: SecurityException) {
                null
            }
        }
    }

    /** A dark, slightly desaturated tint of the artwork, like Spotify's header and player backgrounds. */
    suspend fun dominantColor(uri: Uri): Color? {
        colors[uri]?.let { return it }
        val bitmap = load(uri, ArtworkSize.Small) ?: return null
        return withContext(Dispatchers.Default) {
            tintOf(bitmap.asAndroidBitmap())?.also { colors[uri] = it }
        }
    }

    /** The tint for an already-decoded image. CPU-bound; call off the main thread. */
    fun tintOf(bitmap: Bitmap): Color? {
        val palette = Palette.from(bitmap).maximumColorCount(PALETTE_COLORS).generate()
        val swatch = palette.vibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch ?: return null
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(swatch.rgb, hsl)
        hsl[1] = hsl[1].coerceAtMost(MAX_SATURATION)
        hsl[2] = hsl[2].coerceAtMost(MAX_LIGHTNESS)
        return Color(ColorUtils.HSLToColor(hsl))
    }

    private fun key(uri: Uri, size: ArtworkSize) = "$uri@${size.name}"
}
