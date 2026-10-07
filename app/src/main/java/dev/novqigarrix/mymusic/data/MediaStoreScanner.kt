package dev.novqigarrix.mymusic.data

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import android.util.Log

private const val TAG = "MediaStoreScanner"
private const val MIN_DURATION_MS = 20_000
private const val TRACK_DISC_FACTOR = 1000

/** Reads every music file on the device from MediaStore and groups it into albums and artists. */
class MediaStoreScanner(private val resolver: ContentResolver) {

    fun scan(): MusicLibrary {
        val songs = querySongs()
        val albums = songs
            .groupBy { it.albumId }
            .map { (albumId, tracks) -> buildAlbum(albumId, tracks) }
            .sortedByDescending { it.dateAddedSec }
        val albumsByArtist = albums.groupBy { it.artistId }
        val artists = songs
            .groupBy { it.artistId }
            .map { (artistId, tracks) ->
                val artistAlbums = albumsByArtist[artistId].orEmpty().sortedByDescending { it.year }
                Artist(
                    id = artistId,
                    name = tracks.first().artist,
                    albums = artistAlbums,
                    songs = tracks.sortedWith(compareBy({ it.album.lowercase() }, { it.trackNumber })),
                    artworkUri = artistAlbums.firstOrNull()?.artworkUri ?: tracks.first().albumArtUri,
                )
            }
            .sortedBy { it.name.lowercase() }
        return MusicLibrary(
            songs = songs.sortedByDescending { it.dateAddedSec },
            albums = albums,
            artists = artists,
        )
    }

    private fun buildAlbum(albumId: Long, tracks: List<Song>): Album {
        val sorted = tracks.sortedWith(compareBy({ it.trackNumber }, { it.title.lowercase() }))
        // Compilation albums have many artists; the most frequent one is the best "album artist".
        val mainArtist = tracks.groupingBy { it.artistId }.eachCount().maxBy { it.value }.key
        val artistName = tracks.first { it.artistId == mainArtist }.artist
        return Album(
            id = albumId,
            title = tracks.first().album,
            artist = artistName,
            artistId = mainArtist,
            year = tracks.maxOf { it.year },
            songs = sorted,
            dateAddedSec = tracks.maxOf { it.dateAddedSec },
            artworkUri = tracks.first().albumArtUri,
        )
    }

    private fun querySongs(): List<Song> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val args = arrayOf(MIN_DURATION_MS.toString())
        val songs = mutableListOf<Song>()
        try {
            resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, args, null)
                ?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                    val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                    val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val albumId = cursor.getLong(albumIdCol)
                        songs += Song(
                            id = id,
                            title = cursor.getString(titleCol).orEmpty().ifBlank { "Track $id" },
                            artist = cursor.getString(artistCol).cleanTag(),
                            artistId = cursor.getLong(artistIdCol),
                            album = cursor.getString(albumCol).cleanTag(),
                            albumId = albumId,
                            durationMs = cursor.getLong(durationCol),
                            trackNumber = cursor.getInt(trackCol) % TRACK_DISC_FACTOR,
                            year = cursor.getInt(yearCol),
                            dateAddedSec = cursor.getLong(addedCol),
                            uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                            albumArtUri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId),
                        )
                    }
                }
        } catch (e: SecurityException) {
            Log.w(TAG, "Audio permission not granted", e)
        }
        return songs
    }

    /** MediaStore reports missing tags as "<unknown>"; the UI substitutes a localized label for "". */
    private fun String?.cleanTag(): String =
        if (this == null || this == MediaStore.UNKNOWN_STRING) "" else this
}
