package com.pointlessapps.filman

import android.app.Application
import com.pointlessapps.filman.config.FilmanConfig
import com.pointlessapps.filman.data.cache.ModelCache
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SearchHistoryManager
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.data.scraper.EkinoScraper
import com.pointlessapps.filman.data.scraper.FilmanClient
import com.pointlessapps.filman.data.scraper.FilmanScraper
import com.pointlessapps.filman.data.scraper.TmdbClient
import com.pointlessapps.filman.data.scraper.VideoUrlResolver
import com.pointlessapps.filman.data.scraper.extractors.OkHttpDownloader
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
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

fun getUnsafeOkHttpClient(): OkHttpClient {
    try {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    } catch (e: Exception) {
        throw RuntimeException(e)
    }
}

val appModule = module {
    singleOf(::SessionManager)
    singleOf(::SettingsManager)
    singleOf(::FavoritesManager)
    singleOf(::SearchHistoryManager)
    singleOf(::ProgressManager)
    singleOf(::TvRecommendationManager)
    singleOf(::FilmanClient)
    singleOf(::ModelCache)
    singleOf(::FilmanScraper)
    singleOf(::EkinoScraper)
    singleOf(::VideoUrlResolver)
    single { getUnsafeOkHttpClient() }
    singleOf(::TmdbClient)

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
}

class FilmanApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@FilmanApplication)
            modules(appModule)
        }

        NewPipe.init(OkHttpDownloader(getUnsafeOkHttpClient()), Localization.DEFAULT)

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
