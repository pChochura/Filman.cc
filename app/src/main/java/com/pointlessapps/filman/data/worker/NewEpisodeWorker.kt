package com.pointlessapps.filman.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pointlessapps.filman.config.TiwiConfig
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.TiwiRepository
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.NewEpisodesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.RECENTLY_FINISHED_WINDOW_MS
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.local.TvShowSettingsManager
import com.pointlessapps.filman.data.local.canonicalUrl
import com.pointlessapps.filman.data.local.distinctBySeries
import com.pointlessapps.filman.data.local.isActive
import com.pointlessapps.filman.data.local.isSampled
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.data.model.TvShowSourceSettings
import com.pointlessapps.filman.data.model.getTvShowKey
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class NewEpisodeWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val favoritesManager: FavoritesManager by inject()
    private val progressManager: ProgressManager by inject()
    private val newEpisodesManager: NewEpisodesManager by inject()
    private val tvShowSettingsManager: TvShowSettingsManager by inject()
    private val repository: TiwiRepository by inject()
    private val settingsManager: SettingsManager by inject()

    override suspend fun doWork(): Result {
        val notificationsEnabled = settingsManager.notificationsEnabledFlow.firstOrNull() ?: true
        if (!notificationsEnabled) {
            return Result.success()
        }

        val now = System.currentTimeMillis()
        val favorites = favoritesManager.favoritesFlow.firstOrNull() ?: emptyList()
        val allProgress = progressManager.progressItemsFlow.firstOrNull() ?: emptyList()
        val distinctSeries = allProgress.distinctBySeries()

        // Actively watched or recently finished TV shows from watch history
        val candidateShowsFromProgress = distinctSeries.mapNotNull { item ->
            val isTv = item.season != null || (item.parentUrl != null && item.parentUrl != item.url) || item.parentUrl?.startsWith("show_") == true || item.url.startsWith("show_")
            if (!isTv) return@mapNotNull null

            val isActivelyWatched = !item.isSampled(allProgress) && item.isActive(now)
            val isRecentlyFinished = item is ProgressItem.Watched && !item.hasNextEpisode && (now - item.timestamp <= RECENTLY_FINISHED_WINDOW_MS)

            if (isActivelyWatched || isRecentlyFinished) {
                val showUrl = item.parentUrl ?: item.url
                MovieItem(
                    url = showUrl,
                    titlePl = item.seriesTitle ?: item.titlePl,
                    posterUrl = item.posterUrl,
                    isTvShow = true,
                )
            } else {
                null
            }
        }

        val candidateFavorites = favorites.filter { it.isTvShow }

        val candidateShows = (candidateShowsFromProgress + candidateFavorites)
            .distinctBy { it.url.canonicalUrl() }

        for (show in candidateShows) {
            val showKey = show.getTvShowKey() ?: continue
            val tvSettings =
                tvShowSettingsManager.getSettingsForTvShow(showKey) ?: TvShowSourceSettings()

            val details = repository.getMediaDetails(show.url, isTvShow = true) ?: continue
            val seasons = details.baseItem.seasons ?: continue
            val totalEpisodes = seasons.sumOf { it.episodes.size }

            val lastKnown = tvSettings.lastKnownEpisodeCount ?: 0

            if (totalEpisodes > lastKnown) {
                tvShowSettingsManager.saveSettingsForTvShow(
                    showKey = showKey,
                    settings = tvSettings.copy(lastKnownEpisodeCount = totalEpisodes),
                )

                val latestSeason = seasons.lastOrNull()
                val latestEpisode = latestSeason?.episodes?.lastOrNull()

                if (latestEpisode != null) {
                    val episodeItem = MovieItem(
                        url = latestEpisode.url,
                        titlePl = "${show.titlePl} - ${latestEpisode.title}",
                        posterUrl = show.posterUrl,
                        seriesUrl = show.url,
                        isTvShow = true,
                    )

                    newEpisodesManager.addNewEpisode(episodeItem)

                    postNotification(show.titlePl, latestEpisode.title, latestEpisode.url)
                }
            } else if (lastKnown == 0) {
                tvShowSettingsManager.saveSettingsForTvShow(
                    showKey,
                    tvSettings.copy(lastKnownEpisodeCount = totalEpisodes),
                )
            }
        }

        return Result.success()
    }

    private fun postNotification(showTitle: String, episodeTitle: String, url: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "new_episodes_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.common_new_episodes),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("${TiwiConfig.DEEP_LINK_BASE_URI}?url=$url")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            url.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_movie)
            .setContentTitle(context.getString(R.string.common_notification_new_episode_title, showTitle))
            .setContentText(episodeTitle)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(url.hashCode(), notification)
    }
}
