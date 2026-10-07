package dev.novqigarrix.mymusic.shortcuts

import android.content.Context
import android.net.Uri
import android.graphics.Canvas
import android.graphics.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.data.LibraryState
import dev.novqigarrix.mymusic.data.MusicLibrary
import dev.novqigarrix.mymusic.data.MusicRepository
import dev.novqigarrix.mymusic.data.PlaySource
import dev.novqigarrix.mymusic.data.RecentsRepository
import dev.novqigarrix.mymusic.ui.artwork.ArtworkLoader
import dev.novqigarrix.mymusic.ui.artwork.ArtworkSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Launchers show ~4-5 shortcuts in total and four are static, so only the latest item fits. */
private const val MAX_RECENT_SHORTCUTS = 1
private const val UPDATE_DEBOUNCE_MS = 1_000L

/** Adaptive icons are 108dp with the visible circle in the middle 72dp; 2.25x density headroom. */
private const val ADAPTIVE_ICON_PX = 243

/**
 * Keeps the launcher's long-press menu in sync with what was played most recently, like Spotify's
 * recently-played shortcuts. Static shortcuts (Resume, Liked Songs, Local Files, Your Library) live in
 * `res/xml/shortcuts.xml`; these dynamic ones show the real cover art.
 */
class ShortcutPublisher(
    private val context: Context,
    scope: CoroutineScope,
    recentsRepository: RecentsRepository,
    musicRepository: MusicRepository,
    private val artworkLoader: ArtworkLoader,
) {
    init {
        @OptIn(FlowPreview::class)
        scope.launch {
            combine(
                recentsRepository.recents.map { recents ->
                    // Liked Songs and Local Files already have static shortcuts.
                    recents.filterNot { it is PlaySource.FromPlaylist }.take(MAX_RECENT_SHORTCUTS)
                }.distinctUntilChanged(),
                musicRepository.state.filterIsInstance<LibraryState.Ready>(),
            ) { recents, state -> recents to state.library }
                .debounce(UPDATE_DEBOUNCE_MS)
                .collect { (recents, library) -> publish(recents, library) }
        }
    }

    private suspend fun publish(recents: List<PlaySource>, library: MusicLibrary) {
        val shortcuts = recents.mapIndexedNotNull { rank, source -> build(source, library, rank) }
        // Rate limiting can reject updates while in the background; the next update will catch up.
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    private suspend fun build(source: PlaySource, library: MusicLibrary, rank: Int): ShortcutInfoCompat? {
        val (label, artworkUri) = when (source) {
            is PlaySource.FromAlbum -> library.album(source.albumId)?.let { it.title to it.artworkUri }
            is PlaySource.FromArtist -> library.artist(source.artistId)?.let { it.name to it.artworkUri }
            is PlaySource.FromPlaylist -> null
        } ?: return null
        val icon = artworkUri?.let { coverIcon(it) } ?: IconCompat.createWithResource(context, R.drawable.ic_shortcut_library)
        val title = label.ifBlank { context.getString(R.string.unknown_album) }
        return ShortcutInfoCompat.Builder(context, source.key)
            .setShortLabel(title)
            .setLongLabel(title)
            .setIcon(icon)
            .setIntent(LaunchAction.intentToOpen(context, source))
            .setRank(rank)
            .build()
    }

    private suspend fun coverIcon(uri: Uri): IconCompat? {
        val cover = artworkLoader.load(uri, ArtworkSize.Small)?.asAndroidBitmap() ?: return null
        val bitmap = createBitmap(ADAPTIVE_ICON_PX, ADAPTIVE_ICON_PX)
        Canvas(bitmap).drawBitmap(cover, null, Rect(0, 0, ADAPTIVE_ICON_PX, ADAPTIVE_ICON_PX), null)
        return IconCompat.createWithAdaptiveBitmap(bitmap)
    }
}
