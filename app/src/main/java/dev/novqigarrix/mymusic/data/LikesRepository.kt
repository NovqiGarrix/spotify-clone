package dev.novqigarrix.mymusic.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Liked songs, stored as songId -> time liked so "Liked Songs" can sort newest first. */
class LikesRepository(context: Context) {

    private val prefs = context.getSharedPreferences("liked_songs", Context.MODE_PRIVATE)

    private val _likes = MutableStateFlow(readAll())
    val likes: StateFlow<Map<Long, Long>> = _likes.asStateFlow()

    fun toggle(songId: Long) {
        _likes.update { current ->
            if (songId in current) current - songId else current + (songId to System.currentTimeMillis())
        }
        val likedAt = _likes.value[songId]
        prefs.edit {
            if (likedAt == null) remove(songId.toString()) else putLong(songId.toString(), likedAt)
        }
    }

    private fun readAll(): Map<Long, Long> = prefs.all.mapNotNull { (key, value) ->
        val id = key.toLongOrNull() ?: return@mapNotNull null
        val likedAt = value as? Long ?: return@mapNotNull null
        id to likedAt
    }.toMap()
}
