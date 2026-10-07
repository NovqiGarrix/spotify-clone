package dev.novqigarrix.mymusic.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.data.Album
import dev.novqigarrix.mymusic.data.Artist
import dev.novqigarrix.mymusic.data.Song
import java.util.Locale
import java.util.concurrent.TimeUnit

/** "3:07" style track time. */
fun formatTrackTime(ms: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0))
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

/** "1 hr 12 min" / "38 min 20 sec", as in Spotify's collection headers. */
@Composable
@ReadOnlyComposable
fun formatCollectionDuration(ms: Long): String {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    return if (totalMinutes >= 60) {
        stringResource(R.string.duration_hours_minutes, (totalMinutes / 60).toInt(), (totalMinutes % 60).toInt())
    } else {
        stringResource(R.string.duration_minutes_seconds, totalMinutes.toInt(), (TimeUnit.MILLISECONDS.toSeconds(ms) % 60).toInt())
    }
}

@Composable
@ReadOnlyComposable
fun songCount(count: Int): String = pluralStringResource(R.plurals.song_count, count, count)

@Composable
@ReadOnlyComposable
fun Song.displayArtist(): String = artist.ifBlank { stringResource(R.string.unknown_artist) }

@Composable
@ReadOnlyComposable
fun Song.displayAlbum(): String = album.ifBlank { stringResource(R.string.unknown_album) }

@Composable
@ReadOnlyComposable
fun Album.displayTitle(): String = title.ifBlank { stringResource(R.string.unknown_album) }

@Composable
@ReadOnlyComposable
fun Album.displayArtist(): String = artist.ifBlank { stringResource(R.string.unknown_artist) }

@Composable
@ReadOnlyComposable
fun Artist.displayName(): String = name.ifBlank { stringResource(R.string.unknown_artist) }
