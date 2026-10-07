package dev.novqigarrix.mymusic.ui.components

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val BarDurations = intArrayOf(420, 560, 360)
private const val IDLE_HEIGHT = 0.3f
private const val BAR_GAP_FRACTION = 0.5f

/** The animated green bars Spotify shows next to the track that is currently playing. */
@Composable
fun EqualizerBars(
    isAnimating: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val heights: List<State<Float>> = BarDurations.map { duration ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = bar(duration),
            label = "bar",
        )
    }
    Canvas(modifier = modifier.size(14.dp)) {
        val barCount = heights.size
        val barWidth = size.width / (barCount + (barCount - 1) * BAR_GAP_FRACTION)
        val gap = barWidth * BAR_GAP_FRACTION
        heights.forEachIndexed { index, height ->
            val fraction = if (isAnimating) height.value else IDLE_HEIGHT + index * 0.15f
            val barHeight = size.height * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 4),
            )
        }
    }
}

private fun bar(duration: Int): InfiniteRepeatableSpec<Float> =
    infiniteRepeatable(tween(duration, easing = LinearEasing), RepeatMode.Reverse)
