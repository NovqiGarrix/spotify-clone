package dev.novqigarrix.mymusic.ui.app

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import dev.novqigarrix.mymusic.data.PlaySource
import dev.novqigarrix.mymusic.data.Song

/** The slice of player state that browse screens need (green current row, play/pause on headers). */
@Immutable
data class PlaybackInfo(
    val currentSongId: Long? = null,
    val playWhenReady: Boolean = false,
    val isPlaying: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val smartShuffle: Boolean = false,
    val source: PlaySource? = null,
    val likedIds: Set<Long> = emptySet(),
) {
    fun isSourcePlaying(source: PlaySource): Boolean = this.source == source && playWhenReady
    fun isSourceShuffled(source: PlaySource): Boolean = this.source == source && shuffleEnabled
    fun isSourceSmartShuffled(source: PlaySource): Boolean = this.source == source && smartShuffle
}

/** Playback commands handed to screens instead of the ViewModel itself. */
@Stable
class PlaybackActions(
    val play: (songs: List<Song>, startIndex: Int, source: PlaySource?) -> Unit,
    val shufflePlay: (songs: List<Song>, source: PlaySource?) -> Unit,
    val playOrToggle: (songs: List<Song>, source: PlaySource) -> Unit,
    val toggleLike: (songId: Long) -> Unit,
    val showSongOptions: (Song) -> Unit,
)
