package com.pointlessapps.filman

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.pointlessapps.filman.data.api.FilmanRepository
import com.pointlessapps.filman.data.api.StreamApiLinkSource
import com.pointlessapps.filman.data.api.VideoUrlResolver
import com.pointlessapps.filman.data.api.createOkHttpClient
import com.pointlessapps.filman.data.api.extractors.OkHttpDownloader
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
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.data.recommendation.RecommendationManager
import com.pointlessapps.filman.data.sync.SyncManager
import com.pointlessapps.filman.data.sync.api.SyncApiClient
import com.pointlessapps.filman.data.tv.TvRecommendationManager
import com.pointlessapps.filman.data.worker.NewEpisodeWorker
import com.pointlessapps.filman.data.worker.SyncWorker
import com.pointlessapps.filman.player.lektor.engine.LektorModelManager
import com.pointlessapps.filman.player.lektor.engine.LektorVoiceTester
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceCatalog
import com.pointlessapps.filman.ui.actor.ActorViewModel
import com.pointlessapps.filman.ui.details.MovieDetailsViewModel
import com.pointlessapps.filman.ui.forkids.ForKidsViewModel
import com.pointlessapps.filman.ui.home.HomeViewModel
import com.pointlessapps.filman.ui.login.LoginViewModel
import com.pointlessapps.filman.ui.movies.MoviesViewModel
import com.pointlessapps.filman.ui.player.PlayerViewModel
import com.pointlessapps.filman.ui.screensaver.ScreensaverViewModel
import com.pointlessapps.filman.ui.search.SearchViewModel
import com.pointlessapps.filman.ui.tvshows.TvShowsViewModel
import com.pointlessapps.filman.ui.watchhistory.WatchHistoryViewModel
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
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization
import java.io.File
import java.util.concurrent.TimeUnit

val appModule =
    module {
        singleOf(::SessionManager)
        singleOf(::SettingsManager)
        singleOf(::TvShowSettingsManager)
        singleOf(::FavoritesManager)
        singleOf(::WatchlistManager)
        singleOf(::NewEpisodesManager)
        singleOf(::SearchHistoryManager)
        singleOf(::ProgressManager)
        singleOf(::TvRecommendationManager)

        singleOf(::TmdbLocaleProvider)
        singleOf(::TmdbApiClient)
        single {
            StreamApiLinkSource(
                createOkHttpClient(),
                com.pointlessapps.filman.core.data.BuildConfig.STREAM_API_BASE_URL,
                File(androidContext().cacheDir, "stream_responses"),
            )
        }
        single { SyncApiClient(get(), com.pointlessapps.filman.core.data.BuildConfig.STREAM_API_BASE_URL) }
        singleOf(::SyncManager)
        singleOf(::FilmanRepository)
        singleOf(::VideoUrlResolver)
        single { LektorModelManager(androidContext()) }
        single { SystemVoiceCatalog(androidContext()) }
        single { LektorVoiceTester(androidContext(), get(), get()) }

        single { createOkHttpClient() }
        singleOf(::RecommendationManager)

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
    }

class FilmanApplication :
    Application(),
    ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader
            .Builder(this)
            .okHttpClient { createOkHttpClient() }
            .build()

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@FilmanApplication)
            modules(appModule)
        }

        NewPipe.init(OkHttpDownloader(createOkHttpClient()), Localization.DEFAULT)

        setupTvRecommendations()
        setupWorkManager()
    }

    private fun setupWorkManager() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<NewEpisodeWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "NewEpisodeWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun setupTvRecommendations() {
        val tvRecommendationManager: TvRecommendationManager by inject()
        val progressManager: ProgressManager by inject()

        GlobalScope.launch {
            progressManager.progressItemsFlow.collect { items ->
                val distinctSeries =
                    items.distinctBy { p ->
                        p.parentUrl ?: p.url
                    }
                val mapped =
                    distinctSeries.mapNotNull { p ->
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

        val watchlistManager: WatchlistManager by inject()
        GlobalScope.launch {
            watchlistManager.watchlistFlow.collect { items ->
                tvRecommendationManager.syncWatchlistChannel(items)
            }
        }

        SyncWorker.schedule(this)
        val appSession: SessionManager by inject()
        val syncManager: SyncManager by inject()
        if (appSession.isLoggedIn()) {
            syncManager.triggerSync()
        }
    }
}
