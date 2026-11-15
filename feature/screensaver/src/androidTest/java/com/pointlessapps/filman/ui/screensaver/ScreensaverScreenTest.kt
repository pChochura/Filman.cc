package com.pointlessapps.filman.ui.screensaver

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.model.MovieItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreensaverScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun navigationKeepsButtonFocusedAndTogglesCurrentSlide() {
        val first = MovieItem(url = "movie_1", titlePl = "First slide", posterUrl = "")
        val second = MovieItem(url = "movie_2", titlePl = "Second slide", posterUrl = "")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val addLabel = context.getString(R.string.common_add_to_watchlist)
        val removeLabel = context.getString(R.string.common_remove_from_watchlist)
        var watchlist by mutableStateOf(listOf(second))
        val clicked = mutableListOf<String>()

        compose.setContent {
            ScreensaverBackground(
                movies = listOf(first, second),
                watchlist = watchlist,
                slideDuration = 60_000L,
                onToggleWatchlist = { movie ->
                    clicked.add(movie.url)
                    watchlist = if (watchlist.any { it.url == movie.url }) {
                        watchlist.filterNot { it.url == movie.url }
                    } else {
                        watchlist + movie
                    }
                },
            )
        }

        val add = compose.onNodeWithText(addLabel)
        add.assertIsFocused()
        val buttonId = add.fetchSemanticsNode().id
        add.performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        val remove = compose.onNodeWithText(removeLabel)
        remove.assertIsFocused()
        assertEquals(buttonId, remove.fetchSemanticsNode().id)
        remove.performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        compose.runOnIdle {
            assertEquals(listOf(second.url), clicked)
            assertEquals(emptyList<MovieItem>(), watchlist)
        }
        val addAgain = compose.onNodeWithText(addLabel)
        addAgain.assertIsFocused()
        assertEquals(buttonId, addAgain.fetchSemanticsNode().id)
        addAgain.performKeyInput {
            keyDown(Key.DirectionLeft)
            keyUp(Key.DirectionLeft)
        }
        val firstButton = compose.onNodeWithText(addLabel)
        assertEquals(buttonId, firstButton.fetchSemanticsNode().id)
        firstButton.performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        compose.runOnIdle { assertEquals(listOf(first), watchlist) }
        val firstRemove = compose.onNodeWithText(removeLabel)
        firstRemove.performKeyInput {
            keyDown(Key.DirectionLeft)
            keyUp(Key.DirectionLeft)
        }
        compose.onNodeWithText(addLabel).assertIsFocused()
        compose.runOnIdle { watchlist = listOf(second) }
        compose.onNodeWithText(removeLabel).assertIsFocused()
        assertEquals(buttonId, compose.onNodeWithText(removeLabel).fetchSemanticsNode().id)
    }

    @Test
    fun manualNavigationRestartsAutomaticAdvance() {
        val first = MovieItem(url = "movie_1", titlePl = "First slide", posterUrl = "")
        val second = MovieItem(url = "movie_2", titlePl = "Second slide", posterUrl = "")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val addLabel = context.getString(R.string.common_add_to_watchlist)
        val removeLabel = context.getString(R.string.common_remove_from_watchlist)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ScreensaverBackground(
                movies = listOf(first, second),
                watchlist = listOf(second),
                slideDuration = 2_000L,
                onToggleWatchlist = {},
            )
        }
        compose.mainClock.advanceTimeBy(1_500)
        val buttonId = compose.onNodeWithText(addLabel).fetchSemanticsNode().id
        compose.onNodeWithText(addLabel).performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(buttonId, compose.onNodeWithText(removeLabel).fetchSemanticsNode().id)
        compose.mainClock.advanceTimeBy(1_100)
        assertEquals(buttonId, compose.onNodeWithText(addLabel).fetchSemanticsNode().id)
        compose.onNodeWithText(addLabel).assertIsFocused()
    }
}
