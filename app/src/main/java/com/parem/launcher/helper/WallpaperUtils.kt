package com.parem.launcher.helper

import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import android.os.Build
import android.util.Log
import android.view.WindowManager
import com.parem.launcher.R
import com.parem.launcher.data.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Wallpaper and bitmap operations: plain-color wallpapers, bitmap scaling,
 * and the daily-wallpaper HTTP fetch used by WallpaperWorker.
 */
fun setPlainWallpaper(context: Context, color: Int) {
    val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    try {
        bitmap.eraseColor(context.getColor(color))
        val manager = WallpaperManager.getInstance(context)
        manager.setBitmap(bitmap, null, false, WallpaperManager.FLAG_SYSTEM)
        manager.setBitmap(bitmap, null, false, WallpaperManager.FLAG_LOCK)
    } catch (e: Exception) {
        Log.e("Utils", "Failed to set plain wallpaper", e)
    } finally {
        bitmap.recycle()
    }
}

/**
 * The largest power-of-two sample size that still leaves the decode at least as large as
 * the centre-crop to [width]x[height] needs, so sampling never makes the wallpaper softer.
 */
internal fun wallpaperSampleSize(srcWidth: Int, srcHeight: Int, width: Int, height: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    while (srcWidth >= sample * 2 * width && srcHeight >= sample * 2 * height) sample *= 2
    return sample
}

/** Sampled down as far as [wallpaperSampleSize] allows for a [width]x[height] screen. */
suspend fun getBitmapFromURL(src: String?, width: Int, height: Int): Bitmap? {
    return withContext(Dispatchers.IO) {
        var bitmap: Bitmap? = null
        var connection: HttpURLConnection? = null
        try {
            val url = URL(src)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doInput = true
            connection.connect()
            // In memory: the size pass and the decode both read the image from the start
            val bytes = connection.inputStream.use { it.readBytes() }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            options.inSampleSize = wallpaperSampleSize(options.outWidth, options.outHeight, width, height)
            options.inJustDecodeBounds = false
            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (e: Exception) {
            Log.e("Utils", "Failed to decode bitmap from URL", e)
        } finally {
            connection?.disconnect()
        }
        bitmap
    }
}

suspend fun getWallpaperBitmap(originalImage: Bitmap, width: Int, height: Int): Bitmap {
    return withContext(Dispatchers.IO) {

        val background = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val originalWidth: Float = originalImage.width.toFloat()
        val originalHeight: Float = originalImage.height.toFloat()

        val canvas = Canvas(background)
        val heightScale: Float = height / originalHeight
        val widthScale: Float = width / originalWidth
        val scale = maxOf(heightScale, widthScale)

        val (xTranslation, yTranslation) = if (heightScale > widthScale)
            Pair((width - originalWidth * heightScale) / 2.0f, 0f)
        else
            Pair(0f, (height - originalHeight * widthScale) / 2.0f)

        val transformation = Matrix()
        transformation.postTranslate(xTranslation, yTranslation)
        transformation.preScale(scale, scale)

        val paint = Paint()
        paint.isFilterBitmap = true
        canvas.drawBitmap(originalImage, transformation, paint)

        background
    }
}

suspend fun setWallpaper(appContext: Context, url: String): Boolean {
    return withContext(Dispatchers.IO) {
        // Before the download, whose result this check would only discard
        if (appContext.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && appContext.resources.getBoolean(R.bool.lock_portrait)) {
            return@withContext false
        }

        val wallpaperManager = WallpaperManager.getInstance(appContext)
        val (width, height) = getScreenDimensions(appContext)
        val originalImageBitmap = getBitmapFromURL(url, width, height) ?: return@withContext false
        // Freed before the two encodes below, which only read the screen-sized copy
        val scaledBitmap = try {
            getWallpaperBitmap(originalImageBitmap, width, height)
        } finally {
            try { originalImageBitmap.recycle() } catch (_: Exception) {}
        }

        try {
            // Two calls, not FLAG_SYSTEM or FLAG_LOCK: a combined call leaves no lock-specific
            // wallpaper, so the lock screen would share the home one instead of its own copy
            wallpaperManager.setBitmap(scaledBitmap, null, false, WallpaperManager.FLAG_SYSTEM)
            wallpaperManager.setBitmap(scaledBitmap, null, false, WallpaperManager.FLAG_LOCK)
        } catch (e: Exception) {
            CrashReporter.reportNonFatal("WallpaperUtils", e)
            return@withContext false
        } finally {
            try { scaledBitmap.recycle() } catch (_: Exception) {}
        }
        true
    }
}

fun getScreenDimensions(context: Context): Pair<Int, Int> {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val windowMetrics = windowManager.currentWindowMetrics
        val bounds = windowMetrics.bounds
        Pair(bounds.width(), bounds.height())
    } else {
        val point = Point()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealSize(point)
        Pair(point.x, point.y)
    }
}

suspend fun getTodaysWallpaper(wallType: String, firstOpenTime: Long): String {
    return withContext(Dispatchers.IO) {
        var wallpaperUrl: String
        var connection: HttpURLConnection? = null
        try {
            val key = if (firstOpenTime.isDaySince() < 10)
                String.format("0_%s", firstOpenTime.isDaySince().toString())
            else {
                val month = SimpleDateFormat("M", Locale.ENGLISH).format(Date()) ?: "0"
                val day = SimpleDateFormat("d", Locale.ENGLISH).format(Date()) ?: "0"
                String.format("%s_%s", month, day)
            }

            val url = URL(Constants.URL_WALLPAPERS)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doInput = true
            connection.connect()

            val result = connection.inputStream.bufferedReader().use { it.readText() }

            val json = JSONObject(result)
            val wallpapers = json.getString(key)
            val wallpapersJson = JSONObject(wallpapers)
            // The list has a blank entry (1_30 dark); an empty URL would fail silently and keep yesterday's
            wallpaperUrl = wallpapersJson.getString(wallType).ifBlank { getBackupWallpaper(wallType) }
            wallpaperUrl

        } catch (e: Exception) {
            CrashReporter.reportNonFatal("WallpaperUtils", e)
            wallpaperUrl = getBackupWallpaper(wallType)
            wallpaperUrl
        } finally {
            connection?.disconnect()
        }
    }
}

fun getBackupWallpaper(wallType: String): String {
    return if (wallType == Constants.WALL_TYPE_LIGHT)
        Constants.URL_DEFAULT_LIGHT_WALLPAPER
    else Constants.URL_DEFAULT_DARK_WALLPAPER
}
