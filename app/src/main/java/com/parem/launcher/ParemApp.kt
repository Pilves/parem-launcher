package com.parem.launcher

import android.app.Application
import com.parem.launcher.helper.CrashReporter

// Installed here, not in MainActivity, so crashes in the accessibility service
// and in workers (same process) are captured too.
class ParemApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
