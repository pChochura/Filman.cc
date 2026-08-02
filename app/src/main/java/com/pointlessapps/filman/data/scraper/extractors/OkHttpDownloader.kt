package com.pointlessapps.filman.data.scraper.extractors

import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val dataToSend = request.dataToSend()
        
        val requestBody = if (dataToSend != null) {
            val contentType = request.headers()["Content-Type"]?.firstOrNull() ?: "application/octet-stream"
            dataToSend.toRequestBody(contentType.toMediaTypeOrNull())
        } else if (method.equals("POST", ignoreCase = true) || method.equals("PUT", ignoreCase = true) || method.equals("PATCH", ignoreCase = true)) {
            ByteArray(0).toRequestBody(null)
        } else {
            null
        }

        val builder = okhttp3.Request.Builder()
            .method(method, requestBody)
            .url(request.url())

        request.headers().forEach { (key, values) ->
            values.forEach { value ->
                builder.addHeader(key, value)
            }
        }

        val okHttpRequest = builder.build()
        val response = client.newCall(okHttpRequest).execute()

        val responseBody = response.body?.string() ?: ""
        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            responseBody,
            request.url(),
        )
    }
}
