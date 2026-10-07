package dev.novqigarrix.mymusic.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.ui.theme.MyMusicTheme
import dev.novqigarrix.mymusic.ui.theme.Sizes

private const val ICON_FRACTION = 0.55f
private const val ICON_SWAP_MS = 160

/** The round play/pause button: green on collection pages, white in the full player. */
@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.playButton,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val label = stringResource(if (isPlaying) R.string.pause else R.string.play)
    Box(
        modifier = modifier
            .size(size)
            .bounceClick(onClickLabel = label, pressedScale = 0.92f, onClick = onClick)
            .background(containerColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                (scaleIn(tween(ICON_SWAP_MS), initialScale = 0.6f) + fadeIn(tween(ICON_SWAP_MS)))
                    .togetherWith(scaleOut(tween(ICON_SWAP_MS), targetScale = 0.6f) + fadeOut(tween(ICON_SWAP_MS)))
            },
            label = "playPauseIcon",
            modifier = Modifier.fillMaxSize(ICON_FRACTION),
        ) { playing ->
            Icon(
                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview
@Composable
private fun PlayPauseButtonPreview() {
    MyMusicTheme { PlayPauseButton(isPlaying = false, onClick = {}) }
}
