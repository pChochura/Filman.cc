package com.pointlessapps.filman.data.api

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

val trustAllCerts: Array<TrustManager> =
    arrayOf(
        object : X509TrustManager {
            override fun checkClientTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?,
            ) {
            }

            override fun checkServerTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?,
            ) {
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        },
    )

val unsafeSslSocketFactory: Pair<SSLSocketFactory, X509TrustManager> by lazy {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, trustAllCerts, SecureRandom())
    sslContext.socketFactory to (trustAllCerts[0] as X509TrustManager)
}

fun OkHttpClient.Builder.applyUnsafeSsl(): OkHttpClient.Builder {
    val (factory, trustManager) = unsafeSslSocketFactory
    return this
        .sslSocketFactory(factory, trustManager)
        .hostnameVerifier { _, _ -> true }
}

fun getUnsafeOkHttpClient(): OkHttpClient {
    return OkHttpClient
        .Builder()
        .applyUnsafeSsl()
        .build()
}
