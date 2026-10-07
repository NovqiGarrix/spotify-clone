package dev.novqigarrix.mymusic.recommend

import android.content.Context
import dev.novqigarrix.mymusic.data.LibraryState
import dev.novqigarrix.mymusic.data.LikesRepository
import dev.novqigarrix.mymusic.data.MusicLibrary
import dev.novqigarrix.mymusic.data.MusicRepository
import dev.novqigarrix.mymusic.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalTime

private const val MODEL_FILE = "taste_model.json"
private const val SAVE_DEBOUNCE_MS = 2_000L
private const val SESSION_TIMEOUT_MS = 30L * 60 * 1000
private const val RECENT_HISTORY = 100

/** Like Spotify, leaving a song within its first 30 seconds counts as a skip. */
private const val SKIP_WINDOW_MS = 30_000L
private const val FINISHED_FRACTION = 0.75

/** Songs scored in full before the greedy pass; keeps ranking fast on large libraries. */
private const val SHORTLIST_MIN = 40
private const val SHORTLIST_PER_PICK = 4

/** Positions filled one by one (each aware of the song before it); the rest are sorted in one pass. */
private const val GREEDY_POSITIONS = 50

/** Spotify rarely plays the same artist twice in a row in a mix. */
private const val SAME_ARTIST_PENALTY = 0.6
private const val AFFINITY_ARTIST = 0.5
private const val AFFINITY_ALBUM = 0.15
private const val AFFINITY_DECADE = 0.2

@Serializable
private class SavedTaste(
    val model: TasteSnapshot = TasteSnapshot(),
    val plays: Map<Long, Int> = emptyMap(),
)

private class PendingPlay(val song: Song, val features: List<String>)

/**
 * Learns what the user likes from how they listen and picks songs for autoplay and Smart Shuffle.
 *
 * Every song that starts is scored in its context; when it ends, finishing it (or liking it) is a
 * reward and skipping it early is a penalty. The [TasteModel] updates immediately, and the
 * session's skips feed straight back into the features, so two skipped songs from the same artist
 * push that artist down the rest of the queue right away.
 *
 * Main thread only.
 */
