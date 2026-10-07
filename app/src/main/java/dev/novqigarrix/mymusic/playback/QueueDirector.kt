package dev.novqigarrix.mymusic.playback

import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dev.novqigarrix.mymusic.recommend.Recommender

/** Songs added each time the queue is about to run out. */
private const val AUTOPLAY_BATCH = 10

/** Songs the next autoplay batch should sound like. */
private const val AUTOPLAY_SEEDS = 5

/** Smart Shuffle mixes in about one recommendation for every three songs, as Spotify does. */
private const val SMART_PICK_RATIO = 3
private const val SMART_PICKS_MIN = 2
private const val SMART_PICKS_MAX = 20

/**
 * Runs inside [PlaybackService] and shapes the queue the way Spotify does:
 * - **Autoplay**: when the last song in the queue starts (for example a single), similar songs are
 *   appended so the music never just stops.
 * - **Shuffle / Smart Shuffle**: the shuffle button cycles off → shuffle → Smart Shuffle. Smart
 *   Shuffle mixes recommendations into the queue and orders it with the [Recommender].
 * - **Learning**: every finished or skipped song is reported to the [Recommender], and after a
 *   skip the songs the app chose are re-ranked so similar ones move further down.
 *
 * [sessionPlayer] must be the player given to the MediaSession, so a new queue can be detected.
 */
