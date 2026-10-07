package dev.novqigarrix.mymusic.playback

import android.content.Context
import androidx.core.content.edit
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val KEY_QUEUE = "queue"
private const val KEY_INDEX = "index"
private const val KEY_POSITION = "position_ms"
private const val KEY_SHUFFLE = "shuffle"
private const val KEY_REPEAT = "repeat_mode"
private const val SEPARATOR = ","

/** What the playback service is doing, observable without a MediaController (used by the widget). */
data class NowPlaying(
    val songId: Long? = null,
    /** Playing or about to play; drives the play/pause icon like [PlaybackState.playWhenReady]. */
    val isPlaying: Boolean = false,
)

/** The last queue the service played, in timeline (unshuffled) order. */
data class SavedQueue(
    val songIds: List<Long>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    /** A [Player] REPEAT_MODE_* constant. */
    val repeatMode: Int,
) {
    val currentSongId: Long? get() = songIds.getOrNull(index)
}

/**
 * Written by [PlaybackService] on every player event. The queue is persisted so the widget can show
 * the last song after the process dies, and so pressing play there resumes where the user left off.
 */
class NowPlayingStore(context: Context) {

    private val prefs = context.getSharedPreferences("now_playing", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(NowPlaying(songId = savedQueue()?.currentSongId))
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    fun savedQueue(): SavedQueue? {
        val ids = prefs.getString(KEY_QUEUE, null).orEmpty()
            .split(SEPARATOR)
            .mapNotNull(String::toLongOrNull)
        if (ids.isEmpty()) return null
        return SavedQueue(
            songIds = ids,
            index = prefs.getInt(KEY_INDEX, 0).coerceIn(ids.indices),
            positionMs = prefs.getLong(KEY_POSITION, 0L),
            shuffle = prefs.getBoolean(KEY_SHUFFLE, false),
            repeatMode = prefs.getInt(KEY_REPEAT, Player.REPEAT_MODE_OFF),
        )
    }

    fun record(player: Player, queueChanged: Boolean) {
        // An empty player is a fresh service, not a cleared queue: keep the last one to resume.
        if (player.mediaItemCount == 0) {
            _state.update { it.copy(isPlaying = false) }
            return
        }
        _state.value = NowPlaying(
            songId = player.currentMediaItem?.mediaId?.toLongOrNull(),
            isPlaying = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
        )
        prefs.edit {
            if (queueChanged) {
                putString(
                    KEY_QUEUE,
                    (0 until player.mediaItemCount).joinToString(SEPARATOR) { player.getMediaItemAt(it).mediaId },
                )
            }
            putInt(KEY_INDEX, player.currentMediaItemIndex)
            putLong(KEY_POSITION, player.currentPosition)
            putBoolean(KEY_SHUFFLE, player.shuffleModeEnabled)
            putInt(KEY_REPEAT, player.repeatMode)
        }
    }

    fun onServiceStopped(player: Player) {
        record(player, queueChanged = false)
        _state.update { it.copy(isPlaying = false) }
    }
}
