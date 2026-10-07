package dev.novqigarrix.mymusic.ui.artist

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.ui.app.PlaybackActions
import dev.novqigarrix.mymusic.ui.app.PlaybackInfo
import dev.novqigarrix.mymusic.ui.artwork.Artwork
import dev.novqigarrix.mymusic.ui.artwork.ArtworkSize
import dev.novqigarrix.mymusic.ui.artwork.rememberArtworkColor
import dev.novqigarrix.mymusic.ui.collection.ShuffleToggle
import dev.novqigarrix.mymusic.ui.components.CollapsingTopBar
import dev.novqigarrix.mymusic.ui.components.MediaListRow
import dev.novqigarrix.mymusic.ui.components.PinnedPlayButton
import dev.novqigarrix.mymusic.ui.components.SectionHeader
import dev.novqigarrix.mymusic.ui.components.SongRow
import dev.novqigarrix.mymusic.ui.components.displayAlbum
import dev.novqigarrix.mymusic.ui.components.displayName
import dev.novqigarrix.mymusic.ui.components.displayTitle
import dev.novqigarrix.mymusic.ui.components.headerCollapseFraction
import dev.novqigarrix.mymusic.ui.components.headerScrollPx
import dev.novqigarrix.mymusic.ui.navigation.Navigator
import dev.novqigarrix.mymusic.ui.theme.Sizes
import dev.novqigarrix.mymusic.ui.theme.Spacing

private const val ACTIONS_KEY = "actions"
private val HeroHeight = 360.dp
private const val HERO_PARALLAX = 0.5f

@Composable
fun ArtistScreenRoute(
    viewModel: ArtistViewModel,
    playback: PlaybackInfo,
    actions: PlaybackActions,
    navigator: Navigator,
    contentPadding: PaddingValues,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    when (val state = uiState) {
        ArtistUiState.Loading, ArtistUiState.NotFound -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state is ArtistUiState.Loading) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        is ArtistUiState.Ready -> ArtistScreen(state, playback, actions, navigator, contentPadding)
    }
}

@Composable
fun ArtistScreen(
    state: ArtistUiState.Ready,
    playback: PlaybackInfo,
    actions: PlaybackActions,
    navigator: Navigator,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val artist = state.artist
    val color = rememberArtworkColor(artist.artworkUri)
    val name = artist.displayName()
    val current by rememberUpdatedState(state)

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + Spacing.xl),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "hero", contentType = "hero") {
                ArtistHero(name = name, artworkUri = artist.artworkUri, listState = listState)
            }
            item(key = ACTIONS_KEY, contentType = ACTIONS_KEY) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(start = Spacing.screen, end = Spacing.screen),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            R.string.artist_local_listeners,
                            pluralStringResource(R.plurals.song_count, artist.songs.size, artist.songs.size),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    ShuffleToggle(
                        isActive = playback.isSourceShuffled(state.source),
                        isSmart = playback.isSourceSmartShuffled(state.source),
                        onClick = { actions.shufflePlay(current.artist.songs, current.source) },
                    )
                    Spacer(Modifier.width(Spacing.small + Sizes.playButton))
                }
            }
            item(key = "popularHeader", contentType = "sectionHeader") {
                SectionHeader(
                    title = stringResource(R.string.popular),
                    modifier = Modifier.padding(top = Spacing.small, bottom = Spacing.small),
                )
            }
            items(state.popular, key = { "popular-${it.id}" }, contentType = { "song" }) { song ->
                SongRow(
                    title = song.title,
                    subtitle = song.displayAlbum(),
                    isCurrent = song.id == playback.currentSongId,
                    isPlaying = playback.isPlaying,
                    isLiked = song.id in playback.likedIds,
                    onClick = {
                        val songs = current.artist.songs
                        actions.play(songs, songs.indexOf(song).coerceAtLeast(0), current.source)
                    },
                    onMoreClick = { actions.showSongOptions(song) },
                    artworkUri = song.albumArtUri,
                )
            }
            if (artist.albums.isNotEmpty()) {
                item(key = "discographyHeader", contentType = "sectionHeader") {
                    SectionHeader(
                        title = stringResource(R.string.discography),
                        modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.small),
                    )
                }
                items(artist.albums, key = { "album-${it.id}" }, contentType = { "album" }) { album ->
                    MediaListRow(
                        title = album.displayTitle(),
                        subtitle = if (album.year > 0) {
                            stringResource(R.string.collection_meta, album.year.toString(), stringResource(R.string.type_album))
                        } else {
                            stringResource(R.string.type_album)
                        },
                        onClick = { navigator.openAlbum(album.id) },
                        artworkSize = 72.dp,
                        artwork = { m -> Artwork(uri = album.artworkUri, contentDescription = null, modifier = m) },
                    )
                }
            }
        }

        CollapsingTopBar(
            title = name,
            color = color,
            listState = listState,
            onBack = navigator.back,
            showBackScrim = true,
        )
        PinnedPlayButton(
            listState = listState,
            anchorKey = ACTIONS_KEY,
            isPlaying = playback.isSourcePlaying(state.source),
            onClick = { actions.playOrToggle(current.artist.songs, current.source) },
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

/** Full-bleed artist image with the name over a fade, parallaxing away on scroll. */
@Composable
private fun ArtistHero(name: String, artworkUri: Uri?, listState: LazyListState) {
    val background = MaterialTheme.colorScheme.background
    val statusBar = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeroHeight + statusBar),
    ) {
        Artwork(
            uri = artworkUri,
            contentDescription = null,
            size = ArtworkSize.Large,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scrolled = listState.headerScrollPx()
                    translationY = if (scrolled == Int.MAX_VALUE) 0f else scrolled * HERO_PARALLAX
                    alpha = 1f - listState.headerCollapseFraction()
                },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to background)),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = Spacing.screen, vertical = Spacing.small)
                .graphicsLayer { alpha = 1f - listState.headerCollapseFraction() },
        )
    }
}
