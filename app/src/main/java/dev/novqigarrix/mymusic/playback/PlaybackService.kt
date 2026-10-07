package dev.novqigarrix.mymusic.playback

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dev.novqigarrix.mymusic.MainActivity
import dev.novqigarrix.mymusic.MyMusicApplication
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.data.LibraryState
import dev.novqigarrix.mymusic.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val ACTION_TOGGLE_LIKE = "dev.novqigarrix.mymusic.TOGGLE_LIKE"

/** Custom session command: off → shuffle → Smart Shuffle → off. */
internal const val ACTION_CYCLE_SHUFFLE = "dev.novqigarrix.mymusic.CYCLE_SHUFFLE"

/** Session extra telling controllers whether Smart Shuffle is on. */
internal const val EXTRA_SMART_SHUFFLE = "dev.novqigarrix.mymusic.SMART_SHUFFLE"

/**
 * Hosts the ExoPlayer so music keeps playing in the background, with a media notification.
 *
 * Like Spotify, the notification / system media controls get two extra buttons after the
 * transport controls: "Add to Liked Songs" (+ / ✓) and shuffle. The status-bar icon is overridden
 * via the `media3_notification_small_icon` drawable.
 *
 * The queue itself (autoplay, Smart Shuffle, learning from skips) is shaped by [QueueDirector].
 */
class PlaybackService : MediaSessionService() {

    private val container get() = (application as MyMusicApplication).container
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private var queueDirector: QueueDirector? = null

    private val likeCommand = SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY)
    private val shuffleCommand = SessionCommand(ACTION_CYCLE_SHUFFLE, Bundle.EMPTY)

    private val nowPlayingListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            container.nowPlayingStore.record(player, queueChanged = events.contains(Player.EVENT_TIMELINE_CHANGED))
            // Keep the notification buttons in sync with the current track and shuffle mode.
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
                refreshButtons()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(nowPlayingListener)
        val director = QueueDirector(player, container.recommender) { smartShuffle ->
            mediaSession?.setSessionExtras(Bundle().apply { putBoolean(EXTRA_SMART_SHUFFLE, smartShuffle) })
            refreshButtons()
        }
        queueDirector = director

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaSession.Builder(this, director.sessionPlayer)
            .setSessionActivity(openApp)
            .setCallback(LocalMediaCallback())
            .build()

        // Liking from inside the app should flip the notification's + / ✓ too.
        scope.launch { container.likesRepository.likes.collect { refreshButtons() } }
        // Autoplay needs the library; playback can be resumed before the app has scanned it.
        scope.launch {
            container.musicRepository.state.filterIsInstance<LibraryState.Ready>().first()
            director.onLibraryReady()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        queueDirector?.release()
        queueDirector = null
        mediaSession?.run {
            container.nowPlayingStore.onServiceStopped(player)
            player.removeListener(nowPlayingListener)
            player.release()
            release()
        }
        mediaSession = null
        scope.cancel()
        super.onDestroy()
    }

    private fun currentSongId(): Long? = mediaSession?.player?.currentMediaItem?.mediaId?.toLongOrNull()

    /**
     * Both buttons go in the overflow slot so they appear after previous / play / next. Without an
     * explicit slot Media3 puts the first custom button where "next" belongs and drops "next" from
     * the legacy (Android 12 / MIUI) notification.
     */
    @OptIn(UnstableApi::class)
    private fun buildButtons(): List<CommandButton> {
        val isLiked = currentSongId()?.let { it in container.likesRepository.likes.value } ?: false
        val shuffleIcon = when {
            mediaSession?.player?.shuffleModeEnabled != true -> CommandButton.ICON_SHUFFLE_OFF
            queueDirector?.isSmartShuffle == true -> CommandButton.ICON_SHUFFLE_STAR
            else -> CommandButton.ICON_SHUFFLE_ON
        }
        return listOf(
            CommandButton.Builder(
                if (isLiked) CommandButton.ICON_CHECK_CIRCLE_FILLED else CommandButton.ICON_PLUS_CIRCLE_UNFILLED,
            )
                .setDisplayName(getString(if (isLiked) R.string.unlike else R.string.like))
                .setSessionCommand(likeCommand)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            CommandButton.Builder(shuffleIcon)
                .setDisplayName(getString(if (shuffleIcon == CommandButton.ICON_SHUFFLE_STAR) R.string.smart_shuffle else R.string.shuffle))
                .setSessionCommand(shuffleCommand)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
        )
    }

    // The only way to change buttons after connection; Media3 still marks it unstable.
    @OptIn(UnstableApi::class)
    private fun refreshButtons() {
        mediaSession?.setMediaButtonPreferences(buildButtons())
    }

    /**
     * Media items sent by a controller can arrive without their playback URI, so it is rebuilt here
     * from the MediaStore id. This also means external controllers (Bluetooth, Auto) are restricted
     * to local files.
     */
    private inner class LocalMediaCallback : MediaSession.Callback {

        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(likeCommand)
                .add(shuffleCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setMediaButtonPreferences(buildButtons())
                .build()
        }

        @OptIn(UnstableApi::class)
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_TOGGLE_LIKE -> currentSongId()?.let(container.likesRepository::toggle)
                ACTION_CYCLE_SHUFFLE -> queueDirector?.cycleShuffle()
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.mapNotNull { item ->
                val uri = item.requestMetadata.mediaUri
                    ?: item.mediaId.toLongOrNull()?.let {
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it)
                    }
                    ?: return@mapNotNull null
                item.buildUpon().setUri(uri).build()
            }
            return Futures.immediateFuture(resolved.toMutableList())
        }

        /** Play pressed with nothing loaded (e.g. on the widget after the app was closed): restore the last queue. */
        @OptIn(UnstableApi::class)
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val saved = container.nowPlayingStore.savedQueue()
                ?: return Futures.immediateFailedFuture(UnsupportedOperationException("No queue to resume"))
            val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                val library = container.musicRepository.awaitLibrary()
                val songs = saved.songIds.mapNotNull(library::song)
                if (songs.isEmpty()) {
                    result.setException(UnsupportedOperationException("Saved songs are no longer on the device"))
                    return@launch
                }
                val startIndex = songs.indexOfFirst { it.id == saved.currentSongId }
                mediaSession.player.shuffleModeEnabled = saved.shuffle
                mediaSession.player.repeatMode = saved.repeatMode
                result.set(
                    MediaSession.MediaItemsWithStartPosition(
                        songs.map(Song::toMediaItem),
                        startIndex.coerceAtLeast(0),
                        if (startIndex >= 0) saved.positionMs else 0L,
                    ),
                )
            }
            return result
        }
    }
}
