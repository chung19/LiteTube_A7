package com.example.litetube.net

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class OkHttpNewPipeDownloader : Downloader() {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
            )

        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { value -> builder.addHeader(name, value) }
        }

        val bodyBytes = request.dataToSend()
        val body = (bodyBytes ?: ByteArray(0))
            .toRequestBody("application/octet-stream".toMediaTypeOrNull())

        when (request.httpMethod().uppercase()) {
            "GET" -> builder.get()
            "HEAD" -> builder.head()
            "POST" -> builder.post(body)
            else -> builder.method(
                request.httpMethod().uppercase(),
                if (bodyBytes == null) null else body
            )
        }

        client.newCall(builder.build()).execute().use { response ->
            val responseBody = if (request.httpMethod().equals("HEAD", true)) {
                ""
            } else {
                response.body?.string().orEmpty()
            }

            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                responseBody,
                response.request.url.toString()
            )
        }
    }
}
