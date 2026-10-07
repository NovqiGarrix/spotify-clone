package dev.novqigarrix.mymusic.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp

object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Horizontal gutter used by every Spotify screen. */
    val screen = 16.dp
}

object Sizes {
    val miniPlayerHeight = 56.dp
    val bottomBarHeight = 64.dp
    val rowArtwork = 48.dp
    val libraryArtwork = 64.dp
    val shortcutHeight = 56.dp
    val carouselCard = 136.dp
    val playButton = 56.dp
    val playButtonSmall = 48.dp
    val avatar = 34.dp
    val headerArtwork = 240.dp
    val playerControlLarge = 68.dp
    val chipHeight = 32.dp
}

object Motion {
    /** Spotify's press feedback: a quick, slightly springy shrink. */
    val pressSpring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    val bouncy = spring<Float>(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow)
    val sheetSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f)
    val colorFade = tween<androidx.compose.ui.graphics.Color>(durationMillis = 700, easing = FastOutSlowInEasing)
    const val PRESSED_SCALE = 0.96f
    const val NAV_DURATION_MS = 280
}
