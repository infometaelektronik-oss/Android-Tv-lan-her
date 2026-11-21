package com.example.data

import com.example.model.WallpaperItem
import com.example.util.TvImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class WallpaperRepository(private val prefs: LauncherPrefs) {

    suspend fun fetchLandscapeGallery(): List<WallpaperItem> {
        return withContext(Dispatchers.IO) {
            val items = mutableListOf<WallpaperItem>()

            val unsplashKey = prefs.unsplashAccessKey.trim()
            if (unsplashKey.isNotEmpty()) {
                items.addAll(fetchFromUnsplash(unsplashKey))
            }

            val pexelsKey = prefs.pexelsApiKey.trim()
            if (pexelsKey.isNotEmpty()) {
                items.addAll(fetchFromPexels(pexelsKey))
            }

            if (items.isEmpty()) {
                items.addAll(fetchFromWikimediaLandscapes())
            }
            if (items.size < 6) {
                items.addAll(fetchFromPicsum())
            }
            items.distinctBy { it.id }
        }
    }

    private fun fetchFromUnsplash(accessKey: String): List<WallpaperItem> {
        return try {
            val url =
                "https://api.unsplash.com/search/photos?query=nature+landscape&orientation=landscape&per_page=12"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Client-ID $accessKey")
                .build()
            val body = TvImageLoader.httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                resp.body?.string() ?: return emptyList()
            }
            val arr = JSONObject(body).optJSONArray("results") ?: return emptyList()
            val list = mutableListOf<WallpaperItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val urls = obj.optJSONObject("urls") ?: continue
                val regular = urls.optString("regular")
                val small = urls.optString("small")
                val user = obj.optJSONObject("user")?.optString("name") ?: "Unsplash"
                if (regular.isNotBlank()) {
                    list.add(
                        WallpaperItem(
                            id = "uns_${obj.optString("id")}",
                            title = user,
                            thumbUrl = small.ifBlank { regular },
                            fullUrl = regular,
                            sourceLabel = "Unsplash"
                        )
                    )
                }
            }
            list
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun fetchFromPexels(apiKey: String): List<WallpaperItem> {
        return try {
            val url =
                "https://api.pexels.com/v1/search?query=landscape+mountain+lake&orientation=landscape&per_page=12"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", apiKey)
                .build()
            val body = TvImageLoader.httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                resp.body?.string() ?: return emptyList()
            }
            val arr = JSONObject(body).optJSONArray("photos") ?: return emptyList()
            val list = mutableListOf<WallpaperItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val src = obj.optJSONObject("src") ?: continue
                val large = src.optString("large2x").ifBlank { src.optString("large") }
                val medium = src.optString("medium")
                val photographer = obj.optString("photographer", "Pexels")
                if (large.isNotBlank()) {
                    list.add(
                        WallpaperItem(
                            id = "px_${obj.optInt("id")}",
                            title = photographer,
                            thumbUrl = medium.ifBlank { large },
                            fullUrl = large,
                            sourceLabel = "Pexels"
                        )
                    )
                }
            }
            list
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun fetchFromWikimediaLandscapes(): List<WallpaperItem> {
        return try {
            val url =
                "https://commons.wikimedia.org/w/api.php?action=query&generator=categorymembers&gcmtitle=Category:Quality_images_of_landscapes&gcmnamespace=6&gcmlimit=12&prop=imageinfo&iiprop=url|dimensions&iiurlwidth=1280&format=json"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "SadeTVLauncher/1.0 (Android TV)")
                .build()
            val body = TvImageLoader.httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                resp.body?.string() ?: return emptyList()
            }
            val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages")
                ?: return emptyList()
            val list = mutableListOf<WallpaperItem>()
            val keys = pages.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val page = pages.getJSONObject(key)
                val infoArr = page.optJSONArray("imageinfo") ?: continue
                if (infoArr.length() == 0) continue
                val info = infoArr.getJSONObject(0)
                val w = info.optInt("width", 0)
                val h = info.optInt("height", 0)
                if (w > h) {
                    val thumbUrl = info.optString("thumburl").ifBlank { info.optString("url") }
                    val rawTitle = page.optString("title", "Manzara")
                        .removePrefix("File:")
                        .substringBeforeLast(".")
                    if (thumbUrl.isNotBlank()) {
                        list.add(
                            WallpaperItem(
                                id = "wm_$key",
                                title = rawTitle,
                                thumbUrl = thumbUrl,
                                fullUrl = thumbUrl,
                                sourceLabel = "Wikimedia"
                            )
                        )
                    }
                }
            }
            list
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun fetchFromPicsum(): List<WallpaperItem> {
        return try {
            val url = "https://picsum.photos/v2/list?page=2&limit=10"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "SadeTVLauncher/1.0")
                .build()
            val body = TvImageLoader.httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                resp.body?.string() ?: return emptyList()
            }
            val arr = JSONArray(body)
            val list = mutableListOf<WallpaperItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id", "")
                val w = obj.optInt("width", 0)
                val h = obj.optInt("height", 0)
                if (id.isNotEmpty() && w >= h) {
                    list.add(
                        WallpaperItem(
                            id = "pic_$id",
                            title = obj.optString("author", "Manzara #$id"),
                            thumbUrl = "https://picsum.photos/id/$id/480/270",
                            fullUrl = "https://picsum.photos/id/$id/1920/1080",
                            sourceLabel = "Picsum"
                        )
                    )
                }
            }
            list
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
