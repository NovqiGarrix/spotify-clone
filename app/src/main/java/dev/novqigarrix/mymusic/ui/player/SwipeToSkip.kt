package dev.novqigarrix.mymusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val COMMIT_FRACTION = 0.3f
private const val FLING_VELOCITY = 1_200f
private const val EXIT_MS = 140
private const val SWAP_DELAY_MS = 60L
private const val MAX_FADE = 0.8f

/**
 * Swipe the content sideways to change track, like Spotify's mini player and cover art:
 * left for next, right for previous. The content slides out and the new track slides in.
 */
@Composable
fun SwipeToSkip(
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
    canGoPrevious: Boolean = true,
    canGoNext: Boolean = true,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var widthPx by remember { mutableIntStateOf(1) }
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    val allowPrevious by rememberUpdatedState(canGoPrevious)
    val allowNext by rememberUpdatedState(canGoNext)

    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    scope.launch { offset.snapTo(offset.value + delta) }
                },
                onDragStopped = { velocity ->
                    val width = widthPx.toFloat()
                    val direction = when {
                        allowNext && (offset.value < -width * COMMIT_FRACTION || velocity < -FLING_VELOCITY) -> -1
                        allowPrevious && (offset.value > width * COMMIT_FRACTION || velocity > FLING_VELOCITY) -> 1
                        else -> 0
                    }
                    if (direction == 0) {
                        offset.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                    } else {
                        offset.animateTo(direction * width, tween(EXIT_MS))
                        if (direction < 0) next() else previous()
                        offset.snapTo(-direction * width)
                        delay(SWAP_DELAY_MS)
                        offset.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
                    }
                },
            )
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (abs(offset.value) / widthPx).coerceIn(0f, 1f) * MAX_FADE
            },
    ) {
        content()
    }
}
