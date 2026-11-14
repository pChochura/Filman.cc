package com.pointlessapps.filman.data.api

import okhttp3.OkHttpClient

fun createOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()
