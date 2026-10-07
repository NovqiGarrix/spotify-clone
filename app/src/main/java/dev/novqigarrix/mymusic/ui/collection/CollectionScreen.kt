package dev.novqigarrix.mymusic.ui.collection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.data.PlaylistKind
import dev.novqigarrix.mymusic.ui.app.PlaybackActions
import dev.novqigarrix.mymusic.ui.app.PlaybackInfo
import dev.novqigarrix.mymusic.ui.artwork.rememberArtworkColor
import dev.novqigarrix.mymusic.ui.components.CollapsingTopBar
import dev.novqigarrix.mymusic.ui.components.PinnedPlayButton
import dev.novqigarrix.mymusic.ui.components.SongRow
import dev.novqigarrix.mymusic.ui.components.displayAlbum
import dev.novqigarrix.mymusic.ui.components.displayArtist
import dev.novqigarrix.mymusic.ui.components.displayTitle
import dev.novqigarrix.mymusic.ui.navigation.Navigator
import dev.novqigarrix.mymusic.ui.theme.MusicTheme
import dev.novqigarrix.mymusic.ui.theme.Spacing

private const val ACTIONS_KEY = "actions"

/** Stateful entry point shared by album and playlist destinations. */
@Composable
fun CollectionScreenRoute(
    uiStateFlow: StateFlow<CollectionUiState>,
    playback: PlaybackInfo,
    actions: PlaybackActions,
    navigator: Navigator,
    contentPadding: PaddingValues,
) {
    val uiState by uiStateFlow.collectAsStateWithLifecycle()
    when (val state = uiState) {
        CollectionUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        CollectionUiState.NotFound -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleMedium)
        }
        is CollectionUiState.Ready -> CollectionScreen(state, playback, actions, navigator, contentPadding)
    }
}

@Composable
fun CollectionScreen(
    state: CollectionUiState.Ready,
    playback: PlaybackInfo,
    actions: PlaybackActions,
    navigator: Navigator,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val headerColor = collectionColor(state.kind)
    val title = when (val kind = state.kind) {
        is CollectionKind.AlbumCollection -> kind.album.displayTitle()
        is CollectionKind.PlaylistCollection -> stringResource(kind.kind.titleRes())
    }
    val isAlbum = state.kind is CollectionKind.AlbumCollection
    val currentState by rememberUpdatedState(state)

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + Spacing.xl),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header", contentType = "header") {
                CollectionHeader(
                    kind = state.kind,
                    title = title,
                    headerColor = headerColor,
                    listState = listState,
                    onArtistClick = navigator.openArtist,
                )
            }
            item(key = ACTIONS_KEY, contentType = ACTIONS_KEY) {
                CollectionActionRow(
                    isShuffleActive = playback.isSourceShuffled(state.source),
                    isSmartShuffle = playback.isSourceSmartShuffled(state.source),
                    onShuffle = { actions.shufflePlay(currentState.songs, currentState.source) },
                )
            }
            if (state.songs.isEmpty()) {
                item(key = "empty", contentType = "empty") { EmptyPlaylist() }
            }
            itemsIndexed(state.songs, key = { _, song -> song.id }, contentType = { _, _ -> "song" }) { index, song ->
                SongRow(
                    title = song.title,
                    subtitle = if (isAlbum) song.displayArtist() else "${song.displayArtist()} • ${song.displayAlbum()}",
                    isCurrent = song.id == playback.currentSongId,
                    isPlaying = playback.isPlaying,
                    isLiked = song.id in playback.likedIds,
                    onClick = { actions.play(currentState.songs, index, currentState.source) },
                    onMoreClick = { actions.showSongOptions(song) },
                    artworkUri = if (isAlbum) null else song.albumArtUri,
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = "footer", contentType = "footer") {
                CollectionFooter(state)
            }
        }

        CollapsingTopBar(
            title = title,
            color = headerColor,
            listState = listState,
            onBack = navigator.back,
        )

        if (state.songs.isNotEmpty()) {
            PinnedPlayButton(
                listState = listState,
                anchorKey = ACTIONS_KEY,
                isPlaying = playback.isSourcePlaying(state.source),
                onClick = { actions.playOrToggle(currentState.songs, currentState.source) },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
private fun collectionColor(kind: CollectionKind): State<Color> = when (kind) {
    is CollectionKind.AlbumCollection -> rememberArtworkColor(kind.album.artworkUri)
    is CollectionKind.PlaylistCollection -> {
        val gradient = when (kind.kind) {
            PlaylistKind.Liked -> MusicTheme.colors.likedGradient
            PlaylistKind.LocalFiles -> MusicTheme.colors.localFilesGradient
        }
        remember(gradient) { mutableStateOf(gradient.first()) }
    }
}

@Composable
private fun EmptyPlaylist() {
    Column(
        modifier = Modifier
            .padding(horizontal = Spacing.xxl, vertical = Spacing.xxl)
            .padding(top = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.liked_songs_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.liked_songs_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.small),
        )
    }
}

fun PlaylistKind.titleRes(): Int = when (this) {
    PlaylistKind.Liked -> R.string.liked_songs
    PlaylistKind.LocalFiles -> R.string.local_files
}
