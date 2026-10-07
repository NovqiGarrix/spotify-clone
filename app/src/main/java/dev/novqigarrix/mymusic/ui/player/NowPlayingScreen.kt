package dev.novqigarrix.mymusic.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.data.PlaylistKind
import dev.novqigarrix.mymusic.data.Song
import dev.novqigarrix.mymusic.playback.RepeatMode
import dev.novqigarrix.mymusic.ui.artwork.Artwork
import dev.novqigarrix.mymusic.ui.artwork.ArtworkSize
import dev.novqigarrix.mymusic.ui.artwork.LocalArtworkLoader
import dev.novqigarrix.mymusic.ui.artwork.rememberArtworkColor
import dev.novqigarrix.mymusic.ui.collection.ShuffleToggle
import dev.novqigarrix.mymusic.ui.collection.titleRes
import dev.novqigarrix.mymusic.ui.components.LikeButton
import dev.novqigarrix.mymusic.ui.components.PlayPauseButton
import dev.novqigarrix.mymusic.ui.components.bounceClick
import dev.novqigarrix.mymusic.ui.components.displayArtist
import dev.novqigarrix.mymusic.ui.theme.Sizes
import dev.novqigarrix.mymusic.ui.theme.Spacing

private const val GRADIENT_BOTTOM_DARKEN = 0.82f
private const val ARTWORK_CROSSFADE_MS = 350

/** Everything the full player can ask the app to do. */
class NowPlayingCallbacks(
    val onCollapse: () -> Unit,
    val onTogglePlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onToggleShuffle: () -> Unit,
    val onCycleRepeat: () -> Unit,
    val onToggleLike: () -> Unit,
    val onOpenQueue: () -> Unit,
    val onShare: (Song) -> Unit,
    val onMore: (Song) -> Unit,
    val onOpenArtist: (Long) -> Unit,
)

/** Spotify's full-screen player on a gradient taken from the cover. */
@Composable
fun NowPlayingScreen(
    song: Song,
    state: PlayerUiState,
    progressMs: () -> Long,
    callbacks: NowPlayingCallbacks,
    modifier: Modifier = Modifier,
) {
    val tint = rememberArtworkColor(song.albumArtUri)
    val black = Color.Black
    PrefetchNextArtwork(state.upNext.firstOrNull()?.song)

    Column(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(tint.value, lerp(tint.value, black, GRADIENT_BOTTOM_DARKEN)),
                    ),
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Spacing.xl),
    ) {
        PlayerTopBar(
            label = state.sourceLabel,
            onCollapse = callbacks.onCollapse,
            onMore = { callbacks.onMore(song) },
        )
        Spacer(Modifier.weight(1f))
        SwipeToSkip(
            onNext = callbacks.onNext,
            onPrevious = callbacks.onPrevious,
            canGoNext = state.nextSongId != null,
        ) {
            Crossfade(
                targetState = song.albumArtUri,
                animationSpec = tween(ARTWORK_CROSSFADE_MS),
                label = "artwork",
            ) { uri ->
                Artwork(
                    uri = uri,
                    contentDescription = stringResource(R.string.artwork_of, song.title),
                    size = ArtworkSize.Large,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shadow(elevation = 24.dp, shape = RoundedCornerShape(8.dp)),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
                Text(
                    text = song.displayArtist(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.bounceClick { callbacks.onOpenArtist(song.artistId) },
                )
            }
            LikeButton(
                isLiked = state.isLiked,
                onToggle = callbacks.onToggleLike,
                iconSize = 28.dp,
                idleTint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(Spacing.medium))
        SeekBar(positionMs = progressMs, durationMs = state.durationMs, onSeek = callbacks.onSeek)
        Spacer(Modifier.height(Spacing.small))
        PlayerControls(state = state, callbacks = callbacks)
        Spacer(Modifier.height(Spacing.large))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Devices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.this_device),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Spacing.small),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { callbacks.onShare(song) }) {
                Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.share), modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = callbacks.onOpenQueue) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = stringResource(R.string.queue))
            }
        }
        Spacer(Modifier.height(Spacing.large))
    }
}

@Composable
private fun PlayerTopBar(label: SourceLabel?, onCollapse: () -> Unit, onMore: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCollapse, modifier = Modifier.offset(x = -Spacing.medium)) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.collapse_player),
                modifier = Modifier.size(32.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(label.headerRes()).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            val title = when (label) {
                is SourceLabel.Album -> label.title
                is SourceLabel.Artist -> label.name
                is SourceLabel.Playlist -> stringResource(label.kind.titleRes())
                null -> null
            }
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        IconButton(onClick = onMore, modifier = Modifier.offset(x = Spacing.medium)) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
    }
}

@Composable
private fun PlayerControls(state: PlayerUiState, callbacks: NowPlayingCallbacks) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ShuffleToggle(
            isActive = state.shuffleEnabled,
            isSmart = state.smartShuffle,
            onClick = callbacks.onToggleShuffle,
            modifier = Modifier.offset(x = -Spacing.medium),
        )
        IconButton(onClick = callbacks.onPrevious, modifier = Modifier.size(56.dp)) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(R.string.previous),
                modifier = Modifier.size(44.dp),
            )
        }
        PlayPauseButton(
            isPlaying = state.playWhenReady,
            onClick = callbacks.onTogglePlayPause,
            size = Sizes.playerControlLarge,
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        )
        IconButton(onClick = callbacks.onNext, modifier = Modifier.size(56.dp)) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.next),
                modifier = Modifier.size(44.dp),
            )
        }
        RepeatToggle(mode = state.repeatMode, onClick = callbacks.onCycleRepeat, modifier = Modifier.offset(x = Spacing.medium))
    }
}

@Composable
private fun RepeatToggle(mode: RepeatMode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val active = mode != RepeatMode.Off
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val description = stringResource(
        when (mode) {
            RepeatMode.Off -> R.string.repeat_off
            RepeatMode.All -> R.string.repeat_all
            RepeatMode.One -> R.string.repeat_one
        },
    )
    IconButton(onClick = onClick, modifier = modifier) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (mode == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = description,
                tint = tint,
                modifier = Modifier.size(28.dp),
            )
            if (active) {
                Box(
                    modifier = Modifier
                        .offset(y = 18.dp)
                        .size(4.dp)
                        .background(tint, CircleShape),
                )
            }
        }
    }
}

/** Warms the cache so the next cover is already decoded when the track changes. */
@Composable
private fun PrefetchNextArtwork(next: Song?) {
    val loader = LocalArtworkLoader.current
    LaunchedEffect(next?.albumArtUri, loader) {
        val uri = next?.albumArtUri ?: return@LaunchedEffect
        loader?.load(uri, ArtworkSize.Large)
        loader?.dominantColor(uri)
    }
}

private fun SourceLabel?.headerRes(): Int = when (this) {
    is SourceLabel.Album -> R.string.playing_from_album
    is SourceLabel.Artist -> R.string.playing_from_artist
    is SourceLabel.Playlist -> if (kind == PlaylistKind.Liked) R.string.playing_from_playlist else R.string.playing_from_library
    null -> R.string.now_playing
}
