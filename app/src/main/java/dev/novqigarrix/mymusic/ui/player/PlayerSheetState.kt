package dev.novqigarrix.mymusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import dev.novqigarrix.mymusic.ui.theme.Motion

private const val FLING_VELOCITY = 1.6f
private const val SETTLE_THRESHOLD = 0.5f

/**
 * Drives the mini player → full player transition. [progress] is 0 when collapsed and 1 when the
 * Now Playing screen fully covers the app; drags move it directly, releases spring to an end.
 */
@Stable
class PlayerSheetState(initiallyExpanded: Boolean) {
    val progress = Animatable(if (initiallyExpanded) 1f else 0f)

    val isExpanded: Boolean get() = progress.targetValue == 1f

    suspend fun expand() = progress.animateTo(1f, Motion.sheetSpring)

    suspend fun collapse() = progress.animateTo(0f, Motion.sheetSpring)

    /** [deltaFraction] is the drag distance as a fraction of the screen height (+ = towards expanded). */
    suspend fun dragBy(deltaFraction: Float) {
        progress.snapTo((progress.value + deltaFraction).coerceIn(0f, 1f))
    }

    /** [velocityFraction] is in screen-heights per second (+ = towards expanded). */
    suspend fun settle(velocityFraction: Float) {
        val target = when {
            velocityFraction > FLING_VELOCITY -> 1f
            velocityFraction < -FLING_VELOCITY -> 0f
            progress.value > SETTLE_THRESHOLD -> 1f
            else -> 0f
        }
        progress.animateTo(target, Motion.sheetSpring, initialVelocity = velocityFraction)
    }

    companion object {
        val Saver = Saver<PlayerSheetState, Boolean>(
            save = { it.isExpanded },
            restore = { PlayerSheetState(it) },
        )
    }
}

@Composable
fun rememberPlayerSheetState(): PlayerSheetState =
    rememberSaveable(saver = PlayerSheetState.Saver) { PlayerSheetState(initiallyExpanded = false) }
