package com.homeflix.app.navigation

object Routes {
    const val LOGIN = "login"
    const val MOVIES = "movies"
    const val MOVIE_DETAIL = "movie/{movieId}"
    const val PLAYER = "player/{movieId}"

    const val ARG_MOVIE_ID = "movieId"

    fun movieDetail(movieId: String) = "movie/$movieId"

    fun player(movieId: String) = "player/$movieId"
}
