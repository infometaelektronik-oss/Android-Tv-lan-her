package com.example.tv

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri
import android.os.Build
import com.example.data.LauncherPrefs
import com.example.model.InstalledAppInfo
import com.example.model.TvSourceOption

object TvInputHelper {

    private val knownTvPackages = listOf(
        "com.google.android.tv",
        "com.android.tv",
        "com.mediatek.wwtv.tvcenter",
        "com.tcl.tv",
        "com.hisense.tv",
        "com.vestel.tv",
        "com.realtek.tv",
        "com.droidlogic.tv",
        "com.amlogic.tv",
        "org.dtvkit.inputsource"
    )

    private val tvKeywords = listOf(
        "tv izle", "canlı tv", "live tv", "televizyon", "uydu", "satellite", "dtv", "tuner"
    )

    fun queryInstalledApps(context: Context, includeHidden: Boolean, hiddenSet: Set<String>): List<InstalledAppInfo> {
        val pm = context.packageManager
        val selfPkg = context.packageName
        val map = LinkedHashMap<String, InstalledAppInfo>()

        val leanbackIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        queryActivities(pm, leanbackIntent).forEach { info ->
            val pkg = info.activityInfo.packageName
            if (pkg != selfPkg && (includeHidden || !hiddenSet.contains(pkg))) {
                map[pkg] = InstalledAppInfo(
                    label = info.loadLabel(pm).toString(),
                    packageName = pkg,
                    activityName = info.activityInfo.name,
                    isTvApp = true
                )
            }
        }

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        queryActivities(pm, launcherIntent).forEach { info ->
            val pkg = info.activityInfo.packageName
            if (pkg != selfPkg && !map.containsKey(pkg) && (includeHidden || !hiddenSet.contains(pkg))) {
                map[pkg] = InstalledAppInfo(
                    label = info.loadLabel(pm).toString(),
                    packageName = pkg,
                    activityName = info.activityInfo.name,
                    isTvApp = false
                )
            }
        }

        return map.values.sortedBy { it.label.lowercase() }
    }

