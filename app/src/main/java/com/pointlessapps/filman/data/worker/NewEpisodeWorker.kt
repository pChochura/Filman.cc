package com.pointlessapps.filman.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pointlessapps.filman.R
import com.pointlessapps.filman.config.FilmanConfig
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.NewEpisodesManager
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.local.TvShowSettingsManager
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.TvShowSourceSettings
import com.pointlessapps.filman.data.model.getTvShowKey
import com.pointlessapps.filman.data.scraper.FilmanScraper
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import androidx.core.net.toUri

class NewEpisodeWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val favoritesManager: FavoritesManager by inject()
    private val newEpisodesManager: NewEpisodesManager by inject()
    private val tvShowSettingsManager: TvShowSettingsManager by inject()
    private val scraper: FilmanScraper by inject()
    private val settingsManager: SettingsManager by inject()

    override suspend fun doWork(): Result {
        val notificationsEnabled = settingsManager.notificationsEnabledFlow.firstOrNull() ?: true
        if (!notificationsEnabled) {
            return Result.success()
        }

        val favorites = favoritesManager.favoritesFlow.firstOrNull() ?: emptyList()

        for (show in favorites) {
            val showKey = show.getTvShowKey() ?: continue
            val tvSettings =
                tvShowSettingsManager.getSettingsForTvShow(showKey) ?: TvShowSourceSettings()

            val details = scraper.getMediaDetails(show.url) ?: continue
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun postNotification(showTitle: String, episodeTitle: String, url: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "new_episodes_channel"

        val channel = NotificationChannel(
            channelId,
            context.getString(R.string.home_new_episodes),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("${FilmanConfig.DEEP_LINK_BASE_URI}?url=$url")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            url.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_movie)
            .setContentTitle(context.getString(R.string.notification_new_episode_title, showTitle))
            .setContentText(episodeTitle)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(url.hashCode(), notification)
    }
}
