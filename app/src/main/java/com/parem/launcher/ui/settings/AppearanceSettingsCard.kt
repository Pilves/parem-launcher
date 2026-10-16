package com.parem.launcher.ui.settings

import android.app.TimePickerDialog
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.doOnAttach
import com.parem.launcher.MainViewModel
import com.parem.launcher.R
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.databinding.FragmentSettingsBinding
import com.parem.launcher.helper.TextScale
import com.parem.launcher.helper.ThemeScheduleManager
import com.parem.launcher.helper.WeatherManager
import com.parem.launcher.helper.isDarkThemeOn
import com.parem.launcher.helper.isParemDefault
import com.parem.launcher.helper.onOff
import com.parem.launcher.helper.openUrl
import com.parem.launcher.helper.setPlainWallpaper
import com.parem.launcher.helper.setStatusBarVisible
import com.parem.launcher.helper.showToast
import com.parem.launcher.ui.BottomSheetMenu
import com.parem.launcher.ui.SettingsFragment
import com.parem.launcher.ui.dismissOnDestroy
import java.util.Locale

/**
 * Card 3 ("Appearance"): theme mode (incl. scheduled/sunrise-sunset), text
 * size, bold font, daily wallpaper, notification bar, and weather (city picker).
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
        populateBoldFont()
        populateStatusBar()

        initClickListeners()
    }

    private fun initClickListeners() {
        binding.appThemeText.setOnClickListener(this)
        binding.textSizeValue.setOnClickListener(this)
        binding.boldFont.setOnClickListener(this)
        binding.dailyWallpaperUrl.setOnClickListener(this)
        binding.dailyWallpaper.setOnClickListener(this)
        binding.statusBar.setOnClickListener(this)
        binding.weatherToggle?.setOnClickListener(this)

        binding.textSize1.setOnClickListener(this)
        binding.textSize2.setOnClickListener(this)
        binding.textSize3.setOnClickListener(this)
        binding.textSize4.setOnClickListener(this)

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
            R.id.boldFont -> toggleBoldFont()

            R.id.textSize1 -> updateTextSizeScale(TextScale.SMALLER)
            R.id.textSize2 -> updateTextSizeScale(TextScale.DEFAULT)
            R.id.textSize3 -> updateTextSizeScale(TextScale.LARGE)
            R.id.textSize4 -> updateTextSizeScale(TextScale.LARGER)

            R.id.weatherToggle -> {
                if (WeatherManager.isEnabled(context)) {
                    WeatherManager.setEnabled(context, false)
                } else {
                    WeatherSettingsDialog(context, fragment.viewLifecycleOwner, onCityChosen = onWellbeingChanged)
                        .dismissOnDestroy(fragment.viewLifecycleOwner).show()
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
        binding.statusBar.text = context.onOff(prefs.showStatusBar)
        // On a rotation restore this runs while MainActivity is still inflating its
        // layout, before setContentView installs the decor, and window.insetsController
        // throws. Once the view is attached the decor exists.
        binding.root.doOnAttach {
            if (!fragment.isAdded) return@doOnAttach
            fragment.requireActivity().setStatusBarVisible(prefs.showStatusBar)
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

    private fun updateTextSizeScale(step: Float) {
        if (TextScale.nearestStep(prefs.textSizeScale) == step) return
        prefs.textSizeScale = step
        fragment.activity?.recreate()
    }

    private fun populateTextSize() {
        // Pre-6.0 installs stored 0.6..1.3; show the step the activity actually applies
        binding.textSizeValue.text = context.getString(
            when (TextScale.nearestStep(prefs.textSizeScale)) {
                TextScale.SMALLER -> R.string.text_size_smaller
                TextScale.LARGE -> R.string.text_size_large
                TextScale.LARGER -> R.string.text_size_larger
                else -> R.string.text_size_default
            }
        )
    }

    private fun toggleBoldFont() {
        prefs.boldFont = !prefs.boldFont
        populateBoldFont()
        // The font lives in a theme overlay applied in MainActivity.onCreate
        fragment.activity?.recreate()
    }

    private fun populateBoldFont() {
        binding.boldFont.text = context.onOff(prefs.boldFont)
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
        fragment.activity?.recreate()
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
            .dismissOnDestroy(fragment.viewLifecycleOwner)
    }

    private fun setManualTheme(mode: Int) {
        ThemeScheduleManager.setMode(context, Constants.ThemeScheduleMode.MANUAL)
        ThemeScheduleManager.cancelWorker(context)
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
        ThemeScheduleManager.startWorker(ctx)
        // Apply immediately instead of waiting for the periodic worker's next run
        val newTheme = if (ThemeScheduleManager.shouldBeDark(ctx))
            AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        if (AppCompatDelegate.getDefaultNightMode() != newTheme) {
            prefs.appTheme = newTheme
            fragment.activity?.recreate()
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

        // Taken up front: the dark picker opens later, from the light one's callback
        val owner = fragment.viewLifecycleOwner
        val (lightH, lightM) = parse(ThemeScheduleManager.getLightTime(ctx), 7)
        TimePickerDialog(ctx, { _, h, m ->
            ThemeScheduleManager.setLightTime(ctx, String.format(Locale.ROOT, "%02d:%02d", h, m))
            val (darkH, darkM) = parse(ThemeScheduleManager.getDarkTime(ctx), 19)
            TimePickerDialog(ctx, { _, h2, m2 ->
                ThemeScheduleManager.setDarkTime(ctx, String.format(Locale.ROOT, "%02d:%02d", h2, m2))
                onDone()
            }, darkH, darkM, true).apply { setTitle(ctx.getString(R.string.dark_theme_from)) }
                .dismissOnDestroy(owner).show()
        }, lightH, lightM, true).apply { setTitle(ctx.getString(R.string.light_theme_from)) }
            .dismissOnDestroy(owner).show()
    }
}
