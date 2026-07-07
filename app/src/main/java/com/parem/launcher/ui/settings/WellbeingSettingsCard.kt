package com.parem.launcher.ui.settings

import android.os.Build
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.parem.launcher.MainViewModel
import com.parem.launcher.R
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.databinding.FragmentSettingsBinding
import com.parem.launcher.helper.appUsagePermissionGranted
import com.parem.launcher.helper.getAppsList
import com.parem.launcher.ui.FocusModeDialog
import com.parem.launcher.ui.ScreenTimeLimitDialog
import com.parem.launcher.ui.SettingsFragment
import kotlinx.coroutines.launch

/**
 * Card 5 ("Digital Wellbeing"): screen-time permission status, per-app time
 * limits, and focus mode.
 *
 * Extracted from SettingsFragment; mirrors HomeWidgetController's shape.
 */
class WellbeingSettingsCard(
    private val fragment: SettingsFragment,
    private val binding: FragmentSettingsBinding,
    private val prefs: Prefs,
    private val viewModel: MainViewModel,
    private val onWellbeingChanged: () -> Unit,
) : View.OnClickListener {

    private val context get() = binding.root.context

    fun bind() {
        populateScreenTimeOnOff()

        initClickListeners()
    }

    private fun initClickListeners() {
        binding.screenTimeOnOff.setOnClickListener(this)
        binding.focusModeToggle?.setOnClickListener(this)
        binding.screenTimeLimitsToggle?.setOnClickListener(this)
    }

    override fun onClick(view: View) {
        fragment.resetOpenPickers(view.id)
        when (view.id) {
            R.id.screenTimeOnOff -> viewModel.showDialog.postValue(Constants.Dialog.DIGITAL_WELLBEING)
            R.id.focusModeToggle -> showFocusModeFromSettings()
            R.id.screenTimeLimitsToggle -> showScreenTimeLimitsDialog()
        }
    }

    private fun populateScreenTimeOnOff() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (context.appUsagePermissionGranted()) binding.screenTimeOnOff.text = context.getString(R.string.on)
            else binding.screenTimeOnOff.text = context.getString(R.string.off)
        } else binding.screenTimeLayout.visibility = View.GONE
    }

    private fun showFocusModeFromSettings() {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            // Same source as the app drawer and folder picker: all user profiles, non-hidden apps, no cap.
            val apps = getAppsList(context, prefs, includeRegularApps = true, includeHiddenApps = false)

            if (!fragment.isAdded || !fragment.isBindingAlive()) return@launch

            // The whitelist is keyed by package name only, so collapse per-profile
            // duplicates — otherwise a work-profile app renders twice and one
            // visible app burns two of the five whitelist slots.
            val allApps = apps.map { it.appPackage to it.appLabel }.distinctBy { it.first }
            val dialog = FocusModeDialog(context, allApps)
            dialog.setOnDismissListener { onWellbeingChanged() }
            dialog.show()
        }
    }

    private fun showScreenTimeLimitsDialog() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !context.appUsagePermissionGranted()) {
            viewModel.showDialog.postValue(Constants.Dialog.DIGITAL_WELLBEING)
            return
        }
        val usageMap = viewModel.perAppScreenTime.value ?: emptyMap()
        ScreenTimeLimitDialog(context, usageMap).show()
    }
}
