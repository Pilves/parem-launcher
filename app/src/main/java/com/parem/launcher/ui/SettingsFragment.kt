package com.parem.launcher.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.parem.launcher.BuildConfig
import com.parem.launcher.MainActivity
import com.parem.launcher.MainViewModel
import com.parem.launcher.R
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.databinding.FragmentSettingsBinding
import com.parem.launcher.helper.DoubleTapActionManager
import com.parem.launcher.helper.FocusModeManager
import com.parem.launcher.helper.GestureLetterManager
import com.parem.launcher.helper.IconPackManager

import com.parem.launcher.helper.ThemeScheduleManager
import com.parem.launcher.helper.CityResult
import com.parem.launcher.helper.WeatherManager
import com.parem.launcher.helper.animateAlpha
import com.parem.launcher.helper.appUsagePermissionGranted
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.helper.isAccessServiceEnabled
import com.parem.launcher.helper.isDarkThemeOn
import com.parem.launcher.helper.isParemDefault
import com.parem.launcher.helper.openAppInfo
import com.parem.launcher.helper.openUrl
import com.parem.launcher.helper.setPlainWallpaper
import com.parem.launcher.helper.showToast
import com.parem.launcher.listener.DeviceAdmin
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var importSettingsLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        importSettingsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                result.data?.data?.let { uri -> importSettings(uri) }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
        viewModel.isParemDefault()

        deviceManager = requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(requireContext(), DeviceAdmin::class.java)

        binding.homeAppsNum.text = prefs.homeAppsNum.toString()
        populateKeyboardText()
        populateScreenTimeOnOff()
        populateSortByUsage()
        populateWidgetPlacement()
        populateShowIcons()
        populateWallpaperText()
        populateAppThemeText()
        populateTextSize()
        populateAlignment()
        populateStatusBar()
        populateDateTime()
        populateSwipeApps()
        populateSwipeDownAction()
        populateDoubleTapAction()
        populateWellbeingSection()
        initClickListeners()
        initObservers()
    }

    override fun onClick(view: View) {
        binding.appsNumSelectLayout.visibility = View.GONE
        binding.dateTimeSelectLayout.visibility = View.GONE
        binding.swipeDownSelectLayout.visibility = View.GONE
        binding.flSwipeDown.visibility = View.GONE
        binding.textSizesLayout.visibility = View.GONE
        if (view.id != R.id.alignmentBottom)
            binding.alignmentSelectLayout.visibility = View.GONE

        when (view.id) {
            R.id.paremHiddenApps -> showHiddenApps()
            R.id.screenTimeOnOff -> viewModel.showDialog.postValue(Constants.Dialog.DIGITAL_WELLBEING)
            R.id.sortByUsage -> toggleSortByUsage()
            R.id.appInfo -> openAppInfo(requireContext(), Process.myUserHandle(), BuildConfig.APPLICATION_ID)
            R.id.setLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.autoShowKeyboard -> toggleKeyboardText()
            R.id.homeAppsNum -> binding.appsNumSelectLayout.visibility = View.VISIBLE
            R.id.dailyWallpaperUrl -> requireContext().openUrl(prefs.dailyWallpaperUrl)
            R.id.dailyWallpaper -> toggleDailyWallpaperUpdate()
            R.id.alignment -> binding.alignmentSelectLayout.visibility = View.VISIBLE
            R.id.alignmentLeft -> viewModel.updateHomeAlignment(Gravity.START)
            R.id.alignmentCenter -> viewModel.updateHomeAlignment(Gravity.CENTER)
            R.id.alignmentRight -> viewModel.updateHomeAlignment(Gravity.END)
            R.id.alignmentBottom -> updateHomeBottomAlignment()
            R.id.statusBar -> toggleStatusBar()
            R.id.dateTime -> binding.dateTimeSelectLayout.visibility = View.VISIBLE
            R.id.dateTimeOn -> toggleDateTime(Constants.DateTime.ON)
            R.id.dateTimeOff -> toggleDateTime(Constants.DateTime.OFF)
            R.id.dateOnly -> toggleDateTime(Constants.DateTime.DATE_ONLY)
            R.id.appThemeText -> showThemePicker()
            R.id.textSizeValue -> binding.textSizesLayout.visibility = View.VISIBLE
            R.id.actionAccessibility -> openAccessibilityService()
            R.id.closeAccessibility -> toggleAccessibilityVisibility(false)
            R.id.notWorking -> requireContext().openUrl(Constants.URL_DOUBLE_TAP)

            R.id.tvGestures -> binding.flSwipeDown.visibility = View.VISIBLE

            R.id.maxApps0 -> updateHomeAppsNum(0)
            R.id.maxApps1 -> updateHomeAppsNum(1)
            R.id.maxApps2 -> updateHomeAppsNum(2)
            R.id.maxApps3 -> updateHomeAppsNum(3)
            R.id.maxApps4 -> updateHomeAppsNum(4)
            R.id.maxApps5 -> updateHomeAppsNum(5)
            R.id.maxApps6 -> updateHomeAppsNum(6)
            R.id.maxApps7 -> updateHomeAppsNum(7)
            R.id.maxApps8 -> updateHomeAppsNum(8)

            R.id.textSize1 -> updateTextSizeScale(Constants.TextSize.ONE)
            R.id.textSize2 -> updateTextSizeScale(Constants.TextSize.TWO)
            R.id.textSize3 -> updateTextSizeScale(Constants.TextSize.THREE)
            R.id.textSize4 -> updateTextSizeScale(Constants.TextSize.FOUR)
            R.id.textSize5 -> updateTextSizeScale(Constants.TextSize.FIVE)
            R.id.textSize6 -> updateTextSizeScale(Constants.TextSize.SIX)
            R.id.textSize7 -> updateTextSizeScale(Constants.TextSize.SEVEN)

            R.id.swipeLeftApp -> showSwipeActionPicker(isLeft = true)
            R.id.swipeRightApp -> showSwipeActionPicker(isLeft = false)
            R.id.swipeDownAction -> binding.swipeDownSelectLayout.visibility = View.VISIBLE
            R.id.notifications -> updateSwipeDownAction(Constants.SwipeDownAction.NOTIFICATIONS)
            R.id.search -> updateSwipeDownAction(Constants.SwipeDownAction.SEARCH)

            R.id.widgetPlacement -> toggleWidgetPlacement()
            R.id.showIconsToggle -> toggleShowIcons()
            R.id.exportSettings -> exportSettings()
            R.id.importSettings -> confirmImportSettings()
            R.id.aboutParem -> requireContext().openUrl(Constants.URL_ABOUT_PAREM)
            R.id.github -> requireContext().openUrl(Constants.URL_PAREM_GITHUB)
            R.id.privacy -> requireContext().openUrl(Constants.URL_PAREM_PRIVACY)
            R.id.focusModeToggle -> showFocusModeFromSettings()
            R.id.screenTimeLimitsToggle -> showScreenTimeLimitsDialog()

            R.id.doubleTapAction -> showDoubleTapActionPicker()
            R.id.gestureLettersToggle -> {
                if (GestureLetterManager.isEnabled(requireContext())) {
                    showGestureLetterConfigDialog()
                } else {
                    GestureLetterManager.setEnabled(requireContext(), true)
                    populateWellbeingSection()
                }
            }
            R.id.weatherToggle -> {
                if (WeatherManager.isEnabled(requireContext())) {
                    WeatherManager.setEnabled(requireContext(), false)
                } else {
                    showWeatherSettingsDialog()
                }
                populateWellbeingSection()
            }
        }
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.alignment -> {
                prefs.appLabelAlignment = prefs.homeAlignment
                findNavController().navigate(R.id.action_settingsFragment_to_appListFragment)
                requireContext().showToast(getString(R.string.alignment_changed))
            }

            R.id.dailyWallpaper -> removeWallpaper()
            R.id.appThemeText -> showThemePicker()

            R.id.swipeLeftApp -> toggleSwipeLeft()
            R.id.swipeRightApp -> toggleSwipeRight()
        }
        return true
    }

    private fun populateWellbeingSection() {
        binding.focusModeToggle?.text = if (FocusModeManager.isActive(requireContext())) getString(R.string.on) else getString(R.string.off)

        binding.gestureLettersToggle?.text = if (GestureLetterManager.isEnabled(requireContext())) getString(R.string.on) else getString(R.string.off)
        binding.weatherToggle?.text = if (WeatherManager.isEnabled(requireContext())) {
            val city = WeatherManager.getCityName(requireContext())
            city.ifEmpty { getString(R.string.on) }
        } else getString(R.string.off)
    }

    private fun getDoubleTapLabel(): String {
        return when (DoubleTapActionManager.getAction(requireContext())) {
            Constants.GestureAction.OPEN_APP -> getString(R.string.open_app)
            Constants.GestureAction.OPEN_NOTIFICATIONS -> getString(R.string.notifications)
            Constants.GestureAction.OPEN_SEARCH -> getString(R.string.search)
            Constants.GestureAction.LOCK_SCREEN -> getString(R.string.lock_screen)
            Constants.GestureAction.OPEN_CAMERA -> getString(R.string.camera)
            Constants.GestureAction.TOGGLE_FLASHLIGHT -> getString(R.string.flashlight)
            Constants.GestureAction.NONE -> getString(R.string.none)
            else -> getString(R.string.lock_screen)
        }
    }

    private fun initClickListeners() {
        binding.paremHiddenApps.setOnClickListener(this)
        binding.scrollLayout.setOnClickListener(this)
        binding.appInfo.setOnClickListener(this)
        binding.setLauncher.setOnClickListener(this)
        binding.exportSettings?.setOnClickListener(this)
        binding.importSettings?.setOnClickListener(this)
        binding.aboutParem.setOnClickListener(this)
        binding.autoShowKeyboard.setOnClickListener(this)
        binding.doubleTapAction?.setOnClickListener(this)
        binding.homeAppsNum.setOnClickListener(this)
        binding.screenTimeOnOff.setOnClickListener(this)
        binding.sortByUsage?.setOnClickListener(this)
        binding.widgetPlacement?.setOnClickListener(this)
        binding.showIconsToggle?.setOnClickListener(this)
        binding.dailyWallpaperUrl.setOnClickListener(this)
        binding.dailyWallpaper.setOnClickListener(this)
        binding.alignment.setOnClickListener(this)
        binding.alignmentLeft.setOnClickListener(this)
        binding.alignmentCenter.setOnClickListener(this)
        binding.alignmentRight.setOnClickListener(this)
        binding.alignmentBottom.setOnClickListener(this)
        binding.statusBar.setOnClickListener(this)
        binding.dateTime.setOnClickListener(this)
        binding.dateTimeOn.setOnClickListener(this)
        binding.dateTimeOff.setOnClickListener(this)
        binding.dateOnly.setOnClickListener(this)
        binding.swipeLeftApp.setOnClickListener(this)
        binding.swipeRightApp.setOnClickListener(this)
        binding.swipeDownAction.setOnClickListener(this)
        binding.search.setOnClickListener(this)
        binding.notifications.setOnClickListener(this)
        binding.appThemeText.setOnClickListener(this)
        binding.textSizeValue.setOnClickListener(this)
        binding.actionAccessibility.setOnClickListener(this)
        binding.closeAccessibility.setOnClickListener(this)
        binding.notWorking.setOnClickListener(this)

        binding.github.setOnClickListener(this)
        binding.privacy.setOnClickListener(this)

        binding.maxApps0.setOnClickListener(this)
        binding.maxApps1.setOnClickListener(this)
        binding.maxApps2.setOnClickListener(this)
        binding.maxApps3.setOnClickListener(this)
        binding.maxApps4.setOnClickListener(this)
        binding.maxApps5.setOnClickListener(this)
        binding.maxApps6.setOnClickListener(this)
        binding.maxApps7.setOnClickListener(this)
        binding.maxApps8.setOnClickListener(this)

        binding.textSize1.setOnClickListener(this)
        binding.textSize2.setOnClickListener(this)
        binding.textSize3.setOnClickListener(this)
        binding.textSize4.setOnClickListener(this)
        binding.textSize5.setOnClickListener(this)
        binding.textSize6.setOnClickListener(this)
        binding.textSize7.setOnClickListener(this)

        binding.focusModeToggle?.setOnClickListener(this)
        binding.screenTimeLimitsToggle?.setOnClickListener(this)

        binding.gestureLettersToggle?.setOnClickListener(this)
        binding.tvGestures?.setOnClickListener(this)
        binding.weatherToggle?.setOnClickListener(this)

        binding.dailyWallpaper.setOnLongClickListener(this)
        binding.alignment.setOnLongClickListener(this)
        binding.appThemeText.setOnLongClickListener(this)
        binding.swipeLeftApp.setOnLongClickListener(this)
        binding.swipeRightApp.setOnLongClickListener(this)
    }

    private fun initObservers() {
        viewModel.isParemDefault.observe(viewLifecycleOwner) {
            if (it) {
                binding.setLauncher.text = getString(R.string.change_default_launcher)
            }
        }
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            populateAlignment()
        }
        viewModel.updateSwipeApps.observe(viewLifecycleOwner) {
            populateSwipeApps()
        }
    }

    private fun toggleSwipeLeft() {
        prefs.swipeLeftEnabled = !prefs.swipeLeftEnabled
        if (prefs.swipeLeftEnabled) {
            binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
            requireContext().showToast(getString(R.string.swipe_left_app_enabled))
        } else {
            binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
            requireContext().showToast(getString(R.string.swipe_left_app_disabled))
        }
    }

    private fun toggleSwipeRight() {
        prefs.swipeRightEnabled = !prefs.swipeRightEnabled
        if (prefs.swipeRightEnabled) {
            binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
            requireContext().showToast(getString(R.string.swipe_right_app_enabled))
        } else {
            binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
            requireContext().showToast(getString(R.string.swipe_right_app_disabled))
        }
    }

    private fun toggleStatusBar() {
        prefs.showStatusBar = !prefs.showStatusBar
        populateStatusBar()
    }

    private fun populateStatusBar() {
        if (prefs.showStatusBar) {
            showStatusBar()
            binding.statusBar.text = getString(R.string.on)
        } else {
            hideStatusBar()
            binding.statusBar.text = getString(R.string.off)
        }
    }

    private fun toggleDateTime(selected: Int) {
        prefs.dateTimeVisibility = selected
        populateDateTime()
        viewModel.toggleDateTime()
    }

    private fun populateDateTime() {
        binding.dateTime.text = getString(
            when (prefs.dateTimeVisibility) {
                Constants.DateTime.DATE_ONLY -> R.string.date
                Constants.DateTime.ON -> R.string.on
                else -> R.string.off
            }
        )
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

    private fun showHiddenApps() {
        if (prefs.hiddenApps.isEmpty()) {
            requireContext().showToast(getString(R.string.no_hidden_apps))
            return
        }
        viewModel.getHiddenApps()
        findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf(Constants.Key.FLAG to Constants.FLAG_HIDDEN_APPS)
        )
    }

    private fun toggleAccessibilityVisibility(show: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            binding.notWorking.visibility = View.VISIBLE
        if (isAccessServiceEnabled(requireContext()))
            binding.actionAccessibility.text = getString(R.string.disable)
        binding.accessibilityLayout.isVisible = show
        binding.scrollView.animateAlpha(if (show) 0.5f else 1f)
    }

    private fun openAccessibilityService() {
        toggleAccessibilityVisibility(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun removeActiveAdmin(toastMessage: String? = null) {
        try {
            deviceManager.removeActiveAdmin(componentName) // for backward compatibility
            requireContext().showToast(toastMessage)
        } catch (e: Exception) {
            Log.e("SettingsFragment", "Failed to remove device admin", e)
        }
    }

    private fun removeWallpaper() {
        setPlainWallpaper(requireContext(), android.R.color.black)
        if (!prefs.dailyWallpaper) return
        prefs.dailyWallpaper = false
        populateWallpaperText()
        viewModel.cancelWallpaperWorker()
    }

    private fun toggleDailyWallpaperUpdate() {
        if (prefs.dailyWallpaper.not() && viewModel.isParemDefault.value == false) {
            requireContext().showToast(R.string.set_as_default_launcher_first)
            return
        }
        prefs.dailyWallpaper = !prefs.dailyWallpaper
        populateWallpaperText()
        if (prefs.dailyWallpaper) {
            viewModel.setWallpaperWorker()
            showWallpaperToasts()
        } else viewModel.cancelWallpaperWorker()
    }

    private fun showWallpaperToasts() {
        if (isParemDefault(requireContext()))
            requireContext().showToast(getString(R.string.your_wallpaper_will_update_shortly))
        else
            requireContext().showToast(getString(R.string.parem_is_not_default_launcher), Toast.LENGTH_LONG)
    }

    private fun updateHomeAppsNum(num: Int) {
        binding.homeAppsNum.text = num.toString()
        binding.appsNumSelectLayout.visibility = View.GONE
        prefs.homeAppsNum = num
        viewModel.refreshHome(true)
    }

    private fun updateTextSizeScale(sizeScale: Float) {
        if (prefs.textSizeScale == sizeScale) return
        prefs.textSizeScale = sizeScale
        requireActivity().recreate()
    }

    private fun toggleKeyboardText() {
        if (prefs.autoShowKeyboard && prefs.keyboardMessageShown.not()) {
            viewModel.showDialog.postValue(Constants.Dialog.KEYBOARD)
            prefs.keyboardMessageShown = true
        } else {
            prefs.autoShowKeyboard = !prefs.autoShowKeyboard
            populateKeyboardText()
        }
    }

    private fun updateTheme(appTheme: Int) {
        if (AppCompatDelegate.getDefaultNightMode() == appTheme) return
        prefs.appTheme = appTheme
        populateAppThemeText(appTheme)
        setAppTheme(appTheme)
    }

    private fun setAppTheme(theme: Int) {
        if (AppCompatDelegate.getDefaultNightMode() == theme) return
        if (prefs.dailyWallpaper) {
            setPlainWallpaper(theme)
            viewModel.setWallpaperWorker()
        }
        requireActivity().recreate()
    }

    private fun setPlainWallpaper(appTheme: Int) {
        when (appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> setPlainWallpaper(requireContext(), android.R.color.black)
            AppCompatDelegate.MODE_NIGHT_NO -> setPlainWallpaper(requireContext(), android.R.color.white)
            else -> {
                if (requireContext().isDarkThemeOn())
                    setPlainWallpaper(requireContext(), android.R.color.black)
                else setPlainWallpaper(requireContext(), android.R.color.white)
            }
        }
    }

    private fun populateAppThemeText(appTheme: Int = prefs.appTheme) {
        val scheduleMode = ThemeScheduleManager.getMode(requireContext())
        binding.appThemeText.text = when {
            scheduleMode == Constants.ThemeScheduleMode.SCHEDULED -> getString(R.string.scheduled)
            scheduleMode == Constants.ThemeScheduleMode.SUNRISE_SUNSET -> getString(R.string.sunrise_sunset)
            appTheme == AppCompatDelegate.MODE_NIGHT_YES -> getString(R.string.dark)
            appTheme == AppCompatDelegate.MODE_NIGHT_NO -> getString(R.string.light)
            else -> getString(R.string.system_default)
        }
    }

    private fun populateTextSize() {
        binding.textSizeValue.text = when (prefs.textSizeScale) {
            Constants.TextSize.ONE -> 1
            Constants.TextSize.TWO -> 2
            Constants.TextSize.THREE -> 3
            Constants.TextSize.FOUR -> 4
            Constants.TextSize.FIVE -> 5
            Constants.TextSize.SIX -> 6
            Constants.TextSize.SEVEN -> 7
            else -> "--"
        }.toString()
    }

    private fun populateScreenTimeOnOff() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (requireContext().appUsagePermissionGranted()) binding.screenTimeOnOff.text = getString(R.string.on)
            else binding.screenTimeOnOff.text = getString(R.string.off)
        } else binding.screenTimeLayout.visibility = View.GONE
    }

    private fun populateSortByUsage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && requireContext().appUsagePermissionGranted()) {
            binding.sortByUsageLayout?.visibility = View.VISIBLE
            binding.sortByUsage?.text = if (prefs.appDrawerSortByUsage) getString(R.string.on) else getString(R.string.off)
        } else {
            binding.sortByUsageLayout?.visibility = View.GONE
        }
    }

    private fun toggleSortByUsage() {
        prefs.appDrawerSortByUsage = !prefs.appDrawerSortByUsage
        populateSortByUsage()
    }

    private fun toggleWidgetPlacement() {
        prefs.widgetPlacement = if (prefs.widgetPlacement == Constants.WidgetPlacement.ABOVE)
            Constants.WidgetPlacement.BELOW
        else
            Constants.WidgetPlacement.ABOVE
        populateWidgetPlacement()
        viewModel.refreshHome(false)
    }

    private fun populateWidgetPlacement() {
        binding.widgetPlacement?.text = if (prefs.widgetPlacement == Constants.WidgetPlacement.ABOVE)
            getString(R.string.above_apps)
        else
            getString(R.string.below_apps)
    }

    private fun toggleShowIcons() {
        if (!prefs.showIcons) {
            // Turning on - check for icon packs
            val packs = IconPackManager.getAvailableIconPacks(requireContext())
            if (packs.isEmpty()) {
                prefs.showIcons = true
                prefs.iconPackPackage = ""
            } else {
                showIconPackPicker(packs)
                return
            }
        } else {
            prefs.showIcons = false
            prefs.iconPackPackage = ""
        }
        populateShowIcons()
        viewModel.refreshHome(false)
    }

    private fun showIconPackPicker(packs: List<Pair<String, String>>) {
        val menu = BottomSheetMenu(requireContext())
            .title(getString(R.string.select_icon_pack))
        menu.option(getString(R.string.system_icons)) { applyIconPack("") }
        for ((pkg, label) in packs) {
            menu.option(label) { applyIconPack(pkg) }
        }
        menu.show()
    }

    private fun applyIconPack(pkg: String) {
        prefs.showIcons = true
        prefs.iconPackPackage = pkg
        populateShowIcons()
        viewModel.refreshHome(false)
    }

    private fun populateShowIcons() {
        binding.showIconsToggle?.text = if (prefs.showIcons) getString(R.string.on) else getString(R.string.off)
    }

    private fun populateKeyboardText() {
        if (prefs.autoShowKeyboard) binding.autoShowKeyboard.text = getString(R.string.on)
        else binding.autoShowKeyboard.text = getString(R.string.off)
    }

    private fun populateWallpaperText() {
        if (prefs.dailyWallpaper) binding.dailyWallpaper.text = getString(R.string.on)
        else binding.dailyWallpaper.text = getString(R.string.off)
    }

    private fun updateHomeBottomAlignment() {
        if (viewModel.isParemDefault.value != true) {
            requireContext().showToast(getString(R.string.please_set_parem_as_default_first), Toast.LENGTH_LONG)
            return
        }
        prefs.homeBottomAlignment = !prefs.homeBottomAlignment
        populateAlignment()
        viewModel.updateHomeAlignment(prefs.homeAlignment)
    }

    private fun populateAlignment() {
        when (prefs.homeAlignment) {
            Gravity.START -> binding.alignment.text = getString(R.string.left)
            Gravity.CENTER -> binding.alignment.text = getString(R.string.center)
            Gravity.END -> binding.alignment.text = getString(R.string.right)
        }
        binding.alignmentBottom.text = if (prefs.homeBottomAlignment)
            getString(R.string.bottom_on)
        else getString(R.string.bottom_off)
    }

    private fun populateDoubleTapAction() {
        binding.doubleTapAction?.text = getDoubleTapLabel()
    }

    private fun populateSwipeDownAction() {
        binding.swipeDownAction.text = when (prefs.swipeDownAction) {
            Constants.SwipeDownAction.NOTIFICATIONS -> getString(R.string.notifications)
            else -> getString(R.string.search)
        }
    }

    private fun updateSwipeDownAction(swipeDownFor: Int) {
        if (prefs.swipeDownAction == swipeDownFor) return
        prefs.swipeDownAction = swipeDownFor
        populateSwipeDownAction()
    }

    private fun populateSwipeApps() {
        binding.swipeLeftApp.text = gestureActionLabel(prefs.getEffectiveSwipeLeftAction(), prefs.appNameSwipeLeft)
        binding.swipeRightApp.text = gestureActionLabel(prefs.getEffectiveSwipeRightAction(), prefs.appNameSwipeRight)
        if (!prefs.swipeLeftEnabled)
            binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
        if (!prefs.swipeRightEnabled)
            binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
    }

    private fun gestureActionLabel(action: Int, appName: String): String {
        return when (action) {
            Constants.GestureAction.OPEN_APP -> appName
            Constants.GestureAction.OPEN_NOTIFICATIONS -> getString(R.string.notifications)
            Constants.GestureAction.OPEN_SEARCH -> getString(R.string.search)
            Constants.GestureAction.LOCK_SCREEN -> getString(R.string.lock_screen)
            Constants.GestureAction.OPEN_CAMERA -> getString(R.string.camera)
            Constants.GestureAction.TOGGLE_FLASHLIGHT -> getString(R.string.flashlight)
            Constants.GestureAction.NONE -> getString(R.string.none)
            else -> appName
        }
    }

    private fun showSwipeActionPicker(isLeft: Boolean) {
        if (isLeft && !prefs.swipeLeftEnabled) {
            requireContext().showToast(getString(R.string.long_press_to_enable))
            return
        }
        if (!isLeft && !prefs.swipeRightEnabled) {
            requireContext().showToast(getString(R.string.long_press_to_enable))
            return
        }

        val menu = BottomSheetMenu(requireContext())
            .title(getString(R.string.select_action))
        for ((label, actionValue) in gestureActionChoices()) {
            menu.option(label) {
                if (isLeft) prefs.swipeLeftAction = actionValue
                else prefs.swipeRightAction = actionValue
                if (actionValue == Constants.GestureAction.OPEN_APP) {
                    showAppListForSwipe(if (isLeft) Constants.FLAG_SET_SWIPE_LEFT_APP else Constants.FLAG_SET_SWIPE_RIGHT_APP)
                } else {
                    populateSwipeApps()
                }
            }
        }
        menu.show()
    }

    private fun gestureActionChoices() = arrayOf(
        getString(R.string.open_app) to Constants.GestureAction.OPEN_APP,
        getString(R.string.notifications) to Constants.GestureAction.OPEN_NOTIFICATIONS,
        getString(R.string.search) to Constants.GestureAction.OPEN_SEARCH,
        getString(R.string.lock_screen) to Constants.GestureAction.LOCK_SCREEN,
        getString(R.string.camera) to Constants.GestureAction.OPEN_CAMERA,
        getString(R.string.flashlight) to Constants.GestureAction.TOGGLE_FLASHLIGHT,
        getString(R.string.none) to Constants.GestureAction.NONE,
    )

    private fun showDoubleTapActionPicker() {
        val menu = BottomSheetMenu(requireContext())
            .title(getString(R.string.double_tap_action))
        // Lock screen first: it is the double-tap default
        val choices = gestureActionChoices()
            .sortedByDescending { it.second == Constants.GestureAction.LOCK_SCREEN }
        for ((label, actionValue) in choices) {
            menu.option(label) {
                DoubleTapActionManager.setAction(requireContext(), actionValue)
                if (actionValue == Constants.GestureAction.OPEN_APP) {
                    showAppListForSwipe(Constants.FLAG_SET_DOUBLE_TAP_APP)
                }
                if (actionValue == Constants.GestureAction.LOCK_SCREEN) {
                    ensureLockPermission()
                }
                populateDoubleTapAction()
            }
        }
        menu.show()
    }

    private fun ensureLockPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!isAccessServiceEnabled(requireContext())) {
                toggleAccessibilityVisibility(true)
            }
        } else {
            if (!deviceManager.isAdminActive(componentName)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                intent.putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.admin_permission_message)
                )
                (requireActivity() as MainActivity).enableAdminLauncher.launch(intent)
            }
        }
    }

    private fun showWeatherSettingsDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_weather_settings, null)
        val searchInput = view.findViewById<android.widget.EditText>(R.id.citySearchInput)
        val statusText = view.findViewById<android.widget.TextView>(R.id.searchStatus)
        val resultsList = view.findViewById<android.widget.ListView>(R.id.cityResultsList)
        val currentCityLabel = view.findViewById<android.widget.TextView>(R.id.currentCityLabel)

        // Show current city if configured
        val currentCity = WeatherManager.getCityName(requireContext())
        if (currentCity.isNotEmpty()) {
            currentCityLabel.text = getString(R.string.current_city, currentCity)
            currentCityLabel.visibility = View.VISIBLE
        }

        val cities = mutableListOf<CityResult>()
        val textColor = requireContext().getColorFromAttr(R.attr.primaryColor)
        val subtextColor = requireContext().getColorFromAttr(R.attr.primaryColorTrans50)

        val adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = cities.size
            override fun getItem(position: Int) = cities[position]
            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val city = cities[position]
                val container = (convertView as? android.widget.LinearLayout)
                    ?: android.widget.LinearLayout(requireContext()).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        setPadding(8.dpToPx(), 10.dpToPx(), 8.dpToPx(), 10.dpToPx())
                    }
                container.removeAllViews()

                val nameTv = android.widget.TextView(requireContext()).apply {
                    text = city.name
                    textSize = 16f
                    setTextColor(textColor)
                }
                container.addView(nameTv)

                val detailParts = mutableListOf<String>()
                if (city.admin1.isNotEmpty()) detailParts.add(city.admin1)
                if (city.country.isNotEmpty()) detailParts.add(city.country)
                if (detailParts.isNotEmpty()) {
                    val detailTv = android.widget.TextView(requireContext()).apply {
                        text = detailParts.joinToString(", ")
                        textSize = 13f
                        setTextColor(subtextColor)
                    }
                    container.addView(detailTv)
                }

                return container
            }
        }
        resultsList.adapter = adapter

        resultsList.setOnItemClickListener { _, _, position, _ ->
            val city = cities[position]
            WeatherManager.setLocation(requireContext(), city.latitude.toString(), city.longitude.toString())
            WeatherManager.setCityName(requireContext(), city.displayName)
            WeatherManager.setEnabled(requireContext(), true)
            populateWellbeingSection()
            dialog.dismiss()
        }

        // Debounced search
        var searchJob: kotlinx.coroutines.Job? = null
        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                searchJob?.cancel()
                val query = s?.toString() ?: ""
                if (query.length < 2) {
                    cities.clear()
                    adapter.notifyDataSetChanged()
                    statusText.visibility = View.GONE
                    return
                }
                statusText.text = getString(R.string.searching)
                statusText.visibility = View.VISIBLE
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    kotlinx.coroutines.delay(300)
                    val results = WeatherManager.searchCities(query)
                    if (!isAdded || _binding == null) return@launch
                    cities.clear()
                    cities.addAll(results)
                    adapter.notifyDataSetChanged()
                    if (results.isEmpty()) {
                        statusText.text = getString(R.string.no_results)
                        statusText.visibility = View.VISIBLE
                    } else {
                        statusText.visibility = View.GONE
                    }
                }
            }
        })

        dialog.setContentView(view)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
    }

    private fun showThemePicker() {
        BottomSheetMenu(requireContext())
            .title(getString(R.string.theme_mode))
            .option(getString(R.string.light)) { setManualTheme(AppCompatDelegate.MODE_NIGHT_NO) }
            .option(getString(R.string.dark)) { setManualTheme(AppCompatDelegate.MODE_NIGHT_YES) }
            .option(getString(R.string.system_default)) { setManualTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
            .option(getString(R.string.scheduled)) { setScheduledTheme(Constants.ThemeScheduleMode.SCHEDULED) }
            .option(getString(R.string.sunrise_sunset)) { setScheduledTheme(Constants.ThemeScheduleMode.SUNRISE_SUNSET) }
            .show()
    }

    private fun setManualTheme(mode: Int) {
        ThemeScheduleManager.setMode(requireContext(), Constants.ThemeScheduleMode.MANUAL)
        viewModel.cancelThemeScheduleWorker()
        updateTheme(mode)
    }

    private fun setScheduledTheme(scheduleMode: Int) {
        ThemeScheduleManager.setMode(requireContext(), scheduleMode)
        viewModel.startThemeScheduleWorker()
        populateAppThemeText()
    }

    private fun showGestureLetterConfigDialog() {
        val menu = BottomSheetMenu(requireContext())
            .title(getString(R.string.gesture_letters))
        lateinit var dialog: com.google.android.material.bottomsheet.BottomSheetDialog

        val allMappings = GestureLetterManager.getAllMappings(requireContext())
        for (letter in GestureLetterManager.getSupportedLetters()) {
            val mapping = allMappings[letter]
            val appLabel = if (mapping != null) {
                try {
                    val pm = requireContext().packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(mapping.first, 0)).toString()
                } catch (_: Exception) { mapping.first }
            } else null

            val row = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(24.dpToPx(), 14.dpToPx(), 24.dpToPx(), 14.dpToPx())
            }

            val letterTv = android.widget.TextView(requireContext()).apply {
                text = if (appLabel != null) "$letter  →  $appLabel" else "$letter  —  Not set"
                textSize = 16f
                setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    dialog.dismiss()
                    viewModel.pendingGestureLetter = letter
                    showAppListForSwipe(Constants.FLAG_SET_GESTURE_LETTER_APP)
                }
            }
            row.addView(letterTv)

            if (mapping != null) {
                val clearBtn = android.widget.TextView(requireContext()).apply {
                    text = "✕"
                    textSize = 16f
                    setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
                    setPadding(12.dpToPx(), 0, 0, 0)
                    setOnClickListener {
                        GestureLetterManager.removeMapping(requireContext(), letter)
                        dialog.dismiss()
                        showGestureLetterConfigDialog()
                    }
                }
                row.addView(clearBtn)
            }

            menu.customView(row)
        }

        menu.option(getString(R.string.disable), dimmed = true) {
            GestureLetterManager.setEnabled(requireContext(), false)
            populateWellbeingSection()
        }

        dialog = menu.show()
    }


    private fun showFocusModeFromSettings() {
        val topApps = viewModel.perAppScreenTime.value?.entries
            ?.sortedByDescending { it.value }
            ?.take(10)
            ?.map { it.key to (requireContext().packageManager.let { pm ->
                try { pm.getApplicationLabel(pm.getApplicationInfo(it.key, 0)).toString() } catch (_: Exception) { it.key }
            }) } ?: emptyList()
        val dialog = FocusModeDialog(requireContext(), topApps)
        dialog.setOnDismissListener { populateWellbeingSection() }
        dialog.show()
    }

    private fun showScreenTimeLimitsDialog() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !requireContext().appUsagePermissionGranted()) {
            viewModel.showDialog.postValue(Constants.Dialog.DIGITAL_WELLBEING)
            return
        }
        val usageMap = viewModel.perAppScreenTime.value ?: emptyMap()
        ScreenTimeLimitDialog(requireContext(), usageMap).show()
    }

    private fun exportSettings() {
        val ctx = requireContext()
        val resolver = ctx.contentResolver
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val jsonString = withContext(Dispatchers.IO) {
                    val json = prefs.exportToJson()
                    json.toString(2)
                }

                withContext(Dispatchers.IO) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, "parem_settings.json")
                        put(MediaStore.Downloads.MIME_TYPE, "application/json")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                    }

                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                        ?: throw java.io.IOException("Failed to create export file")
                    resolver.openOutputStream(uri)?.use { it.write(jsonString.toByteArray()) }
                }
                ctx.showToast(getString(R.string.settings_exported))
            } catch (e: Exception) {
                ctx.showToast(getString(R.string.settings_export_failed, e.message))
            }
        }
    }

    private fun confirmImportSettings() {
        BottomSheetMenu(requireContext())
            .message(getString(R.string.import_settings_confirm))
            .option(getString(R.string.confirm)) {
                val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                }
                importSettingsLauncher.launch(intent)
            }
            .option(getString(R.string.cancel), dimmed = true) {}
            .show()
    }

    private fun importSettings(uri: android.net.Uri) {
        val ctx = requireContext()
        val activity = requireActivity()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val jsonString = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                }
                if (jsonString != null) {
                    val json = org.json.JSONObject(jsonString)
                    withContext(Dispatchers.IO) {
                        prefs.importFromJson(json)
                    }
                    ctx.showToast(getString(R.string.settings_imported))
                    activity.recreate()
                }
            } catch (e: Exception) {
                ctx.showToast(getString(R.string.settings_import_failed, e.message))
            }
        }
    }

    private fun showAppListForSwipe(flag: Int) {
        viewModel.getAppList(true)
        findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf(Constants.Key.FLAG to flag)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}