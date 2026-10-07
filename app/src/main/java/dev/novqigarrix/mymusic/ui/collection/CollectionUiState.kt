package dev.novqigarrix.mymusic.ui.collection

import androidx.compose.runtime.Immutable
import dev.novqigarrix.mymusic.data.Album
import dev.novqigarrix.mymusic.data.PlaySource
import dev.novqigarrix.mymusic.data.PlaylistKind
import dev.novqigarrix.mymusic.data.Song

/** What the collection page is showing: a real album or one of the built-in playlists. */
@Immutable
sealed interface CollectionKind {
    data class AlbumCollection(val album: Album) : CollectionKind
    data class PlaylistCollection(val kind: PlaylistKind) : CollectionKind
}

sealed interface CollectionUiState {
    data object Loading : CollectionUiState
    data object NotFound : CollectionUiState

    @Immutable
    data class Ready(
        val kind: CollectionKind,
        val songs: List<Song>,
        val source: PlaySource,
    ) : CollectionUiState {
        val totalDurationMs: Long get() = songs.sumOf { it.durationMs }
    }
}
