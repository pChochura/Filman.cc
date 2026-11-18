package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.config.PLAYER_USER_AGENT
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkClient {
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request()
                val hasUserAgent = request.header("User-Agent") != null
                val newRequest =
                    if (!hasUserAgent) {
                        request.newBuilder().header("User-Agent", PLAYER_USER_AGENT).build()
                    } else {
                        request
                    }
                chain.proceed(newRequest)
            }.build()
    }
}
