package dev.novqigarrix.mymusic.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlin.random.Random

/**
 * A shuffle order the app controls, instead of ExoPlayer's fully random one:
 * - a new queue gets a random order (the service then puts the starting song first);
 * - songs inserted into an existing queue play right after the song before them, so "Add to queue"
 *   plays next and autoplay songs follow the current one, even while shuffled.
 *
 * [order] lists playlist indices in play order.
 */
@OptIn(UnstableApi::class)
class QueueShuffleOrder(private val order: IntArray) : ShuffleOrder {

    private val positions = IntArray(order.size).also { positions ->
        order.forEachIndexed { position, index -> positions[index] = position }
    }

    override fun getLength(): Int = order.size

    override fun getNextIndex(index: Int): Int =
        positions[index].let { if (it + 1 < order.size) order[it + 1] else C.INDEX_UNSET }

    override fun getPreviousIndex(index: Int): Int =
        positions[index].let { if (it > 0) order[it - 1] else C.INDEX_UNSET }

    override fun getLastIndex(): Int = order.lastOrNull() ?: C.INDEX_UNSET

    override fun getFirstIndex(): Int = order.firstOrNull() ?: C.INDEX_UNSET

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        if (order.isEmpty()) return random(insertionCount)
        val inserted = IntArray(insertionCount) { insertionIndex + it }
        val shifted = order.map { if (it >= insertionIndex) it + insertionCount else it }
        val after = if (insertionIndex == 0) -1 else positions[insertionIndex - 1]
        return QueueShuffleOrder(
            (shifted.subList(0, after + 1) + inserted.toList() + shifted.subList(after + 1, shifted.size)).toIntArray(),
        )
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val removed = indexToExclusive - indexFrom
        return QueueShuffleOrder(
            order.filter { it < indexFrom || it >= indexToExclusive }
                .map { if (it >= indexToExclusive) it - removed else it }
                .toIntArray(),
        )
    }

    override fun cloneAndClear(): ShuffleOrder = QueueShuffleOrder(IntArray(0))

    /** Playlist indices in play order. */
    fun indices(): List<Int> = order.asList()

    companion object {
        fun random(length: Int): QueueShuffleOrder =
            QueueShuffleOrder((0 until length).shuffled(Random.Default).toIntArray())
    }
}
