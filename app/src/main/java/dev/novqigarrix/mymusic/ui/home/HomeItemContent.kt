package dev.novqigarrix.mymusic.ui.home

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.ui.artwork.Artwork
import dev.novqigarrix.mymusic.ui.collection.titleRes
import dev.novqigarrix.mymusic.ui.components.PlaylistCover
import dev.novqigarrix.mymusic.ui.components.displayArtist
import dev.novqigarrix.mymusic.ui.components.displayName
import dev.novqigarrix.mymusic.ui.components.displayTitle

@Composable
fun HomeItem.title(): String = when (this) {
    is HomeItem.AlbumItem -> album.displayTitle()
    is HomeItem.ArtistItem -> artist.displayName()
    is HomeItem.PlaylistItem -> stringResource(kind.titleRes())
}

@Composable
fun HomeItem.subtitle(): String = when (this) {
    is HomeItem.AlbumItem -> album.displayArtist()
    is HomeItem.ArtistItem -> stringResource(R.string.type_artist)
    is HomeItem.PlaylistItem -> stringResource(
        R.string.playlist_subtitle,
        pluralStringResource(R.plurals.song_count, songCount, songCount),
    )
}

@Composable
fun HomeItemArtwork(item: HomeItem, modifier: Modifier = Modifier, shape: Shape = RectangleShape) {
    when (item) {
        is HomeItem.AlbumItem -> Artwork(uri = item.album.artworkUri, contentDescription = null, shape = shape, modifier = modifier)
        is HomeItem.ArtistItem -> Artwork(
            uri = item.artist.artworkUri,
            contentDescription = null,
            shape = CircleShape,
            placeholderIcon = Icons.Outlined.Person,
            modifier = modifier,
        )
        is HomeItem.PlaylistItem -> PlaylistCover(kind = item.kind, shape = shape, modifier = modifier)
    }
}

/** Square cover for carousels (keeps aspect ratio). */
@Composable
fun HomeCarouselArtwork(item: HomeItem) {
    HomeItemArtwork(
        item = item,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    )
}
