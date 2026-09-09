package com.homeflix.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.homeflix.app.ui.SessionViewModel
import com.homeflix.app.ui.screens.HomeScreen
import com.homeflix.app.ui.screens.LoginScreen
import com.homeflix.app.ui.screens.LoginViewModel
import com.homeflix.app.ui.screens.MovieDetailScreen
import com.homeflix.app.ui.screens.MoviesScreen
import com.homeflix.app.ui.screens.PlayerScreen
import com.homeflix.app.ui.screens.SearchScreen
import com.homeflix.app.ui.screens.SeriesDetailScreen
import com.homeflix.app.ui.screens.ServerUrlScreen
import com.homeflix.app.ui.screens.ServerUrlViewModel
import com.homeflix.app.ui.screens.TvSeriesScreen

@Composable
fun AppNavHost(
    sessionViewModel: SessionViewModel,
    modifier: Modifier = Modifier,
    serverUrlViewModel: ServerUrlViewModel? = null,
    loginViewModel: LoginViewModel? = null
) {
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
        sessionViewModel.logoutEvents.collect {
            navController.navigate(Routes.LOGIN) {
                popUpTo(Routes.HOME) { inclusive = true }
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Column(modifier = modifier) {
        Box(modifier = Modifier.weight(1f)) {
            NavHost(
                navController = navController,
                startDestination = Routes.SERVER,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Routes.SERVER) {
                    val onSaved = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.SERVER) { inclusive = true }
                        }
                    }
                    val vm = serverUrlViewModel
                    if (vm != null) {
                        ServerUrlScreen(onSaved = onSaved, viewModel = vm)
                    } else {
                        ServerUrlScreen(onSaved = onSaved)
                    }
                }

                composable(Routes.LOGIN) {
                    val onLogin = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                    val vm = loginViewModel
                    if (vm != null) {
                        LoginScreen(onLogin = onLogin, viewModel = vm)
                    } else {
                        LoginScreen(onLogin = onLogin)
                    }
                }

                composable(Routes.HOME) {
                    HomeScreen(
                        onItemClick = { itemId -> navController.navigate(Routes.player(itemId)) },
                        onLogout = sessionViewModel::logout
                    )
                }

                composable(Routes.MOVIES) {
                    MoviesScreen(
                        onMovieClick = { movieId -> navController.navigate(Routes.movieDetail(movieId)) },
                        onLogout = sessionViewModel::logout
                    )
                }

                composable(Routes.TV_SERIES) {
                    TvSeriesScreen(
                        onSeriesClick = { seriesId -> navController.navigate(Routes.seriesDetail(seriesId)) },
                        onLogout = sessionViewModel::logout
                    )
                }

                composable(Routes.SEARCH) {
                    SearchScreen(
                        onItemClick = { item ->
                            if (item.isSeries) {
                                navController.navigate(Routes.seriesDetail(item.id))
                            } else {
                                navController.navigate(Routes.movieDetail(item.id))
                            }
                        },
                        onLogout = sessionViewModel::logout
                    )
                }

                composable(
                    route = Routes.MOVIE_DETAIL,
                    arguments = listOf(navArgument(Routes.ARG_MOVIE_ID) { type = NavType.StringType })
                ) { backStackEntry ->
                    val movieId = backStackEntry.arguments?.getString(Routes.ARG_MOVIE_ID).orEmpty()
                    MovieDetailScreen(
                        onPlay = { navController.navigate(Routes.player(movieId)) },
                        onBack = { navController.popBackStack() },
                        onLogout = sessionViewModel::logout
                    )
                }

                composable(
                    route = Routes.SERIES_DETAIL,
                    arguments = listOf(navArgument(Routes.ARG_SERIES_ID) { type = NavType.StringType })
                ) { backStackEntry ->
                    val seriesId = backStackEntry.arguments?.getString(Routes.ARG_SERIES_ID).orEmpty()
                    SeriesDetailScreen(
                        onPlayEpisode = { episodeId -> navController.navigate(Routes.player(episodeId)) },
                        onBack = { navController.popBackStack() },
                        onLogout = sessionViewModel::logout
                    )
                }

                composable(
                    route = Routes.PLAYER,
                    arguments = listOf(navArgument(Routes.ARG_MOVIE_ID) { type = NavType.StringType })
                ) {
                    PlayerScreen(
                        onBack = { navController.popBackStack() },
                        onLogout = sessionViewModel::logout
                    )
                }
            }
        }

        if (currentRoute == Routes.HOME || currentRoute == Routes.MOVIES || currentRoute == Routes.TV_SERIES || currentRoute == Routes.SEARCH) {
            NavigationBar(windowInsets = WindowInsets(0.dp)) {
                NavigationBarItem(
                    selected = currentRoute == Routes.HOME,
                    onClick = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.MOVIES,
                    onClick = {
                        navController.navigate(Routes.MOVIES) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Filled.Movie, contentDescription = "Movies") },
                    label = { Text("Movies") }
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.TV_SERIES,
                    onClick = {
                        navController.navigate(Routes.TV_SERIES) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Filled.Tv, contentDescription = "TV Shows") },
                    label = { Text("TV Shows") }
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.SEARCH,
                    onClick = {
                        navController.navigate(Routes.SEARCH) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                    label = { Text("Search") }
                )
            }
        }
    }
}
