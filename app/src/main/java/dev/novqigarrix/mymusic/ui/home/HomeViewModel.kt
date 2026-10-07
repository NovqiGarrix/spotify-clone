package dev.novqigarrix.mymusic.ui.home

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
import java.time.LocalDate
import kotlin.random.Random

private const val SHORTCUT_COUNT = 8
private const val CAROUSEL_SIZE = 12

enum class HomeFilter { All, Music, Artists }

enum class HomeSectionType { JumpBackIn, RecentlyAdded, YourArtists, AlbumsForYou }

@Immutable
sealed interface HomeItem {
    val key: String
    val source: PlaySource

    data class AlbumItem(val album: Album) : HomeItem {
        override val key get() = "album:${album.id}"
        override val source get() = PlaySource.FromAlbum(album.id)
    }

    data class ArtistItem(val artist: Artist) : HomeItem {
        override val key get() = "artist:${artist.id}"
        override val source get() = PlaySource.FromArtist(artist.id)
    }

    data class PlaylistItem(val kind: PlaylistKind, val songCount: Int) : HomeItem {
        override val key get() = "playlist:${kind.name}"
        override val source get() = PlaySource.FromPlaylist(kind)
    }
}

@Immutable
data class HomeSection(val type: HomeSectionType, val items: List<HomeItem>)

@Immutable
data class HomeUiState(
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val filter: HomeFilter = HomeFilter.All,
    val shortcuts: List<HomeItem> = emptyList(),
    val sections: List<HomeSection> = emptyList(),
)

class HomeViewModel(
    musicRepository: MusicRepository,
    likesRepository: LikesRepository,
    recentsRepository: RecentsRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(HomeFilter.All)

    val uiState: StateFlow<HomeUiState> = combine(
        musicRepository.state,
        likesRepository.likes,
        recentsRepository.recents,
        filter,
    ) { state, likes, recents, filter ->
        when (state) {
            LibraryState.Loading -> HomeUiState(isLoading = true, filter = filter)
            is LibraryState.Ready -> buildState(state.library, likes.size, recents, filter)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onFilterSelected(selected: HomeFilter) {
        filter.value = if (filter.value == selected) HomeFilter.All else selected
    }

    private fun buildState(
        library: MusicLibrary,
        likedCount: Int,
        recents: List<PlaySource>,
        filter: HomeFilter,
    ): HomeUiState {
        if (library.isEmpty) return HomeUiState(isLoading = false, isEmpty = true, filter = filter)

        val liked = HomeItem.PlaylistItem(PlaylistKind.Liked, likedCount)
        val localFiles = HomeItem.PlaylistItem(PlaylistKind.LocalFiles, library.songs.size)
        val recentItems = recents.mapNotNull { it.toItem(library, liked, localFiles) }
        val recentlyAdded = library.albums.take(CAROUSEL_SIZE).map(HomeItem::AlbumItem)
        val topArtists = library.artists
            .sortedByDescending { it.songs.size }
            .take(CAROUSEL_SIZE)
            .map(HomeItem::ArtistItem)
        // A stable "daily mix" of albums: reshuffles once per day.
        val forYou = library.albums
            .shuffled(Random(LocalDate.now().toEpochDay()))
            .take(CAROUSEL_SIZE)
            .map(HomeItem::AlbumItem)

        val shortcuts = (recentItems + listOf(liked, localFiles) + recentlyAdded)
            .distinctBy { it.key }
            .take(SHORTCUT_COUNT)

        val sections = buildList {
            if (recentItems.isNotEmpty()) add(HomeSection(HomeSectionType.JumpBackIn, recentItems))
            add(HomeSection(HomeSectionType.RecentlyAdded, recentlyAdded))
            add(HomeSection(HomeSectionType.YourArtists, topArtists))
            add(HomeSection(HomeSectionType.AlbumsForYou, forYou))
        }.mapNotNull { section ->
            val items = section.items.filter { item ->
                when (filter) {
                    HomeFilter.All -> true
                    HomeFilter.Music -> item !is HomeItem.ArtistItem
                    HomeFilter.Artists -> item is HomeItem.ArtistItem
                }
            }
            section.copy(items = items).takeIf { items.isNotEmpty() }
        }

        return HomeUiState(
            isLoading = false,
            isEmpty = false,
            filter = filter,
            shortcuts = if (filter == HomeFilter.All) shortcuts else emptyList(),
            sections = sections,
        )
    }

    private fun PlaySource.toItem(
        library: MusicLibrary,
        liked: HomeItem.PlaylistItem,
        localFiles: HomeItem.PlaylistItem,
    ): HomeItem? = when (this) {
        is PlaySource.FromAlbum -> library.album(albumId)?.let(HomeItem::AlbumItem)
        is PlaySource.FromArtist -> library.artist(artistId)?.let(HomeItem::ArtistItem)
        is PlaySource.FromPlaylist -> if (kind == PlaylistKind.Liked) liked else localFiles
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as MyMusicApplication).container
                HomeViewModel(container.musicRepository, container.likesRepository, container.recentsRepository)
            }
        }
    }
}
