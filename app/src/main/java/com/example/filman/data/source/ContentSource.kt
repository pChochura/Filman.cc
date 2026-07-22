package com.example.filman.data.source

import com.example.filman.data.model.ActorDetails
import com.example.filman.data.model.DetailedMedia
import com.example.filman.data.model.FilterData
import com.example.filman.data.model.FilterOption
import com.example.filman.data.model.PageResult
import com.example.filman.data.model.SearchResults

interface ContentSource {
    suspend fun getFilters(path: String): FilterData
    suspend fun getCategoryPage(path: String, page: Int = 1): PageResult
    suspend fun searchMovies(query: String): SearchResults
    suspend fun getActorDetails(actorUrl: String): ActorDetails?
    suspend fun getMediaDetails(mediaUrl: String): DetailedMedia?
    suspend fun getCategories(): List<FilterOption>
}
