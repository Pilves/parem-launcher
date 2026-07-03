package com.parem.launcher.ui

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.parem.launcher.MainActivity
import com.parem.launcher.MainViewModel
import com.parem.launcher.R
import com.parem.launcher.data.AppModel
import com.parem.launcher.data.FolderApp
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.databinding.FragmentHomeBinding
import com.parem.launcher.helper.appUsagePermissionGranted
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.helper.expandNotificationDrawer
import com.parem.launcher.helper.getUserHandleFromString
import com.parem.launcher.helper.isPackageInstalled
import com.parem.launcher.helper.openAlarmApp
import com.parem.launcher.helper.openCalendar
import com.parem.launcher.helper.openCameraApp
import com.parem.launcher.helper.openDialerApp
import com.parem.launcher.helper.openSearch
import com.parem.launcher.helper.showToast
import com.parem.launcher.listener.OnSwipeTouchListener
import com.parem.launcher.listener.ViewSwipeTouchListener
import com.parem.launcher.helper.DoubleTapActionManager
import com.parem.launcher.helper.FocusModeManager
import com.parem.launcher.helper.FolderManager
import com.parem.launcher.helper.GestureLetterManager
import com.parem.launcher.helper.IconPackManager
import com.parem.launcher.helper.isAccessServiceEnabled
import com.parem.launcher.helper.AppLimitManager

