package dev.novqigarrix.mymusic.ui.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.data.PlaySource
import dev.novqigarrix.mymusic.data.Song
import dev.novqigarrix.mymusic.shortcuts.LaunchAction
import dev.novqigarrix.mymusic.ui.navigation.AlbumRoute
import dev.novqigarrix.mymusic.ui.navigation.ArtistRoute
import dev.novqigarrix.mymusic.ui.navigation.MusicBottomBar
import dev.novqigarrix.mymusic.ui.navigation.MusicNavHost
import dev.novqigarrix.mymusic.ui.navigation.Navigator
import dev.novqigarrix.mymusic.ui.navigation.PlaylistRoute
import dev.novqigarrix.mymusic.ui.navigation.TopLevelDestination
import dev.novqigarrix.mymusic.ui.player.MiniPlayer
import dev.novqigarrix.mymusic.ui.player.NowPlayingCallbacks
import dev.novqigarrix.mymusic.ui.player.NowPlayingScreen
import dev.novqigarrix.mymusic.ui.player.PlayerSheetState
import dev.novqigarrix.mymusic.ui.player.PlayerViewModel
import dev.novqigarrix.mymusic.ui.player.QueueSheet
import dev.novqigarrix.mymusic.ui.player.rememberPlayerSheetState
import dev.novqigarrix.mymusic.ui.theme.Sizes
import dev.novqigarrix.mymusic.ui.theme.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val AudioPermission =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private const val MINI_FADE_SPEED = 3f
private val ChromeFadeHeight = 32.dp

/** Root composable: asks for audio access, then hosts the tabs, mini player and full player. */
@Composable
fun MusicApp(
    onPermissionGranted: () -> Unit,
    launchAction: StateFlow<LaunchAction?>,
    onLaunchActionHandled: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(context.hasAudioPermission()) }
    var wasDenied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        wasDenied = !granted
    }
    // The user may grant access from system settings and come back.
    LifecycleResumeEffect(Unit) {
        hasPermission = context.hasAudioPermission()
        onPauseOrDispose { }
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission) onPermissionGranted()
    }

    if (hasPermission) {
        // Shortcuts wait here until access is granted and the shell exists.
        MusicShell(launchAction = launchAction, onLaunchActionHandled = onLaunchActionHandled)
    } else {
        PermissionScreen(
            showSettingsHint = wasDenied,
            onRequest = { launcher.launch(AudioPermission) },
            onOpenSettings = { context.openAppSettings() },
        )
    }
}

