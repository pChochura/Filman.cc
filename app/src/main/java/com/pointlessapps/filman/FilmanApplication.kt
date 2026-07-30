package com.pointlessapps.filman

import android.app.Application
import com.pointlessapps.filman.config.FilmanConfig
import com.pointlessapps.filman.data.cache.ModelCache
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.local.SearchHistoryManager
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.data.scraper.FilmanClient
import com.pointlessapps.filman.data.scraper.FilmanScraper
import com.pointlessapps.filman.data.scraper.VideoUrlResolver
import com.pointlessapps.filman.data.tv.TvRecommendationManager
import com.pointlessapps.filman.ui.actor.ActorViewModel
import com.pointlessapps.filman.ui.details.MovieDetailsViewModel
import com.pointlessapps.filman.ui.forkids.ForKidsViewModel
import com.pointlessapps.filman.ui.home.HomeViewModel
import com.pointlessapps.filman.ui.login.LoginViewModel
import com.pointlessapps.filman.ui.movies.MoviesViewModel
import com.pointlessapps.filman.ui.player.PlayerViewModel
import com.pointlessapps.filman.ui.search.SearchViewModel
import com.pointlessapps.filman.ui.tvshows.TvShowsViewModel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    singleOf(::SessionManager)
    singleOf(::FavoritesManager)
    singleOf(::SearchHistoryManager)
    singleOf(::ProgressManager)
    singleOf(::TvRecommendationManager)
    singleOf(::FilmanClient)
    singleOf(::ModelCache)
    singleOf(::FilmanScraper)
    singleOf(::VideoUrlResolver)

    viewModelOf(::HomeViewModel)
    viewModelOf(::LoginViewModel)
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
                val distinctSeries = items.distinctBy { p ->
                    p.parentUrl?.substringAfter(FilmanConfig.DOMAIN)?.trimEnd('/')
                }
                val mapped = distinctSeries.mapNotNull { p ->
                    if (p is ProgressItem.Watched) {
                        if (p.parentUrl != null && p.parentUrl != p.url && p.hasNextEpisode) {
                            ProgressItem.NextEpisode(
                                url = p.url,
                                parentUrl = p.parentUrl,
                                posterUrl = p.posterUrl,
                                titlePl = p.seriesTitle ?: p.titlePl,
                                seriesTitle = p.seriesTitle,
                            )
                        } else {
                            null
                        }
                    } else {
                        p
                    }
                }

                tvRecommendationManager.syncContinueWatchingChannel(mapped)
            }
        }
    }
}
