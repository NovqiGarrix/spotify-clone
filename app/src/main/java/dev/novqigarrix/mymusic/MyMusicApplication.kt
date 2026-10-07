package dev.novqigarrix.mymusic

import android.app.Application
import android.content.Context
import dev.novqigarrix.mymusic.data.LikesRepository
import dev.novqigarrix.mymusic.data.MusicRepository
import dev.novqigarrix.mymusic.data.RecentsRepository
import dev.novqigarrix.mymusic.playback.NowPlayingStore
import dev.novqigarrix.mymusic.playback.PlayerConnection
import dev.novqigarrix.mymusic.recommend.Recommender
import dev.novqigarrix.mymusic.shortcuts.ShortcutPublisher
import dev.novqigarrix.mymusic.ui.artwork.ArtworkLoader
import dev.novqigarrix.mymusic.widget.NowPlayingWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container; the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val musicRepository = MusicRepository(context, appScope)
    val likesRepository = LikesRepository(context)
    val recentsRepository = RecentsRepository(context)
    val recommender = Recommender(context, appScope, musicRepository, likesRepository)
    val playerConnection = PlayerConnection(context, appScope)
    val artworkLoader = ArtworkLoader(context)
    val nowPlayingStore = NowPlayingStore(context)
    val shortcutPublisher = ShortcutPublisher(context, appScope, recentsRepository, musicRepository, artworkLoader)
    val widgetUpdater = NowPlayingWidgetUpdater(
        context = context,
        scope = appScope,
        nowPlaying = nowPlayingStore.state,
        musicRepository = musicRepository,
        likesRepository = likesRepository,
        artworkLoader = artworkLoader,
    )
}

class MyMusicApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
