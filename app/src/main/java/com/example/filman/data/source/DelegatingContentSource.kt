package com.example.filman.data.source

import com.example.filman.data.model.ActorDetails
import com.example.filman.data.model.DetailedMedia
import com.example.filman.data.model.FilterData
import com.example.filman.data.model.FilterOption
import com.example.filman.data.model.PageResult
import com.example.filman.data.model.SearchResults
import com.example.filman.data.scraper.FilmanDataSource

internal class DelegatingContentSource(
    private val filmanDataSource: FilmanDataSource,
    private val obejrzyjDataSource: ObejrzyjDataSource,
    private val sourceManager: SourceManager,
) : ContentSource {

    private val currentSource: ContentSource
        get() = when (sourceManager.activeSource) {
            SourceType.FILMAN -> filmanDataSource
            SourceType.OBEJRZYJ -> obejrzyjDataSource
        }

    override suspend fun getFilters(path: String): FilterData =
        currentSource.getFilters(path)

    override suspend fun getCategoryPage(path: String, page: Int): PageResult =
        currentSource.getCategoryPage(path, page)

    override suspend fun searchMovies(query: String): SearchResults =
        currentSource.searchMovies(query)

    override suspend fun getActorDetails(actorUrl: String): ActorDetails? =
        currentSource.getActorDetails(actorUrl)

    override suspend fun getMediaDetails(mediaUrl: String): DetailedMedia? =
        currentSource.getMediaDetails(mediaUrl)

    override suspend fun getCategories(): List<FilterOption> =
        currentSource.getCategories()
}
