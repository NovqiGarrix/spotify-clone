package dev.novqigarrix.mymusic.ui.search

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.novqigarrix.mymusic.ui.artwork.Artwork
import dev.novqigarrix.mymusic.ui.components.bounceClick
import dev.novqigarrix.mymusic.ui.theme.Spacing

private const val ART_TILT_DEGREES = 25f

/** Colored "Browse all" category card with the cover tilted into the bottom-right corner. */
@Composable
fun BrowseCard(
    title: String,
    color: Color,
    artworkUri: Uri?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(6.dp))
            .background(color),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = Spacing.medium, top = Spacing.medium, end = 56.dp),
        )
        Artwork(
            uri = artworkUri,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 18.dp, y = 4.dp)
                .rotate(ART_TILT_DEGREES)
                .shadow(8.dp, RoundedCornerShape(4.dp))
                .size(70.dp),
        )
    }
}
