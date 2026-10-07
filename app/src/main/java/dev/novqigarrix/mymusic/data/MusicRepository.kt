package dev.novqigarrix.mymusic.data

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val RESCAN_DEBOUNCE_MS = 400L

/** Single source of truth for the on-device music library. Rescans when MediaStore changes. */
class MusicRepository(context: Context, scope: CoroutineScope) {

    private val scanner = MediaStoreScanner(context.contentResolver)
    private val refreshRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    init {
        @OptIn(FlowPreview::class)
        scope.launch {
            refreshRequests.debounce(RESCAN_DEBOUNCE_MS).collectLatest {
                val library = withContext(Dispatchers.IO) { scanner.scan() }
                _state.value = LibraryState.Ready(library)
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    if (_state.value is LibraryState.Ready) refresh()
                }
            },
        )
    }

    /** Call once audio permission is granted, and whenever a manual rescan is wanted. */
    fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

    /** The scanned library, starting the scan if the UI hasn't (e.g. playback resumed from the widget). */
    suspend fun awaitLibrary(): MusicLibrary {
        if (_state.value is LibraryState.Loading) refresh()
        return state.filterIsInstance<LibraryState.Ready>().first().library
    }
}
