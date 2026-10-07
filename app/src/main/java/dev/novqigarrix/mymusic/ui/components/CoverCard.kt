package dev.novqigarrix.mymusic.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.novqigarrix.mymusic.ui.artwork.Artwork
import dev.novqigarrix.mymusic.ui.theme.Sizes
import dev.novqigarrix.mymusic.ui.theme.Spacing

/**
 * Carousel card from Spotify's Home rows. Artists get a circular image and centered label.
 * Pass [Dp.Unspecified] as [width] to let the parent (e.g. a grid cell) size it.
 */
@Composable
fun CoverCard(
    title: String,
    subtitle: String?,
    artworkUri: Uri?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    circular: Boolean = false,
    width: Dp = Sizes.carouselCard,
    artwork: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .then(if (width != Dp.Unspecified) Modifier.width(width) else Modifier)
            .bounceClick(onClick = onClick),
        horizontalAlignment = if (circular) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (artwork != null) {
            artwork()
        } else {
            Artwork(
                uri = artworkUri,
                contentDescription = null,
                shape = if (circular) CircleShape else RoundedCornerShape(4.dp),
                placeholderIcon = if (circular) Icons.Outlined.Person else Icons.Outlined.MusicNote,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
        }
        Spacer(Modifier.height(Spacing.small))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (subtitle == null) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (circular) TextAlign.Center else TextAlign.Start,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (circular) TextAlign.Center else TextAlign.Start,
            )
        }
    }
}
