package com.example.filman.config

object FilmanConfig {
    const val BASE_URL = "https://filman.cc"
    const val DOMAIN = "filman.cc"
    const val LOGIN_PATH = "/logowanie"
    const val LOGIN_URL = "$BASE_URL$LOGIN_PATH"

    // Website Paths
    const val PATH_HOME = "/"
    const val PATH_MOVIES = "/filmy/"
    const val PATH_FOR_KIDS = "/dla-dzieci-pl/"
    const val PATH_MOVIES_CATEGORY = "/filmy/category:"
    const val PATH_TV_SHOWS_CATEGORY = "/seriale/category:"
    const val PATH_TV_SHOWS_ALL = "/seriale/category:all/"
    const val PATH_SEARCH = "/search?phrase="
    const val PATH_SERIAL_ONLINE = "/serial-online/"

    // Sort Paths
    const val SORT_VIEW = "sort:view/"
    const val SORT_FILMWEB = "sort:filmweb/"
    const val SORT_DATE = "sort:date/"
    const val SORT_NEW_EPISODE = "sort:newepisode/"
    const val SORT_RATE = "sort:rate/"
}
