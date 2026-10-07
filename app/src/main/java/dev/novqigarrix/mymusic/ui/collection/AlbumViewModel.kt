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
import dev.novqigarrix.mymusic.data.MusicRepository
import dev.novqigarrix.mymusic.data.PlaySource
import dev.novqigarrix.mymusic.ui.navigation.AlbumRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class AlbumViewModel(savedStateHandle: SavedStateHandle, musicRepository: MusicRepository) : ViewModel() {

    private val albumId = savedStateHandle.toRoute<AlbumRoute>().albumId

    val uiState: StateFlow<CollectionUiState> = musicRepository.state
        .map { state ->
            when (state) {
                LibraryState.Loading -> CollectionUiState.Loading
                is LibraryState.Ready -> state.library.album(albumId)?.let { album ->
                    CollectionUiState.Ready(
                        kind = CollectionKind.AlbumCollection(album),
                        songs = album.songs,
                        source = PlaySource.FromAlbum(album.id),
                    )
                } ?: CollectionUiState.NotFound
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionUiState.Loading)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as MyMusicApplication).container
                AlbumViewModel(createSavedStateHandle(), container.musicRepository)
            }
        }
    }
}
