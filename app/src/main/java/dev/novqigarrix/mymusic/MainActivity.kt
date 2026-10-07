package dev.novqigarrix.mymusic

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.novqigarrix.mymusic.shortcuts.LaunchAction
import dev.novqigarrix.mymusic.ui.app.MusicApp
import dev.novqigarrix.mymusic.ui.artwork.LocalArtworkLoader
import dev.novqigarrix.mymusic.ui.theme.MyMusicTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val container get() = (application as MyMusicApplication).container

    /** A launcher-shortcut request waiting to be handled by the UI. */
    private val launchAction = MutableStateFlow<LaunchAction?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Only on a fresh launch: after a configuration change the intent was already handled.
        if (savedInstanceState == null) launchAction.value = LaunchAction.from(intent)
        setContent {
            MyMusicTheme {
                // Surface sets LocalContentColor so un-tinted text and icons default to onBackground.
                Surface(color = MaterialTheme.colorScheme.background) {
                    CompositionLocalProvider(LocalArtworkLoader provides container.artworkLoader) {
                        MusicApp(
                            onPermissionGranted = container.musicRepository::refresh,
                            launchAction = launchAction,
                            onLaunchActionHandled = { launchAction.value = null },
                        )
                    }
                }
            }
        }
    }

    /** A shortcut tapped while the app is already open (singleTop) arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        LaunchAction.from(intent)?.let { launchAction.value = it }
    }

    override fun onStart() {
        super.onStart()
        container.playerConnection.connect()
    }

    override fun onStop() {
        container.playerConnection.disconnect()
        super.onStop()
    }
}