class Recommender(
    context: Context,
    private val scope: CoroutineScope,
    private val musicRepository: MusicRepository,
    private val likesRepository: LikesRepository,
) {
    private val file = File(context.filesDir, MODEL_FILE)
    private val json = Json { ignoreUnknownKeys = true }

    private var model = TasteModel(::featurePrior)
    private val plays = HashMap<Long, Int>()
    private var isLoaded = false
    private var saveJob: Job? = null

    private var pending: PendingPlay? = null
    private val recentlyPlayed = ArrayDeque<Long>()

    // Listening session: reset after half an hour of silence.
    private var lastActivityMs = 0L
    private var previous: Song? = null
    private val skippedArtists = HashMap<Long, Int>()
    private val skippedAlbums = HashSet<Long>()
    private val skippedDecades = HashMap<Int, Int>()
    private val finishedArtists = HashSet<Long>()

    init {
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { json.decodeFromString<SavedTaste>(file.readText()) }.getOrNull()
            }
            if (saved != null) {
                model = TasteModel(::featurePrior, saved.model)
                plays.putAll(saved.plays)
            }
            isLoaded = true
        }
        scope.launch {
            var known: Set<Long>? = null
            likesRepository.likes.collect { likes ->
                val ids = likes.keys
                known?.let { before ->
                    (ids - before).forEach { learnFromLike(it, liked = true) }
                    (before - ids).forEach { learnFromLike(it, liked = false) }
                }
                known = ids
            }
        }
    }

    private val library: MusicLibrary?
        get() = (musicRepository.state.value as? LibraryState.Ready)?.library

    /** A song began playing; remembers its context so the outcome can be learned later. */
    fun onSongStarted(songId: Long) {
        val song = library?.song(songId) ?: return
        val now = System.currentTimeMillis()
        if (now - lastActivityMs > SESSION_TIMEOUT_MS) resetSession()
        lastActivityMs = now
        pending = PendingPlay(song, songFeatures(song, currentContext(previous), history(song)))
        previous = song
        plays[songId] = (plays[songId] ?: 0) + 1
        recentlyPlayed.remove(songId)
        recentlyPlayed.addLast(songId)
        if (recentlyPlayed.size > RECENT_HISTORY) recentlyPlayed.removeFirst()
    }

    /**
     * The song that was playing ended: [listenedMs] is how far in the user skipped, or null when
     * it played to the end. Returns true when this counted as a skip.
     */
    fun onSongEnded(songId: Long, listenedMs: Long?): Boolean {
        val play = pending?.takeIf { it.song.id == songId } ?: return false
        pending = null
        lastActivityMs = System.currentTimeMillis()
        val song = play.song
        val finished = when {
            listenedMs == null -> true
            listenedMs >= song.durationMs * FINISHED_FRACTION -> true
            listenedMs < minOf(SKIP_WINDOW_MS, song.durationMs / 2) -> false
            // Left halfway through: not a clear signal either way.
            else -> return false
        }
        if (finished) {
            finishedArtists += song.artistId
        } else {
            skippedArtists[song.artistId] = (skippedArtists[song.artistId] ?: 0) + 1
            skippedAlbums += song.albumId
            song.decade?.let { skippedDecades[it] = (skippedDecades[it] ?: 0) + 1 }
        }
        if (isLoaded) {
            model.update(play.features, finished)
            scheduleSave()
        }
        return !finished
    }

    /**
     * Songs to keep playing after the queue runs out, similar to [seedIds] (the songs just played),
     * in the order they should play. [exclude] is everything already in the queue.
     */
    fun autoplay(seedIds: List<Long>, exclude: Set<Long>, count: Int): List<Song> {
        val library = library ?: return emptyList()
        val seeds = seedIds.mapNotNull(library::song)
        val recent = recentlyPlayed.toSet()
        var candidates = library.songs.filter { it.id !in exclude && it.id !in recent }
        if (candidates.isEmpty()) candidates = library.songs.filter { it.id !in exclude }
        // Small library: start over rather than going silent.
        if (candidates.isEmpty()) candidates = library.songs.filter { it.id != seeds.lastOrNull()?.id }
        return rank(candidates, seeds.lastOrNull(), count, affinity(seeds))
    }

    /** Songs from outside [sourceIds] that fit them, for Smart Shuffle to mix in. */
    fun recommendFor(sourceIds: List<Long>, exclude: Set<Long>, count: Int): List<Song> {
        val library = library ?: return emptyList()
        val sources = sourceIds.mapNotNull(library::song)
        val candidates = library.songs.filter { it.id !in exclude }
        return rank(candidates, previous, count, affinity(sources))
    }

    /** Reorders [songIds] into the order the model expects the user to enjoy most, starting after [afterId]. */
    fun order(songIds: List<Long>, afterId: Long?): List<Long> {
        val library = library ?: return songIds
        val songs = songIds.mapNotNull(library::song)
        val unknown = songIds.filter { library.song(it) == null }
        return rank(songs, afterId?.let(library::song), songs.size) { 0.0 }.map(Song::id) + unknown
    }

    /**
     * Thompson-sampled ranking: one draw of the weights scores every candidate. The first
     * positions are filled greedily so each pick is scored with the song before it as context.
     */
    private fun rank(candidates: List<Song>, start: Song?, count: Int, affinity: (Song) -> Double): List<Song> {
        if (candidates.isEmpty() || count <= 0) return emptyList()
        val sample = model.sampler()
        fun score(song: Song, before: Song?): Double {
            val penalty = if (before != null && before.artistId == song.artistId) SAME_ARTIST_PENALTY else 0.0
            return sample(songFeatures(song, currentContext(before), history(song))) + affinity(song) - penalty
        }

        val shortlistSize = maxOf(SHORTLIST_MIN, count * SHORTLIST_PER_PICK)
        val scored = candidates.map { it to score(it, start) }.sortedByDescending { it.second }
        val remaining = scored.take(shortlistSize).map { it.first }.toMutableList()
        val picks = ArrayList<Song>(count)
        var before = start
        while (picks.size < minOf(count, GREEDY_POSITIONS) && remaining.isNotEmpty()) {
            val best = remaining.maxBy { score(it, before) }
            remaining.remove(best)
            picks += best
            before = best
        }
        if (picks.size < count) {
            val picked = picks.mapTo(HashSet(), Song::id)
            picks += scored.asSequence().map { it.first }.filter { it.id !in picked }.take(count - picks.size)
        }
        return picks
    }

    private fun affinity(seeds: List<Song>): (Song) -> Double {
        val artists = seeds.mapTo(HashSet()) { it.artistId }
        val albums = seeds.mapTo(HashSet()) { it.albumId }
        val decades = seeds.mapNotNullTo(HashSet()) { it.decade }
        return { song ->
            (if (song.artistId in artists) AFFINITY_ARTIST else 0.0) +
                (if (song.albumId in albums) AFFINITY_ALBUM else 0.0) +
                (if (song.decade in decades) AFFINITY_DECADE else 0.0)
        }
    }

    private fun learnFromLike(songId: Long, liked: Boolean) {
        val song = library?.song(songId) ?: return
        if (!isLoaded) return
        model.update(songFeatures(song, PlayContext(previous = null, daypart = daypart()), history(song)), liked)
        scheduleSave()
    }

    private fun currentContext(before: Song?) = PlayContext(
        previous = before,
        daypart = daypart(),
        sessionSkippedArtists = skippedArtists,
        sessionSkippedAlbums = skippedAlbums,
        sessionSkippedDecades = skippedDecades,
        sessionFinishedArtists = finishedArtists,
    )

    private fun history(song: Song) = SongHistory(
        isLiked = song.id in likesRepository.likes.value,
        plays = plays[song.id] ?: 0,
        nowSec = System.currentTimeMillis() / 1000,
    )

    private fun daypart(): Int = LocalTime.now().hour / 6

    private fun resetSession() {
        previous = null
        skippedArtists.clear()
        skippedAlbums.clear()
        skippedDecades.clear()
        finishedArtists.clear()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            val text = json.encodeToString(SavedTaste(model.snapshot(), HashMap(plays)))
            withContext(Dispatchers.IO) {
                val temp = File(file.parentFile, "$MODEL_FILE.tmp")
                temp.writeText(text)
                temp.renameTo(file)
            }
        }
    }
}
