package dev.novqigarrix.mymusic.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.ui.app.PlaybackInfo
import dev.novqigarrix.mymusic.ui.components.PillChip
import dev.novqigarrix.mymusic.ui.components.ProfileAvatar
import dev.novqigarrix.mymusic.ui.components.bounceClick
import dev.novqigarrix.mymusic.ui.navigation.Navigator
import dev.novqigarrix.mymusic.ui.theme.Sizes
import dev.novqigarrix.mymusic.ui.theme.Spacing

private const val GRID_COLUMNS = 3

@Composable
fun LibraryScreenRoute(
    viewModel: LibraryViewModel,
    playback: PlaybackInfo,
    navigator: Navigator,
    contentPadding: PaddingValues,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(
        uiState = uiState,
        playback = playback,
        onFilterSelected = viewModel::onFilterSelected,
        onSortSelected = viewModel::onSortSelected,
        onToggleViewMode = viewModel::onToggleViewMode,
        onEntryClick = { entry ->
            when (entry) {
                is LibraryEntry.PlaylistEntry -> navigator.openPlaylist(entry.kind)
                is LibraryEntry.AlbumEntry -> navigator.openAlbum(entry.album.id)
                is LibraryEntry.ArtistEntry -> navigator.openArtist(entry.artist.id)
            }
        },
        contentPadding = contentPadding,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    playback: PlaybackInfo,
    onFilterSelected: (LibraryFilter?) -> Unit,
    onSortSelected: (LibrarySort) -> Unit,
    onToggleViewMode: () -> Unit,
    onEntryClick: (LibraryEntry) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    val bottomPadding = contentPadding.calculateBottomPadding() + Spacing.xl

    Column(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            modifier = Modifier.padding(start = Spacing.screen, end = Spacing.xs, top = Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            ProfileAvatar()
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
        }
        FilterRow(selected = uiState.filter, onFilterSelected = onFilterSelected)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.screen, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.bounceClick(onClickLabel = stringResource(R.string.library_sort_by)) {
                    showSortSheet = true
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Icon(Icons.Rounded.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text = stringResource(uiState.sort.labelRes()), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggleViewMode) {
                val isList = uiState.viewMode == LibraryViewMode.List
                Icon(
                    imageVector = if (isList) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.List,
                    contentDescription = stringResource(if (isList) R.string.library_grid_view else R.string.library_list_view),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        AnimatedContent(
            targetState = uiState.viewMode,
            transitionSpec = { fadeIn().togetherWith(fadeOut()) },
            label = "libraryViewMode",
        ) { mode ->
            when (mode) {
                LibraryViewMode.List -> LazyColumn(
                    contentPadding = PaddingValues(top = Spacing.xs, bottom = bottomPadding),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(uiState.entries, key = { it.key }, contentType = { it::class }) { entry ->
                        LibraryEntryRow(
                            entry = entry,
                            isActive = playback.source?.key == entry.key,
                            onClick = { onEntryClick(entry) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                LibraryViewMode.Grid -> LazyVerticalGrid(
                    columns = GridCells.Fixed(GRID_COLUMNS),
                    contentPadding = PaddingValues(
                        start = Spacing.screen,
                        end = Spacing.screen,
                        top = Spacing.small,
                        bottom = bottomPadding,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(Spacing.large),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(uiState.entries, key = { it.key }, contentType = { it::class }) { entry ->
                        LibraryEntryCard(
                            entry = entry,
                            onClick = { onEntryClick(entry) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = stringResource(R.string.library_sort_by),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.medium),
            )
            LibrarySort.entries.forEach { sort ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick(pressedScale = 0.98f) {
                            onSortSelected(sort)
                            showSortSheet = false
                        }
                        .padding(horizontal = Spacing.xl, vertical = Spacing.large),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val selected = sort == uiState.sort
                    Text(
                        text = stringResource(sort.labelRes()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.size(Spacing.xl))
        }
    }
}

/** Spotify's library filters: picking one collapses the others and reveals a round ✕ chip. */
@Composable
private fun FilterRow(selected: LibraryFilter?, onFilterSelected: (LibraryFilter?) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = selected != null,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            Box(
                modifier = Modifier
                    .padding(end = Spacing.small)
                    .size(Sizes.chipHeight)
                    .bounceClick(onClickLabel = stringResource(R.string.library_clear_filter)) { onFilterSelected(null) }
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.library_clear_filter), modifier = Modifier.size(18.dp))
            }
        }
        LibraryFilter.entries.forEach { filter ->
            AnimatedVisibility(
                visible = selected == null || selected == filter,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                PillChip(
                    label = stringResource(filter.labelRes()),
                    selected = selected == filter,
                    onClick = { onFilterSelected(filter) },
                    modifier = Modifier.padding(end = Spacing.small),
                )
            }
        }
    }
}

private fun LibraryFilter.labelRes(): Int = when (this) {
    LibraryFilter.Playlists -> R.string.library_filter_playlists
    LibraryFilter.Albums -> R.string.library_filter_albums
    LibraryFilter.Artists -> R.string.library_filter_artists
}

private fun LibrarySort.labelRes(): Int = when (this) {
    LibrarySort.Recents -> R.string.library_sort_recents
    LibrarySort.Alphabetical -> R.string.library_sort_alphabetical
    LibrarySort.Creator -> R.string.library_sort_creator
}
