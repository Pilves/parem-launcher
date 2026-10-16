package com.parem.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.databinding.ActivityMainBinding
import com.parem.launcher.helper.CrashReporter
import com.parem.launcher.helper.GrayscaleController
import com.parem.launcher.helper.HomeKeyInput
import com.parem.launcher.helper.TextScale
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.helper.isDarkThemeOn
import com.parem.launcher.helper.isDefaultLauncher
import com.parem.launcher.helper.isEinkDisplay
import com.parem.launcher.helper.resetLauncherViaFakeActivity
import com.parem.launcher.helper.setPlainWallpaper
import com.parem.launcher.helper.showLauncherSelector
import com.parem.launcher.helper.showToast
import com.parem.launcher.ui.HomeFragment
import com.parem.launcher.ui.showCrashReport
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
    // Where the per-app grayscale watch starts reading app switches
    private var lastPauseMs = 0L

    lateinit var appWidgetHost: AppWidgetHost
    lateinit var appWidgetManager: AppWidgetManager
    // The bind/configure step HomeWidgetController is waiting on (its keys, see
    // HomeWidgetController.PendingOp). Saved state, not a callback: a rotation or
    // process death under the system dialog must still finish or cancel the widget
    var pendingWidgetOp: Bundle? = null
    // A result that arrived while no HomeFragment was up to take it
    var pendingWidgetResult: Boolean? = null

    var onAdminResult: ((Boolean) -> Unit)? = null

    lateinit var bindWidgetLauncher: ActivityResultLauncher<Intent>
    lateinit var configureWidgetLauncher: ActivityResultLauncher<Intent>
    lateinit var enableAdminLauncher: ActivityResultLauncher<Intent>
    lateinit var launcherSelectorLauncher: ActivityResultLauncher<Intent>

    override fun attachBaseContext(context: Context) {
        val newConfig = Configuration(context.resources.configuration)
        // Parem's step rides on top of the system font size; it never replaces it (DESIGN.md 3.1)
        newConfig.fontScale = TextScale.effective(
            context.resources.configuration.fontScale,
            TextScale.nearestStep(Prefs(context).textSizeScale)
        )
        applyOverrideConfiguration(newConfig)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        // First-run default only: forcing it every launch would undo a dark-theme choice
        // on e-ink (and on any misdetected phone)
        if (isEinkDisplay() && !prefs.hasAppTheme) prefs.appTheme = AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        // HomeFragment restores widgets during content inflation, so its orphan sweep
        // and deferred-result delivery must already see the saved operation.
        pendingWidgetOp = savedInstanceState?.getBundle("pendingWidgetOp")
        pendingWidgetResult = savedInstanceState?.takeIf { it.containsKey("pendingWidgetResult") }
            ?.getBoolean("pendingWidgetResult")
        super.onCreate(savedInstanceState)
        if (prefs.boldFont) theme.applyStyle(R.style.BoldFontOverlay, true)

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, Constants.APPWIDGET_HOST_ID)

        bindWidgetLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            deliverWidgetResult(result.resultCode == Activity.RESULT_OK)
        }

        configureWidgetLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            deliverWidgetResult(result.resultCode == Activity.RESULT_OK)
        }

        enableAdminLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK)
                prefs.lockModeOn = true
            onAdminResult?.invoke(result.resultCode == Activity.RESULT_OK)
            onAdminResult = null
        }

        launcherSelectorLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            // Only when the grant didn't take: once Parem holds the role, the fake-activity
            // reset would fire a HOME chooser and then open Default apps settings
            if (result.resultCode == Activity.RESULT_OK && !isDefaultLauncher())
                resetLauncherViaFakeActivity()
        }

        // Register the result launchers before restored fragments can use them.
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        CrashReporter.pending(this)?.let { showCrashReport(this, it) }

        navController = this.findNavController(R.id.nav_host_fragment)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Home swallows Back; other destinations delegate to Navigation.
        val onBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {}
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            onBackPressedCallback.isEnabled = destination.id == R.id.mainFragment
        }

        if (prefs.firstOpen) {
            viewModel.firstOpen(true)
            prefs.firstOpen = false
            prefs.firstOpenTime = System.currentTimeMillis()
            viewModel.setDefaultClockApp()
            // No role prompt here: onboarding asks for home with context, on the user's tap
        }

        initObservers(viewModel)
        // Heals grey left behind by a crash or a missed trigger
        GrayscaleController.reconcile(this)
        viewModel.getAppList()
        setupOrientation()

        // Edge-to-edge on API 29-34 (35+ enforces it); fragments pad by WindowInsets
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle("pendingWidgetOp", pendingWidgetOp)
        pendingWidgetResult?.let { outState.putBoolean("pendingWidgetResult", it) }
    }

    private fun homeFragment(): HomeFragment? =
        supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
            ?.childFragmentManager?.primaryNavigationFragment as? HomeFragment

    private fun deliverWidgetResult(success: Boolean) {
        val controller = homeFragment()?.widgetController
        if (controller != null) controller.onPendingWidgetResult(success)
        else pendingWidgetResult = success
    }

    // AppWidgetHost.startAppWidgetConfigureActivityForResult (the only way to configure a
    // work-profile widget) reports here, not through an ActivityResultLauncher
    @Deprecated("Needed for AppWidgetHost's configure flow")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE_PROFILE_WIDGET)
            deliverWidgetResult(resultCode == Activity.RESULT_OK)
    }

    override fun onDestroy() {
        onAdminResult = null
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        try { appWidgetHost.startListening() } catch (e: Exception) { Log.e("MainActivity", "Widget host error", e) }
        GrayscaleController.onLauncherStarted(this)
        checkTheme()
    }

    override fun onResume() {
        super.onResume()
        // Not onStart: a translucent or finish-at-once app only pauses Parem,
        // and coming back from it must still bring colour back
        GrayscaleController.stopAppWatch()
        GrayscaleController.setAppForeground(this, false)
    }

    override fun onPause() {
        lastPauseMs = System.currentTimeMillis()
        super.onPause()
    }

    override fun onStop() {
        try { appWidgetHost.stopListening() } catch (e: Exception) { Log.e("MainActivity", "Widget host error", e) }
        GrayscaleController.onLauncherStopped(this)
        if (GrayscaleController.hasMarkedApps(this) && GrayscaleController.appModeReady(this))
            GrayscaleController.startAppWatch(this, lastPauseMs)
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

    // Reached only when no focused view consumed the key, so a focused home slot
    // still gets d-pad/enter and the drawer's search field gets its own typing
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount == 0 && navController.currentDestination?.id == R.id.mainFragment) {
            val query = HomeKeyInput.drawerQuery(
                isMenuKey = keyCode == KeyEvent.KEYCODE_MENU,
                unicodeChar = event.unicodeChar,
                hasShortcutModifier = event.isCtrlPressed || event.isAltPressed || event.isMetaPressed
            )
            val home = homeFragment()
            if (query != null && home != null && home.isAdded) {
                home.showAppList(Constants.FLAG_LAUNCH_APP, query = query)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
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
        viewModel.resetLauncherLiveData.observe(this) {
            if (isDefaultLauncher())
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

                Constants.Dialog.GRAYSCALE_USAGE_ACCESS -> {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.grayscale)
                        .setMessage(R.string.grayscale_app_needs_usage)
                        .setPositiveButton(R.string.okay) { _, _ ->
                            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                        .setNegativeButton(R.string.not_now, null)
                        .show()
                }

                Constants.Dialog.GRAYSCALE_DEFAULT_HOME -> {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.grayscale)
                        .setMessage(R.string.grayscale_app_needs_default)
                        .setPositiveButton(R.string.set_as_default_launcher) { _, _ ->
                            viewModel.resetLauncherLiveData.call()
                        }
                        .setNegativeButton(R.string.not_now, null)
                        .show()
                }
            }
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun setupOrientation() {
        // Set both ways: a fold/unfold recreate must not keep a stale portrait lock
        requestedOrientation = if (resources.getBoolean(R.bool.lock_portrait))
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun backToHomeScreen() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        // Becoming home (chooser "Always", or Home pressed in system settings) arrives
        // as a HOME intent; popping here would skip onboarding's Calm screen
        if (navController.currentDestination?.id == R.id.onboardingFragment) return
        if (navController.currentDestination?.id != R.id.mainFragment)
            navController.popBackStack(R.id.mainFragment, false)
    }

    private fun setPlainWallpaper() {
        if (this.isDarkThemeOn())
            setPlainWallpaper(this, android.R.color.black)
        else setPlainWallpaper(this, android.R.color.white)
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

    companion object {
        const val REQUEST_CONFIGURE_PROFILE_WIDGET = 0x5701
    }
}