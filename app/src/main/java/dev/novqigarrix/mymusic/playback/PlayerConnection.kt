package dev.novqigarrix.mymusic.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dev.novqigarrix.mymusic.data.PlaySource
import dev.novqigarrix.mymusic.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val PROGRESS_TICK_MS = 200L

/**
 * App-wide bridge between the UI and [PlaybackService]. Connects while the UI is visible
 * ([connect] / [disconnect] from the activity lifecycle) and mirrors player state into a StateFlow.
 */
class PlayerConnection(private val context: Context, private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val controller = MutableStateFlow<MediaController?>(null)
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            (player as? MediaController)?.let(::sync)
        }
    }

    /** Position updates, ticking only while something is actually playing. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val progressMs: Flow<Long> = state
        .flatMapLatest { playback ->
            val active = controller.value
            if (playback.isPlaying && active != null) {
                flow {
                    while (true) {
                        emit(active.currentPosition)
                        delay(PROGRESS_TICK_MS)
                    }
                }
            } else {
                flowOf(playback.positionMs)
            }
        }
        .distinctUntilChanged()

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(
                object : MediaController.Listener {
                    // Smart Shuffle lives in the session extras.
                    override fun onExtrasChanged(controller: MediaController, extras: Bundle) = sync(controller)
                },
            )
            .buildAsync()
        controllerFuture = future
        scope.launch {
            val connected = runCatching { future.await(context) }.getOrNull() ?: return@launch
            if (controllerFuture !== future) {
                connected.release()
                return@launch
            }
            connected.addListener(listener)
            controller.value = connected
            sync(connected)
        }
    }

    fun disconnect() {
        controller.value?.removeListener(listener)
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        controller.value = null
        _state.update { it.copy(isConnected = false, isPlaying = false) }
    }

    fun playSongs(songs: List<Song>, startIndex: Int, source: PlaySource?, shuffle: Boolean) {
        if (songs.isEmpty()) return
        _state.update { it.copy(source = source) }
        withController {
            shuffleModeEnabled = shuffle
            setMediaItems(songs.map(Song::toMediaItem), startIndex.coerceIn(songs.indices), 0L)
            prepare()
            play()
        }
    }

    /** Jumps to a song already in the queue. */
    fun skipToQueueItem(songId: Long) = withController {
        val index = (0 until mediaItemCount).firstOrNull { getMediaItemAt(it).mediaId == songId.toString() }
        if (index != null) {
            seekToDefaultPosition(index)
            play()
        }
    }

    /** Spotify's "Add to queue": plays right after the current track. */
    fun addToQueue(song: Song) = withController {
        if (mediaItemCount == 0) {
            setMediaItem(song.toMediaItem())
            prepare()
            play()
        } else {
            addMediaItem(currentMediaItemIndex + 1, song.toMediaItem(QueueItemKind.Queued))
        }
    }

    /**
     * "Resume playing" shortcut: continue the current queue, or, when the player is empty (app was
     * closed), let Media3 ask PlaybackService.onPlaybackResumption to restore the last queue.
     */
    fun resume() = withController {
        if (mediaItemCount > 0) {
            if (playbackState == Player.STATE_IDLE) prepare()
            if (playbackState == Player.STATE_ENDED) seekToDefaultPosition()
        }
        play()
    }

    fun togglePlayPause() = withController {
        if (playWhenReady && playbackState != Player.STATE_ENDED) {
            pause()
        } else {
            if (playbackState == Player.STATE_IDLE) prepare()
            if (playbackState == Player.STATE_ENDED) seekToDefaultPosition()
            play()
        }
    }

    fun next() = withController { seekToNext() }

    fun previous() = withController { seekToPrevious() }

    fun seekTo(positionMs: Long) {
        _state.update { it.copy(positionMs = positionMs) }
        withController { seekTo(positionMs) }
    }

    /** Off → shuffle → Smart Shuffle → off; the service owns the cycle. */
    fun cycleShuffle() = withController {
        sendCustomCommand(SessionCommand(ACTION_CYCLE_SHUFFLE, Bundle.EMPTY), Bundle.EMPTY)
    }

    fun cycleRepeat() = withController {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun withController(block: MediaController.() -> Unit) {
        scope.launch { controller.filterNotNull().first().block() }
    }

    private fun sync(player: MediaController) {
        val shuffle = player.shuffleModeEnabled
        val timeline = player.currentTimeline
        var queueIndex = 0
        val queue = buildList {
            if (!timeline.isEmpty) {
                var index = timeline.getFirstWindowIndex(shuffle)
                while (index != C.INDEX_UNSET) {
                    val item = player.getMediaItemAt(index)
                    item.songId?.let { songId ->
                        if (index == player.currentMediaItemIndex) queueIndex = size
                        add(QueueEntry(songId, item.queueKind))
                    }
                    index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle)
                }
            }
        }
        val currentId = player.currentMediaItem?.songId
        _state.update {
            it.copy(
                isConnected = true,
                currentSongId = currentId,
                playWhenReady = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition,
                durationMs = player.duration.takeIf { d -> d != C.TIME_UNSET } ?: 0L,
                shuffleEnabled = shuffle,
                smartShuffle = shuffle && player.sessionExtras.getBoolean(EXTRA_SMART_SHUFFLE),
                repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_ALL -> RepeatMode.All
                    Player.REPEAT_MODE_ONE -> RepeatMode.One
                    else -> RepeatMode.Off
                },
                queue = queue,
                queueIndex = queueIndex,
            )
        }
    }
}

private suspend fun <T> ListenableFuture<T>.await(context: Context): T =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                try {
                    continuation.resume(get())
                } catch (e: ExecutionException) {
                    continuation.resumeWithException(e.cause ?: e)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        continuation.invokeOnCancellation { cancel(false) }
    }
