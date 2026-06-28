package com.example.filman

import android.util.Log
import org.jsoup.Jsoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun testJsoupUrl(urlStr: String, userAgent: String, cookie: String) = withContext(Dispatchers.IO) {
    try {
        val conn = Jsoup.connect(urlStr)
            .userAgent(userAgent)
            .header("Cookie", cookie)
            .ignoreHttpErrors(true)
        val doc = conn.get()
        Log.d("FilmanTest", "Result URL: ${conn.response().url()}")
        Log.d("FilmanTest", "Title: ${doc.title()}")
    } catch(e: Exception) {
        Log.e("FilmanTest", "Error", e)
    }
}
