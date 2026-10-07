package dev.novqigarrix.mymusic.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import dev.novqigarrix.mymusic.MyMusicApplication

/**
 * Spotify-style "now playing" home-screen widget. The views are pushed by [NowPlayingWidgetUpdater]
 * whenever playback, the library or likes change; this receiver only handles placement, resizing
 * and the like button.
 */
class NowPlayingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) =
        redraw(context)

    /** Before Android 12 the layout for each size is picked here rather than by the launcher. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) = redraw(context)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE_LIKE) {
            val songId = intent.getLongExtra(EXTRA_SONG_ID, NO_SONG)
            // The likes flow change redraws the widget.
            if (songId != NO_SONG) context.container.likesRepository.toggle(songId)
            return
        }
        super.onReceive(context, intent)
    }

    private fun redraw(context: Context) {
        val pending = goAsync()
        context.container.widgetUpdater.updateAll(onComplete = pending::finish)
    }

    companion object {
        internal const val ACTION_TOGGLE_LIKE = "dev.novqigarrix.mymusic.widget.TOGGLE_LIKE"
        internal const val EXTRA_SONG_ID = "song_id"
        private const val NO_SONG = -1L
    }
}

private val Context.container get() = (applicationContext as MyMusicApplication).container
