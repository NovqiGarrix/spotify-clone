package dev.novqigarrix.mymusic.ui.collection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowCircleDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.ui.theme.Sizes
import dev.novqigarrix.mymusic.ui.theme.Spacing

private val ActionRowHeight = 64.dp

/**
 * Secondary actions under a collection header. The trailing space is reserved for the
 * [dev.novqigarrix.mymusic.ui.components.PinnedPlayButton], which is drawn above the list.
 */
@Composable
fun CollectionActionRow(
    isShuffleActive: Boolean,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    isSmartShuffle: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ActionRowHeight)
            .padding(start = Spacing.xs, end = Spacing.screen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Everything here is a local file, so it always gets Spotify's green "downloaded" badge.
        Icon(
            imageVector = Icons.Rounded.ArrowCircleDown,
            contentDescription = stringResource(R.string.downloaded),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = Spacing.medium)
                .size(26.dp),
        )
        Spacer(Modifier.weight(1f))
        ShuffleToggle(isActive = isShuffleActive, isSmart = isSmartShuffle, onClick = onShuffle)
        Spacer(Modifier.width(Spacing.small + Sizes.playButton))
    }
}

/**
 * Shuffle icon that turns green with Spotify's little indicator dot when active. Smart Shuffle
 * adds Spotify's sparkle to the icon.
 */
@Composable
fun ShuffleToggle(isActive: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, isSmart: Boolean = false) {
    val tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    IconButton(onClick = onClick, modifier = modifier) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = stringResource(if (isSmart) R.string.smart_shuffle else R.string.shuffle),
                tint = tint,
                modifier = Modifier.size(28.dp),
            )
            if (isSmart) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier
                        .offset(x = 13.dp, y = (-12).dp)
                        .size(12.dp),
                )
            }
            if (isActive) {
                Box(
                    modifier = Modifier
                        .offset(y = 18.dp)
                        .size(4.dp)
                        .background(tint, CircleShape),
                )
            }
        }
    }
}