@OptIn(UnstableApi::class)
class QueueDirector(
    private val player: ExoPlayer,
    private val recommender: Recommender,
    private val onSmartShuffleChanged: (Boolean) -> Unit,
) : Player.Listener {

    var isSmartShuffle = false
        private set

    /** True while the director itself is editing the queue; suppresses re-entrant autoplay. */
    private var isEditing = false
    private val handler = Handler(player.applicationLooper)

    val sessionPlayer: Player = object : ForwardingPlayer(player) {
        override fun setMediaItems(mediaItems: MutableList<MediaItem>) =
            replaceQueue { super.setMediaItems(mediaItems) }

        override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) =
            replaceQueue { super.setMediaItems(mediaItems, resetPosition) }

        override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) =
            replaceQueue { super.setMediaItems(mediaItems, startIndex, startPositionMs) }

        override fun setMediaItem(mediaItem: MediaItem) =
            replaceQueue { super.setMediaItem(mediaItem) }

        override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) =
            replaceQueue { super.setMediaItem(mediaItem, startPositionMs) }

        override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) =
            replaceQueue { super.setMediaItem(mediaItem, resetPosition) }
    }

    init {
        player.setShuffleOrder(QueueShuffleOrder(IntArray(0)))
        player.addListener(this)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        player.removeListener(this)
    }

    /** Off → Shuffle → Smart Shuffle → Off, like Spotify's shuffle button. */
    fun cycleShuffle() {
        when {
            !player.shuffleModeEnabled -> player.shuffleModeEnabled = true
            !isSmartShuffle -> setSmartShuffle(true)
            else -> player.shuffleModeEnabled = false
        }
    }

    /** The library finished loading (e.g. playback was resumed before the app scanned it). */
    fun onLibraryReady() = edit { }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        if (shuffleModeEnabled) {
            // Turning shuffle on reshuffles everything around the current song, as in Spotify.
            edit { applyRandomOrder(fresh = true) }
        } else if (isSmartShuffle) {
            setSmartShuffle(false)
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        mediaItem?.songId?.let(recommender::onSongStarted)
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        val songId = oldPosition.mediaItem?.songId ?: return
        when (reason) {
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> recommender.onSongEnded(songId, listenedMs = null)
            Player.DISCONTINUITY_REASON_SEEK -> {
                val from = oldPosition.mediaItemIndex
                val to = newPosition.mediaItemIndex
                // Scrubbing within a song or going back to the previous one isn't a verdict on it.
                if (from == to || isPrevious(from, to)) return
                val skipped = recommender.onSongEnded(songId, listenedMs = oldPosition.positionMs)
                if (skipped) handler.post { edit(::rerankUpcoming) }
            }
        }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
            )
        ) {
            if (!isEditing) maybeAutoplay()
        }
    }

    private fun replaceQueue(set: () -> Unit) {
        isEditing = true
        try {
            set()
            if (player.shuffleModeEnabled) {
                if (isSmartShuffle) {
                    addSmartPicks()
                    applySmartOrder(fresh = true)
                } else {
                    applyRandomOrder(fresh = true)
                }
            }
        } finally {
            isEditing = false
        }
        maybeAutoplay()
    }

    /** Runs a queue edit without autoplay reacting to each step, then checks autoplay once. */
    private fun edit(block: () -> Unit) {
        isEditing = true
        try {
            block()
        } finally {
            isEditing = false
        }
        maybeAutoplay()
    }

    private fun setSmartShuffle(enabled: Boolean) {
        if (isSmartShuffle == enabled) return
        isSmartShuffle = enabled
        edit {
            if (enabled) {
                addSmartPicks()
                applySmartOrder(fresh = false)
            } else {
                removeSmartPicks()
                if (player.shuffleModeEnabled) applyRandomOrder(fresh = false)
            }
        }
        onSmartShuffleChanged(enabled)
    }

    /**
     * Keeps the music going: when the current song is the last one that will play, appends songs
     * similar to what was just playing right after it (also right after it in shuffle order).
     */
    private fun maybeAutoplay() {
        if (player.mediaItemCount == 0 || player.repeatMode != Player.REPEAT_MODE_OFF) return
        if (player.nextMediaItemIndex != C.INDEX_UNSET) return
        val current = player.currentMediaItemIndex
        val playOrder = playOrder()
        val seeds = playOrder.take(playOrder.indexOf(current) + 1)
            .takeLast(AUTOPLAY_SEEDS)
            .mapNotNull { player.getMediaItemAt(it).songId }
        val picks = recommender.autoplay(seeds, queuedSongIds(), AUTOPLAY_BATCH)
        if (picks.isEmpty()) return
        val wasEnded = player.playbackState == Player.STATE_ENDED
        isEditing = true
        try {
            player.addMediaItems(current + 1, picks.map { it.toMediaItem(QueueItemKind.Autoplay) })
            if (wasEnded) player.seekToDefaultPosition(current + 1)
        } finally {
            isEditing = false
        }
    }

    private fun addSmartPicks() {
        val sourceIds = (0 until player.mediaItemCount)
            .map(player::getMediaItemAt)
            .filter { it.queueKind == QueueItemKind.Source }
            .mapNotNull { it.songId }
        if (sourceIds.isEmpty()) return
        val count = (sourceIds.size / SMART_PICK_RATIO).coerceIn(SMART_PICKS_MIN, SMART_PICKS_MAX)
        val picks = recommender.recommendFor(sourceIds, queuedSongIds(), count)
        if (picks.isNotEmpty()) {
            player.addMediaItems(picks.map { it.toMediaItem(QueueItemKind.SmartPick) })
        }
    }

    private fun removeSmartPicks() {
        var current = player.currentMediaItemIndex
        if (player.getMediaItemAt(current).queueKind == QueueItemKind.SmartPick) {
            // The playing pick sits at the end of the playlist; move it after the source song that
            // played before it, so the rest of the album / playlist still follows unshuffled.
            val shuffled = playOrder(shuffle = true)
            val anchor = shuffled.take(shuffled.indexOf(current))
                .lastOrNull { player.getMediaItemAt(it).queueKind == QueueItemKind.Source }
            val target = when {
                anchor == null -> 0
                anchor < current -> anchor + 1
                else -> anchor
            }
            player.moveMediaItem(current, target)
            current = player.currentMediaItemIndex
        }
        for (index in player.mediaItemCount - 1 downTo 0) {
            if (index != current && player.getMediaItemAt(index).queueKind == QueueItemKind.SmartPick) {
                player.removeMediaItem(index)
            }
        }
    }

    /**
     * Random order that starts with the current song. With [fresh] (a new queue) everything else is
     * shuffled; otherwise songs already played stay behind it and "Add to queue" songs stay next.
     */
    private fun applyRandomOrder(fresh: Boolean) {
        val (played, current, queued, rest) = splitQueue(fresh) ?: return
        player.setShuffleOrder(QueueShuffleOrder((played + current + queued + rest.shuffled()).toIntArray()))
    }

    /**
     * Like [applyRandomOrder], but upcoming songs are ordered by the recommender. Autoplay songs
     * stay after the rest of the queue.
     */
    private fun applySmartOrder(fresh: Boolean) {
        val (played, current, queued, rest) = splitQueue(fresh) ?: return
        val (autoplay, mix) = rest.partition { player.getMediaItemAt(it).queueKind == QueueItemKind.Autoplay }
        val upcoming = ranked(mix, current) + ranked(autoplay, current)
        player.setShuffleOrder(QueueShuffleOrder((played + current + queued + upcoming).toIntArray()))
    }

    /**
     * After a skip: re-rank the upcoming songs the app chose (the whole queue in Smart Shuffle,
     * otherwise just the autoplay songs) so the model's new beliefs take effect immediately.
     */
    private fun rerankUpcoming() {
        if (player.mediaItemCount == 0) return
        if (player.shuffleModeEnabled && isSmartShuffle) {
            applySmartOrder(fresh = false)
            return
        }
        val current = player.currentMediaItemIndex
        val order = playOrder()
        val upcoming = order.drop(order.indexOf(current) + 1)
        val slots = upcoming.filter { player.getMediaItemAt(it).queueKind == QueueItemKind.Autoplay }
        if (slots.size < 2) return
        val ranked = ranked(slots, current)
        if (player.shuffleModeEnabled) {
            val replacement = slots.zip(ranked).toMap()
            player.setShuffleOrder(QueueShuffleOrder(order.map { replacement[it] ?: it }.toIntArray()))
        } else {
            // Unshuffled, autoplay songs sit in one block after the queue; reorder it in place.
            if (slots.last() - slots.first() != slots.size - 1) return
            val ids = ranked.map { player.getMediaItemAt(it).mediaId }
            ids.forEachIndexed { offset, id ->
                val target = slots.first() + offset
                val from = (target..slots.last()).first { player.getMediaItemAt(it).mediaId == id }
                if (from != target) player.moveMediaItem(from, target)
            }
        }
    }

    /** Orders playlist [indices] with the recommender, as if they follow [after]. */
    private fun ranked(indices: List<Int>, after: Int): List<Int> {
        val byId = indices.groupByTo(LinkedHashMap()) { player.getMediaItemAt(it).mediaId }
            .mapValues { ArrayDeque(it.value) }
        val ids = indices.mapNotNull { player.getMediaItemAt(it).songId }
        val ordered = recommender.order(ids, player.getMediaItemAt(after).songId)
            .mapNotNull { byId[it.toString()]?.removeFirstOrNull() }
        // Anything the recommender couldn't place (e.g. a file outside the library) goes last.
        return ordered + byId.values.flatten()
    }

    private data class QueueSplit(val played: List<Int>, val current: Int, val queued: List<Int>, val rest: List<Int>)

    private fun splitQueue(fresh: Boolean): QueueSplit? {
        if (player.mediaItemCount == 0) return null
        val current = player.currentMediaItemIndex
        val order = playOrder()
        val position = order.indexOf(current)
        val played = if (fresh) emptyList() else order.take(position)
        val upcoming = if (fresh) order - current else order.drop(position + 1)
        val (queued, rest) = upcoming.partition { player.getMediaItemAt(it).queueKind == QueueItemKind.Queued }
        return QueueSplit(played, current, queued, rest)
    }

    /** Playlist indices in the order they play, respecting shuffle. */
    private fun playOrder(shuffle: Boolean = player.shuffleModeEnabled): List<Int> {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return emptyList()
        return buildList {
            var index = timeline.getFirstWindowIndex(shuffle)
            while (index != C.INDEX_UNSET) {
                add(index)
                index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle)
            }
        }
    }

    private fun isPrevious(from: Int, to: Int): Boolean =
        player.currentTimeline.getPreviousWindowIndex(from, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled) == to

    private fun queuedSongIds(): Set<Long> =
        (0 until player.mediaItemCount).mapNotNullTo(HashSet()) { player.getMediaItemAt(it).songId }
}
