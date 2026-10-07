package dev.novqigarrix.mymusic.data

import android.net.Uri
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val dateAddedSec: Long,
    val uri: Uri,
    val albumArtUri: Uri,
)

@Immutable
data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val year: Int,
    val songs: List<Song>,
    val dateAddedSec: Long,
    val artworkUri: Uri,
) {
    val durationMs: Long get() = songs.sumOf { it.durationMs }
}

@Immutable
data class Artist(
    val id: Long,
    val name: String,
    val albums: List<Album>,
    val songs: List<Song>,
    val artworkUri: Uri?,
)

@Keep
@Serializable
enum class PlaylistKind { Liked, LocalFiles }

/** Where a queue was started from; drives "Playing from …" and the Home recents. */
@Immutable
sealed interface PlaySource {
    val key: String

    data class FromAlbum(val albumId: Long) : PlaySource {
        override val key get() = "album:$albumId"
    }

    data class FromArtist(val artistId: Long) : PlaySource {
        override val key get() = "artist:$artistId"
    }

    data class FromPlaylist(val kind: PlaylistKind) : PlaySource {
        override val key get() = "playlist:${kind.name}"
    }

    companion object {
        fun parse(key: String): PlaySource? {
            val (type, value) = key.split(':', limit = 2).takeIf { it.size == 2 } ?: return null
            return when (type) {
                "album" -> value.toLongOrNull()?.let(::FromAlbum)
                "artist" -> value.toLongOrNull()?.let(::FromArtist)
                "playlist" -> PlaylistKind.entries.firstOrNull { it.name == value }?.let(::FromPlaylist)
                else -> null
            }
        }
    }
}

@Immutable
data class MusicLibrary(
    val songs: List<Song>,
    val albums: List<Album>,
    val artists: List<Artist>,
) {
    private val songsById = songs.associateBy { it.id }
    private val albumsById = albums.associateBy { it.id }
    private val artistsById = artists.associateBy { it.id }

    fun song(id: Long): Song? = songsById[id]
    fun album(id: Long): Album? = albumsById[id]
    fun artist(id: Long): Artist? = artistsById[id]

    val isEmpty: Boolean get() = songs.isEmpty()

    companion object {
        val Empty = MusicLibrary(emptyList(), emptyList(), emptyList())
    }
}

sealed interface LibraryState {
    data object Loading : LibraryState
    data class Ready(val library: MusicLibrary) : LibraryState
}
