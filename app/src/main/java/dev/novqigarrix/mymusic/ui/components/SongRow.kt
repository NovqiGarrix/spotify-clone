package dev.novqigarrix.mymusic.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.ui.artwork.Artwork
import dev.novqigarrix.mymusic.ui.theme.MyMusicTheme
import dev.novqigarrix.mymusic.ui.theme.Sizes
import dev.novqigarrix.mymusic.ui.theme.Spacing

/**
 * A track row. The current track is tinted green and shows equalizer bars, as in Spotify.
 * Pass [artworkUri] for list-with-art layouts or [trackNumber] for album track lists.
 */
@Composable
fun SongRow(
    title: String,
    subtitle: String,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isLiked: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    artworkUri: Uri? = null,
    trackNumber: Int? = null,
    isRecommended: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .bounceClick(pressedScale = 0.985f, onClick = onClick)
            .padding(start = Spacing.screen, top = Spacing.small, bottom = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            artworkUri != null -> Artwork(
                uri = artworkUri,
                contentDescription = null,
                modifier = Modifier.size(Sizes.rowArtwork),
            )
            trackNumber != null -> Row(
                modifier = Modifier.widthIn(min = 24.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                if (isCurrent) {
                    EqualizerBars(isAnimating = isPlaying)
                } else {
                    Text(
                        text = trackNumber.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        if (artworkUri != null || trackNumber != null) Spacer(Modifier.width(Spacing.medium))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent && trackNumber == null) {
                    EqualizerBars(isAnimating = isPlaying, modifier = Modifier.padding(end = Spacing.small))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLiked) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = stringResource(R.string.liked_songs),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(end = Spacing.xs)
                            .size(14.dp),
                    )
                }
                if (isRecommended) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = stringResource(R.string.recommended),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(end = Spacing.xs)
                            .size(14.dp),
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onMoreClick) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
private fun SongRowPreview() {
    MyMusicTheme {
        SongRow(
            title = "Midnight City",
            subtitle = "M83",
            isCurrent = true,
            isPlaying = true,
            isLiked = true,
            onClick = {},
            onMoreClick = {},
            trackNumber = 3,
        )
    }
}
