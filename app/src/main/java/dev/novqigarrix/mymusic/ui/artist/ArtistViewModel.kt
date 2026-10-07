package dev.novqigarrix.mymusic.ui.artist

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import dev.novqigarrix.mymusic.MyMusicApplication
import dev.novqigarrix.mymusic.data.Artist
import dev.novqigarrix.mymusic.data.LibraryState
import dev.novqigarrix.mymusic.data.LikesRepository
import dev.novqigarrix.mymusic.data.MusicRepository
import dev.novqigarrix.mymusic.data.PlaySource
import dev.novqigarrix.mymusic.data.Song
import dev.novqigarrix.mymusic.ui.navigation.ArtistRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

private const val POPULAR_COUNT = 5

sealed interface ArtistUiState {
    data object Loading : ArtistUiState
    data object NotFound : ArtistUiState

    @Immutable
    data class Ready(
        val artist: Artist,
        /** No play counts exist for local files, so "Popular" favours liked songs, then newest. */
        val popular: List<Song>,
        val source: PlaySource,
    ) : ArtistUiState
}

class ArtistViewModel(
    savedStateHandle: SavedStateHandle,
    musicRepository: MusicRepository,
    likesRepository: LikesRepository,
) : ViewModel() {

    private val artistId = savedStateHandle.toRoute<ArtistRoute>().artistId

    val uiState: StateFlow<ArtistUiState> = combine(musicRepository.state, likesRepository.likes) { state, likes ->
        when (state) {
            LibraryState.Loading -> ArtistUiState.Loading
            is LibraryState.Ready -> state.library.artist(artistId)?.let { artist ->
                ArtistUiState.Ready(
                    artist = artist,
                    popular = artist.songs
                        .sortedWith(compareByDescending<Song> { it.id in likes }.thenByDescending { it.dateAddedSec })
                        .take(POPULAR_COUNT),
                    source = PlaySource.FromArtist(artist.id),
                )
            } ?: ArtistUiState.NotFound
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArtistUiState.Loading)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as MyMusicApplication).container
                ArtistViewModel(createSavedStateHandle(), container.musicRepository, container.likesRepository)
            }
        }
    }
}
