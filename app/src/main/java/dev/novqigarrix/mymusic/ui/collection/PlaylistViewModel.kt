package dev.novqigarrix.mymusic.ui.collection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import dev.novqigarrix.mymusic.MyMusicApplication
import dev.novqigarrix.mymusic.data.LibraryState
import dev.novqigarrix.mymusic.data.LikesRepository
import dev.novqigarrix.mymusic.data.MusicRepository
import dev.novqigarrix.mymusic.data.PlaySource
import dev.novqigarrix.mymusic.data.PlaylistKind
import dev.novqigarrix.mymusic.ui.navigation.PlaylistRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class PlaylistViewModel(
    savedStateHandle: SavedStateHandle,
    musicRepository: MusicRepository,
    likesRepository: LikesRepository,
) : ViewModel() {

    private val kind = savedStateHandle.toRoute<PlaylistRoute>().kind

    val uiState: StateFlow<CollectionUiState> = combine(musicRepository.state, likesRepository.likes) { state, likes ->
        when (state) {
            LibraryState.Loading -> CollectionUiState.Loading
            is LibraryState.Ready -> {
                val songs = when (kind) {
                    PlaylistKind.Liked -> state.library.songs
                        .filter { it.id in likes }
                        .sortedByDescending { likes[it.id] }
                    PlaylistKind.LocalFiles -> state.library.songs
                }
                CollectionUiState.Ready(
                    kind = CollectionKind.PlaylistCollection(kind),
                    songs = songs,
                    source = PlaySource.FromPlaylist(kind),
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionUiState.Loading)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as MyMusicApplication).container
                PlaylistViewModel(createSavedStateHandle(), container.musicRepository, container.likesRepository)
            }
        }
    }
}
