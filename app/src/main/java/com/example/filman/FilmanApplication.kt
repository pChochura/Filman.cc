package com.example.filman

import android.app.Application
import com.example.filman.data.cache.ModelCache
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.local.ProgressManager
import com.example.filman.data.local.SessionManager
import com.example.filman.data.model.ProgressItem
import com.example.filman.data.scraper.FilmanClient
import com.example.filman.data.scraper.FilmanDataSource
import com.example.filman.data.source.ContentSource
import com.example.filman.data.source.DelegatingContentSource
import com.example.filman.data.source.ObejrzyjDataSource
import com.example.filman.data.source.SourceManager
import com.example.filman.data.tv.TvRecommendationManager
import com.example.filman.ui.actor.ActorViewModel
import com.example.filman.ui.auth.AuthViewModel
import com.example.filman.ui.details.MovieDetailsViewModel
import com.example.filman.ui.forkids.ForKidsViewModel
import com.example.filman.ui.home.HomeViewModel
import com.example.filman.ui.movies.MoviesViewModel
import com.example.filman.ui.player.PlayerViewModel
import com.example.filman.ui.search.SearchViewModel
import com.example.filman.ui.tvshows.TvShowsViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val appModule = module {
    singleOf(::SessionManager)
    singleOf(::FavoritesManager)
    singleOf(::ProgressManager)
    singleOf(::TvRecommendationManager)
    singleOf(::FilmanClient)
    singleOf(::ModelCache)

    singleOf(::SourceManager)
    singleOf(::FilmanDataSource)

    single {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(
                    Json {
                        prettyPrint = true
                        isLenient = true
                        ignoreUnknownKeys = true
                    },
                )
            }
        }
    }
    singleOf(::ObejrzyjDataSource)
    singleOf(::DelegatingContentSource).bind<ContentSource>()

    viewModelOf(::AuthViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::MoviesViewModel)
    viewModelOf(::TvShowsViewModel)
    viewModelOf(::ForKidsViewModel)
    viewModelOf(::MovieDetailsViewModel)
    viewModelOf(::PlayerViewModel)
    viewModelOf(::PlayerViewModel)
    viewModelOf(::ActorViewModel)
}

class FilmanApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@FilmanApplication)
            modules(appModule)
        }

        setupTvRecommendations()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun setupTvRecommendations() {
        val tvRecommendationManager: TvRecommendationManager by inject()
        val progressManager: ProgressManager by inject()

        GlobalScope.launch {
            progressManager.progressItemsFlow.collect { items ->
                tvRecommendationManager.syncContinueWatchingChannel(
                    items.filterIsInstance<ProgressItem.InProgress>(),
                )
            }
        }
    }
}
