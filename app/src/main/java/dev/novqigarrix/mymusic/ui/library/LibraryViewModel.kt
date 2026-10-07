package dev.novqigarrix.mymusic.ui.library

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.novqigarrix.mymusic.MyMusicApplication
import dev.novqigarrix.mymusic.data.Album
import dev.novqigarrix.mymusic.data.Artist
import dev.novqigarrix.mymusic.data.LibraryState
import dev.novqigarrix.mymusic.data.LikesRepository
import dev.novqigarrix.mymusic.data.MusicLibrary
import dev.novqigarrix.mymusic.data.MusicRepository
import dev.novqigarrix.mymusic.data.PlaySource
import dev.novqigarrix.mymusic.data.PlaylistKind
import dev.novqigarrix.mymusic.data.RecentsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class LibraryFilter { Playlists, Albums, Artists }
enum class LibrarySort { Recents, Alphabetical, Creator }
enum class LibraryViewMode { List, Grid }

@Immutable
sealed interface LibraryEntry {
    val key: String
    val sortTitle: String
    val creator: String

    data class PlaylistEntry(val kind: PlaylistKind, val songCount: Int) : LibraryEntry {
        override val key get() = "playlist:${kind.name}"
        override val sortTitle get() = kind.name
        override val creator get() = ""
    }

    data class AlbumEntry(val album: Album) : LibraryEntry {
        override val key get() = "album:${album.id}"
        override val sortTitle get() = album.title.lowercase()
        override val creator get() = album.artist.lowercase()
    }

    data class ArtistEntry(val artist: Artist) : LibraryEntry {
        override val key get() = "artist:${artist.id}"
        override val sortTitle get() = artist.name.lowercase()
        override val creator get() = artist.name.lowercase()
    }
}

@Immutable
data class LibraryUiState(
    val filter: LibraryFilter? = null,
    val sort: LibrarySort = LibrarySort.Recents,
    val viewMode: LibraryViewMode = LibraryViewMode.List,
    val entries: List<LibraryEntry> = emptyList(),
)

private data class LibraryOptions(
    val filter: LibraryFilter? = null,
    val sort: LibrarySort = LibrarySort.Recents,
    val viewMode: LibraryViewMode = LibraryViewMode.List,
)

class LibraryViewModel(
    musicRepository: MusicRepository,
    likesRepository: LikesRepository,
    recentsRepository: RecentsRepository,
) : ViewModel() {

    private val options = MutableStateFlow(LibraryOptions())

    val uiState: StateFlow<LibraryUiState> = combine(
        musicRepository.state,
        likesRepository.likes,
        recentsRepository.recents,
        options,
    ) { state, likes, recents, options ->
        val library = (state as? LibraryState.Ready)?.library ?: MusicLibrary.Empty
        LibraryUiState(
            filter = options.filter,
            sort = options.sort,
            viewMode = options.viewMode,
            entries = buildEntries(library, likes.size, recents, options),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onFilterSelected(filter: LibraryFilter?) =
        options.update { it.copy(filter = if (it.filter == filter) null else filter) }

    fun onSortSelected(sort: LibrarySort) = options.update { it.copy(sort = sort) }

    fun onToggleViewMode() = options.update {
        it.copy(viewMode = if (it.viewMode == LibraryViewMode.List) LibraryViewMode.Grid else LibraryViewMode.List)
    }

    private fun buildEntries(
        library: MusicLibrary,
        likedCount: Int,
        recents: List<PlaySource>,
        options: LibraryOptions,
    ): List<LibraryEntry> {
        if (library.isEmpty) return emptyList()
        val playlists = listOf(
            LibraryEntry.PlaylistEntry(PlaylistKind.Liked, likedCount),
            LibraryEntry.PlaylistEntry(PlaylistKind.LocalFiles, library.songs.size),
        )
        val albums = library.albums.map(LibraryEntry::AlbumEntry)
        val artists = library.artists.map(LibraryEntry::ArtistEntry)
        val content: List<LibraryEntry> = when (options.filter) {
            null -> albums + artists
            LibraryFilter.Playlists -> emptyList()
            LibraryFilter.Albums -> albums
            LibraryFilter.Artists -> artists
        }
        val recentRank = recents.withIndex().associate { (index, source) -> source.key to index }
        val sorted = when (options.sort) {
            // Recently played first, then everything else in date-added order (albums arrive pre-sorted).
            LibrarySort.Recents -> content.sortedBy { recentRank[it.key] ?: Int.MAX_VALUE }
            LibrarySort.Alphabetical -> content.sortedBy { it.sortTitle }
            LibrarySort.Creator -> content.sortedWith(compareBy({ it.creator }, { it.sortTitle }))
        }
        // Liked Songs and Local Files stay pinned to the top, as in Spotify.
        val showPlaylists = options.filter == null || options.filter == LibraryFilter.Playlists
        return if (showPlaylists) playlists + sorted else sorted
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as MyMusicApplication).container
                LibraryViewModel(container.musicRepository, container.likesRepository, container.recentsRepository)
            }
        }
    }
}
