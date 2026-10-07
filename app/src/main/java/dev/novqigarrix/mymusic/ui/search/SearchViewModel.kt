package dev.novqigarrix.mymusic.ui.search

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
import dev.novqigarrix.mymusic.data.MusicLibrary
import dev.novqigarrix.mymusic.data.MusicRepository
import dev.novqigarrix.mymusic.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.text.Normalizer

private const val BROWSE_COUNT = 16
private const val MAX_RESULTS = 60

enum class ResultFilter { All, Songs, Artists, Albums }

@Immutable
sealed interface SearchResult {
    val key: String

    data class SongResult(val song: Song) : SearchResult {
        override val key get() = "song:${song.id}"
    }

    data class AlbumResult(val album: Album) : SearchResult {
        override val key get() = "album:${album.id}"
    }

    data class ArtistResult(val artist: Artist) : SearchResult {
        override val key get() = "artist:${artist.id}"
    }
}

@Immutable
data class SearchUiState(
    val query: String = "",
    val filter: ResultFilter = ResultFilter.All,
    val browse: List<Artist> = emptyList(),
    val results: List<SearchResult> = emptyList(),
) {
    val isSearching: Boolean get() = query.isNotBlank()
}

class SearchViewModel(musicRepository: MusicRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(ResultFilter.All)

    val uiState: StateFlow<SearchUiState> = combine(musicRepository.state, query, filter) { state, query, filter ->
        val library = (state as? LibraryState.Ready)?.library ?: MusicLibrary.Empty
        SearchUiState(
            query = query,
            filter = filter,
            browse = library.artists.sortedByDescending { it.songs.size }.take(BROWSE_COUNT),
            results = if (query.isBlank()) emptyList() else search(library, query, filter),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onFilterSelected(value: ResultFilter) {
        filter.value = if (filter.value == value) ResultFilter.All else value
    }

    private fun search(library: MusicLibrary, rawQuery: String, filter: ResultFilter): List<SearchResult> {
        val needle = rawQuery.normalized()
        val scored = buildList {
            if (filter == ResultFilter.All || filter == ResultFilter.Artists) {
                library.artists.forEach { artist -> score(artist.name, needle)?.let { add(it to SearchResult.ArtistResult(artist)) } }
            }
            if (filter == ResultFilter.All || filter == ResultFilter.Albums) {
                library.albums.forEach { album -> score(album.title, needle)?.let { add(it + 1 to SearchResult.AlbumResult(album)) } }
            }
            if (filter == ResultFilter.All || filter == ResultFilter.Songs) {
                library.songs.forEach { song ->
                    val best = listOfNotNull(
                        score(song.title, needle),
                        score(song.artist, needle)?.plus(ARTIST_MATCH_PENALTY),
                    ).minOrNull()
                    best?.let { add(it + 2 to SearchResult.SongResult(song)) }
                }
            }
        }
        return scored.sortedBy { it.first }.take(MAX_RESULTS).map { it.second }
    }

    /** Lower is better: exact < prefix < word-prefix < substring; null means no match. */
    private fun score(text: String, needle: String): Int? {
        val haystack = text.normalized()
        return when {
            haystack == needle -> 0
            haystack.startsWith(needle) -> 10
            haystack.split(' ').any { it.startsWith(needle) } -> 20
            haystack.contains(needle) -> 30
            else -> null
        }
    }

    private fun String.normalized(): String =
        Normalizer.normalize(lowercase().trim(), Normalizer.Form.NFD).replace(DIACRITICS, "")

    companion object {
        private val DIACRITICS = "\\p{Mn}+".toRegex()
        private const val ARTIST_MATCH_PENALTY = 5

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as MyMusicApplication).container
                SearchViewModel(container.musicRepository)
            }
        }
    }
}
