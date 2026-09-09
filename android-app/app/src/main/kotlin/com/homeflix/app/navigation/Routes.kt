package com.homeflix.app.navigation

object Routes {
    const val SERVER = "server"
    const val LOGIN = "login"
    const val MOVIES = "movies"
    const val MOVIE_DETAIL = "movie/{movieId}"
    const val PLAYER = "player/{movieId}"
    const val TV_SERIES = "tv"
    const val SERIES_DETAIL = "series/{seriesId}"

    const val ARG_MOVIE_ID = "movieId"
    const val ARG_SERIES_ID = "seriesId"

    fun movieDetail(movieId: String) = "movie/$movieId"

    fun player(movieId: String) = "player/$movieId"

    fun seriesDetail(seriesId: String) = "series/$seriesId"
}
