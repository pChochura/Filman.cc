package com.pointlessapps.filman.data.api

import okhttp3.OkHttpClient
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

val trustAllCertManager =
    object : X509ExtendedTrustManager() {
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

        override fun checkClientTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
            socket: Socket?,
        ) {
        }

        override fun checkServerTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
            socket: Socket?,
        ) {
        }

        override fun checkClientTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
            engine: SSLEngine?,
        ) {
        }

        override fun checkServerTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
            engine: SSLEngine?,
        ) {
        }

        @Suppress("unused")
        fun checkServerTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
            host: String?,
        ): List<X509Certificate> = chain?.toList() ?: emptyList()

        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

val trustAllCerts: Array<TrustManager> = arrayOf(trustAllCertManager)

val unsafeSslSocketFactory: Pair<SSLSocketFactory, X509TrustManager> by lazy {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, trustAllCerts, SecureRandom())
    sslContext.socketFactory to trustAllCertManager
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
