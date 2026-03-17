package com.zonik.app.ui.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Main : Screen("main")
    data object AlbumDetail : Screen("album/{albumId}") {
        fun createRoute(albumId: String) = "album/$albumId"
    }
    data object ArtistDetail : Screen("artist/{artistId}") {
        fun createRoute(artistId: String) = "artist/$artistId"
    }
    data object Stats : Screen("stats")
}

sealed class MainTab(val route: String, val label: String) {
    data object Home : MainTab("home", "Home")
    data object Library : MainTab("library", "Library")
    data object Search : MainTab("search", "Search")
    data object Downloads : MainTab("downloads", "Downloads")
    data object Settings : MainTab("settings", "Settings")
}
