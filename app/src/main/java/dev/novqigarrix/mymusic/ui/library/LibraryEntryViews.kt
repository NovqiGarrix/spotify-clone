package dev.novqigarrix.mymusic.ui.library

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.ui.artwork.Artwork
import dev.novqigarrix.mymusic.ui.collection.titleRes
import dev.novqigarrix.mymusic.ui.components.CoverCard
import dev.novqigarrix.mymusic.ui.components.MediaListRow
import dev.novqigarrix.mymusic.ui.components.PlaylistCover
import dev.novqigarrix.mymusic.ui.components.displayArtist
import dev.novqigarrix.mymusic.ui.components.displayName
import dev.novqigarrix.mymusic.ui.components.displayTitle

@Composable
private fun LibraryEntry.title(): String = when (this) {
    is LibraryEntry.PlaylistEntry -> stringResource(kind.titleRes())
    is LibraryEntry.AlbumEntry -> album.displayTitle()
    is LibraryEntry.ArtistEntry -> artist.displayName()
}

@Composable
private fun LibraryEntry.subtitle(): String = when (this) {
    is LibraryEntry.PlaylistEntry -> stringResource(
        R.string.playlist_subtitle,
        pluralStringResource(R.plurals.song_count, songCount, songCount),
    )
    is LibraryEntry.AlbumEntry -> stringResource(R.string.album_subtitle, album.displayArtist())
    is LibraryEntry.ArtistEntry -> stringResource(R.string.artist_subtitle)
}

@Composable
private fun LibraryEntryArtwork(entry: LibraryEntry, modifier: Modifier) {
    when (entry) {
        is LibraryEntry.PlaylistEntry -> PlaylistCover(kind = entry.kind, modifier = modifier)
        is LibraryEntry.AlbumEntry -> Artwork(uri = entry.album.artworkUri, contentDescription = null, modifier = modifier)
        is LibraryEntry.ArtistEntry -> Artwork(
            uri = entry.artist.artworkUri,
            contentDescription = null,
            shape = CircleShape,
            placeholderIcon = Icons.Outlined.Person,
            modifier = modifier,
        )
    }
}

@Composable
fun LibraryEntryRow(
    entry: LibraryEntry,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MediaListRow(
        title = entry.title(),
        subtitle = entry.subtitle(),
        onClick = onClick,
        isPinned = entry is LibraryEntry.PlaylistEntry,
        highlightTitle = isActive,
        modifier = modifier,
        artwork = { m -> LibraryEntryArtwork(entry, m) },
    )
}

@Composable
fun LibraryEntryCard(
    entry: LibraryEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CoverCard(
        title = entry.title(),
        subtitle = entry.subtitle(),
        artworkUri = null,
        circular = entry is LibraryEntry.ArtistEntry,
        onClick = onClick,
        width = Dp.Unspecified,
        modifier = modifier,
        artwork = {
            LibraryEntryArtwork(
                entry,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
        },
    )
}
