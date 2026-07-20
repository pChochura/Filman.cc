package com.example.filman.data.tv

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import androidx.core.net.toUri
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.example.filman.R
import com.example.filman.config.FilmanConfig
import com.example.filman.data.model.ProgressItem

class TvRecommendationManager(private val context: Context) {

    private val channelName = context.getString(R.string.tv_channel_continue_watching)
    private val appLinkIntentUri = FilmanConfig.DEEP_LINK_BASE_URI.toUri()

    @SuppressLint("RestrictedApi")
    fun syncContinueWatchingChannel(items: List<ProgressItem.InProgress>) {
        val channelId = getOrCreateChannel()
        if (channelId == -1L) return

        // Clear existing programs for this channel
        val programUri = TvContractCompat.buildPreviewProgramsUriForChannel(channelId)
        context.contentResolver.delete(programUri, null, null)

        // Insert new programs
        items.forEach { item ->
            val builder = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setTitle(item.displayTitle)
                .setPosterArtUri(item.posterUrl.toUri())
                .setType(TvContractCompat.PreviewPrograms.TYPE_MOVIE)

            // Handle TV Show vs Movie deep links
            val intentUriBuilder = FilmanConfig.DEEP_LINK_BASE_URI.toUri().buildUpon()

            if (item.parentUrl != null && item.parentUrl != item.url) {
                // TV Show: pass parentUrl (series url) and episodeUrl
                intentUriBuilder.appendQueryParameter(
                    FilmanConfig.DEEP_LINK_PARAM_URL,
                    item.parentUrl,
                )
                intentUriBuilder.appendQueryParameter(
                    FilmanConfig.DEEP_LINK_PARAM_EPISODE_URL,
                    item.url,
                )
            } else {
                // Movie
                intentUriBuilder.appendQueryParameter(FilmanConfig.DEEP_LINK_PARAM_URL, item.url)
            }

            val intentUri = intentUriBuilder.build()

            builder.setIntentUri(intentUri)

            context.contentResolver.insert(
                TvContractCompat.PreviewPrograms.CONTENT_URI,
                builder.build().toContentValues(),
            )
        }
    }

    private fun getOrCreateChannel(): Long {
        // Find existing channel
        val cursor = context.contentResolver.query(
            TvContractCompat.Channels.CONTENT_URI,
            arrayOf(TvContractCompat.Channels._ID, TvContractCompat.Channels.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )

        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val name = it.getString(1)
                if (name == channelName) {
                    return id
                }
            }
        }

        // Create new channel
        val builder = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(channelName)
            .setAppLinkIntentUri(appLinkIntentUri)

        val channelUri = context.contentResolver.insert(
            TvContractCompat.Channels.CONTENT_URI,
            builder.build().toContentValues(),
        )

        return if (channelUri != null) {
            val channelId = ContentUris.parseId(channelUri)
            TvContractCompat.requestChannelBrowsable(context, channelId)
            channelId
        } else {
            -1L
        }
    }
}
