package com.pointlessapps.filman

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import coil.ImageLoader
import coil.ImageLoaderFactory
import io.sentry.android.core.SentryAndroid
import com.pointlessapps.filman.data.api.extractors.OkHttpDownloader
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.local.distinctBySeries
import com.pointlessapps.filman.data.local.extractFullyWatchedUrls
import com.pointlessapps.filman.data.local.filterUnwatched
import com.pointlessapps.filman.data.local.toActiveContinueWatchingItems
import com.pointlessapps.filman.data.sync.SyncManager
import com.pointlessapps.filman.data.tv.TvRecommendationManager
import com.pointlessapps.filman.data.worker.NewEpisodeWorker
import com.pointlessapps.filman.data.worker.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization
import java.util.concurrent.TimeUnit

class TiwiApplication :
    Application(),
    ImageLoaderFactory {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val okHttpClient: OkHttpClient by inject()

    override fun newImageLoader(): ImageLoader =
        ImageLoader
            .Builder(this)
            .okHttpClient { okHttpClient }
            .build()

    override fun onCreate() {
        super.onCreate()

        SentryAndroid.init(this) { options ->
            options.dsn = "https://45df68d3b6da409a8383b152bc750de5@pipistrelus-glitchtip-a927ea-89-168-108-145.sslip.io/1"
            options.setTag("device_type", "android_tv")
            options.isAnrEnabled = true
        }

        startKoin {
            androidLogger()
            androidContext(this@TiwiApplication)
            modules(appModule)
        }

        NewPipe.init(OkHttpDownloader(okHttpClient), Localization.DEFAULT)

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

    private fun setupTvRecommendations() {
        val tvRecommendationManager: TvRecommendationManager by inject()
        val progressManager: ProgressManager by inject()
        val watchlistManager: WatchlistManager by inject()

        applicationScope.launch {
            progressManager.progressItemsFlow.collect { items ->
                val mapped = items.distinctBySeries().toActiveContinueWatchingItems(allProgressItems = items)
                tvRecommendationManager.syncContinueWatchingChannel(mapped)
            }
        }

        applicationScope.launch {
            combine(
                watchlistManager.watchlistFlow,
                progressManager.progressItemsFlow,
            ) { watchlist, progressItems ->
                val fullyWatchedUrls = progressItems.distinctBySeries().extractFullyWatchedUrls()
                watchlist.filterUnwatched(fullyWatchedUrls)
            }.collect { items ->
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

typealias FilmanApplication = TiwiApplication
