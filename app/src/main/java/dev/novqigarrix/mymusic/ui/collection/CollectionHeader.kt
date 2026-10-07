package dev.novqigarrix.mymusic.ui.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.ui.artwork.Artwork
import dev.novqigarrix.mymusic.ui.artwork.ArtworkSize
import dev.novqigarrix.mymusic.ui.components.PlaylistCover
import dev.novqigarrix.mymusic.ui.components.ProfileAvatar
import dev.novqigarrix.mymusic.ui.components.TopBarHeight
import dev.novqigarrix.mymusic.ui.components.bounceClick
import dev.novqigarrix.mymusic.ui.components.displayArtist
import dev.novqigarrix.mymusic.ui.components.formatCollectionDuration
import dev.novqigarrix.mymusic.ui.components.headerCollapseFraction
import dev.novqigarrix.mymusic.ui.components.headerScrollPx
import dev.novqigarrix.mymusic.ui.theme.Sizes
import dev.novqigarrix.mymusic.ui.theme.Spacing

private const val ARTWORK_MIN_SCALE = 0.7f
private const val ARTWORK_FADE_SPEED = 1.4f
private const val ARTWORK_PARALLAX = 0.45f

/** Gradient header with the big cover that shrinks, fades and lags behind the scroll. */
@Composable
fun CollectionHeader(
    kind: CollectionKind,
    title: String,
    headerColor: State<Color>,
    listState: LazyListState,
    onArtistClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.background
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(headerColor.value, background)))
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = TopBarHeight)
            .padding(horizontal = Spacing.screen),
    ) {
        val coverModifier = Modifier
            .align(Alignment.CenterHorizontally)
            .size(Sizes.headerArtwork)
            .graphicsLayer {
                val fraction = listState.headerCollapseFraction()
                val scale = 1f - (1f - ARTWORK_MIN_SCALE) * fraction
                scaleX = scale
                scaleY = scale
                alpha = (1f - fraction * ARTWORK_FADE_SPEED).coerceIn(0f, 1f)
                val scrolled = listState.headerScrollPx()
                translationY = if (scrolled == Int.MAX_VALUE) 0f else scrolled * ARTWORK_PARALLAX
            }
            .shadow(elevation = 24.dp, shape = RoundedCornerShape(4.dp))
        when (kind) {
            is CollectionKind.AlbumCollection -> Artwork(
                uri = kind.album.artworkUri,
                contentDescription = stringResource(R.string.artwork_of, title),
                size = ArtworkSize.Large,
                modifier = coverModifier,
            )
            is CollectionKind.PlaylistCollection -> PlaylistCover(kind = kind.kind, modifier = coverModifier)
        }
        Spacer(Modifier.height(Spacing.large))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.small))
        when (kind) {
            is CollectionKind.AlbumCollection -> {
                val album = kind.album
                Row(
                    modifier = Modifier.bounceClick { onArtistClick(album.artistId) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                ) {
                    Artwork(
                        uri = album.artworkUri,
                        contentDescription = null,
                        shape = CircleShape,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = album.displayArtist(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = if (album.year > 0) {
                        stringResource(R.string.album_subtitle, album.year.toString())
                    } else {
                        stringResource(R.string.type_album)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is CollectionKind.PlaylistCollection -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                ) {
                    ProfileAvatar(Modifier.size(24.dp))
                    Text(
                        text = stringResource(R.string.by_you),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

@Composable
fun CollectionFooter(state: CollectionUiState.Ready, modifier: Modifier = Modifier) {
    if (state.songs.isEmpty()) return
    Column(
        modifier = modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = stringResource(
                R.string.collection_meta,
                pluralStringResource(R.plurals.song_count, state.songs.size, state.songs.size),
                formatCollectionDuration(state.totalDurationMs),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
