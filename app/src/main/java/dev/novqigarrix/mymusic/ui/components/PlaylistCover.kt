package dev.novqigarrix.mymusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import dev.novqigarrix.mymusic.data.PlaylistKind
import dev.novqigarrix.mymusic.ui.theme.MusicTheme
import dev.novqigarrix.mymusic.ui.theme.MyMusicTheme

private const val ICON_FRACTION = 0.42f

/** Generated gradient covers for the built-in playlists (Liked Songs, Local Files). */
@Composable
fun PlaylistCover(
    kind: PlaylistKind,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
) {
    val colors = MusicTheme.colors
    val (gradient, icon) = when (kind) {
        PlaylistKind.Liked -> colors.likedGradient to Icons.Rounded.Favorite
        PlaylistKind.LocalFiles -> colors.localFilesGradient to Icons.Rounded.FolderOpen
    }
    GradientCover(gradient = gradient, icon = icon, modifier = modifier, shape = shape)
}

@Composable
private fun GradientCover(
    gradient: List<Color>,
    icon: ImageVector,
    modifier: Modifier,
    shape: Shape,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.fillMaxSize(ICON_FRACTION),
        )
    }
}

@Preview
@Composable
private fun PlaylistCoverPreview() {
    MyMusicTheme { PlaylistCover(PlaylistKind.Liked, Modifier.size(120.dp)) }
}
