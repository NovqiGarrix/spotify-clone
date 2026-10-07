package dev.novqigarrix.mymusic.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.novqigarrix.mymusic.data.Song

private const val EXTRA_QUEUE_KIND = "dev.novqigarrix.mymusic.QUEUE_KIND"

internal fun Song.toMediaItem(kind: QueueItemKind = QueueItemKind.Source): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(Bundle().apply { putString(EXTRA_QUEUE_KIND, kind.name) })
            .build(),
    )
    .build()

/** Why this item is in the queue; travels with the item to every controller. */
internal val MediaItem.queueKind: QueueItemKind
    get() = mediaMetadata.extras?.getString(EXTRA_QUEUE_KIND)
        ?.let { name -> QueueItemKind.entries.firstOrNull { it.name == name } }
        ?: QueueItemKind.Source

internal val MediaItem.songId: Long? get() = mediaId.toLongOrNull()
