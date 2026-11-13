package com.parem.launcher

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.parem.launcher.data.Prefs
import com.parem.launcher.helper.WallpaperWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.util.Collections
import kotlin.concurrent.thread

/** Runs in an isolated instrumentation process under Android Test Orchestrator. */
@RunWith(AndroidJUnit4::class)
class WallpaperWorkerIntegrationTest {
    @Test
    fun failedWallpaperIsRetriedAndAppliedWithoutForegroundService() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("cmd package set-home-activity ${context.packageName}/${MainActivity::class.java.name}")
        val prefs = Prefs(context)
        prefs.firstOpen = false
        prefs.dailyWallpaper = true
        prefs.dailyWallpaperUrl = "https://fixture.test/previous.png"
        prefs.firstOpenTime = System.currentTimeMillis()
        val manager = WorkManager.getInstance(context)
        val failedRequest = OneTimeWorkRequestBuilder<WallpaperWorker>().build()
        val request = OneTimeWorkRequestBuilder<WallpaperWorker>().build()
        val wallpaper = WallpaperManager.getInstance(context)
        val previousId = wallpaper.getWallpaperId(WallpaperManager.FLAG_SYSTEM)

        LocalWallpaperServer().use { fixture ->
            // Intercept HTTPS only in this isolated test process. The app still uses
            // its production URLs and HttpURLConnection; the fixture is loopback HTTP.
            URL.setURLStreamHandlerFactory { protocol ->
                if (protocol != "https") null else object : URLStreamHandler() {
                    override fun openConnection(url: URL): URLConnection = fixture.connection(url)
                }
            }
            try {
                // The first image response is invalid. The previously applied URL
                // must survive so a later attempt does not skip this same URL.
                manager.enqueue(failedRequest).result.get()
                val retryDeadline = SystemClock.elapsedRealtime() + 30_000
                var attempts = 0
                while (SystemClock.elapsedRealtime() < retryDeadline) {
                    attempts = manager.getWorkInfoById(failedRequest.id).get()?.runAttemptCount ?: 0
                    if (attempts > 0) break
                    SystemClock.sleep(100)
                }
                assertTrue("failed wallpaper was not scheduled to retry", attempts > 0)
                assertEquals("failed apply replaced the previous URL",
                    "https://fixture.test/previous.png", prefs.dailyWallpaperUrl)
                manager.cancelWorkById(failedRequest.id).result.get()

                manager.enqueue(request).result.get()
                val deadline = SystemClock.elapsedRealtime() + 30_000
                var state: WorkInfo.State? = null
                while (SystemClock.elapsedRealtime() < deadline) {
                    state = manager.getWorkInfoById(request.id).get()?.state
                    if (state?.isFinished == true) break
                    SystemClock.sleep(100)
                }
                assertEquals("enabled WallpaperWorker did not complete", WorkInfo.State.SUCCEEDED, state)
                assertTrue("wallpaper list was not requested", fixture.paths.contains("/wallpapers.json"))
                assertEquals("wallpaper image was not retried", 2, fixture.paths.count { it == "/image.png" })
                assertEquals("https://fixture.test/image.png", prefs.dailyWallpaperUrl)
                assertNotEquals("system wallpaper did not change", previousId,
                    wallpaper.getWallpaperId(WallpaperManager.FLAG_SYSTEM))
            } finally {
                manager.cancelWorkById(failedRequest.id).result.get()
                manager.cancelWorkById(request.id).result.get()
                prefs.dailyWallpaper = false
            }
        }
    }
}

/** Two tiny HTTP responses on loopback, read through a test-only HTTPS URL handler. */
private class LocalWallpaperServer : AutoCloseable {
    private val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
    val paths: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private var imageRequests = 0
    private val png = ByteArrayOutputStream().also { output ->
        Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xff42745b.toInt())
            compress(Bitmap.CompressFormat.PNG, 100, output)
            recycle()
        }
    }.toByteArray()
    private val worker = thread(name = "wallpaper-fixture", isDaemon = true) {
        while (!server.isClosed) {
            val socket = try { server.accept() } catch (_: java.io.IOException) { break }
            socket.use {
                val input = it.getInputStream().bufferedReader(Charsets.US_ASCII)
                val path = input.readLine()?.split(" ")?.getOrNull(1).orEmpty()
                while (!input.readLine().isNullOrEmpty()) { }
                paths.add(path)
                val body = if (path == "/wallpapers.json") {
                    val entries = (0..9).joinToString(",") { day ->
                        "\"0_$day\":{\"light\":\"https://fixture.test/image.png\",\"dark\":\"https://fixture.test/image.png\"}"
                    }
                    "{$entries}".toByteArray()
                } else if (++imageRequests == 1) byteArrayOf(0) else png
                it.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(body)
                    flush()
                }
            }
        }
    }

    fun connection(url: URL): HttpURLConnection = object : HttpURLConnection(url) {
        private var socket: Socket? = null
        override fun connect() {
            if (connected) return
            if (!((url.host == "pilves.github.io" && url.path.endsWith("/wallpapers.json")) ||
                    (url.host == "fixture.test" && url.path == "/image.png"))) {
                throw java.io.IOException("Unexpected fixture URL: $url")
            }
            val path = if (url.host == "fixture.test") "/image.png" else "/wallpapers.json"
            socket = Socket("127.0.0.1", server.localPort).also { s ->
                s.soTimeout = 10_000
                s.getOutputStream().write("GET $path HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
                val input = s.getInputStream()
                var matched = 0
                val end = byteArrayOf(13, 10, 13, 10)
                while (matched < end.size) {
                    val byte = input.read()
                    if (byte < 0) throw java.io.EOFException("fixture HTTP headers")
                    matched = if (byte == end[matched].toInt()) matched + 1 else 0
                }
            }
            connected = true
        }
        override fun getInputStream(): java.io.InputStream { connect(); return socket!!.getInputStream() }
        override fun disconnect() { socket?.close(); connected = false }
        override fun usingProxy() = false
    }

    override fun close() { server.close(); worker.join(1_000) }
}
