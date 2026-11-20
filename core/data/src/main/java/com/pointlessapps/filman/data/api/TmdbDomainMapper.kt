package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.data.api.dto.ActorDetail
import com.pointlessapps.filman.data.api.dto.MediaSummary
import com.pointlessapps.filman.data.api.dto.Movie
import com.pointlessapps.filman.data.api.dto.TvShow
import com.pointlessapps.filman.data.api.tmdb.TmdbApiClient
import com.pointlessapps.filman.data.model.ActorDetails
import com.pointlessapps.filman.data.model.ActorInfo
import com.pointlessapps.filman.data.model.CategoryInfo
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.EpisodeLink
import com.pointlessapps.filman.data.model.MediaMetadata
import com.pointlessapps.filman.data.model.MediaRef
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.Rating
import com.pointlessapps.filman.data.model.Season
import kotlin.time.Duration

internal fun MediaSummary.toMovieItem(): MovieItem {
    val isTv = mediaType == "TV_SHOW"
    val itemUrl = if (isTv) "show_$id" else "movie_$id"
    return MovieItem(
        url = itemUrl,
        detailsRequest = DetailsRequest.Url(itemUrl, isTv),
        titlePl = title,
        titleEn = originalTitle,
        posterUrl = if (!posterPath.isNullOrBlank()) TmdbApiClient.POSTER_BASE_URL + posterPath else "",
        backgroundUrl = if (!backdropPath.isNullOrBlank()) TmdbApiClient.BACKDROP_BASE_URL + backdropPath else "",
        description = overview,
        year = releaseDate?.take(4)?.toIntOrNull(),
        tiwiRating = voteAverage?.takeIf { it > 0.0 }?.let { Rating(it.toFloat(), 10f) },
        imdbRating = voteAverage?.takeIf { it > 0.0 }?.let { Rating(it.toFloat(), 10f) },
        isTvShow = isTv,
        genreIds = genreIds,
        voteCount = voteCount,
    )
}

internal fun TvShow.toDetailedMedia(ref: MediaRef): DetailedMedia {
    val season = ref.season
    val episode = ref.episode

    val mappedSeasons = seasons.filter { it.seasonNumber > 0 }.map { s ->
        Season(
            name = s.name,
            episodes = s.episodes.map { ep ->
                EpisodeLink(
                    title = ep.name,
                    url = "show_${id}_${s.seasonNumber}_${ep.episodeNumber}",
                )
            },
        )
    }

    var currentEpisodeTitle: String? = null
    var nextEpisodeUrl: String? = null
    var prevEpisodeUrl: String? = null

    if (season != null && episode != null) {
        val seasonIndex = mappedSeasons.indexOfFirst { s ->
            s.episodes.any { it.url.startsWith("show_${id}_${season}_") }
        }
        val epList = mappedSeasons.getOrNull(seasonIndex)?.episodes.orEmpty()
        val epIndex = epList.indexOfFirst { it.url == "show_${id}_${season}_${episode}" }
        if (epIndex != -1) {
            currentEpisodeTitle = epList[epIndex].title
            val nextEp = epList.getOrNull(epIndex + 1)
                ?: mappedSeasons.getOrNull(seasonIndex + 1)?.episodes?.firstOrNull()
            nextEpisodeUrl = nextEp?.url
            val prevEp = if (epIndex > 0) {
                epList.getOrNull(epIndex - 1)
            } else {
                mappedSeasons.getOrNull(seasonIndex - 1)?.episodes?.lastOrNull()
            }
            prevEpisodeUrl = prevEp?.url
        }
    }

    val canonicalShowUrl = if (season != null && episode != null) {
        "show_${id}_${season}_${episode}"
    } else {
        "show_$id"
    }

    return DetailedMedia(
        tmdbId = ref.tmdbId,
        imdbId = imdbId,
        baseItem = MovieItem(
            url = canonicalShowUrl,
            seriesUrl = "show_$id",
            seasonNumber = season,
            episodeNumber = episode,
            episodeTitle = currentEpisodeTitle,
            nextEpisodeUrl = nextEpisodeUrl,
            prevEpisodeUrl = prevEpisodeUrl,
            titlePl = if (currentEpisodeTitle != null) "$title - S${season}E${episode}" else title,
            titleEn = originalTitle,
            posterUrl = if (posterPath != null) TmdbApiClient.POSTER_BASE_URL + posterPath else "",
            backgroundUrl = if (backdropPath != null) TmdbApiClient.BACKDROP_BASE_URL + backdropPath else "",
            description = overview,
            year = firstAirDate?.take(4)?.toIntOrNull(),
            tiwiRating = voteAverage?.takeIf { it > 0.0 }?.let { Rating(it.toFloat(), 10f) },
            imdbRating = voteAverage?.takeIf { it > 0.0 }?.let { Rating(it.toFloat(), 10f) },
            isTvShow = true,
            seasons = mappedSeasons,
            genreIds = genres.map { it.id },
        ),
        embeds = emptyList(),
        actors = cast.map {
            ActorInfo(
                role = it.role,
                name = it.name,
                avatarUrl = if (it.profilePath != null) TmdbApiClient.PROFILE_BASE_URL + it.profilePath else null,
                url = it.id.toString(),
            )
        },
        categories = genres.map { CategoryInfo(name = it.name, url = it.id.toString(), id = it.id) },
        metaInfo = MediaMetadata(
            year = firstAirDate?.take(4)?.toIntOrNull(),
            views = null,
            duration = duration?.let { Duration.parseIsoString("PT${it}M") },
            countries = countries,
        ),
        similarMovies = similar.filter { !it.posterPath.isNullOrBlank() }.map { it.toMovieItem() },
        recommendations = recommendations.filter { !it.posterPath.isNullOrBlank() }.map { it.toMovieItem() },
        recommendationsTotalPages = recommendationsTotalPages,
        trailerUrl = trailerUrl,
    )
}

