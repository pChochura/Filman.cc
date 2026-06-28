package com.example.filman.ui.player

import com.example.filman.data.model.Season

object PlayerStateHolder {
    var seriesTitle: String? = null
    var seasons: List<Season> = emptyList()
    var currentSeasonIndex: Int = -1
    var currentEpisodeIndex: Int = -1

    fun clear() {
        seriesTitle = null
        seasons = emptyList()
        currentSeasonIndex = -1
        currentEpisodeIndex = -1
    }

    fun hasNextEpisode(): Boolean {
        if (currentSeasonIndex == -1 || currentEpisodeIndex == -1) return false
        val currentSeason = seasons.getOrNull(currentSeasonIndex) ?: return false
        
        return if (currentEpisodeIndex + 1 < currentSeason.episodes.size) {
            true // Next episode in the same season
        } else {
            currentSeasonIndex + 1 < seasons.size // First episode of next season
        }
    }

    fun hasPrevEpisode(): Boolean {
        if (currentSeasonIndex == -1 || currentEpisodeIndex == -1) return false
        
        return if (currentEpisodeIndex - 1 >= 0) {
            true // Prev episode in the same season
        } else {
            currentSeasonIndex - 1 >= 0 // Last episode of prev season
        }
    }

    fun moveToNextEpisode() {
        if (!hasNextEpisode()) return
        val currentSeason = seasons[currentSeasonIndex]
        if (currentEpisodeIndex + 1 < currentSeason.episodes.size) {
            currentEpisodeIndex++
        } else {
            currentSeasonIndex++
            currentEpisodeIndex = 0
        }
    }

    fun moveToPrevEpisode() {
        if (!hasPrevEpisode()) return
        if (currentEpisodeIndex - 1 >= 0) {
            currentEpisodeIndex--
        } else {
            currentSeasonIndex--
            currentEpisodeIndex = seasons[currentSeasonIndex].episodes.size - 1
        }
    }
    
    fun getCurrentSeasonName(): String? {
        return seasons.getOrNull(currentSeasonIndex)?.name
    }

    fun getCurrentEpisodeUrl(): String? {
        return seasons.getOrNull(currentSeasonIndex)?.episodes?.getOrNull(currentEpisodeIndex)?.url
    }
}
