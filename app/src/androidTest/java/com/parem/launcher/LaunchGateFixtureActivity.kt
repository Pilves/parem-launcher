package com.parem.launcher

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** An isolated launchable calendar/camera handler for Focus routing tests. */
class LaunchGateFixtureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Focus camera fixture" })
    }
}
