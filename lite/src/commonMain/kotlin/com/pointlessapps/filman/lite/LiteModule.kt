package com.pointlessapps.filman.lite

import com.pointlessapps.filman.lite.data.LiteApi
import com.pointlessapps.filman.lite.data.LiteRepository
import com.pointlessapps.filman.lite.download.DownloadManager
import com.pointlessapps.filman.lite.ui.LiteViewModel
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module

internal fun liteModule(baseUrl: String = defaultBaseUrl): Module = module {
    single {
        HttpClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 15_000
            }
        }
    }
    single { ServerSettings(downloadsDirectory(), baseUrl) }
    single { FilmanSession(downloadsDirectory()) }
    single {
        LiteRepository(
            LiteApi(
                client = get(),
                baseUrl = { get<ServerSettings>().url.value },
                credentials = { get<FilmanSession>().credentials.value },
            ),
        )
    }
    single { DownloadManager(get(), CoroutineScope(SupervisorJob() + Dispatchers.Default), downloadsDirectory()) }
    factory { LiteViewModel(get(), get(), get(), get()) }
}
