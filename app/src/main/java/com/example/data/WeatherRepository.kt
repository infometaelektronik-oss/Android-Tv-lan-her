package com.example.data

import com.example.model.CitySearchResult
import com.example.model.WeatherData
import com.example.util.TvImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.roundToInt

class WeatherRepository(private val prefs: LauncherPrefs) {

    @Volatile
    private var memoryWeather: WeatherData? = null

    fun getCachedWeather(): WeatherData? {
        memoryWeather?.let { return it }
        val temp = prefs.cachedWeatherTemp
        if (temp == Int.MIN_VALUE) return null
        return WeatherData(
            tempC = temp,
            weatherCode = prefs.cachedWeatherCode,
            isDay = prefs.cachedWeatherIsDay,
            cityName = prefs.cityName,
            updatedAt = prefs.weatherUpdatedAt
        ).also { memoryWeather = it }
    }

    suspend fun getWeatherIfStale(forceRefresh: Boolean = false): WeatherData? {
        val now = System.currentTimeMillis()
        val cached = getCachedWeather()
        if (!forceRefresh && cached != null && (now - cached.updatedAt) < TTL_30_MIN_MS) {
            return cached
        }

        return withContext(Dispatchers.IO) {
            try {
                if (prefs.cityModeAuto) {
                    detectCityByIp()
                }
                val lat = prefs.cityLat
                val lon = prefs.cityLon
                val url =
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day&timezone=auto"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "SadeTVLauncher/1.0")
                    .build()
                val body = TvImageLoader.httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext cached
                    resp.body?.string() ?: return@withContext cached
                }
                val json = JSONObject(body)
                val current = json.optJSONObject("current") ?: return@withContext cached
                val temp = current.optDouble("temperature_2m", Double.NaN)
                if (temp.isNaN()) return@withContext cached
                val code = current.optInt("weather_code", 0)
                val isDay = current.optInt("is_day", 1) == 1

                val fresh = WeatherData(
                    tempC = temp.roundToInt(),
                    weatherCode = code,
                    isDay = isDay,
                    cityName = prefs.cityName,
                    updatedAt = System.currentTimeMillis()
                )
                memoryWeather = fresh
                prefs.cachedWeatherTemp = fresh.tempC
                prefs.cachedWeatherCode = fresh.weatherCode
                prefs.cachedWeatherIsDay = fresh.isDay
                prefs.weatherUpdatedAt = fresh.updatedAt
                fresh
            } catch (_: Throwable) {
                cached
            }
        }
    }

    suspend fun searchCities(query: String): List<CitySearchResult> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                val url =
                    "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=8&language=tr&format=json"
                val req = Request.Builder().url(url).build()
                val body = TvImageLoader.httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    resp.body?.string() ?: return@withContext emptyList()
                }
                val results = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()
                val list = mutableListOf<CitySearchResult>()
                for (i in 0 until results.length()) {
                    val obj = results.getJSONObject(i)
                    list.add(
                        CitySearchResult(
                            name = obj.optString("name", ""),
                            country = obj.optString("country", ""),
                            admin1 = obj.optString("admin1", ""),
                            latitude = obj.optDouble("latitude", 0.0),
                            longitude = obj.optDouble("longitude", 0.0)
                        )
                    )
                }
                list
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }

    fun clearMemoryCache() {
        memoryWeather = null
    }

    private fun detectCityByIp() {
        try {
            val req = Request.Builder()
                .url("https://ipwho.is/")
                .header("User-Agent", "SadeTVLauncher/1.0")
                .build()
            val body = TvImageLoader.httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return
                resp.body?.string() ?: return
            }
            val json = JSONObject(body)
            if (json.optBoolean("success", false)) {
                val city = json.optString("city", "").trim()
                val lat = json.optDouble("latitude", Double.NaN)
                val lon = json.optDouble("longitude", Double.NaN)
                if (city.isNotEmpty() && !lat.isNaN() && !lon.isNaN()) {
                    prefs.cityName = city
                    prefs.cityLat = lat
                    prefs.cityLon = lon
                }
            }
        } catch (_: Throwable) {
            // Keep existing coordinates on offline/failure
        }
    }

    companion object {
        private const val TTL_30_MIN_MS = 30L * 60L * 1000L
    }
}