import com.parem.launcher.helper.QuickNoteManager
import com.parem.launcher.helper.UsageStatsHelper
import com.parem.launcher.helper.SwipeUpAppManager
import com.parem.launcher.helper.WeatherManager
import com.parem.launcher.ui.ScreenTimeGraphDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class HomeFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var screenTouchListener: OnSwipeTouchListener? = null
    private val viewTouchListeners = mutableListOf<ViewSwipeTouchListener>()
    private lateinit var folderManager: FolderManager
    private var expandedFolderSlot: Int = -1
    private var effectiveNoteSlot: Int = -1
    private var flashlightOn: Boolean = false
    private var torchCallback: android.hardware.camera2.CameraManager.TorchCallback? = null

    private var widgetController: HomeWidgetController? = null

    companion object {
        private var cachedLocale: Locale? = null
        private var dateFormatter: DateTimeFormatter? = null
        fun getDateFormatter(): DateTimeFormatter {
            val current = Locale.getDefault()
            if (current != cachedLocale || dateFormatter == null) {
                cachedLocale = current
                dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", current)
            }
            return dateFormatter!!
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        folderManager = FolderManager(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        deviceManager = context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        widgetController = HomeWidgetController(this, binding, prefs)

        // Track the real torch state so toggleFlashlight() can't desync when
        // another app (or quick settings) switches the torch
        try {
            val cameraManager = requireContext().getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            torchCallback = object : android.hardware.camera2.CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    flashlightOn = enabled
                }
            }.also { cameraManager.registerTorchCallback(it, null) }
        } catch (e: Exception) {
            Log.e("HomeFragment", "Failed to register torch callback", e)
        }

        initObservers()
        setHomeAlignment(prefs.homeAlignment)
        initSwipeTouchListener()
        initClickListeners()
        initGestureLetterOverlay()
        viewLifecycleOwner.lifecycleScope.launch {
            widgetController?.restoreWidgets()
        }

        if (!prefs.onboardingComplete) {
            prefs.onboardingComplete = true
            findNavController().navigate(R.id.action_mainFragment_to_onboardingFragment)
        }
    }

    override fun onResume() {
        super.onResume()
        // Cancel any pending long-press timers from before the app was backgrounded
        widgetController?.cancelPendingLongPresses()
        populateHomeScreen(false)
        viewModel.isParemDefault()
        if (prefs.showStatusBar) showStatusBar()
        else hideStatusBar()
        FocusModeManager.checkAndExpire(requireContext())
        viewModel.getWeather()
        updateGestureLetterOverlay()
    }

    override fun onClick(view: View) {
        when (view.id) {
            // The lock view must stay clickable but do nothing here: clicking it emits the
            // accessibility event MyAccessibilityService matches (by contentDescription) to
            // perform GLOBAL_ACTION_LOCK_SCREEN. See lockPhone().
            R.id.lock -> {}
            R.id.clock -> openClockApp()
            R.id.date -> openCalendarApp()
            R.id.setDefaultLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.tvScreenTime -> openScreenTimeDigitalWellbeing()

            else -> {
                try { // Launch app
                    val appLocation = view.tag.toString().toInt()
                    homeAppClicked(appLocation)
                } catch (e: Exception) {
                    Log.e("HomeFragment", "Failed to launch app", e)
                }
            }
        }
    }

    private fun openClockApp() {
        if (prefs.clockAppPackage.isBlank())
            openAlarmApp(requireContext())
        else
            launchApp(
                "Clock",
                prefs.clockAppPackage,
                prefs.clockAppClassName,
                prefs.clockAppUser
            )
    }

    private fun openCalendarApp() {
        if (prefs.calendarAppPackage.isBlank())
            openCalendar(requireContext())
        else
            launchApp(
                "Calendar",
                prefs.calendarAppPackage,
                prefs.calendarAppClassName,
                prefs.calendarAppUser
            )
    }

    override fun onLongClick(view: View): Boolean {
        val homeAppIds = mapOf(
            R.id.homeApp1 to 1, R.id.homeApp2 to 2, R.id.homeApp3 to 3, R.id.homeApp4 to 4,
            R.id.homeApp5 to 5, R.id.homeApp6 to 6, R.id.homeApp7 to 7, R.id.homeApp8 to 8
        )
        val slot = homeAppIds[view.id]
        if (slot != null) {
            showHomeSlotMenu(slot)
            return true
        }
        when (view.id) {
            // Don't clear the current clock/calendar prefs here: if the user backs out
            // of the app list without choosing, their existing choice must survive.
            R.id.clock -> showAppList(Constants.FLAG_SET_CLOCK_APP)

            R.id.date -> showAppList(Constants.FLAG_SET_CALENDAR_APP)

            R.id.setDefaultLauncher -> {
                prefs.hideSetDefaultLauncher = true
                binding.setDefaultLauncher.visibility = View.GONE
                if (viewModel.isParemDefault.value != true) {
                    requireContext().showToast(R.string.set_as_default_launcher)
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                }
            }
        }
        return true
    }

    private fun initObservers() {
        viewModel.refreshHome.observe(viewLifecycleOwner) {
            populateHomeScreen(it)
            if (widgetController?.needsRestore() == true) {
                viewLifecycleOwner.lifecycleScope.launch { widgetController?.restoreWidgets() }
            }
        }
        viewModel.isParemDefault.observe(viewLifecycleOwner) {
            if (it != true) {
                if (prefs.dailyWallpaper) {
                    prefs.dailyWallpaper = false
                    viewModel.cancelWallpaperWorker()
                }
                prefs.homeBottomAlignment = false
                setHomeAlignment()
            }
            binding.setDefaultLauncher.isVisible = it.not() && prefs.hideSetDefaultLauncher.not()
        }
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            setHomeAlignment(it)
        }
        viewModel.toggleDateTime.observe(viewLifecycleOwner) {
            populateDateTime()
        }
        viewModel.screenTimeValue.observe(viewLifecycleOwner) {
            it?.let { binding.tvScreenTime.text = it }
        }
        viewModel.weatherValue.observe(viewLifecycleOwner) {
            populateDateTime()
        }
    }

    private fun initSwipeTouchListener() {
        val context = requireContext()
        viewTouchListeners.forEach { it.cleanup() }
        viewTouchListeners.clear()
        screenTouchListener = getSwipeGestureListener(context)
        binding.mainLayout.setOnTouchListener(screenTouchListener)
        binding.homeApp1.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp1))
        binding.homeApp2.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp2))
        binding.homeApp3.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp3))
        binding.homeApp4.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp4))
        binding.homeApp5.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp5))
        binding.homeApp6.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp6))
        binding.homeApp7.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp7))
        binding.homeApp8.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp8))
    }

    private fun initClickListeners() {
        binding.lock.setOnClickListener(this)
        binding.clock.setOnClickListener(this)
        binding.date.setOnClickListener(this)
        binding.clock.setOnLongClickListener(this)
        binding.date.setOnLongClickListener(this)
        binding.setDefaultLauncher.setOnClickListener(this)
        binding.setDefaultLauncher.setOnLongClickListener(this)
        binding.tvScreenTime.setOnClickListener(this)
    }

    private fun setHomeAlignment(horizontalGravity: Int = prefs.homeAlignment) {
        val verticalGravity = if (prefs.homeBottomAlignment) Gravity.BOTTOM else Gravity.CENTER_VERTICAL
        binding.homeAppsLayout.gravity = horizontalGravity or verticalGravity
        binding.dateTimeLayout.gravity = horizontalGravity
        binding.homeApp1.gravity = horizontalGravity
        binding.homeApp2.gravity = horizontalGravity
        binding.homeApp3.gravity = horizontalGravity
        binding.homeApp4.gravity = horizontalGravity
        binding.homeApp5.gravity = horizontalGravity
        binding.homeApp6.gravity = horizontalGravity
        binding.homeApp7.gravity = horizontalGravity
        binding.homeApp8.gravity = horizontalGravity
    }

    private fun populateDateTime() {
        binding.dateTimeLayout.isVisible = prefs.dateTimeVisibility != Constants.DateTime.OFF
        binding.clock.isVisible = Constants.DateTime.isTimeVisible(prefs.dateTimeVisibility)
        binding.date.isVisible = Constants.DateTime.isDateVisible(prefs.dateTimeVisibility)

        val weatherTemp = WeatherManager.getDisplayString(requireContext())
        var dateText = LocalDate.now().format(getDateFormatter())
        if (weatherTemp.isNotEmpty()) dateText = "$weatherTemp  $dateText"

        if (!prefs.showStatusBar) {
            val ctx = requireContext()
            val batteryIntent = ctx.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val battery = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            if (battery > 0)
                dateText = getString(R.string.day_battery, dateText, battery)
        }
        val displayDate = dateText.replace(".,", ",")
        binding.date.text = displayDate
        binding.date.contentDescription = displayDate
        binding.clock.contentDescription = binding.clock.text
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun populateScreenTime() {
        if (requireContext().appUsagePermissionGranted().not()) return

        viewModel.getTodaysScreenTime()
        binding.tvScreenTime.visibility = View.VISIBLE
    }

    private fun populateHomeScreen(appCountUpdated: Boolean) {
        if (appCountUpdated) hideHomeApps()
        populateDateTime()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            populateScreenTime()

        val homeAppViews = listOf(
            binding.homeApp1, binding.homeApp2, binding.homeApp3, binding.homeApp4,
            binding.homeApp5, binding.homeApp6, binding.homeApp7, binding.homeApp8
        )
        val homeAppsNum = prefs.homeAppsNum.coerceAtMost(homeAppViews.size)

        val noteEnabled = QuickNoteManager.isEnabled(requireContext())
        effectiveNoteSlot = if (noteEnabled) homeAppsNum else -1

        for (i in 0 until homeAppsNum) {
            val slot = i + 1
            val view = homeAppViews[i]
            view.visibility = View.VISIBLE

            if (noteEnabled && slot == homeAppsNum) {
                // Quick Note always pins to the last visible slot
                view.text = QuickNoteManager.getPreviewText(requireContext())
                view.contentDescription = getString(R.string.quick_note)
                view.setCompoundDrawablesRelative(null, null, null, null)
            } else if (folderManager.isFolderSlot(slot)) {
                val folder = folderManager.getFolderGroup(slot)
                view.text = folder?.name ?: ""
                view.contentDescription = folder?.name ?: getString(R.string.long_press_to_select_app)
            } else {
                val appName = prefs.getHomeAppName(slot)
                if (!setHomeAppText(view, appName, prefs.getHomeAppPackage(slot), prefs.getHomeAppUser(slot))) {
                    prefs.setHomeAppName(slot, "")
                    prefs.setHomeAppPackage(slot, "")
                }
                view.contentDescription = appName.ifEmpty { getString(R.string.long_press_to_select_app) }
            }
        }

        // Dynamic fitting: adjust padding to clear date/time, then hide overflow apps
        if (homeAppsNum > 0) {
            binding.homeAppsLayout.post {
                if (!isAdded || _binding == null) return@post
                val layout = binding.homeAppsLayout

                // Adjust top padding to clear the dateTimeLayout
                val dtLayout = binding.dateTimeLayout
                val minTopPadding = if (dtLayout.isVisible) {
                    (dtLayout.top + dtLayout.height + (8 * resources.displayMetrics.density).toInt())
                } else {
                    (56 * resources.displayMetrics.density).toInt()
                }
                if (layout.paddingTop != minTopPadding) {
                    layout.setPadding(layout.paddingLeft, minTopPadding, layout.paddingRight, layout.paddingBottom)
                }

                // Run fitting after the padding change has been laid out
                layout.post fitApps@{
                    if (!isAdded || _binding == null) return@fitApps

                    val availableHeight = layout.height - layout.paddingTop - layout.paddingBottom
                    if (availableHeight <= 0) return@fitApps

                    // Subtract widget container height
                    val widgetHeight = when {
                        prefs.widgetPlacement == Constants.WidgetPlacement.ABOVE ->
                            binding.widgetScrollViewAbove?.let { if (it.isVisible) it.height else 0 } ?: 0
                        else ->
                            binding.widgetScrollViewBelow?.let { if (it.isVisible) it.height else 0 } ?: 0
                    }

                    val usableHeight = availableHeight - widgetHeight
                    if (usableHeight <= 0) return@fitApps

                    // Measure height of a single app view
                    val firstVisible = homeAppViews.firstOrNull { it.isVisible }
                    val singleAppHeight = firstVisible?.height ?: return@fitApps
                    if (singleAppHeight <= 0) return@fitApps

                    val maxFitting = usableHeight / singleAppHeight
                    if (maxFitting < homeAppsNum) {
                        // Hide excess apps from the bottom, but keep Quick Note at last visible slot
                        for (j in homeAppViews.indices.reversed()) {
                            if (j >= maxFitting && homeAppViews[j].isVisible) {
                                homeAppViews[j].visibility = View.GONE
                            }
                        }
                        // If note is enabled, ensure it shows at the last visible slot
                        if (noteEnabled && maxFitting > 0) {
                            val ctx = context ?: return@fitApps
                            val noteView = homeAppViews[maxFitting - 1]
                            noteView.visibility = View.VISIBLE
                            noteView.text = QuickNoteManager.getPreviewText(ctx)
                            noteView.contentDescription = getString(R.string.quick_note)
                            noteView.setCompoundDrawablesRelative(null, null, null, null)
                            effectiveNoteSlot = maxFitting
                        }
                    }
                }
            }
        }
    }

    private fun setHomeAppText(textView: TextView, appName: String, packageName: String, userString: String): Boolean {
        if (isPackageInstalled(requireContext(), packageName, userString)) {
            textView.text = appName
            if (prefs.showIcons && packageName.isNotEmpty()) {
                val iconSize = 20.dpToPx()
                val icon = if (prefs.iconPackPackage.isNotEmpty()) {
                    IconPackManager.getIconForApp(requireContext(), prefs.iconPackPackage, packageName, null)
                } else null
                val drawable = icon ?: try {
                    requireContext().packageManager.getApplicationIcon(packageName)
                } catch (_: Exception) { null }
                drawable?.setBounds(0, 0, iconSize, iconSize)
                textView.setCompoundDrawablesRelative(drawable, null, null, null)
                textView.compoundDrawablePadding = 8.dpToPx()
            } else {
                textView.setCompoundDrawablesRelative(null, null, null, null)
            }
            return true
        }
        textView.text = ""
        textView.setCompoundDrawablesRelative(null, null, null, null)
        return false
    }

    private fun hideHomeApps() {
        binding.homeApp1.visibility = View.GONE
        binding.homeApp2.visibility = View.GONE
        binding.homeApp3.visibility = View.GONE
        binding.homeApp4.visibility = View.GONE
        binding.homeApp5.visibility = View.GONE
        binding.homeApp6.visibility = View.GONE
        binding.homeApp7.visibility = View.GONE
        binding.homeApp8.visibility = View.GONE
    }

    private fun homeAppClicked(location: Int) {
        if (folderManager.isFolderSlot(location)) {
            toggleFolderExpansion(location)
            return
        }
        if (effectiveNoteSlot > 0 && location == effectiveNoteSlot) {
            showQuickNoteDialog()
            return
        }
        if (prefs.getHomeAppName(location).isEmpty()) showLongPressToast()
        else checkBadHabitAndLaunch(
            prefs.getHomeAppName(location),
            prefs.getHomeAppPackage(location),
            prefs.getHomeAppActivityClassName(location),
            prefs.getHomeAppUser(location)
        )
    }

    private fun launchApp(appName: String, packageName: String, activityClassName: String?, userString: String) {
        viewModel.selectedApp(
            AppModel(
                appName,
                null,
                packageName,
                activityClassName,
                false,
                getUserHandleFromString(requireContext(), userString)
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun checkBadHabitAndLaunch(name: String, pkg: String, activity: String?, user: String) {
        val ctx = context ?: return
        if (!AppLimitManager.hasLimit(ctx, pkg)) {
            launchApp(name, pkg, activity, user)
            return
        }
        val limitMinutes = AppLimitManager.getLimit(ctx, pkg) ?: run {
            launchApp(name, pkg, activity, user)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val usageMs = withContext(Dispatchers.IO) {
                UsageStatsHelper.getUsageForApp(ctx, pkg)
            }
            if (!isAdded) return@launch
            val usageMinutes = usageMs / 60_000
            if (usageMinutes >= limitMinutes) {
                showBadHabitWarningDialog(name, pkg, activity, user, usageMinutes, limitMinutes)
            } else {
                launchApp(name, pkg, activity, user)
            }
        }
    }

    private fun showBadHabitWarningDialog(
        name: String, pkg: String, activity: String?, user: String,
        usageMinutes: Long, limitMinutes: Int
    ) {
        val ctx = context ?: return
        BadHabitDialogs.showLimitWarning(ctx, name.ifEmpty { pkg }, usageMinutes, limitMinutes) {
            launchApp(name, pkg, activity, user)
        }
    }

    private fun showAppList(flag: Int, rename: Boolean = false, includeHiddenApps: Boolean = false) {
        viewModel.getAppList(includeHiddenApps)
        try {
            findNavController().navigate(
                R.id.action_mainFragment_to_appListFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
        } catch (e: Exception) {
            findNavController().navigate(
                R.id.appListFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
            Log.e("HomeFragment", "Navigation to app list failed, using fallback", e)
        }
    }

    private fun swipeDownAction() {
        when (prefs.swipeDownAction) {
            Constants.SwipeDownAction.SEARCH -> requireContext().openSearch()
            else -> expandNotificationDrawer(requireContext())
        }
    }

    private fun openSwipeRightApp() {
        if (!prefs.swipeRightEnabled) return
        executeGestureAction(prefs.getEffectiveSwipeRightAction()) {
            // Fallback for OPEN_APP action
            if (prefs.appPackageSwipeRight.isNotEmpty())
                launchApp(
                    prefs.appNameSwipeRight,
                    prefs.appPackageSwipeRight,
                    prefs.appActivityClassNameRight,
                    prefs.appUserSwipeRight
                )
            else openDialerApp(requireContext())
        }
    }

    private fun openSwipeLeftApp() {
        if (!prefs.swipeLeftEnabled) return
        executeGestureAction(prefs.getEffectiveSwipeLeftAction()) {
            // Fallback for OPEN_APP action
            if (prefs.appPackageSwipeLeft.isNotEmpty())
                launchApp(
                    prefs.appNameSwipeLeft,
                    prefs.appPackageSwipeLeft,
                    prefs.appActivityClassNameSwipeLeft,
                    prefs.appUserSwipeLeft
                )
            else openCameraApp(requireContext())
        }
    }

    private fun executeGestureAction(action: Int, openAppFallback: () -> Unit) {
        when (action) {
            Constants.GestureAction.OPEN_APP -> openAppFallback()
            Constants.GestureAction.OPEN_NOTIFICATIONS -> expandNotificationDrawer(requireContext())
            Constants.GestureAction.OPEN_SEARCH -> requireContext().openSearch()
            Constants.GestureAction.LOCK_SCREEN -> lockPhone()
            Constants.GestureAction.OPEN_CAMERA -> openCameraApp(requireContext())
            Constants.GestureAction.TOGGLE_FLASHLIGHT -> toggleFlashlight()
            Constants.GestureAction.NONE -> { /* do nothing */ }
        }
    }

    private fun toggleFlashlight() {
        try {
            val cameraManager = requireContext().getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return
            flashlightOn = !flashlightOn
            cameraManager.setTorchMode(cameraId, flashlightOn)
        } catch (e: Exception) {
            Log.e("HomeFragment", "Failed to toggle flashlight", e)
        }
    }

    private fun lockPhone() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isAccessServiceEnabled(requireContext())) {
                binding.lock.performClick()
            } else {
                deviceManager.lockNow()
            }
        } catch (e: SecurityException) {
            prefs.lockModeOn = false
            requireContext().showToast(getString(R.string.please_turn_on_double_tap_to_unlock), Toast.LENGTH_LONG)
            findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
        } catch (e: Exception) {
            requireContext().showToast(getString(R.string.launcher_failed_to_lock_device), Toast.LENGTH_LONG)
        }
    }

    private fun showStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            requireActivity().window.insetsController?.show(WindowInsets.Type.statusBars())
        else
            @Suppress("DEPRECATION", "InlinedApi")
            requireActivity().window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
    }

    private fun hideStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            requireActivity().window.insetsController?.hide(WindowInsets.Type.statusBars())
        else {
            @Suppress("DEPRECATION")
            requireActivity().window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN
            }
        }
    }

    private fun openScreenTimeDigitalWellbeing() {
        try {
            ScreenTimeGraphDialog(requireContext()).show()
        } catch (e: Exception) {
            Log.e("HomeFragment", "Failed to show screen time graph", e)
            // Fallback to Digital Wellbeing
            val intent = Intent()
            try {
                intent.setClassName(
                    Constants.DIGITAL_WELLBEING_PACKAGE_NAME,
                    Constants.DIGITAL_WELLBEING_ACTIVITY
                )
                startActivity(intent)
            } catch (e2: Exception) {
                Log.e("HomeFragment", "Failed to open Digital Wellbeing", e2)
            }
        }
    }

    private fun toggleFolderExpansion(slot: Int) {
        val folder = folderManager.getFolderGroup(slot) ?: return
        if (folder.apps.isEmpty()) return

        val menu = BottomSheetMenu(requireContext()).title(folder.name)
        for (app in folder.apps) {
            menu.option(app.appName) {
                launchApp(app.appName, app.packageName, app.activityClassName, app.userString)
            }
        }
        menu.show()
    }

    private fun showHomeSlotMenu(slot: Int) {
        val hasApp = prefs.getHomeAppName(slot).isNotEmpty()
        val isFolder = folderManager.isFolderSlot(slot)
        val isNote = effectiveNoteSlot > 0 && slot == effectiveNoteSlot

        val menu = BottomSheetMenu(requireContext())

        // Set / change app
        menu.option(if (hasApp) getString(R.string.change_app) else getString(R.string.select_app)) {
            showAppList(slot, hasApp, true)
        }

        // Create folder
        menu.option(getString(R.string.create_folder)) {
            showCreateFolderDialog(slot)
        }

        // App limit toggle (only for regular apps, not folders or notes)
        if (hasApp && !isFolder && !isNote) {
            val pkg = prefs.getHomeAppPackage(slot)
            if (pkg.isNotEmpty()) {
                val hasLimit = AppLimitManager.hasLimit(requireContext(), pkg)
                menu.option(if (hasLimit) getString(R.string.remove_time_limit) else getString(R.string.set_time_limit)) {
                    if (hasLimit) {
                        AppLimitManager.removeLimit(requireContext(), pkg)
                    } else {
                        val ctx = context ?: return@option
                        BadHabitDialogs.showTimeLimitPicker(ctx, pkg) { populateHomeScreen(false) }
                    }
                }
            }
        }

        // Remove (if occupied)
        if (hasApp || isFolder || isNote) {
            menu.option(getString(R.string.delete)) {
                if (isFolder) folderManager.removeFolder(slot)
                if (isNote) {
                    // The note only overlays the last slot; removing it must not wipe
                    // the app that is pinned underneath
                    QuickNoteManager.setEnabled(requireContext(), false)
                } else {
                    prefs.setHomeAppName(slot, "")
                    prefs.setHomeAppPackage(slot, "")
                    prefs.setHomeAppActivityClassName(slot, "")
                    prefs.setHomeAppUser(slot, "")
                }
                populateHomeScreen(false)
            }
        }

        menu.show()
    }

    private fun showCreateFolderDialog(slot: Int) {
        val pm = requireContext().packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val resolveInfos = withContext(Dispatchers.IO) {
                pm.queryIntentActivities(intent, 0)
                    .sortedBy { it.loadLabel(pm).toString().lowercase() }
                    .take(50)
            }

            if (!isAdded || _binding == null) return@launch

            val dialog = BottomSheetDialog(requireContext())
            val container = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setBackgroundColor(requireContext().getColorFromAttr(R.attr.primaryInverseColor))
                setPadding(24.dpToPx(), 16.dpToPx(), 24.dpToPx(), 24.dpToPx())
            }

            // Title
            val title = TextView(requireContext()).apply {
                text = getString(R.string.create_folder)
                textSize = 18f
                setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 12.dpToPx())
            }
            container.addView(title)

            // Folder name input
            val nameLabel = TextView(requireContext()).apply {
                text = getString(R.string.folder_name)
                textSize = 14f
                setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
            }
            container.addView(nameLabel)

            val nameInput = android.widget.EditText(requireContext()).apply {
                textSize = 16f
                setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
                setHintTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
                hint = "Social, Work, etc."
                background = null
                setPadding(0, 8.dpToPx(), 0, 16.dpToPx())
            }
            container.addView(nameInput)

            // App selection label
            val appsLabel = TextView(requireContext()).apply {
                text = getString(R.string.select_apps)
                textSize = 14f
                setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
                setPadding(0, 8.dpToPx(), 0, 4.dpToPx())
            }
            container.addView(appsLabel)

            val selectedApps = mutableListOf<FolderApp>()
            val checkboxes = mutableListOf<android.widget.CheckBox>()

            val scrollView = android.widget.ScrollView(requireContext()).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, 300.dpToPx()
                )
            }
            val appsContainer = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.VERTICAL
            }

            for (ri in resolveInfos) {
                val appLabel = ri.loadLabel(pm).toString()
                val pkg = ri.activityInfo.packageName
                val activity = ri.activityInfo.name
                val cb = android.widget.CheckBox(requireContext()).apply {
                    text = appLabel
                    textSize = 14f
                    setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
                    setPadding(8.dpToPx(), 2.dpToPx(), 0, 2.dpToPx())
                    setOnCheckedChangeListener { _, isChecked ->
                        val app = FolderApp(appLabel, pkg, activity, android.os.Process.myUserHandle().toString())
                        if (isChecked) {
                            if (selectedApps.size < 4) selectedApps.add(app)
                            else this.isChecked = false
                        } else {
                            selectedApps.removeAll { it.packageName == pkg }
                        }
                    }
                }
                checkboxes.add(cb)
                appsContainer.addView(cb)
            }
            scrollView.addView(appsContainer)
            container.addView(scrollView)

            // Save button
            val saveButton = TextView(requireContext()).apply {
                text = getString(R.string.save)
                textSize = 16f
                setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 16.dpToPx(), 0, 0)
                setOnClickListener {
                    val folderName = nameInput.text.toString().trim()
                    if (folderName.isEmpty() || selectedApps.isEmpty()) {
                        requireContext().showToast("Enter a name and select at least one app")
                        return@setOnClickListener
                    }
                    // Clear any existing app at this slot
                    prefs.setHomeAppName(slot, "")
                    prefs.setHomeAppPackage(slot, "")
                    folderManager.createFolder(slot, folderName, selectedApps)
                    populateHomeScreen(false)
                    dialog.dismiss()
                }
            }
            container.addView(saveButton)

            dialog.setContentView(container)
            dialog.show()
        }
    }

    private fun showQuickNoteDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_quick_note, null)
        val editText = view.findViewById<android.widget.EditText>(R.id.noteEditText)
        val saveButton = view.findViewById<TextView>(R.id.noteSaveButton)
        val clearButton = view.findViewById<TextView>(R.id.noteClearButton)
        editText.setText(QuickNoteManager.getText(requireContext()))
        saveButton.setOnClickListener {
            QuickNoteManager.setText(requireContext(), editText.text.toString())
            populateHomeScreen(false)
            dialog.dismiss()
        }
        clearButton?.setOnClickListener {
            QuickNoteManager.setText(requireContext(), "")
            editText.setText("")
            populateHomeScreen(false)
        }
        dialog.setContentView(view)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
    }

    private fun initGestureLetterOverlay() {
        binding.gestureLetterOverlay?.onLetterDetected = { letter ->
            val mapping = GestureLetterManager.getMapping(requireContext(), letter)
            if (mapping != null) {
                launchApp("", mapping.first, mapping.second, mapping.third)
            } else {
                requireContext().showToast(getString(R.string.no_app_for_letter, letter.toString()))
            }
        }
    }

    private fun updateGestureLetterOverlay() {
        val enabled = GestureLetterManager.isEnabled(requireContext())
        binding.gestureLetterOverlay?.isGestureLettersEnabled = enabled
        binding.gestureLetterOverlay?.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun showLongPressToast() = requireContext().showToast(getString(R.string.long_press_to_select_app))

    private fun textOnClick(view: View) = onClick(view)

    private fun textOnLongClick(view: View) = onLongClick(view)

    private fun getSwipeGestureListener(context: Context): OnSwipeTouchListener {
        return object : OnSwipeTouchListener(context) {
            override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
                binding.gestureLetterOverlay?.forwardTouchEvent(motionEvent)
                return super.onTouch(view, motionEvent)
            }

            override fun onSwipeLeft() {
                super.onSwipeLeft()
                openSwipeLeftApp()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                openSwipeRightApp()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                showAppList(Constants.FLAG_LAUNCH_APP)
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                swipeDownAction()
            }

            override fun onLongClick() {
                super.onLongClick()
                try {
                    showHomeLongPressMenu()
                } catch (e: Exception) {
                    Log.e("HomeFragment", "Failed to show home long press menu", e)
                }
            }

            override fun onDoubleClick() {
                super.onDoubleClick()
                DoubleTapActionManager.execute(
                    requireContext(),
                    lockPhone = { lockPhone() },
                    toggleFlashlight = { toggleFlashlight() }
                )
            }

            override fun onClick() {
                super.onClick()
            }
        }
    }

    private fun getViewSwipeTouchListener(context: Context, view: View): ViewSwipeTouchListener {
        val listener = object : ViewSwipeTouchListener(context, view) {
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                openSwipeLeftApp()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                openSwipeRightApp()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                val slot = try { view.tag.toString().toInt() } catch (_: Exception) { 0 }
                if (slot in 1..8 && SwipeUpAppManager.hasSwipeUpApp(requireContext(), slot)) {
                    launchApp(
                        SwipeUpAppManager.getSwipeUpAppName(requireContext(), slot),
                        SwipeUpAppManager.getSwipeUpAppPackage(requireContext(), slot),
                        SwipeUpAppManager.getSwipeUpAppActivity(requireContext(), slot),
                        SwipeUpAppManager.getSwipeUpAppUser(requireContext(), slot)
                    )
                } else {
                    showAppList(Constants.FLAG_LAUNCH_APP)
                }
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                swipeDownAction()
            }

            override fun onLongClick(view: View) {
                super.onLongClick(view)
                textOnLongClick(view)
            }

            override fun onClick(view: View) {
                super.onClick(view)
                textOnClick(view)
            }
        }
        viewTouchListeners.add(listener)
        return listener
    }

    private fun showHomeLongPressMenu() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_home_menu, null)
        view.findViewById<TextView>(R.id.menuAddWidget).setOnClickListener {
            dialog.dismiss()
            widgetController?.showWidgetPicker()
        }
        view.findViewById<TextView>(R.id.menuQuickNote).setOnClickListener {
            dialog.dismiss()
            QuickNoteManager.setEnabled(requireContext(), true)
            showQuickNoteDialog()
        }
        view.findViewById<TextView>(R.id.menuSettings).setOnClickListener {
            dialog.dismiss()
            findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
            viewModel.firstOpen(false)
        }
        dialog.setContentView(view)
        dialog.show()
    }


    override fun onDestroyView() {
        torchCallback?.let {
            try {
                val cameraManager = requireContext().getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                cameraManager.unregisterTorchCallback(it)
            } catch (_: Exception) {}
        }
        torchCallback = null
        widgetController?.cleanup()
        widgetController = null
        screenTouchListener?.cleanup()
        screenTouchListener = null
        viewTouchListeners.forEach { it.cleanup() }
        viewTouchListeners.clear()
        super.onDestroyView()
        _binding = null
    }
}