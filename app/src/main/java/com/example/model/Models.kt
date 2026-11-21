package com.example.model

import android.graphics.drawable.Drawable

enum class CardType {
    YOUTUBE,
    TELEVISION,
    TGDIZIFILM,
    PINNED_APP,
    RESUME_LAST,
    CLEAR_CACHE,
    ALL_APPS
}

data class HomeCardItem(
    val id: String,
    val type: CardType,
    val title: String,
    val subtitle: String,
    val badge: String? = null,
    val packageName: String? = null,
    val activityName: String? = null,
    val iconRes: Int = 0,
    val appIcon: Drawable? = null
)

data class YouTubeVideoItem(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val publishedAt: String
)

data class InstalledAppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val isTvApp: Boolean
)

data class WeatherData(
    val tempC: Int,
    val weatherCode: Int,
    val isDay: Boolean,
    val cityName: String,
    val updatedAt: Long
)

data class CitySearchResult(
    val name: String,
    val country: String,
    val admin1: String,
    val latitude: Double,
    val longitude: Double
) {
    fun displayLabel(): String {
        return buildString {
            append(name)
            if (admin1.isNotBlank() && admin1 != name) append(", $admin1")
            if (country.isNotBlank()) append(" ($country)")
        }
    }
}

data class WallpaperItem(
    val id: String,
    val title: String,
    val thumbUrl: String,
    val fullUrl: String,
    val sourceLabel: String
)

data class TvSourceOption(
    val key: String,
    val label: String,
    val subtitle: String
)
