package dev.novqigarrix.mymusic.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val MAX_RECENTS = 12
private const val KEY = "recent_sources"
private const val SEPARATOR = "|"

/** Most recently played albums / artists / playlists, newest first. Powers Home and Library "Recents". */
class RecentsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("recents", Context.MODE_PRIVATE)

    private val _recents = MutableStateFlow(
        prefs.getString(KEY, null).orEmpty()
            .split(SEPARATOR)
            .mapNotNull(PlaySource::parse),
    )
    val recents: StateFlow<List<PlaySource>> = _recents.asStateFlow()

    fun record(source: PlaySource) {
        _recents.update { current -> (listOf(source) + current.filterNot { it == source }).take(MAX_RECENTS) }
        prefs.edit { putString(KEY, _recents.value.joinToString(SEPARATOR) { it.key }) }
    }
}
