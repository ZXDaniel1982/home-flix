package com.homeflix.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.homeflix.app.ui.screens.LoginScreen
import com.homeflix.app.ui.screens.MovieDetailScreen
import com.homeflix.app.ui.screens.MoviesScreen
import com.homeflix.app.ui.screens.PlayerScreen

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.LOGIN, modifier = modifier) {
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
                onMovieClick = { movieId -> navController.navigate(Routes.movieDetail(movieId)) }
            )
        }

        composable(
            route = Routes.MOVIE_DETAIL,
            arguments = listOf(navArgument(Routes.ARG_MOVIE_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val movieId = backStackEntry.arguments?.getString(Routes.ARG_MOVIE_ID).orEmpty()
            MovieDetailScreen(
                movieId = movieId,
                onPlay = { navController.navigate(Routes.player(movieId)) }
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
