package dev.novqigarrix.mymusic.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import dev.novqigarrix.mymusic.ui.app.PlaybackActions
import dev.novqigarrix.mymusic.ui.app.PlaybackInfo
import dev.novqigarrix.mymusic.ui.artist.ArtistScreenRoute
import dev.novqigarrix.mymusic.ui.artist.ArtistViewModel
import dev.novqigarrix.mymusic.ui.collection.AlbumViewModel
import dev.novqigarrix.mymusic.ui.collection.CollectionScreenRoute
import dev.novqigarrix.mymusic.ui.collection.PlaylistViewModel
import dev.novqigarrix.mymusic.ui.home.HomeScreenRoute
import dev.novqigarrix.mymusic.ui.home.HomeViewModel
import dev.novqigarrix.mymusic.ui.library.LibraryScreenRoute
import dev.novqigarrix.mymusic.ui.library.LibraryViewModel
import dev.novqigarrix.mymusic.ui.search.SearchScreenRoute
import dev.novqigarrix.mymusic.ui.search.SearchViewModel
import dev.novqigarrix.mymusic.ui.theme.Motion

private const val TAB_FADE_MS = 180
private const val PUSH_OFFSET_DIVISOR = 4
private const val UNDERLAY_OFFSET_DIVISOR = 10

/**
 * One nested graph per tab keeps each tab's back stack; detail pages are pushed on top of
 * whichever tab is active. Tabs cross-fade, details slide in like Spotify's pages.
 */
@Composable
fun MusicNavHost(
    navController: NavHostController,
    playback: PlaybackInfo,
    actions: PlaybackActions,
    navigator: Navigator,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = HomeGraph,
        modifier = modifier,
        enterTransition = { if (targetState.isDetail()) pushIn() else tabIn() },
        exitTransition = { if (targetState.isDetail()) underlayOut() else tabOut() },
        popEnterTransition = { if (initialState.isDetail()) underlayIn() else tabIn() },
        popExitTransition = { if (initialState.isDetail()) pushOut() else tabOut() },
    ) {
        navigation<HomeGraph>(startDestination = HomeRoute) {
            composable<HomeRoute> {
                Screen { HomeScreenRoute(viewModel(factory = HomeViewModel.Factory), playback, navigator, contentPadding) }
            }
        }
        navigation<SearchGraph>(startDestination = SearchRoute) {
            composable<SearchRoute> {
                Screen {
                    SearchScreenRoute(viewModel(factory = SearchViewModel.Factory), playback, actions, navigator, contentPadding)
                }
            }
        }
        navigation<LibraryGraph>(startDestination = LibraryRoute) {
            composable<LibraryRoute> {
                Screen { LibraryScreenRoute(viewModel(factory = LibraryViewModel.Factory), playback, navigator, contentPadding) }
            }
        }
        composable<AlbumRoute> {
            val viewModel: AlbumViewModel = viewModel(factory = AlbumViewModel.Factory)
            Screen { CollectionScreenRoute(viewModel.uiState, playback, actions, navigator, contentPadding) }
        }
        composable<PlaylistRoute> {
            val viewModel: PlaylistViewModel = viewModel(factory = PlaylistViewModel.Factory)
            Screen { CollectionScreenRoute(viewModel.uiState, playback, actions, navigator, contentPadding) }
        }
        composable<ArtistRoute> {
            Screen {
                ArtistScreenRoute(viewModel(factory = ArtistViewModel.Factory), playback, actions, navigator, contentPadding)
            }
        }
    }
}

/** Opaque backdrop so sliding pages never show the page underneath through transparent areas. */
@Composable
private fun Screen(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) { content() }
}

private fun NavBackStackEntry.isDetail(): Boolean = destination.run {
    hasRoute<AlbumRoute>() || hasRoute<ArtistRoute>() || hasRoute<PlaylistRoute>()
}

private val pushSpec = tween<IntOffset>(Motion.NAV_DURATION_MS, easing = FastOutSlowInEasing)
private val fadeSpec = tween<Float>(Motion.NAV_DURATION_MS, easing = FastOutSlowInEasing)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushIn(): EnterTransition =
    slideInHorizontally(pushSpec) { it / PUSH_OFFSET_DIVISOR } + fadeIn(fadeSpec)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushOut(): ExitTransition =
    slideOutHorizontally(pushSpec) { it / PUSH_OFFSET_DIVISOR } + fadeOut(fadeSpec)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.underlayOut(): ExitTransition =
    slideOutHorizontally(pushSpec) { -it / UNDERLAY_OFFSET_DIVISOR } + fadeOut(fadeSpec)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.underlayIn(): EnterTransition =
    slideInHorizontally(pushSpec) { -it / UNDERLAY_OFFSET_DIVISOR } + fadeIn(fadeSpec)

private fun tabIn(): EnterTransition = fadeIn(tween(TAB_FADE_MS))

private fun tabOut(): ExitTransition = fadeOut(tween(TAB_FADE_MS))
