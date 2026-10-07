package dev.novqigarrix.mymusic.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import dev.novqigarrix.mymusic.ui.components.EqualizerBars
import dev.novqigarrix.mymusic.ui.components.bounceClick
import dev.novqigarrix.mymusic.ui.theme.Sizes
import dev.novqigarrix.mymusic.ui.theme.Spacing

private val TileShape = RoundedCornerShape(4)

/** One of the compact 2-column shortcut tiles at the top of Spotify's Home. */
@Composable
fun ShortcutTile(
    item: HomeItem,
    isPlaying: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(Sizes.shortcutHeight)
            .bounceClick(onClick = onClick)
            .clip(TileShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeItemArtwork(
            item = item,
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f),
        )
        Text(
            text = item.title(),
            style = MaterialTheme.typography.titleSmall,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.small),
        )
        if (isActive) {
            EqualizerBars(isAnimating = isPlaying, modifier = Modifier.padding(end = Spacing.small))
        }
    }
}
