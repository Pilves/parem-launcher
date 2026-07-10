package com.parem.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import androidx.appcompat.app.AlertDialog
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.databinding.ActivityMainBinding
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.helper.isDarkThemeOn
import com.parem.launcher.helper.isDefaultLauncher
import com.parem.launcher.helper.isEinkDisplay
import com.parem.launcher.helper.isTablet
import com.parem.launcher.helper.resetLauncherViaFakeActivity
import com.parem.launcher.helper.setPlainWallpaper
import com.parem.launcher.helper.showLauncherSelector
import com.parem.launcher.helper.showToast
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var themeCheckRetries = 0
    private var isRecreating = false

    private lateinit var prefs: Prefs
    private lateinit var navController: NavController
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding
    private var timerJob: Job? = null

    lateinit var appWidgetHost: AppWidgetHost
    lateinit var appWidgetManager: AppWidgetManager
    var pendingWidgetId: Int = -1
    var pendingWidgetInfo: AppWidgetProviderInfo? = null

    var onWidgetBindResult: ((Boolean) -> Unit)? = null
    var onWidgetConfigureResult: ((Boolean) -> Unit)? = null

    lateinit var bindWidgetLauncher: ActivityResultLauncher<Intent>
    lateinit var configureWidgetLauncher: ActivityResultLauncher<Intent>
    lateinit var enableAdminLauncher: ActivityResultLauncher<Intent>
    lateinit var launcherSelectorLauncher: ActivityResultLauncher<Intent>

    override fun attachBaseContext(context: Context) {
        val newConfig = Configuration(context.resources.configuration)
        newConfig.fontScale = Prefs(context).textSizeScale
        applyOverrideConfiguration(newConfig)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        if (isEinkDisplay()) prefs.appTheme = AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        super.onCreate(savedInstanceState)

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, Constants.APPWIDGET_HOST_ID)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navController = this.findNavController(R.id.nav_host_fragment)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        savedInstanceState?.let {
            pendingWidgetId = it.getInt("pendingWidgetId", -1)
            val providerStr = it.getString("pendingWidgetProvider")
            if (providerStr != null && pendingWidgetId != -1) {
                pendingWidgetInfo = appWidgetManager.getAppWidgetInfo(pendingWidgetId)
            }
        }

        bindWidgetLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val success = result.resultCode == Activity.RESULT_OK
            onWidgetBindResult?.invoke(success)
            onWidgetBindResult = null
        }

        configureWidgetLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val success = result.resultCode == Activity.RESULT_OK
            onWidgetConfigureResult?.invoke(success)
            onWidgetConfigureResult = null
        }

        enableAdminLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK)
                prefs.lockModeOn = true
        }

        launcherSelectorLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK)
                resetLauncherViaFakeActivity()
        }

        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (navController.currentDestination?.id != R.id.mainFragment)
                    navController.popBackStack()
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        if (prefs.firstOpen) {
            viewModel.firstOpen(true)
            prefs.firstOpen = false
            prefs.firstOpenTime = System.currentTimeMillis()
            viewModel.setDefaultClockApp()
            viewModel.resetLauncherLiveData.call()
        }

        initObservers(viewModel)
        viewModel.getAppList()
        setupOrientation()

        window.addFlags(FLAG_LAYOUT_NO_LIMITS)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("pendingWidgetId", pendingWidgetId)
        outState.putString("pendingWidgetProvider", pendingWidgetInfo?.provider?.flattenToString())
    }

    override fun onDestroy() {
        onWidgetBindResult = null
        onWidgetConfigureResult = null
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        try { appWidgetHost.startListening() } catch (e: Exception) { Log.e("MainActivity", "Widget host error", e) }
        checkTheme()
    }

    override fun onStop() {
        try { appWidgetHost.stopListening() } catch (e: Exception) { Log.e("MainActivity", "Widget host error", e) }
        super.onStop()
    }

    // Deliberately NOT popping to home in onUserLeaveHint: that hint also fires
    // when a settings row launches an activity for a result (settings import's
    // document picker, the contact-permission prompt), and popping destroys the
    // fragment whose ActivityResult callback the result must come back to — the
    // result then vanishes silently. Pressing home always lands in onNewIntent
    // (singleTask HOME activity), which covers the "reset to home screen" intent.
    override fun onNewIntent(intent: Intent?) {
        backToHomeScreen()
        super.onNewIntent(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        if (!isRecreating && prefs.dailyWallpaper && AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
            isRecreating = true
            setPlainWallpaper()
            viewModel.setWallpaperWorker()
            recreate()
        }
    }

    private fun initObservers(viewModel: MainViewModel) {
        viewModel.launcherResetFailed.observe(this) {
            openLauncherChooser(it)
        }
        viewModel.resetLauncherLiveData.observe(this) {
            if (isDefaultLauncher() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
                resetLauncherViaFakeActivity()
            else
                showLauncherSelector(launcherSelectorLauncher)
        }
        viewModel.showDialog.observe(this) {
            if (isFinishing || isDestroyed) return@observe
            when (it) {
                Constants.Dialog.HIDDEN -> {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.hidden_apps)
                        .setMessage(R.string.hidden_apps_message)
                        .setPositiveButton(R.string.okay, null)
                        .show()
                }

                Constants.Dialog.KEYBOARD -> {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.app_name)
                        .setMessage(R.string.keyboard_message)
                        .setPositiveButton(R.string.okay, null)
                        .show()
                }

                Constants.Dialog.DIGITAL_WELLBEING -> {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.screen_time)
                        .setMessage(R.string.app_usage_message)
                        .setPositiveButton(R.string.okay) { _, _ ->
                            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                        .setNegativeButton(R.string.not_now, null)
                        .show()
                }
            }
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun setupOrientation() {
        if (isTablet(this) || Build.VERSION.SDK_INT == Build.VERSION_CODES.O)
            return
        // In Android 8.0, windowIsTranslucent cannot be used with screenOrientation=portrait
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun backToHomeScreen() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        if (navController.currentDestination?.id != R.id.mainFragment)
            navController.popBackStack(R.id.mainFragment, false)
    }

    private fun setPlainWallpaper() {
        if (this.isDarkThemeOn())
            setPlainWallpaper(this, android.R.color.black)
        else setPlainWallpaper(this, android.R.color.white)
    }

    private fun openLauncherChooser(resetFailed: Boolean) {
        if (resetFailed) {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivity(intent)
        }
    }

    // PAREM-108 phase 3: the inherited Olauncher 4-hour self-recreate +
    // cacheDir wipe is gone. This force-recreate on wrong resolved theme
    // colors is a separate mechanism and stays.
    private fun checkTheme() {
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            delay(200)
            if ((prefs.appTheme == AppCompatDelegate.MODE_NIGHT_YES && getColorFromAttr(R.attr.primaryColor) != getColor(R.color.white))
                || (prefs.appTheme == AppCompatDelegate.MODE_NIGHT_NO && getColorFromAttr(R.attr.primaryColor) != getColor(R.color.black))
            ) {
                if (themeCheckRetries++ < 2)
                    recreate()
            } else {
                themeCheckRetries = 0
            }
        }
    }

}