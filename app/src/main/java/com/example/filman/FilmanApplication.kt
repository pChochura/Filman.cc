package com.example.filman

import android.app.Application
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.local.ProgressManager
import com.example.filman.data.local.SessionManager
import com.example.filman.data.local.WatchedManager
import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.ui.auth.AuthViewModel
import com.example.filman.ui.details.MovieDetailsViewModel
import com.example.filman.ui.home.HomeViewModel
import com.example.filman.ui.player.PlayerViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    singleOf(::SessionManager)
    singleOf(::FavoritesManager)
    singleOf(::ProgressManager)
    singleOf(::WatchedManager)
    singleOf(::FilmanScraper)

    viewModelOf(::AuthViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::MovieDetailsViewModel)
    viewModelOf(::PlayerViewModel)
}

class FilmanApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@FilmanApplication)
            modules(appModule)
        }
    }
}
