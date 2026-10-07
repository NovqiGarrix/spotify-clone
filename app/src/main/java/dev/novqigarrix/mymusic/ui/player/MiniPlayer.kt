package dev.novqigarrix.mymusic.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.data.Song
import dev.novqigarrix.mymusic.ui.artwork.Artwork
import dev.novqigarrix.mymusic.ui.artwork.rememberArtworkColor
import dev.novqigarrix.mymusic.ui.components.LikeButton
import dev.novqigarrix.mymusic.ui.components.bounceClick
import dev.novqigarrix.mymusic.ui.components.displayArtist
import dev.novqigarrix.mymusic.ui.theme.Sizes
import dev.novqigarrix.mymusic.ui.theme.Spacing

private const val BACKGROUND_DARKEN = 0.35f
private val ProgressHeight = 2.dp
private val MiniShape = RoundedCornerShape(6.dp)

/**
 * The floating "now playing" bar: tinted with the cover's color, a hairline progress bar along
 * the bottom, swipe sideways to skip. Tap or drag up opens the full player (drag handled by parent).
 */
@Composable
fun MiniPlayer(
    song: Song,
    state: PlayerUiState,
    progressMs: () -> Long,
    onOpen: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleLike: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = rememberArtworkColor(song.albumArtUri)
    val black = Color.Black
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val progressColor = MaterialTheme.colorScheme.onSurface
    val artist = song.displayArtist()

    Row(
        modifier = modifier
            .padding(horizontal = Spacing.small)
            .fillMaxWidth()
            .height(Sizes.miniPlayerHeight)
            .bounceClick(
                onClickLabel = stringResource(R.string.open_player, song.title, artist),
                pressedScale = 0.985f,
                onClick = onOpen,
            )
            .clip(MiniShape)
            .drawBehind {
                drawRect(lerp(tint.value, black, BACKGROUND_DARKEN))
                val barHeight = ProgressHeight.toPx()
                val inset = Spacing.small.toPx()
                val width = size.width - inset * 2
                val fraction = if (state.durationMs > 0) {
                    (progressMs().toFloat() / state.durationMs).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val top = size.height - barHeight
                drawRect(track, Offset(inset, top), Size(width, barHeight))
                drawRect(progressColor, Offset(inset, top), Size(width * fraction, barHeight))
            }
            .padding(start = Spacing.small, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SwipeToSkip(
            onNext = onNext,
            onPrevious = onPrevious,
            canGoNext = state.nextSongId != null,
            modifier = Modifier.weight(1f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.small + Spacing.xxs),
            ) {
                Artwork(
                    uri = song.albumArtUri,
                    contentDescription = null,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.size(40.dp),
                )
                Column {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Icon(
            imageVector = Icons.Outlined.Devices,
            contentDescription = stringResource(R.string.devices),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = Spacing.small)
                .size(22.dp),
        )
        LikeButton(isLiked = state.isLiked, onToggle = onToggleLike, iconSize = 24.dp, idleTint = MaterialTheme.colorScheme.onSurface)
        IconButton(onClick = onTogglePlayPause) {
            Icon(
                imageVector = if (state.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(if (state.playWhenReady) R.string.pause else R.string.play),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}
