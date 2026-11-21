package com.example.data

import com.example.model.YouTubeVideoItem
import com.example.util.TvImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URLEncoder

class YouTubeRepository(private val prefs: LauncherPrefs) {

    @Volatile
    private var memoryVideos: List<YouTubeVideoItem> = emptyList()

    @Volatile
    private var memoryUpdatedAt: Long = 0L

    fun getCachedVideos(): List<YouTubeVideoItem> {
        if (memoryVideos.isNotEmpty()) return memoryVideos
        val jsonStr = prefs.youtubeCacheJson
        if (jsonStr.isBlank()) return emptyList()
        val parsed = deserializeList(jsonStr)
        if (parsed.isNotEmpty()) {
            memoryVideos = parsed
            memoryUpdatedAt = prefs.youtubeUpdatedAt
        }
        return parsed
    }

    suspend fun getVideosIfStale(forceRefresh: Boolean = false): List<YouTubeVideoItem> {
        val now = System.currentTimeMillis()
        val cached = getCachedVideos()
        val lastFetch = maxOf(memoryUpdatedAt, prefs.youtubeUpdatedAt)
        if (!forceRefresh && cached.isNotEmpty() && (now - lastFetch) < TTL_30_MIN_MS) {
            return cached
        }

        return withContext(Dispatchers.IO) {
            val apiKey = prefs.youtubeApiKey.trim()
            val mode = prefs.youtubeSourceMode
            val channels = prefs.youtubeChannelIds
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val fetched: List<YouTubeVideoItem> = when {
                mode == LauncherPrefs.YT_MODE_POPULAR && apiKey.isNotEmpty() -> {
                    fetchPopularViaApi(apiKey).ifEmpty {
                        fetchViaRssChannels(channels)
                    }
                }
                apiKey.isNotEmpty() && channels.isNotEmpty() -> {
                    fetchChannelsViaApi(apiKey, channels).ifEmpty {
                        fetchViaRssChannels(channels)
                    }
                }
                else -> {
                    fetchViaRssChannels(channels)
                }
            }

            if (fetched.isNotEmpty()) {
                val nowStamp = System.currentTimeMillis()
                memoryVideos = fetched
                memoryUpdatedAt = nowStamp
                prefs.youtubeCacheJson = serializeList(fetched)
                prefs.youtubeUpdatedAt = nowStamp
                fetched
            } else {
                cached
            }
        }
    }

    fun clearMemoryCache() {
        memoryVideos = emptyList()
        memoryUpdatedAt = 0L
    }

