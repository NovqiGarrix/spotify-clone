package dev.novqigarrix.mymusic.recommend

import dev.novqigarrix.mymusic.data.Song

private const val FRESH_WINDOW_SEC = 30L * 24 * 60 * 60

/** What was going on when a song started: the context the bandit conditions its prediction on. */
class PlayContext(
    val previous: Song?,
    /** 0 night, 1 morning, 2 afternoon, 3 evening. */
    val daypart: Int,
    val sessionSkippedArtists: Map<Long, Int> = emptyMap(),
    val sessionSkippedAlbums: Set<Long> = emptySet(),
    val sessionSkippedDecades: Map<Int, Int> = emptyMap(),
    val sessionFinishedArtists: Set<Long> = emptySet(),
)

/** Facts about the song that come from the user's own history rather than its tags. */
class SongHistory(val isLiked: Boolean, val plays: Int, val nowSec: Long)

/**
 * Starting beliefs for features whose meaning is known up front. Everything else (artists, albums,
 * single songs, time-of-day crosses) starts neutral and is learned purely from listening.
 */
internal fun featurePrior(feature: String): Prior? = when (feature) {
    "bias" -> Prior(0.0, 1.0)
    "liked" -> Prior(0.6, 0.1)
    "session_skip_artist" -> Prior(-0.5, 0.1)
    "session_skip_artist_2" -> Prior(-0.5, 0.1)
    "session_skip_album" -> Prior(-0.4, 0.1)
    "session_skip_decade" -> Prior(-0.4, 0.1)
    "session_finish_artist" -> Prior(0.2, 0.1)
    else -> null
}

/**
 * Sparse binary features for "will they finish [song] right now?". Only tags MediaStore gives us
 * are available, so similarity is expressed through artist, album, decade and length, plus what
 * happened earlier in this listening session.
 */
internal fun songFeatures(song: Song, context: PlayContext, history: SongHistory): List<String> = buildList {
    val decade = song.decade
    val length = lengthBucket(song.durationMs)
    val daypart = "dp${context.daypart}"

    add("bias")
    add("s:${song.id}")
    add("ar:${song.artistId}")
    add("al:${song.albumId}")
    decade?.let { add("dec:$it") }
    add("len:$length")
    if (history.isLiked) add("liked")
    if (history.plays == 0) add("unplayed")
    if (history.nowSec - song.dateAddedSec < FRESH_WINDOW_SEC) add("fresh")

    add("$daypart|ar:${song.artistId}")
    decade?.let { add("$daypart|dec:$it") }
    add("$daypart|len:$length")

    context.previous?.let { previous ->
        if (previous.artistId == song.artistId) add("same_artist")
        if (previous.albumId == song.albumId) add("same_album")
        if (decade != null && previous.decade == decade) add("same_decade")
        add("after:${previous.artistId}|ar:${song.artistId}")
    }

    val artistSkips = context.sessionSkippedArtists[song.artistId] ?: 0
    if (artistSkips >= 1) add("session_skip_artist")
    if (artistSkips >= 2) add("session_skip_artist_2")
    if (song.albumId in context.sessionSkippedAlbums) add("session_skip_album")
    if (decade != null && (context.sessionSkippedDecades[decade] ?: 0) >= 2) add("session_skip_decade")
    if (song.artistId in context.sessionFinishedArtists) add("session_finish_artist")
}

internal val Song.decade: Int? get() = if (year > 0) year / 10 else null

private fun lengthBucket(durationMs: Long): Int = when {
    durationMs < 150_000 -> 0
    durationMs < 240_000 -> 1
    durationMs < 360_000 -> 2
    else -> 3
}
