package dev.novqigarrix.mymusic.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.ui.theme.Motion
import kotlinx.coroutines.launch

private const val POP_SCALE = 1.3f

/** Spotify's "add to Liked Songs" toggle: outlined plus that pops into a green check. */
@Composable
fun LikeButton(
    isLiked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 26.dp,
    idleTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    IconButton(
        onClick = {
            if (!isLiked) {
                scope.launch {
                    scale.snapTo(0.6f)
                    scale.animateTo(1f, Motion.bouncy)
                }
            }
            onToggle()
        },
        modifier = modifier,
    ) {
        AnimatedContent(
            targetState = isLiked,
            transitionSpec = { fadeIn().togetherWith(fadeOut()) },
            label = "likeIcon",
        ) { liked ->
            Icon(
                imageVector = if (liked) Icons.Rounded.CheckCircle else Icons.Outlined.AddCircleOutline,
                contentDescription = stringResource(if (liked) R.string.unlike else R.string.like),
                tint = if (liked) MaterialTheme.colorScheme.primary else idleTint,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        scaleX = scale.value.coerceAtMost(POP_SCALE)
                        scaleY = scale.value.coerceAtMost(POP_SCALE)
                    },
            )
        }
    }
}
