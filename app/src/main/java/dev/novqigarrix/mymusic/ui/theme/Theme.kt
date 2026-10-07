package dev.novqigarrix.mymusic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Spotify is dark-only with fixed brand colors, so dynamic color and a light scheme are
 * intentionally not supported here.
 */
private val SpotifyColorScheme = darkColorScheme(
    primary = SpotifyGreen,
    onPrimary = PureBlack,
    primaryContainer = SpotifyGreenDark,
    onPrimaryContainer = PureBlack,
    secondary = TextPrimary,
    onSecondary = PureBlack,
    secondaryContainer = ElevatedHigh,
    onSecondaryContainer = TextPrimary,
    tertiary = SpotifyGreen,
    background = SpotifyBlack,
    onBackground = TextPrimary,
    surface = SpotifyBlack,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = PureBlack,
    surfaceContainerLow = Elevated,
    surfaceContainer = Elevated,
    surfaceContainerHigh = ElevatedHigh,
    surfaceContainerHighest = ElevatedHighest,
    surfaceBright = Highlight,
    inverseSurface = TextPrimary,
    inverseOnSurface = PureBlack,
    outline = TextSubdued,
    outlineVariant = Highlight,
    error = ErrorRed,
    scrim = PureBlack,
)

/** Brand tokens that have no Material 3 role. */
@Immutable
data class MusicColors(
    val likedGradient: List<Color>,
    val localFilesGradient: List<Color>,
    val browsePalette: List<Color>,
    val subdued: Color,
    val pureBlack: Color,
    val artworkPlaceholder: Color,
)

private val DefaultMusicColors = MusicColors(
    likedGradient = listOf(LikedGradientStart, LikedGradientEnd),
    localFilesGradient = listOf(LocalFilesGradientStart, LocalFilesGradientEnd),
    browsePalette = BrowsePalette,
    subdued = TextSubdued,
    pureBlack = PureBlack,
    artworkPlaceholder = ElevatedHighest,
)

private val LocalMusicColors = staticCompositionLocalOf { DefaultMusicColors }

object MusicTheme {
    val colors: MusicColors
        @Composable @ReadOnlyComposable get() = LocalMusicColors.current
}

@Composable
fun MyMusicTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMusicColors provides DefaultMusicColors) {
        MaterialTheme(
            colorScheme = SpotifyColorScheme,
            typography = MusicTypography,
            content = content,
        )
    }
}
