package com.homeflix.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.homeflix.app.ui.SessionViewModel
import com.homeflix.app.ui.screens.LoginScreen
import com.homeflix.app.ui.screens.MovieDetailScreen
import com.homeflix.app.ui.screens.MoviesScreen
import com.homeflix.app.ui.screens.PlayerScreen
import com.homeflix.app.ui.screens.ServerUrlScreen

@Composable
fun AppNavHost(sessionViewModel: SessionViewModel, modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
        sessionViewModel.logoutEvents.collect {
            navController.navigate(Routes.LOGIN) {
                popUpTo(Routes.MOVIES) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.SERVER, modifier = modifier) {
        composable(Routes.SERVER) {
            ServerUrlScreen(
                onSaved = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SERVER) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLogin = {
                    navController.navigate(Routes.MOVIES) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.MOVIES) {
            MoviesScreen(
                onMovieClick = { movieId -> navController.navigate(Routes.movieDetail(movieId)) },
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
            route = Routes.PLAYER,
            arguments = listOf(navArgument(Routes.ARG_MOVIE_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val movieId = backStackEntry.arguments?.getString(Routes.ARG_MOVIE_ID).orEmpty()
            PlayerScreen(movieId = movieId)
        }
    }
}
