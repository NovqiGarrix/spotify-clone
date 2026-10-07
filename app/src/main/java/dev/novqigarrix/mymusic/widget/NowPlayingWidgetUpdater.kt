package dev.novqigarrix.mymusic.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.data.LibraryState
import dev.novqigarrix.mymusic.data.LikesRepository
import dev.novqigarrix.mymusic.data.MusicRepository
import dev.novqigarrix.mymusic.data.Song
import dev.novqigarrix.mymusic.playback.NowPlaying
import dev.novqigarrix.mymusic.ui.artwork.ArtworkLoader
import dev.novqigarrix.mymusic.ui.artwork.ArtworkSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Small enough to keep RemoteViews well under the binder limit, sharp enough for a 4x2 widget. */
private const val ARTWORK_PX = 256
private const val ARTWORK_CORNER_FRACTION = 0.05f
private val ARTWORK_RECT = RectF(0f, 0f, ARTWORK_PX.toFloat(), ARTWORK_PX.toFloat())
private const val PLACEHOLDER_ICON_FRACTION = 0.42f

/** Letterbox detection: every Nth pixel of a row/column must be near black for it to count as a bar. */
private const val BAR_SAMPLE_STEP = 4
private const val NEAR_BLACK = 24
private const val MIN_CONTENT_FRACTION = 0.4f
private const val MIN_TINT_LIGHTNESS = 0.16f

private fun Int.isNearBlack() = Color.red(this) < NEAR_BLACK && Color.green(this) < NEAR_BLACK && Color.blue(this) < NEAR_BLACK

internal data class WidgetContent(
    val songId: Long?,
    /** Null while the library is still loading, even if [songId] is known. */
    val song: Song?,
    val isPlaying: Boolean,
    val isLiked: Boolean,
)

/** Keeps every placed [NowPlayingWidget] in sync with the playback service, the library and likes. */
class NowPlayingWidgetUpdater(
    private val context: Context,
    private val scope: CoroutineScope,
    private val nowPlaying: StateFlow<NowPlaying>,
    private val musicRepository: MusicRepository,
    private val likesRepository: LikesRepository,
    private val artworkLoader: ArtworkLoader,
) {
    private val appWidgetManager = AppWidgetManager.getInstance(context)
    private val component = ComponentName(context, NowPlayingWidget::class.java)
    private val placeholderArtwork by lazy(::drawPlaceholder)

    init {
        scope.launch {
            combine(nowPlaying, musicRepository.state, likesRepository.likes, ::contentOf)
                .distinctUntilChanged()
                .collectLatest(::render)
        }
    }

    /** Redraws every widget from the current state, e.g. when one is placed or resized. */
    fun updateAll(onComplete: () -> Unit) {
        scope.launch {
            try {
                render(contentOf(nowPlaying.value, musicRepository.state.value, likesRepository.likes.value))
            } finally {
                onComplete()
            }
        }
    }

    private suspend fun render(content: WidgetContent) {
        val widgetIds = appWidgetManager.getAppWidgetIds(component)
        if (widgetIds.isEmpty()) return
        if (content.songId != null && musicRepository.state.value is LibraryState.Loading) {
            // The app UI hasn't run in this process yet; scan so the last song can be shown.
            musicRepository.refresh()
        }

        val cover = content.song?.albumArtUri
            ?.let { artworkLoader.load(it, ArtworkSize.Small) }
            ?.let { withContext(Dispatchers.Default) { squareCover(it.asAndroidBitmap()) } }
        val artwork = cover?.let(::rounded) ?: placeholderArtwork
        val tint = cover
            ?.let { withContext(Dispatchers.Default) { artworkLoader.tintOf(it) } }
            ?.toArgb()
            ?.let(::liftedTint)
            ?: context.getColor(R.color.widget_surface)

        val views = NowPlayingWidgetViews(context, content, artwork, tint)
        widgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, views.build(appWidgetManager.getAppWidgetOptions(id)))
        }
    }

    /**
     * The cover as an [ARTWORK_PX] square. Black letterbox bars (common on covers ripped from
     * videos) are trimmed first, then any non-square remainder is centre-cropped.
     */
    private fun squareCover(source: Bitmap): Bitmap {
        val content = contentBounds(source)
        val side = minOf(content.width(), content.height()).toFloat()
        val crop = RectF(
            content.exactCenterX() - side / 2,
            content.exactCenterY() - side / 2,
            content.exactCenterX() + side / 2,
            content.exactCenterY() + side / 2,
        )
        return createBitmap(ARTWORK_PX, ARTWORK_PX).also { output ->
            val matrix = Matrix().apply { setRectToRect(crop, ARTWORK_RECT, Matrix.ScaleToFit.FILL) }
            Canvas(output).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }

    private fun contentBounds(bitmap: Bitmap): Rect {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height).also { bitmap.getPixels(it, 0, width, 0, 0, width, height) }
        fun rowIsBar(y: Int) = (0 until width step BAR_SAMPLE_STEP).all { pixels[y * width + it].isNearBlack() }
        fun columnIsBar(x: Int) = (0 until height step BAR_SAMPLE_STEP).all { pixels[it * width + x].isNearBlack() }

        var top = 0
        var bottom = height
        while (top < bottom && rowIsBar(top)) top++
        while (bottom > top && rowIsBar(bottom - 1)) bottom--
        var left = 0
        var right = width
        while (left < right && columnIsBar(left)) left++
        while (right > left && columnIsBar(right - 1)) right--

        // Mostly-black art is a dark cover, not a letterboxed one: leave it alone.
        val keepRows = bottom - top >= height * MIN_CONTENT_FRACTION
        val keepColumns = right - left >= width * MIN_CONTENT_FRACTION
        return Rect(
            if (keepColumns) left else 0,
            if (keepRows) top else 0,
            if (keepColumns) right else width,
            if (keepRows) bottom else height,
        )
    }

    private fun rounded(square: Bitmap): Bitmap = createBitmap(ARTWORK_PX, ARTWORK_PX).also { output ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val radius = ARTWORK_PX * ARTWORK_CORNER_FRACTION
        Canvas(output).drawRoundRect(ARTWORK_RECT, radius, radius, paint)
    }

    /** Very dark covers would otherwise give a flat black widget; keep a hint of the color. */
    private fun liftedTint(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[2] = hsl[2].coerceAtLeast(MIN_TINT_LIGHTNESS)
        return ColorUtils.HSLToColor(hsl)
    }

    /** Spotify's grey music note on a raised square, matching the in-app placeholder. */
    private fun drawPlaceholder(): Bitmap = createBitmap(ARTWORK_PX, ARTWORK_PX).also { output ->
        val canvas = Canvas(output)
        val radius = ARTWORK_PX * ARTWORK_CORNER_FRACTION
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.widget_placeholder) }
        canvas.drawRoundRect(ARTWORK_RECT, radius, radius, paint)
        val iconSize = (ARTWORK_PX * PLACEHOLDER_ICON_FRACTION).toInt()
        val inset = (ARTWORK_PX - iconSize) / 2
        ContextCompat.getDrawable(context, R.drawable.ic_widget_music_note)?.run {
            setBounds(inset, inset, inset + iconSize, inset + iconSize)
            draw(canvas)
        }
    }

    private fun contentOf(playing: NowPlaying, library: LibraryState, likes: Map<Long, Long>) = WidgetContent(
        songId = playing.songId,
        song = playing.songId?.let { (library as? LibraryState.Ready)?.library?.song(it) },
        isPlaying = playing.isPlaying,
        isLiked = playing.songId != null && playing.songId in likes,
    )
}
