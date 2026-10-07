package dev.novqigarrix.mymusic.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.ui.components.formatTrackTime

private val TrackHeight = 4.dp
private val ThumbRadius = 6.dp
private const val DRAG_THUMB_SCALE = 1.5f
private const val INACTIVE_ALPHA = 0.25f

/**
 * Spotify's thin seek bar with elapsed / remaining times. [positionMs] is read only in draw and
 * in the tiny time label so playback ticks never recompose the player.
 */
@Composable
fun SeekBar(
    positionMs: () -> Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val thumbScale by animateFloatAsState(if (dragFraction != null) DRAG_THUMB_SCALE else 1f, label = "thumb")
    val currentDuration by rememberUpdatedState(durationMs)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val active = MaterialTheme.colorScheme.onSurface
    val inactive = active.copy(alpha = INACTIVE_ALPHA)
    val description = stringResource(R.string.seek_position)

    fun fraction(): Float = dragFraction
        ?: if (currentDuration > 0) (positionMs().toFloat() / currentDuration).coerceIn(0f, 1f) else 0f

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        currentOnSeek(((offset.x / size.width).coerceIn(0f, 1f) * currentDuration).toLong())
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragFraction = (it.x / size.width).coerceIn(0f, 1f) },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            dragFraction?.let { currentOnSeek((it * currentDuration).toLong()) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                    )
                }
                .semantics {
                    contentDescription = description
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction(), 0f..1f)
                    setProgress { target ->
                        currentOnSeek((target.coerceIn(0f, 1f) * currentDuration).toLong())
                        true
                    }
                },
        ) {
            val trackHeight = TrackHeight.toPx()
            val top = (size.height - trackHeight) / 2
            val progressX = size.width * fraction()
            val radius = CornerRadius(trackHeight / 2)
            drawRoundRect(inactive, Offset(0f, top), Size(size.width, trackHeight), radius)
            drawRoundRect(active, Offset(0f, top), Size(progressX, trackHeight), radius)
            drawCircle(active, ThumbRadius.toPx() * thumbScale, Offset(progressX, size.height / 2))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            TimeLabel { formatTrackTime(dragFraction?.let { (it * durationMs).toLong() } ?: positionMs()) }
            Spacer(Modifier.weight(1f))
            TimeLabel {
                val elapsed = dragFraction?.let { (it * durationMs).toLong() } ?: positionMs()
                "-" + formatTrackTime(durationMs - elapsed)
            }
        }
    }
}

/** Separate restart scope so only this label recomposes on each position tick. */
@Composable
private fun TimeLabel(text: () -> String) {
    Text(
        text = text(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