    private fun fetchPopularViaApi(apiKey: String): List<YouTubeVideoItem> {
        return try {
            val url =
                "https://www.googleapis.com/youtube/v3/videos?part=snippet&chart=mostPopular&regionCode=TR&maxResults=12&key=${
                    URLEncoder.encode(apiKey, "UTF-8")
                }"
            val req = Request.Builder().url(url).build()
            val body = TvImageLoader.httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                resp.body?.string() ?: return emptyList()
            }
            val items = JSONObject(body).optJSONArray("items") ?: return emptyList()
            val list = mutableListOf<YouTubeVideoItem>()
            for (i in 0 until items.length()) {
                val obj = items.getJSONObject(i)
                val id = obj.optString("id", "")
                val snippet = obj.optJSONObject("snippet") ?: continue
                val thumb = extractBestThumb(snippet.optJSONObject("thumbnails"), id)
                if (id.isNotEmpty()) {
                    list.add(
                        YouTubeVideoItem(
                            videoId = id,
                            title = snippet.optString("title", ""),
                            channelTitle = snippet.optString("channelTitle", "YouTube"),
                            thumbnailUrl = thumb,
                            publishedAt = snippet.optString("publishedAt", "")
                        )
                    )
                }
            }
            list
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun fetchChannelsViaApi(apiKey: String, channels: List<String>): List<YouTubeVideoItem> {
        val result = mutableListOf<YouTubeVideoItem>()
        for (channelId in channels.take(3)) {
            try {
                val url =
                    "https://www.googleapis.com/youtube/v3/search?part=snippet&channelId=${
                        URLEncoder.encode(channelId, "UTF-8")
                    }&maxResults=6&order=date&type=video&key=${
                        URLEncoder.encode(apiKey, "UTF-8")
                    }"
                val req = Request.Builder().url(url).build()
                val body = TvImageLoader.httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) continue
                    resp.body?.string() ?: continue
                }
                val items = JSONObject(body).optJSONArray("items") ?: continue
                for (i in 0 until items.length()) {
                    val obj = items.getJSONObject(i)
                    val idObj = obj.optJSONObject("id") ?: continue
                    val videoId = idObj.optString("videoId", "")
                    val snippet = obj.optJSONObject("snippet") ?: continue
                    if (videoId.isNotEmpty()) {
                        result.add(
                            YouTubeVideoItem(
                                videoId = videoId,
                                title = snippet.optString("title", ""),
                                channelTitle = snippet.optString("channelTitle", "YouTube"),
                                thumbnailUrl = extractBestThumb(snippet.optJSONObject("thumbnails"), videoId),
                                publishedAt = snippet.optString("publishedAt", "")
                            )
                        )
                    }
                }
            } catch (_: Throwable) {
                // Skip failed channel
            }
        }
        return result.sortedByDescending { it.publishedAt }.take(12)
    }

    private fun fetchViaRssChannels(channels: List<String>): List<YouTubeVideoItem> {
        val targetChannels = if (channels.isNotEmpty()) channels else listOf("UCdVBWUBCuREx1Q2Ikw9R8Mw")
        val all = mutableListOf<YouTubeVideoItem>()
        for (ch in targetChannels.take(4)) {
            all.addAll(fetchSingleRssFeed(ch))
        }
        return all.sortedByDescending { it.publishedAt }.take(12)
    }

    private fun fetchSingleRssFeed(channelId: String): List<YouTubeVideoItem> {
        return try {
            val url = "https://www.youtube.com/feeds/videos.xml?channel_id=${URLEncoder.encode(channelId, "UTF-8")}"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "SadeTVLauncher/1.0")
                .build()
            val xml = TvImageLoader.httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                resp.body?.string() ?: return emptyList()
            }
            parseYouTubeAtomXml(xml)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun parseYouTubeAtomXml(xml: String): List<YouTubeVideoItem> {
        val items = mutableListOf<YouTubeVideoItem>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var inEntry = false
        var channelName = "YouTube"
        var videoId = ""
        var title = ""
        var published = ""
        var thumb = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name ?: ""
                    when {
                        name == "entry" -> {
                            inEntry = true
                            videoId = ""
                            title = ""
                            published = ""
                            thumb = ""
                        }
                        !inEntry && name == "name" -> {
                            channelName = runCatching { parser.nextText() }.getOrDefault(channelName)
                        }
                        inEntry && (name == "yt:videoId" || name == "videoId") -> {
                            videoId = runCatching { parser.nextText() }.getOrDefault("")
                        }
                        inEntry && name == "title" -> {
                            title = runCatching { parser.nextText() }.getOrDefault("")
                        }
                        inEntry && name == "published" -> {
                            published = runCatching { parser.nextText() }.getOrDefault("")
                        }
                        inEntry && (name == "media:thumbnail" || name == "thumbnail") -> {
                            thumb = parser.getAttributeValue(null, "url") ?: thumb
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "entry" && inEntry) {
                        if (videoId.isNotBlank() && title.isNotBlank()) {
                            val finalThumb = if (thumb.isNotBlank()) {
                                thumb
                            } else {
                                "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
                            }
                            items.add(
                                YouTubeVideoItem(
                                    videoId = videoId,
                                    title = title,
                                    channelTitle = channelName,
                                    thumbnailUrl = finalThumb,
                                    publishedAt = published
                                )
                            )
                        }
                        inEntry = false
                        if (items.size >= 8) break
                    }
                }
            }
            event = parser.next()
        }
        return items
    }

    private fun extractBestThumb(thumbs: JSONObject?, videoId: String): String {
        if (thumbs != null) {
            thumbs.optJSONObject("medium")?.optString("url")?.takeIf { it.isNotBlank() }?.let { return it }
            thumbs.optJSONObject("high")?.optString("url")?.takeIf { it.isNotBlank() }?.let { return it }
            thumbs.optJSONObject("default")?.optString("url")?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
    }

    private fun serializeList(list: List<YouTubeVideoItem>): String {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.videoId)
                    put("title", item.title)
                    put("ch", item.channelTitle)
                    put("thumb", item.thumbnailUrl)
                    put("pub", item.publishedAt)
                }
            )
        }
        return arr.toString()
    }

    private fun deserializeList(json: String): List<YouTubeVideoItem> {
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<YouTubeVideoItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    YouTubeVideoItem(
                        videoId = obj.optString("id"),
                        title = obj.optString("title"),
                        channelTitle = obj.optString("ch"),
                        thumbnailUrl = obj.optString("thumb"),
                        publishedAt = obj.optString("pub")
                    )
                )
            }
            list
        } catch (_: Throwable) {
            emptyList()
        }
    }

    companion object {
        private const val TTL_30_MIN_MS = 30L * 60L * 1000L
    }
}
