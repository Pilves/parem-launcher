package com.parem.launcher.ui.settings

import android.Manifest
import android.os.Build
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.parem.launcher.MainViewModel
import com.parem.launcher.R
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.databinding.FragmentSettingsBinding
import com.parem.launcher.helper.GrayscaleController
import com.parem.launcher.helper.GrayscalePairing
import com.parem.launcher.helper.WellbeingRowState
import com.parem.launcher.helper.WellbeingRowState.Row
import com.parem.launcher.helper.appUsagePermissionGranted
import com.parem.launcher.ui.BadHabitDialogs
import com.parem.launcher.helper.getAppsList
import com.parem.launcher.helper.showToast
import com.parem.launcher.helper.notifications.QuietNotificationsManager
import com.parem.launcher.helper.notifications.QuietNotificationsManager.State
import com.parem.launcher.ui.FocusModeDialog
import com.parem.launcher.ui.GrayscaleSheet
import com.parem.launcher.ui.QuietListSheet
import com.parem.launcher.ui.ScreenTimeLimitDialog
import com.parem.launcher.ui.SettingsFragment
import com.parem.launcher.ui.dismissOnDestroy
import kotlinx.coroutines.launch

/**
 * Card 5 ("Digital Wellbeing"): screen-time permission status, per-app time
 * limits, focus mode, grayscale, and the notification filter ("Hide from shade, keep for later").
 *
 * Extracted from SettingsFragment; mirrors HomeWidgetController's shape.
 */
class WellbeingSettingsCard(
    private val fragment: SettingsFragment,
    private val binding: FragmentSettingsBinding,
    private val prefs: Prefs,
    private val viewModel: MainViewModel,
    private val onWellbeingChanged: () -> Unit,
    private val requestNotificationPermission: ActivityResultLauncher<String>,
) : View.OnClickListener {

    private val context get() = binding.root.context

    fun bind() {
        populateUsageAccess()
        populateQuietNotif()
        populateGrayscale()

        initClickListeners()
        // The access grant happens in system settings; re-derive the state on return
        fragment.viewLifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && fragment.isBindingAlive()) {
                populateUsageAccess()
                populateQuietNotif()
                // The grant can land from the pairing notification while Settings is open
                populateGrayscale()
            }
        })
    }

    private fun initClickListeners() {
        binding.screenTimeOnOff.setOnClickListener(this)
        binding.focusModeToggle?.setOnClickListener(this)
        binding.grayscaleToggle?.setOnClickListener(this)
        binding.screenTimeLimitsToggle?.setOnClickListener(this)
        binding.quietNotifToggle?.setOnClickListener(this)
        binding.quietNotifAllowed?.setOnClickListener(this)
        binding.quietNotifSilent?.setOnClickListener(this)
    }

    override fun onClick(view: View) {
        fragment.resetOpenPickers(view.id)
        when (view.id) {
            R.id.screenTimeOnOff -> BadHabitDialogs.showUsageAccessSheet(context)
            R.id.focusModeToggle -> showFocusModeFromSettings()
            R.id.grayscaleToggle -> GrayscaleSheet.open(fragment, onPair = ::startPairing, onChanged = {
                if (fragment.isBindingAlive()) populateGrayscale()
            })
            R.id.screenTimeLimitsToggle -> showScreenTimeLimitsDialog()
            R.id.quietNotifToggle -> onQuietNotifToggle()
            R.id.quietNotifAllowed -> QuietListSheet.editAllowed(fragment, prefs, fragment::isBindingAlive)
            R.id.quietNotifSilent -> {
                viewModel.getAppList(true)
                fragment.navigateFromSettings(
                    R.id.action_settingsFragment_to_appListFragment,
                    bundleOf(Constants.Key.FLAG to Constants.FLAG_PICK_SILENT_APP)
                )
            }
        }
    }

    private fun onQuietNotifToggle() {
        val onChanged = { if (fragment.isBindingAlive()) populateQuietNotif() }
        when (QuietNotificationsManager.state(context)) {
            State.OFF -> QuietListSheet.startTurnOn(fragment, prefs, fragment::isBindingAlive, onChanged)
            State.NEEDS_ACCESS -> QuietListSheet.showNeedsAccess(context, starting = false, onChanged = onChanged)
            State.ON -> QuietListSheet.confirmTurnOff(context, onChanged)
        }
    }

    private fun startPairing() {
        if (!GrayscalePairing.isSupported()) return
        if (GrayscalePairing.canNotify(context)) GrayscalePairing.start(context)
        else requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** SettingsFragment's permission callback. */
    fun onNotificationPermissionResult(granted: Boolean) {
        if (granted && GrayscalePairing.isSupported()) GrayscalePairing.start(context)
        else context.showToast(R.string.grayscale_pair_needs_notif)
    }

    private fun populateGrayscale() {
        val row = WellbeingRowState.grayscale(
            granted = GrayscaleController.isGranted(context),
            manual = GrayscaleController.isManual(context),
            onFocus = GrayscaleController.isOnFocus(context),
            onLimit = GrayscaleController.isOnLimit(context),
        )
        binding.grayscaleToggle?.text = context.getString(
            when (row) {
                Row.SET_UP -> R.string.grayscale_set_up
                Row.ON -> R.string.on
                Row.AUTOMATIC -> R.string.grayscale_automatic
                else -> R.string.off
            }
        )
    }

    private fun populateQuietNotif() {
        binding.quietNotifLayout ?: return
        val state = QuietNotificationsManager.state(context)
        binding.quietNotifToggle?.text = context.getString(
            when (state) {
                State.OFF -> R.string.off
                State.NEEDS_ACCESS -> R.string.quiet_needs_access
                State.ON -> R.string.on
            }
        )
        binding.quietNotifRows?.visibility = if (state == State.ON) View.VISIBLE else View.GONE
        binding.quietNotifHint?.apply {
            when (state) {
                State.OFF -> visibility = View.GONE
                State.NEEDS_ACCESS -> {
                    var hint = context.getString(R.string.quiet_card_needs_access)
                    if (Build.VERSION.SDK_INT >= 33)
                        hint += " " + context.getString(R.string.lock_consent_restricted)
                    text = hint
                    visibility = View.VISIBLE
                }
                State.ON -> {
                    text = context.getString(R.string.quiet_card_hint)
                    visibility = View.VISIBLE
                }
            }
        }
    }

    /** The permission row and the limits row that depends on it, re-derived on every resume (the grant lands in system settings). */
    private fun populateUsageAccess() {
        val granted = context.appUsagePermissionGranted()
        binding.screenTimeOnOff.text = context.getString(if (granted) R.string.on else R.string.off)
        val needsAccess = WellbeingRowState.appLimits(granted) == Row.NEEDS_ACCESS
        binding.screenTimeLimitsToggle?.apply {
            text = context.getString(if (needsAccess) R.string.quiet_needs_access else R.string.configure)
            alpha = if (needsAccess) 0.5f else 1f
        }
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
            dialog.dismissOnDestroy(fragment.viewLifecycleOwner).show()
        }
    }

    private fun showScreenTimeLimitsDialog() {
        if (!BadHabitDialogs.ensureUsageAccess(context)) return
        // The async usage scan may not have landed on a fresh settings open;
        // fall back to the last persisted map rather than opening an empty sheet
        val usageMap = viewModel.perAppScreenTime.value ?: prefs.getCachedUsageStats()
        ScreenTimeLimitDialog(context, usageMap).dismissOnDestroy(fragment.viewLifecycleOwner).show()
    }
}
