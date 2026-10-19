package com.yesmin.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Optional: needs a YouTube Data API v3 key that the user adds in Settings. */
object YouTubeClient {
    data class Video(val id: String, val title: String, val channel: String)
    data class Result(val videos: List<Video>, val error: String = "")

    suspend fun search(key: String, query: String): Result = withContext(Dispatchers.IO) {
        try {
            val url = URL(
                "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=5&q=" +
                    URLEncoder.encode(query, "UTF-8")
            )
            val c = url.openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 15000
                c.readTimeout = 20000
                c.setRequestProperty("x-goog-api-key", key)
                val code = c.responseCode
                val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
                if (code != 200) {
                    val msg = try { JSONObject(text).optJSONObject("error")?.optString("message").orEmpty() } catch (e: Exception) { "" }
                    return@withContext Result(emptyList(), "($code) " + msg.take(120))
                }
                val items = JSONObject(text).optJSONArray("items")
                val out = mutableListOf<Video>()
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val it = items.getJSONObject(i)
                        val id = it.optJSONObject("id")?.optString("videoId").orEmpty()
                        val sn = it.optJSONObject("snippet")
                        if (id.isNotEmpty()) out.add(Video(id, sn?.optString("title").orEmpty(), sn?.optString("channelTitle").orEmpty()))
                    }
                }
                Result(out, if (out.isEmpty()) "Koi video nahi mila." else "")
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            Result(emptyList(), "Network error: " + e.javaClass.simpleName)
        }
    }
}
