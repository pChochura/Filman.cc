package com.pointlessapps.filman

import android.content.Context
import com.pointlessapps.filman.player.lektor.engine.LektorVoiceTester
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceCatalog
import com.pointlessapps.filman.ui.actor.ActorViewModel
import com.pointlessapps.filman.ui.details.MovieDetailsViewModel
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
                    single { mockk<SystemVoiceCatalog>(relaxed = true) }
                    single { mockk<LektorVoiceTester>(relaxed = true) }
                },
            )
        }

        try {
            val koin = koinApp.koin
            assertNotNull(koin.get<HomeViewModel>())
            assertNotNull(koin.get<LoginViewModel>())
            assertNotNull(koin.get<SearchViewModel>())
            assertNotNull(koin.get<MoviesViewModel>())
            assertNotNull(koin.get<TvShowsViewModel>())
            assertNotNull(koin.get<ForKidsViewModel>())
            assertNotNull(koin.get<MovieDetailsViewModel>())
            assertNotNull(koin.get<PlayerViewModel>())
            assertNotNull(koin.get<ActorViewModel>())
            assertNotNull(koin.get<MainViewModel>())
            assertNotNull(koin.get<ScreensaverViewModel>())
            assertNotNull(koin.get<WatchHistoryViewModel>())
        } finally {
            koinApp.close()
            tempDir.deleteRecursively()
        }
    }
}