    fun discoverAllTvSources(context: Context): List<TvSourceOption> {
        val options = mutableListOf<TvSourceOption>()
        options.add(
            TvSourceOption(
                key = "",
                label = "Otomatik Algıla (Önerilen)",
                subtitle = "Dahili uydu / TV izle girişini otomatik bulur"
            )
        )

        val pm = context.packageManager
        // 1. Android TV Input Framework (TIF) inputs
        val tvInputManager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        if (tvInputManager != null) {
            runCatching {
                tvInputManager.tvInputList.forEach { input ->
                    val label = input.loadLabel(context)?.toString()?.takeIf { it.isNotBlank() }
                        ?: input.id
                    val typeLabel = when (input.type) {
                        TvInputInfo.TYPE_TUNER -> "Dahili Uydu / Tuner"
                        TvInputInfo.TYPE_HDMI -> "HDMI Girişi"
                        TvInputInfo.TYPE_COMPONENT -> "Component"
                        TvInputInfo.TYPE_COMPOSITE -> "AV / Composite"
                        else -> "TV Girişi (${input.id.substringAfterLast('/')})"
                    }
                    options.add(
                        TvSourceOption(
                            key = "TIF:${input.id}",
                            label = label,
                            subtitle = typeLabel
                        )
                    )
                }
            }
        }

        // 2. Installed OEM Live TV / "TV izle" activities
        val candidates = mutableListOf<ResolveInfo>()
        candidates.addAll(queryActivities(pm, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)))
        candidates.addAll(queryActivities(pm, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)))

        val seen = HashSet<String>()
        candidates.forEach { info ->
            val pkg = info.activityInfo.packageName
            val cls = info.activityInfo.name
            val key = "ACT:$pkg/$cls"
            if (pkg == context.packageName || !seen.add(key)) return@forEach

            val label = info.loadLabel(pm).toString()
            val lower = label.lowercase()
            val isTvMatch = knownTvPackages.any { pkg.startsWith(it) } ||
                tvKeywords.any { lower.contains(it) } ||
                pkg.contains(".tv") || pkg.contains("dtv")

            if (isTvMatch) {
                options.add(
                    TvSourceOption(
                        key = key,
                        label = label,
                        subtitle = "Uygulama: $pkg"
                    )
                )
            }
        }

        return options
    }

    fun launchTelevision(context: Context, prefs: LauncherPrefs): Boolean {
        val preferred = prefs.preferredTvTarget.trim()
        if (preferred.isNotEmpty()) {
            if (tryLaunchKey(context, preferred)) {
                prefs.saveLastWatched("Televizyon", "TV_KEY", preferred)
                return true
            }
        }

        // 1. Try OEM "TV izle" / Live TV activity matching keywords first (most reliable on OEM TVs)
        val pm = context.packageManager
        val allActivities = queryActivities(
            pm,
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        ) + queryActivities(
            pm,
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        )

        val matchedTv = allActivities.firstOrNull { info ->
            val pkg = info.activityInfo.packageName
            val label = info.loadLabel(pm).toString().lowercase()
            pkg != context.packageName && (
                label.contains("tv izle") ||
                    label.contains("live tv") ||
                    label.contains("canlı tv") ||
                    knownTvPackages.any { pkg.equals(it, ignoreCase = true) }
                )
        }

        if (matchedTv != null) {
            val key = "ACT:${matchedTv.activityInfo.packageName}/${matchedTv.activityInfo.name}"
            if (tryLaunchKey(context, key)) {
                prefs.saveLastWatched(matchedTv.loadLabel(pm).toString(), "TV_KEY", key)
                return true
            }
        }

        // 2. Try TV Input Framework (TIF) tuner input
        val tvInputManager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        if (tvInputManager != null) {
            val tuner = runCatching {
                tvInputManager.tvInputList.firstOrNull { it.type == TvInputInfo.TYPE_TUNER }
                    ?: tvInputManager.tvInputList.firstOrNull()
            }.getOrNull()

            if (tuner != null) {
                val key = "TIF:${tuner.id}"
                if (tryLaunchKey(context, key)) {
                    val lbl = tuner.loadLabel(context)?.toString() ?: "Uydu Yayını"
                    prefs.saveLastWatched(lbl, "TV_KEY", key)
                    return true
                }
            }
        }

        // 3. Generic TvContract Live Channels intent fallback
        val channelsIntent = Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStartActivity(context, channelsIntent)) {
            prefs.saveLastWatched("Televizyon", "URI", TvContract.Channels.CONTENT_URI.toString())
            return true
        }

        return false
    }

    private fun tryLaunchKey(context: Context, key: String): Boolean {
        return when {
            key.startsWith("ACT:") -> {
                val rest = key.removePrefix("ACT:")
                val pkg = rest.substringBefore("/")
                val cls = rest.substringAfter("/", "")
                launchApp(context, pkg, cls)
            }
            key.startsWith("TIF:") -> {
                val inputId = key.removePrefix("TIF:")
                val uri = TvContract.buildChannelsUriForInput(inputId)
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (tryStartActivity(context, intent)) {
                    true
                } else {
                    // Fallback: launch the TIF service's parent package if it has a leanback activity
                    val pkg = inputId.substringBefore("/")
                    if (pkg.isNotBlank()) launchPackage(context, pkg) else false
                }
            }
            else -> false
        }
    }

    fun launchPackage(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return tryStartActivity(context, intent)
    }

    fun launchApp(context: Context, packageName: String, activityName: String?): Boolean {
        if (!activityName.isNullOrBlank()) {
            val explicit = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(packageName, activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            if (tryStartActivity(context, explicit)) return true
        }
        return launchPackage(context, packageName)
    }

    fun launchYouTubeApp(context: Context): Boolean {
        val candidates = listOf(
            "com.google.android.youtube.tv",
            "com.google.android.youtube",
            "com.liskovsoft.smarttubetv.beta",
            "com.teamsmart.videomanager.tv"
        )
        for (pkg in candidates) {
            if (launchPackage(context, pkg)) return true
        }
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return tryStartActivity(context, webIntent)
    }

    fun launchYouTubeVideo(context: Context, videoId: String): Boolean {
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStartActivity(context, appIntent)) return true

        val tvPkgIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId")).apply {
            setPackage("com.google.android.youtube.tv")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStartActivity(context, tvPkgIntent)) return true

        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return tryStartActivity(context, webIntent)
    }

    fun resumeLastWatched(context: Context, prefs: LauncherPrefs): Boolean {
        val type = prefs.lastWatchedType
        val payload = prefs.lastWatchedPayload
        if (type.isBlank() || payload.isBlank()) return false
        return when (type) {
            "TV_KEY" -> tryLaunchKey(context, payload)
            "URI" -> tryStartActivity(context, Intent(Intent.ACTION_VIEW, Uri.parse(payload)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "YT_VIDEO" -> launchYouTubeVideo(context, payload)
            "APP" -> {
                val pkg = payload.substringBefore("/")
                val cls = payload.substringAfter("/", "")
                launchApp(context, pkg, cls.ifBlank { null })
            }
            else -> false
        }
    }

    private fun tryStartActivity(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: Throwable) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun queryActivities(pm: PackageManager, intent: Intent): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentActivities(intent, 0)
        }
    }
}
