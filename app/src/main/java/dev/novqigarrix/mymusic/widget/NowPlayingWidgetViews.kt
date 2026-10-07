package dev.novqigarrix.mymusic.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.KeyEvent
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import dev.novqigarrix.mymusic.MainActivity
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.playback.PlaybackService

/** Layouts from narrowest to largest; the launcher shows the largest one whose minimum size fits. */
private enum class WidgetLayout(
    @LayoutRes val layoutRes: Int,
    val minWidthDp: Float,
    val minHeightDp: Float,
    val hasSkipButtons: Boolean,
    val hasLikeButton: Boolean,
) {
    Small(R.layout.widget_small, minWidthDp = 180f, minHeightDp = 40f, hasSkipButtons = false, hasLikeButton = false),
    Medium(R.layout.widget_medium, minWidthDp = 250f, minHeightDp = 40f, hasSkipButtons = true, hasLikeButton = false),
    Large(R.layout.widget_large, minWidthDp = 250f, minHeightDp = 140f, hasSkipButtons = true, hasLikeButton = true),
}

private const val IMMUTABLE_UPDATE = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

/** Builds the widget's RemoteViews for one snapshot of [WidgetContent]. */
internal class NowPlayingWidgetViews(
    private val context: Context,
    private val content: WidgetContent,
    private val artwork: Bitmap,
    private val backgroundTint: Int,
) {
    private val openApp = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        IMMUTABLE_UPDATE,
    )

    /** Without a queue to play or resume, every control just opens the app. */
    private val canPlay = content.songId != null

    fun build(options: Bundle): RemoteViews =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RemoteViews(WidgetLayout.entries.associate { SizeF(it.minWidthDp, it.minHeightDp) to build(it) })
        } else {
            build(layoutFor(options))
        }

    private fun layoutFor(options: Bundle): WidgetLayout {
        // In portrait the widget is minWidth wide and maxHeight tall.
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        if (width == 0 || height == 0) return WidgetLayout.Large
        return WidgetLayout.entries.lastOrNull { width >= it.minWidthDp && height >= it.minHeightDp }
            ?: WidgetLayout.Small
    }

    private fun build(layout: WidgetLayout) = RemoteViews(context.packageName, layout.layoutRes).apply {
        setInt(R.id.widget_background, "setColorFilter", backgroundTint)
        setOnClickPendingIntent(android.R.id.background, openApp)
        setImageViewBitmap(R.id.widget_artwork, artwork)

        val song = content.song
        setTextViewText(R.id.widget_title, song?.title ?: context.getString(R.string.app_name))
        setTextViewText(
            R.id.widget_subtitle,
            when {
                song != null -> song.artist.ifEmpty { context.getString(R.string.unknown_artist) }
                canPlay -> context.getString(R.string.widget_resume)
                else -> context.getString(R.string.widget_empty)
            },
        )

        setImageViewResource(
            R.id.widget_play_pause,
            if (content.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )
        setContentDescription(
            R.id.widget_play_pause,
            context.getString(if (content.isPlaying) R.string.pause else R.string.play),
        )
        setOnClickPendingIntent(
            R.id.widget_play_pause,
            when {
                content.isPlaying -> mediaButton(KeyEvent.KEYCODE_MEDIA_PAUSE)
                canPlay -> mediaButton(KeyEvent.KEYCODE_MEDIA_PLAY)
                else -> openApp
            },
        )

        if (layout.hasSkipButtons) {
            setOnClickPendingIntent(
                R.id.widget_previous,
                if (canPlay) mediaButton(KeyEvent.KEYCODE_MEDIA_PREVIOUS) else openApp,
            )
            setOnClickPendingIntent(
                R.id.widget_next,
                if (canPlay) mediaButton(KeyEvent.KEYCODE_MEDIA_NEXT) else openApp,
            )
        }

        if (layout.hasLikeButton) {
            val songId = content.songId
            setViewVisibility(R.id.widget_like, if (songId != null) View.VISIBLE else View.INVISIBLE)
            if (songId != null) {
                setImageViewResource(
                    R.id.widget_like,
                    if (content.isLiked) R.drawable.ic_widget_liked else R.drawable.ic_widget_like,
                )
                setContentDescription(
                    R.id.widget_like,
                    context.getString(if (content.isLiked) R.string.unlike else R.string.like),
                )
                setOnClickPendingIntent(R.id.widget_like, toggleLike(songId))
            }
        }
    }

    /**
     * Media button intents go straight to [PlaybackService], which forwards them to its session.
     * Only "play" may start the service in the foreground: it is the one key guaranteed to start
     * playback (resuming the saved queue if needed), and so to post the notification in time.
     */
    private fun mediaButton(keyCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setComponent(ComponentName(context, PlaybackService::class.java))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        // The key code is the request code: the intents differ only in their extras.
        return if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
            PendingIntent.getForegroundService(context, keyCode, intent, IMMUTABLE_UPDATE)
        } else {
            PendingIntent.getService(context, keyCode, intent, IMMUTABLE_UPDATE)
        }
    }

    private fun toggleLike(songId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, NowPlayingWidget::class.java)
            .setAction(NowPlayingWidget.ACTION_TOGGLE_LIKE)
            .putExtra(NowPlayingWidget.EXTRA_SONG_ID, songId),
        IMMUTABLE_UPDATE,
    )
}
