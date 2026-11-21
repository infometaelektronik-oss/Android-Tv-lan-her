package com.example.data

import android.content.Context
import android.content.SharedPreferences

class LauncherPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var largeTextMode: Boolean
        get() = prefs.getBoolean(KEY_LARGE_TEXT, false)
        set(value) = prefs.edit().putBoolean(KEY_LARGE_TEXT, value).apply()

    var cityModeAuto: Boolean
        get() = prefs.getBoolean(KEY_CITY_AUTO, true)
        set(value) = prefs.edit().putBoolean(KEY_CITY_AUTO, value).apply()

    var cityName: String
        get() = prefs.getString(KEY_CITY_NAME, "İstanbul") ?: "İstanbul"
        set(value) = prefs.edit().putString(KEY_CITY_NAME, value).apply()

    var cityLat: Double
        get() = prefs.getFloat(KEY_CITY_LAT, 41.0082f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_CITY_LAT, value.toFloat()).apply()

    var cityLon: Double
        get() = prefs.getFloat(KEY_CITY_LON, 28.9784f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_CITY_LON, value.toFloat()).apply()

    var cachedWeatherTemp: Int
        get() = prefs.getInt(KEY_WEATHER_TEMP, Int.MIN_VALUE)
        set(value) = prefs.edit().putInt(KEY_WEATHER_TEMP, value).apply()

    var cachedWeatherCode: Int
        get() = prefs.getInt(KEY_WEATHER_CODE, 0)
        set(value) = prefs.edit().putInt(KEY_WEATHER_CODE, value).apply()

    var cachedWeatherIsDay: Boolean
        get() = prefs.getBoolean(KEY_WEATHER_DAY, true)
        set(value) = prefs.edit().putBoolean(KEY_WEATHER_DAY, value).apply()

    var weatherUpdatedAt: Long
        get() = prefs.getLong(KEY_WEATHER_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_WEATHER_TIME, value).apply()

    var youtubeSourceMode: String
        get() = prefs.getString(KEY_YT_MODE, YT_MODE_CHANNEL) ?: YT_MODE_CHANNEL
        set(value) = prefs.edit().putString(KEY_YT_MODE, value).apply()

    var youtubeChannelIds: String
        get() = prefs.getString(KEY_YT_CHANNELS, DEFAULT_YT_CHANNELS) ?: DEFAULT_YT_CHANNELS
        set(value) = prefs.edit().putString(KEY_YT_CHANNELS, value).apply()

    var youtubeApiKey: String
        get() = prefs.getString(KEY_YT_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_YT_API_KEY, value.trim()).apply()

    var youtubeCacheJson: String
        get() = prefs.getString(KEY_YT_CACHE_JSON, "") ?: ""
        set(value) = prefs.edit().putString(KEY_YT_CACHE_JSON, value).apply()

    var youtubeUpdatedAt: Long
        get() = prefs.getLong(KEY_YT_CACHE_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_YT_CACHE_TIME, value).apply()

    var unsplashAccessKey: String
        get() = prefs.getString(KEY_UNSPLASH_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_UNSPLASH_KEY, value.trim()).apply()

    var pexelsApiKey: String
        get() = prefs.getString(KEY_PEXELS_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PEXELS_KEY, value.trim()).apply()

    var preferredTvTarget: String
        get() = prefs.getString(KEY_PREFERRED_TV, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PREFERRED_TV, value).apply()

    var lastWatchedLabel: String
        get() = prefs.getString(KEY_LAST_LABEL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_LABEL, value).apply()

    var lastWatchedType: String
        get() = prefs.getString(KEY_LAST_TYPE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_TYPE, value).apply()

    var lastWatchedPayload: String
        get() = prefs.getString(KEY_LAST_PAYLOAD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_PAYLOAD, value).apply()

    var wallpaperPath: String
        get() = prefs.getString(KEY_WALLPAPER_PATH, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WALLPAPER_PATH, value).apply()

    var screensaverMinutes: Int
        get() = prefs.getInt(KEY_SCREENSAVER_MIN, 5)
        set(value) = prefs.edit().putInt(KEY_SCREENSAVER_MIN, value).apply()

    var hiddenPackages: Set<String>
        get() = prefs.getStringSet(KEY_HIDDEN_PKGS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_HIDDEN_PKGS, value.toSet()).apply()

    var pinnedPackages: List<String>
        get() {
            val raw = prefs.getString(KEY_PINNED_PKGS, "") ?: ""
            return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        set(value) = prefs.edit().putString(KEY_PINNED_PKGS, value.distinct().joinToString(",")).apply()

    var cardOrderIds: List<String>
        get() {
            val raw = prefs.getString(KEY_CARD_ORDER, DEFAULT_CARD_ORDER) ?: DEFAULT_CARD_ORDER
            return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        set(value) = prefs.edit().putString(KEY_CARD_ORDER, value.distinct().joinToString(",")).apply()

    fun saveLastWatched(label: String, type: String, payload: String) {
        prefs.edit()
            .putString(KEY_LAST_LABEL, label)
            .putString(KEY_LAST_TYPE, type)
            .putString(KEY_LAST_PAYLOAD, payload)
            .apply()
    }

    fun clearYouTubeCacheTimestamp() {
        prefs.edit().putLong(KEY_YT_CACHE_TIME, 0L).remove(KEY_YT_CACHE_JSON).apply()
    }

    fun clearWeatherCacheTimestamp() {
        prefs.edit().putLong(KEY_WEATHER_TIME, 0L).apply()
    }

    companion object {
        private const val PREFS_NAME = "sadetv_launcher_prefs"

        const val YT_MODE_CHANNEL = "CHANNEL"
        const val YT_MODE_POPULAR = "POPULAR"

        // Verified Turkish channels (TRT Belgesel, Baris Ozcan) for keyless RSS out of the box
        const val DEFAULT_YT_CHANNELS = "UCdVBWUBCuREx1Q2Ikw9R8Mw,UCv6jcPwFujuTIwFQ11jt1Yw"

        // Default order: YouTube -> Televizyon -> TG Dizi Film -> (Pinned Apps) -> Last Watched -> Cache Clear -> All Apps
        const val DEFAULT_CARD_ORDER = "youtube,television,tgdizifilm,resume_last,clear_cache,all_apps"

        const val TGDIZIFILM_URL = "https://tgdizifilm.ai.studio/"

        private const val KEY_LARGE_TEXT = "large_text_mode"
        private const val KEY_CITY_AUTO = "city_auto"
        private const val KEY_CITY_NAME = "city_name"
        private const val KEY_CITY_LAT = "city_lat"
        private const val KEY_CITY_LON = "city_lon"
        private const val KEY_WEATHER_TEMP = "weather_temp"
        private const val KEY_WEATHER_CODE = "weather_code"
        private const val KEY_WEATHER_DAY = "weather_day"
        private const val KEY_WEATHER_TIME = "weather_time"
        private const val KEY_YT_MODE = "yt_mode"
        private const val KEY_YT_CHANNELS = "yt_channels"
        private const val KEY_YT_API_KEY = "yt_api_key"
        private const val KEY_YT_CACHE_JSON = "yt_cache_json"
        private const val KEY_YT_CACHE_TIME = "yt_cache_time"
        private const val KEY_UNSPLASH_KEY = "unsplash_key"
        private const val KEY_PEXELS_KEY = "pexels_key"
        private const val KEY_PREFERRED_TV = "preferred_tv_target"
        private const val KEY_LAST_LABEL = "last_watched_label"
        private const val KEY_LAST_TYPE = "last_watched_type"
        private const val KEY_LAST_PAYLOAD = "last_watched_payload"
        private const val KEY_WALLPAPER_PATH = "wallpaper_path"
        private const val KEY_SCREENSAVER_MIN = "screensaver_min"
        private const val KEY_HIDDEN_PKGS = "hidden_pkgs"
        private const val KEY_PINNED_PKGS = "pinned_pkgs"
        private const val KEY_CARD_ORDER = "card_order"
    }
}
