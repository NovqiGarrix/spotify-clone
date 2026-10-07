package dev.novqigarrix.mymusic.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.ui.app.PlaybackActions
import dev.novqigarrix.mymusic.ui.app.PlaybackInfo
import dev.novqigarrix.mymusic.ui.artwork.Artwork
import dev.novqigarrix.mymusic.ui.components.MediaListRow
import dev.novqigarrix.mymusic.ui.components.PillChip
import dev.novqigarrix.mymusic.ui.components.ProfileAvatar
import dev.novqigarrix.mymusic.ui.components.SongRow
import dev.novqigarrix.mymusic.ui.components.displayArtist
import dev.novqigarrix.mymusic.ui.components.displayName
import dev.novqigarrix.mymusic.ui.components.displayTitle
import dev.novqigarrix.mymusic.ui.navigation.Navigator
import dev.novqigarrix.mymusic.ui.theme.MusicTheme
import dev.novqigarrix.mymusic.ui.theme.Sizes
import dev.novqigarrix.mymusic.ui.theme.Spacing
import kotlinx.coroutines.flow.collectLatest

private const val BROWSE_COLUMNS = 2

@Composable
fun SearchScreenRoute(
    viewModel: SearchViewModel,
    playback: PlaybackInfo,
    actions: PlaybackActions,
    navigator: Navigator,
    contentPadding: PaddingValues,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val textState = rememberTextFieldState()
    LaunchedEffect(textState) {
        snapshotFlow { textState.text.toString() }.collectLatest(viewModel::onQueryChange)
    }
    SearchScreen(
        uiState = uiState,
        textState = textState,
        playback = playback,
        actions = actions,
        navigator = navigator,
        onFilterSelected = viewModel::onFilterSelected,
        contentPadding = contentPadding,
    )
}

@Composable
fun SearchScreen(
    uiState: SearchUiState,
    textState: TextFieldState,
    playback: PlaybackInfo,
    actions: PlaybackActions,
    navigator: Navigator,
    onFilterSelected: (ResultFilter) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            ProfileAvatar()
            Text(text = stringResource(R.string.search_title), style = MaterialTheme.typography.headlineMedium)
        }
        SearchBar(state = textState, modifier = Modifier.padding(horizontal = Spacing.screen))

        AnimatedContent(
            targetState = uiState.isSearching,
            transitionSpec = { fadeIn().togetherWith(fadeOut()) },
            label = "searchContent",
            modifier = Modifier.fillMaxSize(),
        ) { searching ->
            if (searching) {
                SearchResults(uiState, playback, actions, navigator, onFilterSelected, contentPadding)
            } else {
                BrowseGrid(uiState, navigator, contentPadding)
            }
        }
    }
}

@Composable
private fun BrowseGrid(uiState: SearchUiState, navigator: Navigator, contentPadding: PaddingValues) {
    val palette = MusicTheme.colors.browsePalette
    LazyVerticalGrid(
        columns = GridCells.Fixed(BROWSE_COLUMNS),
        contentPadding = PaddingValues(
            start = Spacing.screen,
            end = Spacing.screen,
            bottom = contentPadding.calculateBottomPadding() + Spacing.xl,
        ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "browseHeader", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = stringResource(R.string.search_browse_all),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.xs),
            )
        }
        itemsIndexed(uiState.browse, key = { _, artist -> artist.id }) { index, artist ->
            BrowseCard(
                title = artist.displayName(),
                color = palette[index % palette.size],
                artworkUri = artist.artworkUri,
                onClick = { navigator.openArtist(artist.id) },
            )
        }
    }
}

@Composable
private fun SearchResults(
    uiState: SearchUiState,
    playback: PlaybackInfo,
    actions: PlaybackActions,
    navigator: Navigator,
    onFilterSelected: (ResultFilter) -> Unit,
    contentPadding: PaddingValues,
) {
    val songResults = remember(uiState.results) {
        uiState.results.filterIsInstance<SearchResult.SongResult>().map { it.song }
    }
    LazyColumn(
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + Spacing.xl),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "filters") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                items(ResultFilter.entries.drop(1), key = { it.name }) { filter ->
                    PillChip(
                        label = stringResource(filter.labelRes()),
                        selected = uiState.filter == filter,
                        onClick = { onFilterSelected(filter) },
                    )
                }
            }
        }
        if (uiState.results.isEmpty()) {
            item(key = "noResults") { NoResults(uiState.query) }
        }
        items(uiState.results, key = { it.key }, contentType = { it::class }) { result ->
            when (result) {
                is SearchResult.SongResult -> {
                    val song = result.song
                    SongRow(
                        title = song.title,
                        subtitle = stringResource(R.string.collection_meta, stringResource(R.string.type_song), song.displayArtist()),
                        isCurrent = song.id == playback.currentSongId,
                        isPlaying = playback.isPlaying,
                        isLiked = song.id in playback.likedIds,
                        onClick = { actions.play(songResults, songResults.indexOf(song), null) },
                        onMoreClick = { actions.showSongOptions(song) },
                        artworkUri = song.albumArtUri,
                        modifier = Modifier.animateItem(),
                    )
                }
                is SearchResult.AlbumResult -> MediaListRow(
                    title = result.album.displayTitle(),
                    subtitle = stringResource(R.string.collection_meta, stringResource(R.string.type_album), result.album.displayArtist()),
                    onClick = { navigator.openAlbum(result.album.id) },
                    artworkSize = Sizes.rowArtwork,
                    modifier = Modifier.animateItem(),
                    artwork = { m -> Artwork(uri = result.album.artworkUri, contentDescription = null, modifier = m) },
                )
                is SearchResult.ArtistResult -> MediaListRow(
                    title = result.artist.displayName(),
                    subtitle = stringResource(R.string.type_artist),
                    onClick = { navigator.openArtist(result.artist.id) },
                    artworkSize = Sizes.rowArtwork,
                    modifier = Modifier.animateItem(),
                    artwork = { m ->
                        Artwork(
                            uri = result.artist.artworkUri,
                            contentDescription = null,
                            shape = CircleShape,
                            placeholderIcon = Icons.Outlined.Person,
                            modifier = m,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun NoResults(query: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xxl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        Text(
            text = stringResource(R.string.search_no_results, query),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.search_no_results_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun ResultFilter.labelRes(): Int = when (this) {
    ResultFilter.All -> R.string.home_chip_all
    ResultFilter.Songs -> R.string.search_songs
    ResultFilter.Artists -> R.string.search_artists
    ResultFilter.Albums -> R.string.search_albums
}