@Composable
private fun MusicShell(
    launchAction: StateFlow<LaunchAction?>,
    onLaunchActionHandled: () -> Unit,
    playerViewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory),
) {
    val context = LocalContext.current
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val progress = playerViewModel.progressMs.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val sheetState = rememberPlayerSheetState()
    val scope = rememberCoroutineScope()
    var optionsSong by remember { mutableStateOf<Song?>(null) }
    var showQueue by rememberSaveable { mutableStateOf(false) }

    val playback = remember(playerState) {
        PlaybackInfo(
            currentSongId = playerState.song?.id,
            playWhenReady = playerState.playWhenReady,
            isPlaying = playerState.isPlaying,
            shuffleEnabled = playerState.shuffleEnabled,
            smartShuffle = playerState.smartShuffle,
            source = playerState.source,
            likedIds = playerState.likedIds,
        )
    }
    val actions = remember(playerViewModel) {
        PlaybackActions(
            play = playerViewModel::play,
            shufflePlay = playerViewModel::shufflePlay,
            playOrToggle = playerViewModel::playOrToggle,
            toggleLike = playerViewModel::toggleLike,
            showSongOptions = { optionsSong = it },
        )
    }
    val navigator = remember(navController) {
        Navigator(
            openAlbum = { navController.navigate(AlbumRoute(it)) },
            openArtist = { navController.navigate(ArtistRoute(it)) },
            openPlaylist = { navController.navigate(PlaylistRoute(it)) },
            back = { navController.popBackStack() },
        )
    }
    val callbacks = remember(playerViewModel, navigator, sheetState) {
        NowPlayingCallbacks(
            onCollapse = { scope.launch { sheetState.collapse() } },
            onTogglePlayPause = playerViewModel::togglePlayPause,
            onNext = playerViewModel::next,
            onPrevious = playerViewModel::previous,
            onSeek = playerViewModel::seekTo,
            onToggleShuffle = playerViewModel::cycleShuffle,
            onCycleRepeat = playerViewModel::cycleRepeat,
            onToggleLike = { playerViewModel.uiState.value.song?.let { playerViewModel.toggleLike(it.id) } },
            onOpenQueue = { showQueue = true },
            onShare = { context.shareSong(it) },
            onMore = { optionsSong = it },
            onOpenArtist = { artistId ->
                scope.launch { sheetState.collapse() }
                navigator.openArtist(artistId)
            },
        )
    }

    val selectedTab = rememberSelectedTab(navController)
    val song = playerState.song

    val pendingLaunch by launchAction.collectAsStateWithLifecycle()
    val currentTab by rememberUpdatedState(selectedTab)
    LaunchedEffect(pendingLaunch) {
        val action = pendingLaunch ?: return@LaunchedEffect
        // Nothing here may suspend: clearing the action changes this effect's key and cancels it.
        if (action != LaunchAction.Resume) scope.launch { sheetState.collapse() }
        when (action) {
            LaunchAction.Resume -> playerViewModel.resume()
            LaunchAction.OpenLibrary -> navController.navigateToTab(TopLevelDestination.Library, currentTab)
            is LaunchAction.Open -> when (val source = action.source) {
                is PlaySource.FromAlbum -> navigator.openAlbum(source.albumId)
                is PlaySource.FromArtist -> navigator.openArtist(source.artistId)
                is PlaySource.FromPlaylist -> navigator.openPlaylist(source.kind)
            }
        }
        onLaunchActionHandled()
    }

    LaunchedEffect(song == null) {
        if (song == null) sheetState.collapse()
    }
    BackHandler(enabled = sheetState.isExpanded) {
        scope.launch { sheetState.collapse() }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottomChrome = Sizes.bottomBarHeight + navBarInset +
            if (song != null) Sizes.miniPlayerHeight + Spacing.small else 0.dp

        MusicNavHost(
            navController = navController,
            playback = playback,
            actions = actions,
            navigator = navigator,
            contentPadding = PaddingValues(bottom = bottomChrome),
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.3f to Color.Black.copy(alpha = 0.9f),
                            0.55f to Color.Black.copy(alpha = 0.98f),
                            1f to Color.Black,
                        ),
                    )
                }
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(ChromeFadeHeight))
            AnimatedVisibility(
                visible = song != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                if (song != null) {
                    MiniPlayer(
                        song = song,
                        state = playerState,
                        progressMs = { progress.value },
                        onOpen = { scope.launch { sheetState.expand() } },
                        onTogglePlayPause = playerViewModel::togglePlayPause,
                        onToggleLike = { playerViewModel.toggleLike(song.id) },
                        onNext = playerViewModel::next,
                        onPrevious = playerViewModel::previous,
                        modifier = Modifier
                            .sheetDrag(sheetState, heightPx, scope)
                            .graphicsLayer {
                                alpha = (1f - sheetState.progress.value * MINI_FADE_SPEED).coerceIn(0f, 1f)
                            },
                    )
                }
            }
            MusicBottomBar(
                selected = selectedTab,
                onSelect = { navController.navigateToTab(it, selectedTab) },
            )
        }

        val showFullPlayer by remember { derivedStateOf { sheetState.progress.value > 0f } }
        if (showFullPlayer && song != null) {
            NowPlayingScreen(
                song = song,
                state = playerState,
                progressMs = { progress.value },
                callbacks = callbacks,
                modifier = Modifier
                    .offset { IntOffset(0, ((1f - sheetState.progress.value) * heightPx).roundToInt()) }
                    .sheetDrag(sheetState, heightPx, scope),
            )
        }
    }

    if (showQueue) {
        QueueSheet(
            state = playerState,
            onDismiss = { showQueue = false },
            onSkipTo = { playerViewModel.skipTo(it.id) },
            onMore = { optionsSong = it },
        )
    }

    optionsSong?.let { selected ->
        SongOptionsSheet(
            song = selected,
            isLiked = selected.id in playerState.likedIds,
            onDismiss = { optionsSong = null },
            onToggleLike = { playerViewModel.toggleLike(selected.id) },
            onAddToQueue = {
                playerViewModel.addToQueue(selected)
                optionsSong = null
                Toast.makeText(context, R.string.added_to_queue, Toast.LENGTH_SHORT).show()
            },
            onGoToAlbum = {
                optionsSong = null
                showQueue = false
                scope.launch { sheetState.collapse() }
                navigator.openAlbum(selected.albumId)
            },
            onGoToArtist = {
                optionsSong = null
                showQueue = false
                scope.launch { sheetState.collapse() }
                navigator.openArtist(selected.artistId)
            },
            onShare = {
                optionsSong = null
                context.shareSong(selected)
            },
        )
    }
}

/** Vertical drag that moves the player sheet; works on both the mini player and the full player. */
@Composable
private fun Modifier.sheetDrag(state: PlayerSheetState, heightPx: Float, scope: CoroutineScope): Modifier =
    draggable(
        orientation = Orientation.Vertical,
        state = rememberDraggableState { delta -> scope.launch { state.dragBy(-delta / heightPx) } },
        onDragStopped = { velocity -> state.settle(-velocity / heightPx) },
    )

/** The tab that owns the current destination; detail pages keep the tab they were opened from. */
@Composable
private fun rememberSelectedTab(navController: NavHostController): TopLevelDestination {
    val entry by navController.currentBackStackEntryAsState()
    var lastTab by rememberSaveable { mutableStateOf(TopLevelDestination.Home) }
    val owner = entry?.destination?.let { destination -> TopLevelDestination.entries.firstOrNull { it.owns(destination) } }
    LaunchedEffect(owner) {
        if (owner != null) lastTab = owner
    }
    return owner ?: lastTab
}

private fun NavHostController.navigateToTab(tab: TopLevelDestination, current: TopLevelDestination) {
    if (tab == current) {
        // Re-tapping the active tab returns to its root, like Spotify.
        popBackStack(tab.start, inclusive = false)
        return
    }
    navigate(tab.graph) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun Context.hasAudioPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, AudioPermission) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun Context.shareSong(song: Song) {
    val text = getString(R.string.share_text, song.title, song.artist.ifBlank { getString(R.string.unknown_artist) })
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(send, null))
}
