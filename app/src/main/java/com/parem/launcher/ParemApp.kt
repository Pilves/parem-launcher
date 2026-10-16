package com.parem.launcher

import android.app.Application
import android.os.StrictMode
import androidx.work.Configuration
import com.parem.launcher.helper.CrashReporter

// Installed here, not in MainActivity, so crashes in the accessibility service
// and in workers (same process) are captured too.
//
// Configuration.Provider + the removed WorkManagerInitializer node in the
// manifest: WorkManager is built on first use (a settings toggle, or the
// system's job service when a scheduled worker fires) instead of on every
// cold start of the home process (M3-WP10).
class ParemApp : Application(), Configuration.Provider {
    override fun onCreate() {
        if (BuildConfig.DEBUG) installStrictMode()
        super.onCreate()
        CrashReporter.install(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    // Debug builds only: main-thread disk/network and leaks go to logcat
    // (tag StrictMode), never a crash, so a device pass can grep for them.
    private fun installStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build()
        )
    }
}
