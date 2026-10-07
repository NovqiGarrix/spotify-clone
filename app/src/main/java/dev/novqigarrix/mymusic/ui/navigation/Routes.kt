package dev.novqigarrix.mymusic.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.data.PlaylistKind
import kotlinx.serialization.Serializable

@Serializable data object HomeGraph
@Serializable data object SearchGraph
@Serializable data object LibraryGraph

@Serializable data object HomeRoute
@Serializable data object SearchRoute
@Serializable data object LibraryRoute

@Serializable data class AlbumRoute(val albumId: Long)
@Serializable data class ArtistRoute(val artistId: Long)
@Serializable data class PlaylistRoute(val kind: PlaylistKind)

enum class TopLevelDestination(
    val graph: Any,
    val start: Any,
    @param:StringRes val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Home(HomeGraph, HomeRoute, R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home),
    Search(SearchGraph, SearchRoute, R.string.nav_search, Icons.Rounded.Search, Icons.Outlined.Search),
    Library(
        LibraryGraph,
        LibraryRoute,
        R.string.nav_library,
        Icons.AutoMirrored.Rounded.LibraryBooks,
        Icons.AutoMirrored.Outlined.LibraryBooks,
    ),
    ;

    /** Whether [destination] belongs to this tab's nested graph. */
    fun owns(destination: NavDestination): Boolean = destination.hierarchy.any {
        when (this) {
            Home -> it.hasRoute<HomeGraph>()
            Search -> it.hasRoute<SearchGraph>()
            Library -> it.hasRoute<LibraryGraph>()
        }
    }
}

/** Callbacks for moving between screens, so screens never see the NavController. */
data class Navigator(
    val openAlbum: (Long) -> Unit,
    val openArtist: (Long) -> Unit,
    val openPlaylist: (PlaylistKind) -> Unit,
    val back: () -> Unit,
)
