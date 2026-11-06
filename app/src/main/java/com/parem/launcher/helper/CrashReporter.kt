package com.parem.launcher.helper

import android.content.Context
import android.os.Build
import android.util.Log
import com.parem.launcher.BuildConfig
import com.parem.launcher.data.Prefs
import java.io.File

/**
 * Opt-in crash capture (M3-WP11). With the setting off nothing is written.
 * With it on, the report is saved to no-backup storage and offered on the next
 * start; it leaves the phone only if the user sends it.
 */
object CrashReporter {

    private var appContext: Context? = null

    private const val FILE_NAME = "crash.txt"

    private fun file(context: Context) = File(context.noBackupFilesDir, FILE_NAME)

    fun install(context: Context) {
        val app = context.applicationContext
        appContext = app
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // The prefs read may be the first one, on a dying thread: nothing
            // here may stop the crash reaching the system handler (and Play vitals).
            try {
                if (Prefs(app).crashReportsEnabled) {
                    file(app).writeText(
                        CrashReportFormatter.format(
                            throwable, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
                            Build.VERSION.SDK_INT, Build.MANUFACTURER, Build.MODEL,
                            thread.name, System.currentTimeMillis()
                        )
                    )
                }
            } catch (_: Throwable) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    /** Diagnostics only: reporting must never replace a caller's recovery path. */
    @Synchronized
    fun reportNonFatal(tag: String, error: Throwable) {
        Log.e(tag, "Recovered failure", error)
        try {
            val app = appContext ?: return
            if (!Prefs(app).crashReportsEnabled) return
            val report = file(app)
            val entry = "\nNon-fatal: $tag\n" + CrashReportFormatter.format(
                error, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
                Build.VERSION.SDK_INT, Build.MANUFACTURER, Build.MODEL,
                Thread.currentThread().name, System.currentTimeMillis()
            )
            report.writeText(CrashReportFormatter.cap((if (report.exists()) report.readText() else "") + entry))
        } catch (failure: Exception) {
            Log.e("CrashReporter", "Could not save recovered failure", failure)
        }
    }

    fun pending(context: Context): String? = try {
        file(context).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    fun clear(context: Context) {
        file(context).delete()
    }
}
