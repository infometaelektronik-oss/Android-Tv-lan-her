package com.example.ui

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.LauncherPrefs
import com.example.data.WallpaperRepository
import com.example.data.WeatherRepository
import com.example.model.WallpaperItem
import com.example.remote.PhoneSetupServer
import com.example.util.TvImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SettingsDialogs(
    private val context: Context,
    private val prefs: LauncherPrefs,
    private val weatherRepo: WeatherRepository,
    private val wallpaperRepo: WallpaperRepository,
    private val scope: CoroutineScope,
    private val onStateChanged: () -> Unit,
    private val onWallpaperChanged: () -> Unit,
    private val onWeatherForceRefresh: () -> Unit,
    private val onYouTubeForceRefresh: () -> Unit,
    private val onClearCacheRequested: () -> Unit,
    private val onStartScreensaverNow: () -> Unit
) {

    fun showMainSettings() {
        val rows = listOf(
            DialogRowItem(
                title = context.getString(R.string.settings_system_btn),
                desc = context.getString(R.string.settings_system_desc),
                iconRes = R.drawable.ic_settings_gear,
                onClick = { openSystemIntent(Settings.ACTION_SETTINGS) }
            ),
            DialogRowItem(
                title = context.getString(R.string.settings_large_text),
                desc = context.getString(R.string.settings_large_text_desc),
                value = if (prefs.largeTextMode) "Açık" else "Kapalı",
                onClick = {
                    prefs.largeTextMode = !prefs.largeTextMode
                    onStateChanged()
                }
            ),
            DialogRowItem(
                title = context.getString(R.string.settings_city),
                desc = if (prefs.cityModeAuto) "Otomatik (${prefs.cityName})" else "Seçili: ${prefs.cityName}",
                value = "Değiştir",
                onClick = { showCityDialog() }
            ),
            DialogRowItem(
                title = context.getString(R.string.settings_tv_input),
                desc = "Dahili uydu / TV izle kaynağını belirle",
                value = if (prefs.preferredTvTarget.isBlank()) "Otomatik" else "Özel",
                onClick = {
                    AppDrawerDialogs.showTvSourcePicker(context, prefs) {
                        Toast.makeText(context, "${it.label} seçildi", Toast.LENGTH_SHORT).show()
                    }
                }
            ),
            DialogRowItem(
                title = context.getString(R.string.settings_youtube),
                desc = if (prefs.youtubeSourceMode == LauncherPrefs.YT_MODE_POPULAR)
                    "Genel Popüler Videolar"
                else
                    "Seçili Kanalların Son Videoları",
                value = "Düzenle",
                onClick = { showYouTubeSettingsDialog() }
            ),
            DialogRowItem(
                title = context.getString(R.string.settings_wallpaper),
                desc = "Manzara galerisinden 1080p arka plan seç",
                value = "Aç",
                onClick = { showWallpaperPicker() }
            ),
            DialogRowItem(
                title = context.getString(R.string.settings_hidden_apps),
                desc = "Ana ekranda veya listede kullanılmayan uygulamaları gizle",
                value = "${prefs.hiddenPackages.size} gizli",
                onClick = { AppDrawerDialogs.showHiddenAppsManager(context, prefs, onStateChanged) }
            ),
            DialogRowItem(
                title = context.getString(R.string.settings_phone_setup),
                desc = "QR kod okutarak telefondan sıra, şehir ve ayar yap",
                value = "Başlat",
                onClick = { showPhoneSetupDialog() }
            ),
            DialogRowItem(
                title = context.getString(R.string.settings_screensaver),
                desc = "Ekran yanmasını önlemek için hareketli saat",
                value = if (prefs.screensaverMinutes == 0) "Kapalı" else "${prefs.screensaverMinutes} dk",
                onClick = {
                    val next = when (prefs.screensaverMinutes) {
                        3 -> 5
                        5 -> 10
                        10 -> 0
                        else -> 3
                    }
                    prefs.screensaverMinutes = next
                    onStateChanged()
                }
            ),
            DialogRowItem(
                title = context.getString(R.string.settings_set_home),
                desc = "TV açılınca doğrudan SadeTV başlasın",
                onClick = { openSystemIntent(Settings.ACTION_HOME_SETTINGS) }
            )
        )

        AppDrawerDialogs.showSimpleRowDialog(
            context = context,
            title = context.getString(R.string.settings_title),
            subtitle = "Kumanda ile yukarı/aşağı seçin",
            rows = rows
        )
    }

    fun showQuickMenu() {
        val rows = listOf(
            DialogRowItem(
                title = context.getString(R.string.quick_sound),
                desc = "Hoparlör, çıkış ve ses düzeyi",
                onClick = { openSystemIntent(Settings.ACTION_SOUND_SETTINGS) }
            ),
            DialogRowItem(
                title = context.getString(R.string.quick_display),
                desc = "Çözünürlük, parlaklık ve HDR",
                onClick = { openSystemIntent(Settings.ACTION_DISPLAY_SETTINGS) }
            ),
            DialogRowItem(
                title = context.getString(R.string.quick_network),
                desc = "Wi-Fi ve Ethernet ayarları",
                onClick = { openSystemIntent(Settings.ACTION_WIFI_SETTINGS) }
            ),
            DialogRowItem(
                title = context.getString(R.string.quick_large_text),
                value = if (prefs.largeTextMode) "Açık" else "Kapalı",
                onClick = {
                    prefs.largeTextMode = !prefs.largeTextMode
                    onStateChanged()
                }
            ),
            DialogRowItem(
                title = context.getString(R.string.quick_clear_cache),
                desc = "Hafızayı ve geçici resimleri temizle",
                onClick = { onClearCacheRequested() }
            ),
            DialogRowItem(
                title = context.getString(R.string.quick_screensaver),
                desc = "Saat ekranını hemen aç",
                onClick = { onStartScreensaverNow() }
            ),
            DialogRowItem(
                title = context.getString(R.string.settings_title),
                desc = "Tüm başlatıcı ayarları",
                onClick = { showMainSettings() }
            )
        )
        AppDrawerDialogs.showSimpleRowDialog(
            context = context,
            title = context.getString(R.string.quick_menu_title),
            subtitle = "Sık kullanılan kısayollar",
            rows = rows
        )
    }

    fun showCityDialog() {
        val rows = listOf(
            DialogRowItem(
                title = context.getString(R.string.settings_city_auto),
                desc = "İnternet bağlantısına göre şehri otomatik bul",
                value = if (prefs.cityModeAuto) "Seçili" else null,
                onClick = {
                    prefs.cityModeAuto = true
                    prefs.clearWeatherCacheTimestamp()
                    onWeatherForceRefresh()
                }
            ),
            DialogRowItem(
                title = "İstanbul",
                desc = "Elle seç",
                onClick = { selectCity("İstanbul", 41.0082, 28.9784) }
            ),
            DialogRowItem(
                title = "Ankara",
                desc = "Elle seç",
                onClick = { selectCity("Ankara", 39.9334, 32.8597) }
            ),
            DialogRowItem(
                title = "İzmir",
                desc = "Elle seç",
                onClick = { selectCity("İzmir", 38.4237, 27.1428) }
            ),
            DialogRowItem(
                title = "Antalya",
                desc = "Elle seç",
                onClick = { selectCity("Antalya", 36.8969, 30.7133) }
            ),
            DialogRowItem(
                title = "Bursa",
                desc = "Elle seç",
                onClick = { selectCity("Bursa", 40.1885, 29.0610) }
            ),
            DialogRowItem(
                title = "Başka Şehir Ara…",
                desc = "Adını yazarak Open-Meteo'dan ara",
                onClick = { showCitySearchInput() }
            )
        )
        AppDrawerDialogs.showSimpleRowDialog(
            context = context,
            title = context.getString(R.string.settings_city),
            subtitle = "Şu anki şehir: ${prefs.cityName}",
            rows = rows
        )
    }

    private fun selectCity(name: String, lat: Double, lon: Double) {
        prefs.cityModeAuto = false
        prefs.cityName = name
        prefs.cityLat = lat
        prefs.cityLon = lon
        prefs.clearWeatherCacheTimestamp()
        onWeatherForceRefresh()
    }

    private fun showCitySearchInput() {
        val input = EditText(context).apply {
            hint = "Örn: Trabzon, Konya, Gaziantep"
            setText(prefs.cityName)
        }
        AlertDialog.Builder(context)
            .setTitle("Şehir Ara")
            .setView(input)
            .setPositiveButton("Ara") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) {
                    scope.launch {
                        val results = weatherRepo.searchCities(query)
                        if (results.isEmpty()) {
                            Toast.makeText(context, "Şehir bulunamadı", Toast.LENGTH_SHORT).show()
                        } else {
                            val rows = results.map { c ->
                                DialogRowItem(
                                    title = c.displayLabel(),
                                    onClick = { selectCity(c.name, c.latitude, c.longitude) }
                                )
                            }
                            AppDrawerDialogs.showSimpleRowDialog(context, "Şehir Seçin", null, rows)
                        }
                    }
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun showYouTubeSettingsDialog() {
        val rows = listOf(
            DialogRowItem(
                title = "Belirli Kanalların Son Videoları",
                desc = "Anahtarsız RSS veya API ile takip edilen kanallar",
                value = if (prefs.youtubeSourceMode == LauncherPrefs.YT_MODE_CHANNEL) "Seçili" else null,
                onClick = {
                    prefs.youtubeSourceMode = LauncherPrefs.YT_MODE_CHANNEL
                    prefs.clearYouTubeCacheTimestamp()
                    onYouTubeForceRefresh()
                }
            ),
            DialogRowItem(
                title = "Genel Popüler Videolar (Türkiye)",
                desc = "YouTube Data API anahtarı girildiğinde trend videoları gösterir",
                value = if (prefs.youtubeSourceMode == LauncherPrefs.YT_MODE_POPULAR) "Seçili" else null,
                onClick = {
                    prefs.youtubeSourceMode = LauncherPrefs.YT_MODE_POPULAR
                    prefs.clearYouTubeCacheTimestamp()
                    onYouTubeForceRefresh()
                }
            ),
            DialogRowItem(
                title = "Kanal ID'lerini Düzenle",
                desc = "Şu anki: ${prefs.youtubeChannelIds}",
                onClick = { showYouTubeEditDialog() }
            ),
            DialogRowItem(
                title = "Opsiyonel YouTube Data API Anahtarı",
                desc = if (prefs.youtubeApiKey.isBlank()) "Girilmedi (RSS aktif)" else "Kayıtlı",
                onClick = { showApiKeysDialog() }
            )
        )
        AppDrawerDialogs.showSimpleRowDialog(
            context = context,
            title = context.getString(R.string.settings_youtube),
            subtitle = "Videolar kota koruması için 30 dakikada bir yenilenir",
            rows = rows
        )
    }

    private fun showYouTubeEditDialog() {
        val input = EditText(context).apply {
            setText(prefs.youtubeChannelIds)
            hint = "Virgülle ayrılmış UC... kanal ID'leri"
        }
        AlertDialog.Builder(context)
            .setTitle("YouTube Kanal ID'leri")
            .setView(input)
            .setPositiveButton("Kaydet") { _, _ ->
                prefs.youtubeChannelIds = input.text.toString().trim()
                prefs.clearYouTubeCacheTimestamp()
                onYouTubeForceRefresh()
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun showApiKeysDialog() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 12)
        }
        val etYt = EditText(context).apply {
            hint = "YouTube Data API v3 Key (Opsiyonel)"
            setText(prefs.youtubeApiKey)
        }
        val etUnsplash = EditText(context).apply {
            hint = "Unsplash Access Key (Opsiyonel)"
            setText(prefs.unsplashAccessKey)
        }
        val etPexels = EditText(context).apply {
            hint = "Pexels API Key (Opsiyonel)"
            setText(prefs.pexelsApiKey)
        }
        container.addView(etYt)
        container.addView(etUnsplash)
        container.addView(etPexels)

        AlertDialog.Builder(context)
            .setTitle("Opsiyonel API Anahtarları")
            .setView(container)
            .setPositiveButton("Kaydet") { _, _ ->
                prefs.youtubeApiKey = etYt.text.toString()
                prefs.unsplashAccessKey = etUnsplash.text.toString()
                prefs.pexelsApiKey = etPexels.text.toString()
                prefs.clearYouTubeCacheTimestamp()
                onYouTubeForceRefresh()
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun showWallpaperPicker() {
        val dialog = Dialog(context, R.style.SadeTvDialog)
        dialog.setContentView(R.layout.dialog_list)

        val tvTitle = dialog.findViewById<TextView>(R.id.tvDialogTitle)
        val tvSub = dialog.findViewById<TextView>(R.id.tvDialogSubtitle)
        val rv = dialog.findViewById<RecyclerView>(R.id.rvDialogList)
        val btnClose = dialog.findViewById<Button>(R.id.btnDialogClose)

        tvTitle.text = context.getString(R.string.settings_wallpaper)
        tvSub.visibility = View.VISIBLE
        tvSub.text = "Fotoğraf önizlemeleri yükleniyor… Seçilen görsel 1080p sıkıştırılıp kaydedilir."
        btnClose.setOnClickListener { dialog.dismiss() }

        rv.layoutManager = GridLayoutManager(context, 3)

        scope.launch {
            val remote = wallpaperRepo.fetchLandscapeGallery()
            val all = listOf(
                WallpaperItem(
                    id = "default_bundled",
                    title = "Varsayılan Akşam Gölü",
                    thumbUrl = "",
                    fullUrl = "",
                    sourceLabel = "Yerleşik"
                )
            ) + remote

            tvSub.text = "Bir manzara seçin (1080p olarak kaydedilir)"
            rv.adapter = object : RecyclerView.Adapter<WallpaperHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WallpaperHolder {
                    val v = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_wallpaper_thumb, parent, false)
                    return WallpaperHolder(v)
                }

                override fun getItemCount(): Int = all.size

                override fun onBindViewHolder(holder: WallpaperHolder, position: Int) {
                    val item = all[position]
                    holder.tvTitle.text = "${item.title} (${item.sourceLabel})"
                    if (item.id == "default_bundled") {
                        holder.ivThumb.setImageResource(R.drawable.img_default_landscape)
                    } else {
                        TvImageLoader.loadInto(
                            imageView = holder.ivThumb,
                            url = item.thumbUrl,
                            targetWidth = 320,
                            targetHeight = 180,
                            scope = scope
                        )
                    }
                    holder.itemView.setOnClickListener {
                        if (item.id == "default_bundled") {
                            prefs.wallpaperPath = ""
                            onWallpaperChanged()
                            Toast.makeText(context, "Varsayılan arka plan seçildi", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        } else {
                            tvSub.text = "İndiriliyor ve 1080p sıkıştırılıyor…"
                            scope.launch {
                                val savedPath = TvImageLoader.downloadAndSave1080pWallpaper(context, item.fullUrl)
                                if (savedPath != null) {
                                    prefs.wallpaperPath = savedPath
                                    onWallpaperChanged()
                                    Toast.makeText(context, "Arka plan kaydedildi", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                } else {
                                    Toast.makeText(context, "Görsel indirilemedi", Toast.LENGTH_SHORT).show()
                                    tvSub.text = "İndirme başarısız, başka bir manzara deneyin"
                                }
                            }
                        }
                    }
                }
            }
            rv.post { rv.getChildAt(0)?.requestFocus() }
        }
        dialog.show()
    }

    private fun showPhoneSetupDialog() {
        val dialog = Dialog(context, R.style.SadeTvDialog)
        dialog.setContentView(R.layout.dialog_phone_setup)

        val ivQr = dialog.findViewById<ImageView>(R.id.ivQrCode)
        val tvUrl = dialog.findViewById<TextView>(R.id.tvSetupUrl)
        val btnClose = dialog.findViewById<Button>(R.id.btnClosePhoneSetup)

        val server = PhoneSetupServer(
            context = context,
            prefs = prefs,
            weatherRepo = weatherRepo,
            scope = scope,
            onConfigApplied = {
                Toast.makeText(context, "Telefondan ayarlar güncellendi", Toast.LENGTH_SHORT).show()
                onStateChanged()
                onWeatherForceRefresh()
                onYouTubeForceRefresh()
            }
        )

        val url = server.start()
        tvUrl.text = url
        ivQr.setImageBitmap(server.generateQrBitmap(url, 360))

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener { server.stop() }
        dialog.show()
        btnClose.requestFocus()
    }

    private fun openSystemIntent(action: String) {
        val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        val ok = runCatching { context.startActivity(intent); true }.getOrDefault(false)
        if (!ok) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure {
                Toast.makeText(context, "Sistem ayarları açılamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private class WallpaperHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumb: ImageView = view.findViewById(R.id.ivWallpaperThumb)
        val tvTitle: TextView = view.findViewById(R.id.tvWallpaperTitle)
    }
}
