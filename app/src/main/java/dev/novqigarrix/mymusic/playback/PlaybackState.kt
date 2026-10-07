package dev.novqigarrix.mymusic.playback

import androidx.compose.runtime.Immutable
import dev.novqigarrix.mymusic.data.PlaySource

enum class RepeatMode { Off, All, One }

enum class QueueItemKind {
    /** From the album / artist / playlist the user started. */
    Source,

    /** Added with "Add to queue". */
    Queued,

    /** Mixed in by Smart Shuffle. */
    SmartPick,

    /** Picked to keep the music going after the queue ran out. */
    Autoplay,
}

@Immutable
data class QueueEntry(val songId: Long, val kind: QueueItemKind)

@Immutable
data class PlaybackState(
    val isConnected: Boolean = false,
    val currentSongId: Long? = null,
    /** True when the user intends playback (playing or buffering) — drives the play/pause icon. */
    val playWhenReady: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    /** Shuffle with recommendations mixed in and a learned order; implies [shuffleEnabled]. */
    val smartShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
    /** The queue in play order (respecting shuffle). */
    val queue: List<QueueEntry> = emptyList(),
    val queueIndex: Int = 0,
    val source: PlaySource? = null,
)
