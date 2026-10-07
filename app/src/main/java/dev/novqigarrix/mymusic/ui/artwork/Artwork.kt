package dev.novqigarrix.mymusic.ui.artwork

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import dev.novqigarrix.mymusic.ui.theme.Motion
import dev.novqigarrix.mymusic.ui.theme.MusicTheme

/** Null in previews, where artwork falls back to the placeholder. */
val LocalArtworkLoader = staticCompositionLocalOf<ArtworkLoader?> { null }

private const val FADE_IN_MS = 220
private const val PLACEHOLDER_ICON_FRACTION = 0.42f

/** Square album art with Spotify's grey music-note placeholder and a soft fade-in. */
@Composable
fun Artwork(
    uri: Uri?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: ArtworkSize = ArtworkSize.Small,
    shape: Shape = RectangleShape,
    placeholderIcon: ImageVector = Icons.Outlined.MusicNote,
) {
    val loader = LocalArtworkLoader.current
    var bitmap by remember(uri, size) {
        mutableStateOf<ImageBitmap?>(if (uri != null) loader?.cached(uri, size) else null)
    }
    val alpha = remember(uri, size) { Animatable(if (bitmap != null) 1f else 0f) }

    LaunchedEffect(uri, size, loader) {
        if (uri == null || loader == null || bitmap != null) return@LaunchedEffect
        val loaded = loader.load(uri, size) ?: return@LaunchedEffect
        bitmap = loaded
        alpha.animateTo(1f, tween(FADE_IN_MS))
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(MusicTheme.colors.artworkPlaceholder),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image == null) {
            Icon(
                imageVector = placeholderIcon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(PLACEHOLDER_ICON_FRACTION),
            )
        } else {
            Image(
                bitmap = image,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { this.alpha = alpha.value },
            )
        }
    }
}

/** Animated artwork tint; falls back to [fallback] while loading or when there is no art. */
@Composable
fun rememberArtworkColor(uri: Uri?, fallback: Color = MaterialTheme.colorScheme.surfaceVariant): State<Color> {
    val loader = LocalArtworkLoader.current
    var target by remember(uri) { mutableStateOf(uri?.let { loader?.cachedColor(it) } ?: fallback) }
    LaunchedEffect(uri, loader, fallback) {
        target = uri?.let { loader?.dominantColor(it) } ?: fallback
    }
    return animateColorAsState(target, Motion.colorFade, label = "artworkColor")
}

