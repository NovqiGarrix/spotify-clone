package dev.novqigarrix.mymusic.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.data.PlaylistKind
import dev.novqigarrix.mymusic.ui.app.PlaybackInfo
import dev.novqigarrix.mymusic.ui.artwork.rememberArtworkColor
import dev.novqigarrix.mymusic.ui.components.CoverCard
import dev.novqigarrix.mymusic.ui.components.PillChip
import dev.novqigarrix.mymusic.ui.components.ProfileAvatar
import dev.novqigarrix.mymusic.ui.components.SectionHeader
import dev.novqigarrix.mymusic.ui.navigation.Navigator
import dev.novqigarrix.mymusic.ui.theme.MyMusicTheme
import dev.novqigarrix.mymusic.ui.theme.Spacing

private const val SHORTCUT_COLUMNS = 2
private const val TINT_ALPHA = 0.55f

@Composable
fun HomeScreenRoute(
    viewModel: HomeViewModel,
    playback: PlaybackInfo,
    navigator: Navigator,
    contentPadding: PaddingValues,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        playback = playback,
        onFilterSelected = viewModel::onFilterSelected,
        onItemClick = { item -> navigator.open(item) },
        contentPadding = contentPadding,
    )
}

private fun Navigator.open(item: HomeItem) = when (item) {
    is HomeItem.AlbumItem -> openAlbum(item.album.id)
    is HomeItem.ArtistItem -> openArtist(item.artist.id)
    is HomeItem.PlaylistItem -> openPlaylist(item.kind)
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    playback: PlaybackInfo,
    onFilterSelected: (HomeFilter) -> Unit,
    onItemClick: (HomeItem) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val tintUri = (uiState.shortcuts.firstOrNull() as? HomeItem.AlbumItem)?.album?.artworkUri
    val tint = rememberArtworkColor(tintUri, fallback = MaterialTheme.colorScheme.surfaceVariant)
    val background = MaterialTheme.colorScheme.background

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to tint.value.copy(alpha = TINT_ALPHA),
                        0.35f to background,
                    ),
                )
            },
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + Spacing.xl),
    ) {
        item(key = "topBar", contentType = "topBar") {
            HomeTopBar(selected = uiState.filter, onFilterSelected = onFilterSelected)
        }
        when {
            uiState.isLoading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(Spacing.xxl), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            uiState.isEmpty -> item(key = "empty") { HomeEmptyState() }
            else -> {
                item(key = "shortcuts", contentType = "shortcuts") {
                    AnimatedVisibility(
                        visible = uiState.shortcuts.isNotEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        ShortcutGrid(uiState.shortcuts, playback, onItemClick)
                    }
                }
                uiState.sections.forEach { section ->
                    item(key = "header-${section.type}", contentType = "sectionHeader") {
                        SectionHeader(
                            title = stringResource(section.type.titleRes()),
                            modifier = Modifier
                                .padding(top = Spacing.xl, bottom = Spacing.medium)
                                .animateItem(),
                        )
                    }
                    item(key = "row-${section.type}", contentType = "carousel") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Spacing.screen),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                            modifier = Modifier.animateItem(),
                        ) {
                            items(section.items, key = { it.key }, contentType = { it::class }) { item ->
                                CoverCard(
                                    title = item.title(),
                                    subtitle = item.subtitle(),
                                    artworkUri = null,
                                    circular = item is HomeItem.ArtistItem,
                                    onClick = { onItemClick(item) },
                                    artwork = { HomeCarouselArtwork(item) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(selected: HomeFilter, onFilterSelected: (HomeFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = Spacing.screen, vertical = Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        ProfileAvatar(Modifier.padding(end = Spacing.xs))
        PillChip(
            label = stringResource(R.string.home_chip_all),
            selected = selected == HomeFilter.All,
            onClick = { onFilterSelected(HomeFilter.All) },
        )
        PillChip(
            label = stringResource(R.string.home_chip_music),
            selected = selected == HomeFilter.Music,
            onClick = { onFilterSelected(HomeFilter.Music) },
        )
        PillChip(
            label = stringResource(R.string.home_chip_artists),
            selected = selected == HomeFilter.Artists,
            onClick = { onFilterSelected(HomeFilter.Artists) },
        )
    }
}

@Composable
private fun ShortcutGrid(
    shortcuts: List<HomeItem>,
    playback: PlaybackInfo,
    onItemClick: (HomeItem) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        shortcuts.chunked(SHORTCUT_COLUMNS).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                rowItems.forEach { item ->
                    ShortcutTile(
                        item = item,
                        isActive = playback.source == item.source,
                        isPlaying = playback.isPlaying,
                        onClick = { onItemClick(item) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowItems.size < SHORTCUT_COLUMNS) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HomeEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xxl, vertical = Spacing.xxl * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.small))
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun HomeSectionType.titleRes(): Int = when (this) {
    HomeSectionType.JumpBackIn -> R.string.home_jump_back_in
    HomeSectionType.RecentlyAdded -> R.string.home_recently_added
    HomeSectionType.YourArtists -> R.string.home_your_top_artists
    HomeSectionType.AlbumsForYou -> R.string.home_albums_for_you
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun HomeScreenPreview() {
    MyMusicTheme {
        HomeScreen(
            uiState = HomeUiState(
                isLoading = false,
                shortcuts = listOf(
                    HomeItem.PlaylistItem(PlaylistKind.Liked, 42),
                    HomeItem.PlaylistItem(PlaylistKind.LocalFiles, 318),
                ),
            ),
            playback = PlaybackInfo(),
            onFilterSelected = {},
            onItemClick = {},
            contentPadding = PaddingValues(),
        )
    }
}
