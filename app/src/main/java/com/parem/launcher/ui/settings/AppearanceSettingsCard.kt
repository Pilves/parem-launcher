package com.parem.launcher.ui.settings

import android.app.TimePickerDialog
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import com.parem.launcher.MainViewModel
import com.parem.launcher.R
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.databinding.FragmentSettingsBinding
import com.parem.launcher.helper.ThemeScheduleManager
import com.parem.launcher.helper.WeatherManager
import com.parem.launcher.helper.isDarkThemeOn
import com.parem.launcher.helper.isParemDefault
import com.parem.launcher.helper.openUrl
import com.parem.launcher.helper.setPlainWallpaper
import com.parem.launcher.helper.showToast
import com.parem.launcher.ui.BottomSheetMenu
import com.parem.launcher.ui.SettingsFragment
import java.util.Locale

/**
 * Card 3 ("Appearance"): theme mode (incl. scheduled/sunrise-sunset), text
 * size, daily wallpaper, notification bar, and weather (city picker).
 *
 * Extracted from SettingsFragment; mirrors HomeWidgetController's shape.
 */
class AppearanceSettingsCard(
    private val fragment: SettingsFragment,
    private val binding: FragmentSettingsBinding,
    private val prefs: Prefs,
    private val viewModel: MainViewModel,
    private val onWellbeingChanged: () -> Unit,
) : View.OnClickListener, View.OnLongClickListener {

    private val context get() = binding.root.context

    fun bind() {
        populateWallpaperText()
        populateAppThemeText()
        populateTextSize()
        populateStatusBar()

        initClickListeners()
    }

    private fun initClickListeners() {
        binding.appThemeText.setOnClickListener(this)
        binding.textSizeValue.setOnClickListener(this)
        binding.dailyWallpaperUrl.setOnClickListener(this)
        binding.dailyWallpaper.setOnClickListener(this)
        binding.statusBar.setOnClickListener(this)
        binding.weatherToggle?.setOnClickListener(this)

        binding.textSize1.setOnClickListener(this)
        binding.textSize2.setOnClickListener(this)
        binding.textSize3.setOnClickListener(this)
        binding.textSize4.setOnClickListener(this)
        binding.textSize5.setOnClickListener(this)
        binding.textSize6.setOnClickListener(this)
        binding.textSize7.setOnClickListener(this)

        binding.dailyWallpaper.setOnLongClickListener(this)
        binding.appThemeText.setOnLongClickListener(this)
    }

    override fun onClick(view: View) {
        fragment.resetOpenPickers(view.id)
        when (view.id) {
            R.id.dailyWallpaperUrl -> context.openUrl(prefs.dailyWallpaperUrl)
            R.id.dailyWallpaper -> toggleDailyWallpaperUpdate()
            R.id.statusBar -> toggleStatusBar()
            R.id.appThemeText -> showThemePicker()
            R.id.textSizeValue -> binding.textSizesLayout.visibility = View.VISIBLE

            R.id.textSize1 -> updateTextSizeScale(Constants.TextSize.ONE)
            R.id.textSize2 -> updateTextSizeScale(Constants.TextSize.TWO)
            R.id.textSize3 -> updateTextSizeScale(Constants.TextSize.THREE)
            R.id.textSize4 -> updateTextSizeScale(Constants.TextSize.FOUR)
            R.id.textSize5 -> updateTextSizeScale(Constants.TextSize.FIVE)
            R.id.textSize6 -> updateTextSizeScale(Constants.TextSize.SIX)
            R.id.textSize7 -> updateTextSizeScale(Constants.TextSize.SEVEN)

            R.id.weatherToggle -> {
                if (WeatherManager.isEnabled(context)) {
                    WeatherManager.setEnabled(context, false)
                } else {
                    WeatherSettingsDialog(context, fragment.viewLifecycleOwner, onCityChosen = onWellbeingChanged).show()
                }
                onWellbeingChanged()
            }
        }
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.dailyWallpaper -> removeWallpaper()
            R.id.appThemeText -> showThemePicker()
        }
        return true
    }

    private fun toggleStatusBar() {
        prefs.showStatusBar = !prefs.showStatusBar
        populateStatusBar()
    }

    private fun populateStatusBar() {
        if (prefs.showStatusBar) {
            showStatusBar()
            binding.statusBar.text = context.getString(R.string.on)
        } else {
            hideStatusBar()
            binding.statusBar.text = context.getString(R.string.off)
        }
    }

    private fun showStatusBar() {
        val activity = fragment.requireActivity()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            activity.window.insetsController?.show(WindowInsets.Type.statusBars())
        else
            @Suppress("DEPRECATION", "InlinedApi")
            activity.window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
    }

    private fun hideStatusBar() {
        val activity = fragment.requireActivity()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            activity.window.insetsController?.hide(WindowInsets.Type.statusBars())
        else {
            @Suppress("DEPRECATION")
            activity.window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN
            }
        }
    }

    private fun removeWallpaper() {
        setPlainWallpaper(context, android.R.color.black)
        if (!prefs.dailyWallpaper) return
        prefs.dailyWallpaper = false
        populateWallpaperText()
        viewModel.cancelWallpaperWorker()
    }

    private fun toggleDailyWallpaperUpdate() {
        if (prefs.dailyWallpaper.not() && viewModel.isParemDefault.value == false) {
            context.showToast(R.string.set_as_default_launcher_first)
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
        if (isParemDefault(context))
            context.showToast(context.getString(R.string.your_wallpaper_will_update_shortly))
        else
            context.showToast(context.getString(R.string.parem_is_not_default_launcher), Toast.LENGTH_LONG)
    }

    private fun populateWallpaperText() {
        if (prefs.dailyWallpaper) binding.dailyWallpaper.text = context.getString(R.string.on)
        else binding.dailyWallpaper.text = context.getString(R.string.off)
    }

    private fun updateTextSizeScale(sizeScale: Float) {
        if (prefs.textSizeScale == sizeScale) return
        prefs.textSizeScale = sizeScale
        fragment.requireActivity().recreate()
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
        fragment.requireActivity().recreate()
    }

    private fun setPlainWallpaper(appTheme: Int) {
        when (appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> setPlainWallpaper(context, android.R.color.black)
            AppCompatDelegate.MODE_NIGHT_NO -> setPlainWallpaper(context, android.R.color.white)
            else -> {
                if (context.isDarkThemeOn())
                    setPlainWallpaper(context, android.R.color.black)
                else setPlainWallpaper(context, android.R.color.white)
            }
        }
    }

    private fun populateAppThemeText(appTheme: Int = prefs.appTheme) {
        val scheduleMode = ThemeScheduleManager.getMode(context)
        binding.appThemeText.text = when {
            scheduleMode == Constants.ThemeScheduleMode.SCHEDULED -> context.getString(R.string.scheduled)
            scheduleMode == Constants.ThemeScheduleMode.SUNRISE_SUNSET -> context.getString(R.string.sunrise_sunset)
            appTheme == AppCompatDelegate.MODE_NIGHT_YES -> context.getString(R.string.dark)
            appTheme == AppCompatDelegate.MODE_NIGHT_NO -> context.getString(R.string.light)
            else -> context.getString(R.string.system_default)
        }
    }

    private fun showThemePicker() {
        BottomSheetMenu(context)
            .title(context.getString(R.string.theme_mode))
            .option(context.getString(R.string.light)) { setManualTheme(AppCompatDelegate.MODE_NIGHT_NO) }
            .option(context.getString(R.string.dark)) { setManualTheme(AppCompatDelegate.MODE_NIGHT_YES) }
            .option(context.getString(R.string.system_default)) { setManualTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
            .option(context.getString(R.string.scheduled)) { setScheduledTheme(Constants.ThemeScheduleMode.SCHEDULED) }
            .option(context.getString(R.string.sunrise_sunset)) { setScheduledTheme(Constants.ThemeScheduleMode.SUNRISE_SUNSET) }
            .show()
    }

    private fun setManualTheme(mode: Int) {
        ThemeScheduleManager.setMode(context, Constants.ThemeScheduleMode.MANUAL)
        viewModel.cancelThemeScheduleWorker()
        updateTheme(mode)
    }

    private fun setScheduledTheme(scheduleMode: Int) {
        if (scheduleMode == Constants.ThemeScheduleMode.SCHEDULED) {
            // The schedule needs actual times; there was previously no UI for them
            promptScheduleTimes { applyScheduledTheme(scheduleMode) }
        } else {
            applyScheduledTheme(scheduleMode)
        }
    }

    private fun applyScheduledTheme(scheduleMode: Int) {
        val ctx = context
        ThemeScheduleManager.setMode(ctx, scheduleMode)
        viewModel.startThemeScheduleWorker()
        // Apply immediately instead of waiting for the periodic worker's next run
        val newTheme = if (ThemeScheduleManager.shouldBeDark(ctx))
            AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        if (AppCompatDelegate.getDefaultNightMode() != newTheme) {
            prefs.appTheme = newTheme
            fragment.requireActivity().recreate()
        } else {
            populateAppThemeText()
        }
    }

    private fun promptScheduleTimes(onDone: () -> Unit) {
        val ctx = context
        fun parse(time: String, defH: Int): Pair<Int, Int> {
            val parts = time.split(":")
            return (parts.getOrNull(0)?.toIntOrNull() ?: defH) to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
        }

        val (lightH, lightM) = parse(ThemeScheduleManager.getLightTime(ctx), 7)
        TimePickerDialog(ctx, { _, h, m ->
            ThemeScheduleManager.setLightTime(ctx, String.format(Locale.ROOT, "%02d:%02d", h, m))
            val (darkH, darkM) = parse(ThemeScheduleManager.getDarkTime(ctx), 19)
            TimePickerDialog(ctx, { _, h2, m2 ->
                ThemeScheduleManager.setDarkTime(ctx, String.format(Locale.ROOT, "%02d:%02d", h2, m2))
                onDone()
            }, darkH, darkM, true).apply { setTitle(ctx.getString(R.string.dark_theme_from)) }.show()
        }, lightH, lightM, true).apply { setTitle(ctx.getString(R.string.light_theme_from)) }.show()
    }
}
