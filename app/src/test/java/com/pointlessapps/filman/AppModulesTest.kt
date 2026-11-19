package com.pointlessapps.filman

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointlessapps.filman.player.lektor.engine.LektorVoiceTester
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceCatalog
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceList
import com.pointlessapps.filman.ui.actor.ActorViewModel
import com.pointlessapps.filman.ui.details.MovieDetailsViewModel
import com.pointlessapps.filman.ui.favorites.FavoritesViewModel
import com.pointlessapps.filman.ui.forkids.ForKidsViewModel
import com.pointlessapps.filman.ui.home.HomeViewModel
import com.pointlessapps.filman.ui.login.LoginViewModel
import com.pointlessapps.filman.ui.movies.MoviesViewModel
import com.pointlessapps.filman.ui.player.PlayerViewModel
import com.pointlessapps.filman.ui.screensaver.ScreensaverViewModel
import com.pointlessapps.filman.ui.search.SearchViewModel
import com.pointlessapps.filman.ui.tvshows.TvShowsViewModel
import com.pointlessapps.filman.ui.watchhistory.WatchHistoryViewModel
import com.pointlessapps.filman.utils.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.nio.file.Files

class AppModulesTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `all appModule viewModels resolve without missing Koin definitions`() {
        val tempDir = Files.createTempDirectory("filman_koin_test").toFile()
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.cacheDir } returns tempDir
        every { mockContext.filesDir } returns tempDir

        val koinApp = koinApplication {
            androidContext(mockContext)
            modules(
                appModule,
                module {
                    val mockCatalog = mockk<SystemVoiceCatalog>(relaxed = true)
                    every { mockCatalog.voices } returns MutableStateFlow(SystemVoiceList())
                    single { mockCatalog }
                    single { mockk<LektorVoiceTester>(relaxed = true) }
                },
            )
        }

        val viewModels = mutableListOf<ViewModel>()
        try {
            val koin = koinApp.koin
            viewModels.add(koin.get<HomeViewModel>())
            viewModels.add(koin.get<LoginViewModel>())
            viewModels.add(koin.get<SearchViewModel>())
            viewModels.add(koin.get<MoviesViewModel>())
            viewModels.add(koin.get<TvShowsViewModel>())
            viewModels.add(koin.get<ForKidsViewModel>())
            viewModels.add(koin.get<MovieDetailsViewModel>())
            viewModels.add(koin.get<PlayerViewModel>())
            viewModels.add(koin.get<ActorViewModel>())
            viewModels.add(koin.get<MainViewModel>())
            viewModels.add(koin.get<ScreensaverViewModel>())
            viewModels.add(koin.get<WatchHistoryViewModel>())
            viewModels.add(koin.get<FavoritesViewModel>())
            viewModels.forEach { assertNotNull(it) }
        } finally {
            viewModels.forEach { it.viewModelScope.cancel() }
            koinApp.close()
            tempDir.deleteRecursively()
        }
    }
}
