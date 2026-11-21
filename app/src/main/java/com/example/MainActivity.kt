package com.example

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.data.LauncherPrefs
import com.example.data.WallpaperRepository
import com.example.data.WeatherRepository
import com.example.data.YouTubeRepository
import com.example.model.CardType
import com.example.model.HomeCardItem
import com.example.model.WeatherData
import com.example.tv.TvInputHelper
import com.example.ui.AppDrawerDialogs
import com.example.ui.HomeCardAdapter
import com.example.ui.SettingsDialogs
import com.example.ui.SmoothTvLayoutManager
import com.example.ui.YouTubeVideoAdapter
import com.example.util.TvImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: LauncherPrefs
    private lateinit var weatherRepo: WeatherRepository
    private lateinit var youtubeRepo: YouTubeRepository
    private lateinit var wallpaperRepo: WallpaperRepository
    private lateinit var settingsDialogs: SettingsDialogs

    private lateinit var ivBackground: ImageView
    private lateinit var tvHomeHeading: TextView
    private lateinit var tvStatusBanner: TextView
    private lateinit var ivNetworkIcon: ImageView
    private lateinit var weatherContainer: LinearLayout
    private lateinit var ivWeatherIcon: ImageView
    private lateinit var tvWeatherTemp: TextView
    private lateinit var tvClock: TextView
    private lateinit var btnSettings: ImageButton

    private lateinit var rvMainCards: RecyclerView
    private lateinit var youtubeRowContainer: LinearLayout
    private lateinit var tvYoutubeHeader: TextView
    private lateinit var tvYoutubeSourceBadge: TextView
    private lateinit var tvYoutubeStatus: TextView
    private lateinit var rvYoutubeVideos: RecyclerView
    private lateinit var tvBottomHint: TextView

    private lateinit var screensaverOverlay: FrameLayout
    private lateinit var screensaverClockBox: LinearLayout
    private lateinit var tvSaverTime: TextView
    private lateinit var tvSaverDate: TextView
    private lateinit var tvSaverWeather: TextView

    private lateinit var mainAdapter: HomeCardAdapter
    private lateinit var youtubeAdapter: YouTubeVideoAdapter

    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("d MMMM EEEE", Locale("tr"))

    private var currentBackgroundBitmap: Bitmap? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isScreensaverActive = false
    private var lastFocusedCardType: CardType? = null

    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClockText()
            handler.postDelayed(this, 15_000L)
        }
    }

    private val idleScreensaverRunnable = Runnable {
        if (!isFinishing && prefs.screensaverMinutes > 0) {
            showScreensaver()
        }
    }

    private val burnInShiftRunnable = object : Runnable {
        override fun run() {
            if (isScreensaverActive) {
                shiftScreensaverPosition()
                handler.postDelayed(this, 30_000L)
            }
        }
    }

    private val hideBannerRunnable = Runnable {
        tvStatusBanner.visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = LauncherPrefs(this)
        weatherRepo = WeatherRepository(prefs)
        youtubeRepo = YouTubeRepository(prefs)
        wallpaperRepo = WallpaperRepository(prefs)

        bindViews()
        initAdapters()
        initDialogs()
        loadWallpaper()
        applyLargeTextStyling()
        rebuildMainCards()
        observeNetworkState()
        updateClockText()

        weatherRepo.getCachedWeather()?.let { renderWeather(it) }
        refreshWeatherIfNeeded(force = false)

        rvMainCards.post {
            rvMainCards.getChildAt(0)?.requestFocus()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isScreensaverActive) {
                    dismissScreensaver()
                    return
                }
                rvMainCards.smoothScrollToPosition(0)
                rvMainCards.post { rvMainCards.getChildAt(0)?.requestFocus() }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        updateClockText()
        handler.removeCallbacks(clockRunnable)
        handler.post(clockRunnable)
        resetIdleTimer()
        refreshWeatherIfNeeded(force = false)
        rebuildMainCards()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockRunnable)
        handler.removeCallbacks(idleScreensaverRunnable)
        handler.removeCallbacks(burnInShiftRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        unregisterNetworkState()
        ivBackground.setImageDrawable(null)
        currentBackgroundBitmap?.recycle()
        currentBackgroundBitmap = null
        super.onDestroy()
    }

    private fun bindViews() {
        ivBackground = findViewById(R.id.ivBackground)
        tvHomeHeading = findViewById(R.id.tvHomeHeading)
        tvStatusBanner = findViewById(R.id.tvStatusBanner)
        ivNetworkIcon = findViewById(R.id.ivNetworkIcon)
        weatherContainer = findViewById(R.id.weatherContainer)
        ivWeatherIcon = findViewById(R.id.ivWeatherIcon)
        tvWeatherTemp = findViewById(R.id.tvWeatherTemp)
        tvClock = findViewById(R.id.tvClock)
        btnSettings = findViewById(R.id.btnSettings)

        rvMainCards = findViewById(R.id.rvMainCards)
        youtubeRowContainer = findViewById(R.id.youtubeRowContainer)
        tvYoutubeHeader = findViewById(R.id.tvYoutubeHeader)
        tvYoutubeSourceBadge = findViewById(R.id.tvYoutubeSourceBadge)
        tvYoutubeStatus = findViewById(R.id.tvYoutubeStatus)
        rvYoutubeVideos = findViewById(R.id.rvYoutubeVideos)
        tvBottomHint = findViewById(R.id.tvBottomHint)

        screensaverOverlay = findViewById(R.id.screensaverOverlay)
        screensaverClockBox = findViewById(R.id.screensaverClockBox)
        tvSaverTime = findViewById(R.id.tvSaverTime)
        tvSaverDate = findViewById(R.id.tvSaverDate)
        tvSaverWeather = findViewById(R.id.tvSaverWeather)

        btnSettings.setOnClickListener { settingsDialogs.showMainSettings() }
        weatherContainer.setOnClickListener { settingsDialogs.showCityDialog() }
        screensaverOverlay.setOnClickListener { dismissScreensaver() }
    }

    private fun initAdapters() {
        mainAdapter = HomeCardAdapter(
            onClick = { item -> handleCardClick(item) },
            onLongClick = { _ -> settingsDialogs.showQuickMenu() },
            onFocused = { item, pos ->
                rvMainCards.smoothScrollToPosition(pos)
                handleCardFocus(item)
            }
        )
        rvMainCards.layoutManager = SmoothTvLayoutManager(this)
        rvMainCards.adapter = mainAdapter
        rvMainCards.itemAnimator = null

        youtubeAdapter = YouTubeVideoAdapter(
            scope = lifecycleScope,
            onVideoClick = { video ->
                prefs.saveLastWatched(video.title, "YT_VIDEO", video.videoId)
                if (!TvInputHelper.launchYouTubeVideo(this, video.videoId)) {
                    Toast.makeText(this, R.string.error_no_youtube_app, Toast.LENGTH_SHORT).show()
                }
            },
            onVideoLongClick = { settingsDialogs.showQuickMenu() }
        )
        rvYoutubeVideos.layoutManager = SmoothTvLayoutManager(this)
        rvYoutubeVideos.adapter = youtubeAdapter
        rvYoutubeVideos.itemAnimator = null
    }

    private fun initDialogs() {
        settingsDialogs = SettingsDialogs(
            context = this,
            prefs = prefs,
            weatherRepo = weatherRepo,
            wallpaperRepo = wallpaperRepo,
            scope = lifecycleScope,
            onStateChanged = {
                applyLargeTextStyling()
                rebuildMainCards()
                resetIdleTimer()
            },
            onWallpaperChanged = { loadWallpaper() },
            onWeatherForceRefresh = { refreshWeatherIfNeeded(force = true) },
            onYouTubeForceRefresh = {
                if (lastFocusedCardType == CardType.YOUTUBE) {
                    loadYouTubeVideos(force = true)
                }
            },
            onClearCacheRequested = { performOneTapCacheClear() },
            onStartScreensaverNow = { showScreensaver() }
        )
    }

    private fun rebuildMainCards() {
        val pm = packageManager
        val hidden = prefs.hiddenPackages
        val pinned = prefs.pinnedPackages.filter { !hidden.contains(it) }

        val coreMap = LinkedHashMap<String, HomeCardItem>()
        coreMap["youtube"] = HomeCardItem(
            id = "youtube",
            type = CardType.YOUTUBE,
            title = getString(R.string.card_youtube),
            subtitle = getString(R.string.card_youtube_sub),
            iconRes = R.drawable.ic_youtube_play
        )
        coreMap["television"] = HomeCardItem(
            id = "television",
            type = CardType.TELEVISION,
            title = getString(R.string.card_television),
            subtitle = getString(R.string.card_television_sub),
            badge = "UYDU",
            iconRes = R.drawable.ic_live_tv
        )
        coreMap["tgdizifilm"] = HomeCardItem(
            id = "tgdizifilm",
            type = CardType.TGDIZIFILM,
            title = getString(R.string.card_tgdizifilm),
            subtitle = getString(R.string.card_tgdizifilm_sub),
            badge = "WEB",
            iconRes = R.drawable.ic_movie_reel
        )

        if (prefs.lastWatchedLabel.isNotBlank() && prefs.lastWatchedPayload.isNotBlank()) {
            coreMap["resume_last"] = HomeCardItem(
                id = "resume_last",
                type = CardType.RESUME_LAST,
                title = getString(R.string.card_resume_channel),
                subtitle = prefs.lastWatchedLabel,
                iconRes = R.drawable.ic_history_resume
            )
        }

        coreMap["clear_cache"] = HomeCardItem(
            id = "clear_cache",
            type = CardType.CLEAR_CACHE,
            title = getString(R.string.card_clear_cache),
            subtitle = getString(R.string.card_clear_cache_sub),
            badge = "RAM",
            iconRes = R.drawable.ic_clean_broom
        )

        coreMap["all_apps"] = HomeCardItem(
            id = "all_apps",
            type = CardType.ALL_APPS,
            title = getString(R.string.card_all_apps),
            subtitle = getString(R.string.card_all_apps_sub),
            iconRes = R.drawable.ic_apps_grid
        )

        val ordered = mutableListOf<HomeCardItem>()
        val orderIds = prefs.cardOrderIds.ifEmpty {
            LauncherPrefs.DEFAULT_CARD_ORDER.split(",")
        }

        // Insert pinned user apps right after core top 3 (youtube, television, tgdizifilm)
        val pinnedCards = pinned.mapNotNull { pkg ->
            val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@mapNotNull null
            val label = pm.getApplicationLabel(appInfo).toString()
            val icon = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
            HomeCardItem(
                id = "app_$pkg",
                type = CardType.PINNED_APP,
                title = label,
                subtitle = "Uygulama",
                packageName = pkg,
                appIcon = icon
            )
        }

        var pinnedInserted = false
        for (id in orderIds) {
            coreMap.remove(id)?.let { ordered.add(it) }
            if (id == "tgdizifilm" && !pinnedInserted) {
                ordered.addAll(pinnedCards)
                pinnedInserted = true
            }
        }
        if (!pinnedInserted) ordered.addAll(pinnedCards)

        // Append any missing core cards (preserving All Apps at the end)
        val allAppsCard = coreMap.remove("all_apps")
        coreMap.values.forEach { ordered.add(it) }
        allAppsCard?.let { ordered.add(it) }

        mainAdapter.submitItems(ordered, prefs.largeTextMode)
    }

    private fun handleCardClick(item: HomeCardItem) {
        when (item.type) {
            CardType.YOUTUBE -> {
                if (!TvInputHelper.launchYouTubeApp(this)) {
                    Toast.makeText(this, R.string.error_no_youtube_app, Toast.LENGTH_SHORT).show()
                }
            }
            CardType.TELEVISION -> {
                val ok = TvInputHelper.launchTelevision(this, prefs)
                if (!ok) {
                    Toast.makeText(this, R.string.error_no_tv_app, Toast.LENGTH_LONG).show()
                    AppDrawerDialogs.showTvSourcePicker(this, prefs) {
                        TvInputHelper.launchTelevision(this, prefs)
                    }
                }
            }
            CardType.TGDIZIFILM -> {
                prefs.saveLastWatched("TG Dizi Film", "URI", LauncherPrefs.TGDIZIFILM_URL)
                val intent = Intent(this, WebViewActivity::class.java).apply {
                    putExtra(WebViewActivity.EXTRA_URL, LauncherPrefs.TGDIZIFILM_URL)
                }
                startActivity(intent)
            }
            CardType.PINNED_APP -> {
                val pkg = item.packageName ?: return
                if (TvInputHelper.launchPackage(this, pkg)) {
                    prefs.saveLastWatched(item.title, "APP", pkg)
                } else {
                    Toast.makeText(this, "${item.title} açılamadı", Toast.LENGTH_SHORT).show()
                }
            }
            CardType.RESUME_LAST -> {
                if (!TvInputHelper.resumeLastWatched(this, prefs)) {
                    Toast.makeText(this, "Son izlenen içerik açılamadı", Toast.LENGTH_SHORT).show()
                }
            }
            CardType.CLEAR_CACHE -> performOneTapCacheClear()
            CardType.ALL_APPS -> {
                AppDrawerDialogs.showAllAppsDialog(this, prefs) {
                    rebuildMainCards()
                }
            }
        }
    }

    private fun handleCardFocus(item: HomeCardItem) {
        lastFocusedCardType = item.type
        if (item.type == CardType.YOUTUBE) {
            if (youtubeRowContainer.visibility != View.VISIBLE) {
                youtubeRowContainer.alpha = 0f
                youtubeRowContainer.visibility = View.VISIBLE
                youtubeRowContainer.animate().alpha(1f).setDuration(120L).start()
            }
            loadYouTubeVideos(force = false)
        } else {
            if (youtubeRowContainer.visibility == View.VISIBLE) {
                youtubeRowContainer.visibility = View.GONE
            }
        }
    }

    private fun loadYouTubeVideos(force: Boolean) {
        val cached = youtubeRepo.getCachedVideos()
        if (cached.isNotEmpty()) {
            tvYoutubeStatus.visibility = View.GONE
            youtubeAdapter.submitVideos(cached, prefs.largeTextMode)
        } else {
            tvYoutubeStatus.visibility = View.VISIBLE
            tvYoutubeStatus.text = getString(R.string.youtube_loading)
        }

        tvYoutubeSourceBadge.text = if (prefs.youtubeSourceMode == LauncherPrefs.YT_MODE_POPULAR &&
            prefs.youtubeApiKey.isNotBlank()
        ) {
            "Popüler • 30 dk önbellek"
        } else {
            "Takip Edilen Kanallar • 30 dk önbellek"
        }

        lifecycleScope.launch {
            val videos = youtubeRepo.getVideosIfStale(forceRefresh = force)
            if (videos.isNotEmpty()) {
                tvYoutubeStatus.visibility = View.GONE
                youtubeAdapter.submitVideos(videos, prefs.largeTextMode)
            } else if (cached.isEmpty()) {
                tvYoutubeStatus.visibility = View.VISIBLE
                tvYoutubeStatus.text = getString(R.string.youtube_empty)
            }
        }
    }

    private fun performOneTapCacheClear() {
        weatherRepo.clearMemoryCache()
        youtubeRepo.clearMemoryCache()
        val freed = TvImageLoader.clearAllCaches(this)
        val msg = getString(R.string.cache_cleared_msg, TvImageLoader.formatBytes(freed))
        showStatusBanner(msg)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun showStatusBanner(message: String) {
        tvStatusBanner.text = message
        tvStatusBanner.visibility = View.VISIBLE
        handler.removeCallbacks(hideBannerRunnable)
        handler.postDelayed(hideBannerRunnable, 4000L)
    }

    private fun applyLargeTextStyling() {
        val large = prefs.largeTextMode
        tvHomeHeading.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (large) 26f else 22f)
        tvWeatherTemp.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (large) 19f else 16f)
        tvClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (large) 28f else 24f)
        tvYoutubeHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (large) 20f else 17f)
        tvBottomHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (large) 15f else 13f)
        mainAdapter.setLargeText(large)
        youtubeAdapter.setLargeText(large)
    }

    private fun loadWallpaper() {
        val path = prefs.wallpaperPath
        if (path.isBlank()) {
            ivBackground.setImageResource(R.drawable.img_default_landscape)
            currentBackgroundBitmap?.recycle()
            currentBackgroundBitmap = null
            return
        }
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                TvImageLoader.decodeSampledFile(path, 1920, 1080)
            }
            if (bmp != null) {
                ivBackground.setImageBitmap(bmp)
                currentBackgroundBitmap?.recycle()
                currentBackgroundBitmap = bmp
            } else {
                ivBackground.setImageResource(R.drawable.img_default_landscape)
            }
        }
    }

    private fun refreshWeatherIfNeeded(force: Boolean) {
        lifecycleScope.launch {
            val data = weatherRepo.getWeatherIfStale(forceRefresh = force)
            if (data != null) {
                renderWeather(data)
            }
        }
    }

    private fun renderWeather(data: WeatherData) {
        tvWeatherTemp.text = "${data.tempC}° ${data.cityName}"
        val iconRes = when (data.weatherCode) {
            0, 1 -> R.drawable.ic_weather_sun
            in 51..99 -> R.drawable.ic_weather_rain
            else -> R.drawable.ic_weather_cloud
        }
        ivWeatherIcon.setImageResource(iconRes)
        tvSaverWeather.text = "${data.cityName} • ${data.tempC}°C"
    }

    private fun updateClockText() {
        val now = Date()
        val t = timeFormat.format(now)
        tvClock.text = t
        tvSaverTime.text = t
        tvSaverDate.text = dateFormat.format(now)
    }

    private fun observeNetworkState() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        updateNetworkIcon(isOnline(cm))
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread { updateNetworkIcon(true) }
            }

            override fun onLost(network: Network) {
                runOnUiThread { updateNetworkIcon(isOnline(cm)) }
            }
        }
        networkCallback = cb
        runCatching {
            cm.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                cb
            )
        }
    }

    private fun unregisterNetworkState() {
        val cb = networkCallback ?: return
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching { cm.unregisterNetworkCallback(cb) }
        networkCallback = null
    }

    private fun isOnline(cm: ConnectivityManager): Boolean {
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun updateNetworkIcon(online: Boolean) {
        ivNetworkIcon.setImageResource(if (online) R.drawable.ic_wifi_on else R.drawable.ic_wifi_off)
    }

    private fun resetIdleTimer() {
        handler.removeCallbacks(idleScreensaverRunnable)
        val mins = prefs.screensaverMinutes
        if (mins > 0 && !isScreensaverActive) {
            handler.postDelayed(idleScreensaverRunnable, mins * 60_000L)
        }
    }

    private fun showScreensaver() {
        isScreensaverActive = true
        updateClockText()
        screensaverOverlay.visibility = View.VISIBLE
        screensaverOverlay.requestFocus()
        shiftScreensaverPosition()
        handler.removeCallbacks(burnInShiftRunnable)
        handler.postDelayed(burnInShiftRunnable, 30_000L)
    }

    private fun dismissScreensaver() {
        if (!isScreensaverActive) return
        isScreensaverActive = false
        handler.removeCallbacks(burnInShiftRunnable)
        screensaverOverlay.visibility = View.GONE
        screensaverClockBox.translationX = 0f
        screensaverClockBox.translationY = 0f
        rvMainCards.requestFocus()
        resetIdleTimer()
    }

    private fun shiftScreensaverPosition() {
        val maxDx = (screensaverOverlay.width * 0.18f).toInt().coerceAtLeast(80)
        val maxDy = (screensaverOverlay.height * 0.16f).toInt().coerceAtLeast(60)
        val dx = Random.nextInt(-maxDx, maxDx + 1).toFloat()
        val dy = Random.nextInt(-maxDy, maxDy + 1).toFloat()
        screensaverClockBox.animate().translationX(dx).translationY(dy).setDuration(600L).start()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isScreensaverActive) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                dismissScreensaver()
            }
            return true
        }
        resetIdleTimer()

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_MENU || event.keyCode == KeyEvent.KEYCODE_SETTINGS) {
                settingsDialogs.showQuickMenu()
                return true
            }
            if (event.isLongPress &&
                (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                settingsDialogs.showQuickMenu()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
