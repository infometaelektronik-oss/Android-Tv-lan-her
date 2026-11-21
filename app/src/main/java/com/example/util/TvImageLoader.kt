package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object TvImageLoader {

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    // Cap in-memory cache conservatively (~10 MB max) for 1 GB RAM Android TVs
    private val cacheMaxKb: Int = minOf(
        10 * 1024,
        (Runtime.getRuntime().maxMemory() / 1024L / 10L).toInt().coerceAtLeast(4 * 1024)
    )

    private val memoryCache = object : LruCache<String, Bitmap>(cacheMaxKb) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun loadInto(
        imageView: ImageView,
        url: String,
        targetWidth: Int,
        targetHeight: Int,
        scope: CoroutineScope,
        placeholderRes: Int = 0
    ) {
        imageView.tag = url
        val cacheKey = "${url}_${targetWidth}x${targetHeight}"
        val cached = memoryCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            imageView.setImageBitmap(cached)
            return
        }

        if (placeholderRes != 0) {
            imageView.setImageResource(placeholderRes)
        } else {
            imageView.setImageDrawable(null)
        }

        val appContext = imageView.context.applicationContext
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                fetchAndDecode(appContext, url, cacheKey, targetWidth, targetHeight)
            }
            if (bitmap != null && imageView.tag == url) {
                imageView.setImageBitmap(bitmap)
            }
        }
    }

    private fun fetchAndDecode(
        context: Context,
        url: String,
        cacheKey: String,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        return try {
            val diskFile = File(getDiskCacheDir(context), sha1(cacheKey) + ".jpg")
            val bytes: ByteArray = if (diskFile.exists() && diskFile.length() > 0L) {
                diskFile.readBytes()
            } else {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "SadeTVLauncher/1.0 (Android TV)")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return null
                    val bodyBytes = response.body?.bytes() ?: return null
                    trimDiskCacheIfNeeded(getDiskCacheDir(context))
                    runCatching { diskFile.writeBytes(bodyBytes) }
                    bodyBytes
                }
            }

            val bitmap = decodeSampledBitmap(bytes, targetWidth, targetHeight, useRgb565 = true)
            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
            }
            bitmap
        } catch (_: Throwable) {
            null
        }
    }

    fun decodeSampledBitmap(
        bytes: ByteArray,
        reqWidth: Int,
        reqHeight: Int,
        useRgb565: Boolean
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds, reqWidth, reqHeight)
            inPreferredConfig = if (useRgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    fun decodeSampledFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds, reqWidth, reqHeight)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeFile(path, options)
    }

    suspend fun downloadAndSave1080pWallpaper(context: Context, url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "SadeTVLauncher/1.0 (Android TV)")
                    .build()
                val bytes = httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    resp.body?.bytes() ?: return@withContext null
                }

                val decoded = decodeSampledBitmap(bytes, 1920, 1080, useRgb565 = true)
                    ?: return@withContext null

                val finalBitmap = if (decoded.width > 1920 || decoded.height > 1080) {
                    val ratio = minOf(1920f / decoded.width, 1080f / decoded.height)
                    val w = (decoded.width * ratio).toInt().coerceAtLeast(1)
                    val h = (decoded.height * ratio).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(decoded, w, h, true)
                    if (scaled !== decoded) decoded.recycle()
                    scaled
                } else {
                    decoded
                }

                val outFile = File(context.filesDir, "custom_wallpaper.jpg")
                FileOutputStream(outFile).use { fos ->
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 84, fos)
                }
                finalBitmap.recycle()
                outFile.absolutePath
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun clearAllCaches(context: Context): Long {
        var freedBytes = 0L
        memoryCache.evictAll()

        val cacheRoot = context.cacheDir
        freedBytes += deleteDirContents(cacheRoot)
        System.gc()
        return freedBytes
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format("%.1f MB", mb)
    }

    private fun deleteDirContents(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        dir.listFiles()?.forEach { child ->
            total += if (child.isDirectory) {
                val sub = deleteDirContents(child)
                child.delete()
                sub
            } else {
                val len = child.length()
                if (child.delete()) len else 0L
            }
        }
        return total
    }

    private fun getDiskCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "img_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun trimDiskCacheIfNeeded(dir: File) {
        val files = dir.listFiles() ?: return
        val maxBytes = 12L * 1024L * 1024L
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        files.sortedBy { it.lastModified() }.forEach { f ->
            if (total <= maxBytes / 2) return
            val len = f.length()
            if (f.delete()) total -= len
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun sha1(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
