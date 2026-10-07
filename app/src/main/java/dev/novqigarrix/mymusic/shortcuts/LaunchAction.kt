package dev.novqigarrix.mymusic.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dev.novqigarrix.mymusic.MainActivity
import dev.novqigarrix.mymusic.data.PlaySource

private const val SCHEME = "mymusic"
private const val HOST_RESUME = "resume"
private const val HOST_LIBRARY = "library"
private const val HOST_OPEN = "open"

/** What a launcher shortcut asked the app to do. */
sealed interface LaunchAction {
    /** Continue playback where the user left off. */
    data object Resume : LaunchAction

    /** Open the Your Library tab. */
    data object OpenLibrary : LaunchAction

    /** Open an album, artist or playlist page. */
    data class Open(val source: PlaySource) : LaunchAction

    companion object {
        /** `mymusic://resume`, `mymusic://library` or `mymusic://open/album:12`; anything else is ignored. */
        fun from(intent: Intent?): LaunchAction? {
            val uri = intent?.data?.takeIf { it.scheme == SCHEME } ?: return null
            return when (uri.host) {
                HOST_RESUME -> Resume
                HOST_LIBRARY -> OpenLibrary
                HOST_OPEN -> uri.lastPathSegment?.let(PlaySource::parse)?.let(::Open)
                else -> null
            }
        }

        fun intentToOpen(context: Context, source: PlaySource): Intent =
            Intent(Intent.ACTION_VIEW, "$SCHEME://$HOST_OPEN/${source.key}".toUri())
                .setClass(context, MainActivity::class.java)
    }
}
