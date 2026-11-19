package com.pointlessapps.filman.ui.components

import com.pointlessapps.filman.data.model.DecadeOption
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.data.model.MediaFilterState
import com.pointlessapps.filman.data.model.SortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterMenuBuilderTest {
    private val genres = listOf(
        FilterOption(id = "28", label = "Action"),
        FilterOption(id = "35", label = "Comedy"),
    )

    @Test
    fun `build includes media type submenu only when showMediaTypeFilter is true`() {
        val withoutMediaType = FilterMenuBuilder.build(
            filterState = MediaFilterState(),
            availableGenres = genres,
            showMediaTypeFilter = false,
            onUpdateFilter = {},
            onResetFilters = {},
        )
        assertFalse(withoutMediaType.items.any { it.id == "menu_filter_media_type" })

        val withMediaType = FilterMenuBuilder.build(
            filterState = MediaFilterState(),
            availableGenres = genres,
            showMediaTypeFilter = true,
            onUpdateFilter = {},
            onResetFilters = {},
        )
        assertTrue(withMediaType.items.any { it.id == "menu_filter_media_type" })
    }

    @Test
    fun `build exposes static submenu IDs for live drawer updates`() {
        val menu = FilterMenuBuilder.build(
            filterState = MediaFilterState(),
            availableGenres = genres,
            showMediaTypeFilter = false,
            onUpdateFilter = {},
            onResetFilters = {},
        )

        val submenuIds = menu.items.filterIsInstance<FilmanOverlayMenuItem.NestedMenu>().map { it.id }
        assertEquals(
            listOf("menu_filter_sort", "menu_filter_genres", "menu_filter_decade", "menu_filter_rating"),
            submenuIds,
        )
        assertFalse(menu.items.any { it.id == "btn_reset_filters" })
    }

    @Test
    fun `selecting genre and reset button invoke expected callbacks`() {
        val updates = mutableListOf<MediaFilterState>()
        var resetCount = 0

        val initial = MediaFilterState(
            selectedGenreIds = listOf(28),
            decade = DecadeOption.YEAR_2010S,
            sortBy = SortOption.RATING_DESC,
        )
        val menu = FilterMenuBuilder.build(
            filterState = initial,
            availableGenres = genres,
            showMediaTypeFilter = false,
            onUpdateFilter = updates::add,
            onResetFilters = { resetCount++ },
        )

        val genresMenu = menu.items
            .filterIsInstance<FilmanOverlayMenuItem.NestedMenu>()
            .single { it.id == "menu_filter_genres" }

        assertTrue(genresMenu.items.any { it.id == "btn_clear_genres" })

        val comedyOption = genresMenu.items
            .filterIsInstance<FilmanOverlayMenuItem.Option>()
            .single { it.id == "genre_35" }
        comedyOption.onClick()
        assertEquals(listOf(28, 35), updates.single().selectedGenreIds)

        val resetBtn = menu.items
            .filterIsInstance<FilmanOverlayMenuItem.Button>()
            .single { it.id == "btn_reset_filters" }
        resetBtn.onClick(object : FilmanOverlayClickScope {
            override fun popBack() = Unit
        })
        assertEquals(1, resetCount)
    }
}
