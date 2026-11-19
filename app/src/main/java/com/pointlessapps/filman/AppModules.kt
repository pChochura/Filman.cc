package com.pointlessapps.filman

import com.pointlessapps.filman.core.data.BuildConfig
import com.pointlessapps.filman.data.api.FilmanRepository
import com.pointlessapps.filman.data.api.StreamApiLinkSource
import com.pointlessapps.filman.data.api.SubtitleRepository
import com.pointlessapps.filman.data.api.VideoUrlResolver
import com.pointlessapps.filman.data.api.createOkHttpClient
import com.pointlessapps.filman.data.api.tmdb.TmdbApiClient
import com.pointlessapps.filman.data.api.tmdb.TmdbLocaleProvider
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.NewEpisodesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SearchHistoryManager
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.local.TvShowSettingsManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.recommendation.RecommendationManager
import com.pointlessapps.filman.data.sync.SyncManager
import com.pointlessapps.filman.data.sync.api.SyncApiClient
import com.pointlessapps.filman.data.tv.TvRecommendationManager
import com.pointlessapps.filman.player.lektor.engine.LektorModelManager
import com.pointlessapps.filman.player.lektor.engine.LektorVoiceTester
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceCatalog
import com.pointlessapps.filman.ui.actor.ActorViewModel
import com.pointlessapps.filman.ui.details.MovieDetailsViewModel
import com.pointlessapps.filman.ui.favorites.FavoritesViewModel
import com.pointlessapps.filman.ui.forkids.ForKidsViewModel
import com.pointlessapps.filman.ui.home.HomeViewModel
import com.pointlessapps.filman.ui.login.LoginViewModel
import com.pointlessapps.filman.ui.movies.MoviesViewModel
import com.pointlessapps.filman.ui.player.PlayerViewModel
import com.pointlessapps.filman.ui.screensaver.ScreensaverViewModel
import com.pointlessapps.filman.ui.search.SearchViewModel
import com.pointlessapps.filman.ui.tvshows.TvShowsViewModel
import com.pointlessapps.filman.ui.watchhistory.WatchHistoryViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import java.io.File

private val networkModule = module {
    single { createOkHttpClient() }
    single { TmdbLocaleProvider() }
    singleOf(::TmdbApiClient)
    single {
        StreamApiLinkSource(
            get(),
            BuildConfig.STREAM_API_BASE_URL,
            File(androidContext().cacheDir, "stream_responses"),
        )
    }
    single { SyncApiClient(get(), BuildConfig.STREAM_API_BASE_URL) }
    single { SubtitleRepository(httpClient = get()) }
}

private val dataModule = module {
    singleOf(::SessionManager)
    singleOf(::SettingsManager)
    singleOf(::TvShowSettingsManager)
    singleOf(::FavoritesManager)
    singleOf(::WatchlistManager)
    singleOf(::NewEpisodesManager)
    singleOf(::SearchHistoryManager)
    singleOf(::ProgressManager)
    singleOf(::TvRecommendationManager)
    singleOf(::SyncManager)
    singleOf(::FilmanRepository)
    singleOf(::VideoUrlResolver)
    singleOf(::RecommendationManager)
}

private val playerModule = module {
    single { LektorModelManager(androidContext()) }
    single { SystemVoiceCatalog(androidContext()) }
    single { LektorVoiceTester(androidContext(), get(), get()) }
}

private val viewModelModule = module {
    viewModelOf(::HomeViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::MoviesViewModel)
    viewModelOf(::TvShowsViewModel)
    viewModelOf(::ForKidsViewModel)
    viewModelOf(::MovieDetailsViewModel)
    viewModelOf(::PlayerViewModel)
    viewModelOf(::ActorViewModel)
    viewModelOf(::MainViewModel)
    viewModelOf(::ScreensaverViewModel)
    viewModelOf(::WatchHistoryViewModel)
    viewModelOf(::FavoritesViewModel)
}

val appModule = module {
    includes(networkModule, dataModule, playerModule, viewModelModule)
}