internal fun Movie.toDetailedMedia(): DetailedMedia = DetailedMedia(
    tmdbId = id,
    imdbId = imdbId,
    baseItem = MovieItem(
        url = "movie_$id",
        titlePl = title,
        titleEn = originalTitle,
        posterUrl = if (posterPath != null) TmdbApiClient.POSTER_BASE_URL + posterPath else "",
        backgroundUrl = if (backdropPath != null) TmdbApiClient.BACKDROP_BASE_URL + backdropPath else "",
        description = overview,
        year = releaseDate?.take(4)?.toIntOrNull(),
        tiwiRating = voteAverage?.takeIf { it > 0.0 }?.let { Rating(it.toFloat(), 10f) },
        imdbRating = voteAverage?.takeIf { it > 0.0 }?.let { Rating(it.toFloat(), 10f) },
        isTvShow = false,
        genreIds = genres.map { it.id },
    ),
    embeds = emptyList(),
    actors = cast.map {
        ActorInfo(
            role = it.role,
            name = it.name,
            avatarUrl = if (it.profilePath != null) TmdbApiClient.PROFILE_BASE_URL + it.profilePath else null,
            url = it.id.toString(),
        )
    },
    categories = genres.map { CategoryInfo(name = it.name, url = it.id.toString(), id = it.id) },
    metaInfo = MediaMetadata(
        year = releaseDate?.take(4)?.toIntOrNull(),
        views = null,
        duration = duration?.let { Duration.parseIsoString("PT${it}M") },
        countries = countries,
    ),
    similarMovies = similar.filter { !it.posterPath.isNullOrBlank() }.map { it.toMovieItem() },
    recommendations = recommendations.filter { !it.posterPath.isNullOrBlank() }.map { it.toMovieItem() },
    recommendationsTotalPages = recommendationsTotalPages,
    trailerUrl = trailerUrl,
)

internal fun ActorDetail.toActorDetails(): ActorDetails = ActorDetails(
    name = name,
    avatarUrl = if (profilePath != null) TmdbApiClient.PROFILE_BASE_URL + profilePath else "",
    birthDate = birthday,
    birthPlace = placeOfBirth,
    height = null,
    description = biography,
    filmwebRating = null,
    moviesDirector = directorCredits.filter { !it.posterPath.isNullOrBlank() }.map { it.toMovieItem() },
    moviesWriter = writerCredits.filter { !it.posterPath.isNullOrBlank() }.map { it.toMovieItem() },
    moviesCast = castCredits.filter { !it.posterPath.isNullOrBlank() }.map { it.toMovieItem() },
)
