package dev.novqigarrix.mymusic.ui.player

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.novqigarrix.mymusic.MyMusicApplication
import dev.novqigarrix.mymusic.data.LibraryState
import dev.novqigarrix.mymusic.data.LikesRepository
import dev.novqigarrix.mymusic.data.MusicRepository
import dev.novqigarrix.mymusic.data.PlaySource
import dev.novqigarrix.mymusic.data.PlaylistKind
import dev.novqigarrix.mymusic.data.RecentsRepository
import dev.novqigarrix.mymusic.data.Song
import dev.novqigarrix.mymusic.playback.PlayerConnection
import dev.novqigarrix.mymusic.playback.QueueItemKind
import dev.novqigarrix.mymusic.playback.RepeatMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

private const val STOP_TIMEOUT_MS = 5_000L
private const val MAX_UP_NEXT = 80

/** Title of the queue's source, resolved for "Playing from …" headers. */
@Immutable
sealed interface SourceLabel {
    data class Album(val title: String) : SourceLabel
    data class Artist(val name: String) : SourceLabel
    data class Playlist(val kind: PlaylistKind) : SourceLabel
}

/** A song waiting in the queue, and why it is there (drives the queue sheet's sections). */
@Immutable
data class UpNextItem(val song: Song, val kind: QueueItemKind)

@Immutable
data class PlayerUiState(
    val song: Song? = null,
    val playWhenReady: Boolean = false,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val smartShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val isLiked: Boolean = false,
    val source: PlaySource? = null,
    val sourceLabel: SourceLabel? = null,
    val upNext: List<UpNextItem> = emptyList(),
    val previousSongId: Long? = null,
    val nextSongId: Long? = null,
    val likedIds: Set<Long> = emptySet(),
)

class PlayerViewModel(
    private val connection: PlayerConnection,
    musicRepository: MusicRepository,
    private val likesRepository: LikesRepository,
    private val recentsRepository: RecentsRepository,
) : ViewModel() {

    val uiState: StateFlow<PlayerUiState> = combine(
        connection.state,
        musicRepository.state,
        likesRepository.likes,
    ) { playback, libraryState, likes ->
        val library = (libraryState as? LibraryState.Ready)?.library
        val song = playback.currentSongId?.let { library?.song(it) }
        val queue = playback.queue
        PlayerUiState(
            song = song,
            playWhenReady = playback.playWhenReady,
            isPlaying = playback.isPlaying,
            durationMs = playback.durationMs.takeIf { it > 0 } ?: song?.durationMs ?: 0L,
            shuffleEnabled = playback.shuffleEnabled,
            smartShuffle = playback.smartShuffle,
            repeatMode = playback.repeatMode,
            isLiked = song != null && song.id in likes,
            source = playback.source,
            sourceLabel = when (val source = playback.source) {
                is PlaySource.FromAlbum -> library?.album(source.albumId)?.let { SourceLabel.Album(it.title) }
                is PlaySource.FromArtist -> library?.artist(source.artistId)?.let { SourceLabel.Artist(it.name) }
                is PlaySource.FromPlaylist -> SourceLabel.Playlist(source.kind)
                null -> null
            },
            upNext = queue.drop(playback.queueIndex + 1).take(MAX_UP_NEXT).mapNotNull { entry ->
                library?.song(entry.songId)?.let { UpNextItem(it, entry.kind) }
            },
            previousSongId = queue.getOrNull(playback.queueIndex - 1)?.songId,
            nextSongId = queue.getOrNull(playback.queueIndex + 1)?.songId,
            likedIds = likes.keys,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlayerUiState())

    val progressMs: StateFlow<Long> = connection.progressMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0L)

    fun play(songs: List<Song>, startIndex: Int, source: PlaySource?) {
        connection.playSongs(songs, startIndex, source, shuffle = false)
        source?.let(recentsRepository::record)
    }

    fun shufflePlay(songs: List<Song>, source: PlaySource?) {
        if (songs.isEmpty()) return
        connection.playSongs(songs, songs.indices.random(), source, shuffle = true)
        source?.let(recentsRepository::record)
    }

    /** Big green button on a collection: resume if it is the active source, otherwise start it. */
    fun playOrToggle(songs: List<Song>, source: PlaySource) {
        if (uiState.value.source == source && uiState.value.song != null) {
            connection.togglePlayPause()
        } else {
            play(songs, 0, source)
        }
    }

    fun togglePlayPause() = connection.togglePlayPause()
    fun resume() = connection.resume()
    fun next() = connection.next()
    fun previous() = connection.previous()
    fun seekTo(positionMs: Long) = connection.seekTo(positionMs)
    fun cycleShuffle() = connection.cycleShuffle()
    fun cycleRepeat() = connection.cycleRepeat()
    fun skipTo(songId: Long) = connection.skipToQueueItem(songId)
    fun addToQueue(song: Song) = connection.addToQueue(song)
    fun toggleLike(songId: Long) = likesRepository.toggle(songId)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as MyMusicApplication).container
                PlayerViewModel(
                    container.playerConnection,
                    container.musicRepository,
                    container.likesRepository,
                    container.recentsRepository,
                )
            }
        }
    }
}
