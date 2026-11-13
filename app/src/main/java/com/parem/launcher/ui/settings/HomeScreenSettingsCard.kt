package com.parem.launcher.ui.settings

import android.Manifest
import android.os.Process
import com.parem.launcher.BuildConfig
import com.parem.launcher.helper.openAppInfo
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import com.parem.launcher.MainViewModel
import com.parem.launcher.R
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.databinding.FragmentSettingsBinding
import com.parem.launcher.helper.ContactSearchManager
import com.parem.launcher.helper.IconPackManager
import com.parem.launcher.helper.appUsagePermissionGranted
import com.parem.launcher.helper.onOff
import com.parem.launcher.helper.showToast
import com.parem.launcher.ui.BottomSheetMenu
import com.parem.launcher.ui.SettingsFragment
import com.parem.launcher.ui.dismissOnDestroy

/**
 * Card 2 ("Home Screen"): number of home apps, date/time visibility, home
 * layout alignment, home layout lock, show-icons, sort-by-usage, widget placement.
 *
 * Extracted from SettingsFragment; mirrors HomeWidgetController's shape.
 */
class HomeScreenSettingsCard(
    private val fragment: SettingsFragment,
    private val binding: FragmentSettingsBinding,
    private val prefs: Prefs,
    private val viewModel: MainViewModel,
    private val requestContactsPermissionLauncher: ActivityResultLauncher<String>,
) : View.OnClickListener, View.OnLongClickListener {

    private val context get() = binding.root.context

    fun bind() {
        binding.homeAppsNum.text = prefs.homeAppsNum.toString()
        binding.homeAppsNumRow.bindValueRow(binding.homeAppsNum.text)
        populateSortByUsage()
        populateWidgetPlacement()
        populateShowIcons()
        populateHomeLayoutLock()
        populateContactSearch()
        populateSearchHistory()
        populateAutoLaunch()
        populateAlignment()
        populateDateTime()

        initClickListeners()
        initObservers()
    }

    private fun initClickListeners() {
        binding.homeAppsNumRow.setOnClickListener(this)
        binding.sortByUsageLayout?.setOnClickListener(this)
        binding.widgetPlacementRow?.setOnClickListener(this)
        binding.showIconsRow?.setOnClickListener(this)
        binding.homeLayoutLockRow.setOnClickListener(this)
        binding.contactSearchLayout?.setOnClickListener(this)
        binding.searchHistoryRow?.setOnClickListener(this)
        binding.searchHistoryClear?.setOnClickListener(this)
        binding.autoLaunchRow.setOnClickListener(this)
        binding.alignmentRow.setOnClickListener(this)
        binding.dateTimeRow.setOnClickListener(this)

        binding.alignmentRow.setOnLongClickListener(this)
    }

    private fun initObservers() {
        viewModel.homeAppAlignment.observe(fragment.viewLifecycleOwner) {
            populateAlignment()
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.homeAppsNumRow -> showAppsNumPicker()
            R.id.alignmentRow -> showAlignmentPicker()
            R.id.dateTimeRow -> showDateTimePicker()

            R.id.widgetPlacementRow -> showWidgetPlacementPicker()
            R.id.showIconsRow -> toggleShowIcons()
            R.id.homeLayoutLockRow -> toggleHomeLayoutLock()
            R.id.sortByUsageLayout -> toggleSortByUsage()
            R.id.contactSearchLayout -> toggleContactSearch()
            R.id.autoLaunchRow -> {
                prefs.autoLaunchSingleMatch = !prefs.autoLaunchSingleMatch
                populateAutoLaunch()
            }
            R.id.searchHistoryRow -> {
                prefs.omniboxHistoryEnabled = !prefs.omniboxHistoryEnabled
                populateSearchHistory()
            }
            R.id.searchHistoryClear -> {
                prefs.omniboxHistory = ""
                context.showToast(context.getString(R.string.search_history_cleared))
            }
        }
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.alignmentRow -> {
                prefs.appLabelAlignment = prefs.homeAlignment
                fragment.navigateFromSettings(R.id.action_settingsFragment_to_appListFragment)
                context.showToast(context.getString(R.string.alignment_changed))
            }
        }
        return true
    }

    private fun showAppsNumPicker() {
        val menu = BottomSheetMenu(context).title(context.getString(R.string.apps_on_home_screen))
        lateinit var dialog: com.google.android.material.bottomsheet.BottomSheetDialog
        for (count in 0..8) {
            menu.option(count.toString(), checked = count == prefs.homeAppsNum, singleChoice = true,
                dimmed = count > viewModel.homeAppsCapacity, dismissOnClick = false) {
                updateHomeAppsNum(count)
                if (count <= viewModel.homeAppsCapacity) dialog.dismiss()
            }
        }
        dialog = menu.show().dismissOnDestroy(fragment.viewLifecycleOwner)
    }

    private fun showDateTimePicker() {
        val menu = BottomSheetMenu(context).title(context.getString(R.string.settings_clock_date))
        listOf(R.string.on to Constants.DateTime.ON, R.string.off to Constants.DateTime.OFF,
            R.string.date_only to Constants.DateTime.DATE_ONLY).forEach { (label, value) ->
            menu.option(context.getString(label), checked = prefs.dateTimeVisibility == value, singleChoice = true) { toggleDateTime(value) }
        }
        menu.show().dismissOnDestroy(fragment.viewLifecycleOwner)
    }

    private fun updateHomeAppsNum(num: Int) {
        // Widgets/clock currently leave room for fewer slots; rejecting here
        // replaces the old behavior of silently hiding the overflow apps
        if (num > viewModel.homeAppsCapacity) {
            context.showToast(context.getString(R.string.apps_dont_fit, viewModel.homeAppsCapacity))
            return
        }
        binding.homeAppsNum.text = num.toString()
        binding.homeAppsNumRow.bindValueRow(binding.homeAppsNum.text)
        prefs.homeAppsNum = num
        viewModel.refreshHome.postValue(true)
    }

    private fun toggleDateTime(selected: Int) {
        prefs.dateTimeVisibility = selected
        populateDateTime()
        viewModel.toggleDateTime.postValue(Unit)
    }

    private fun populateDateTime() {
        binding.dateTime.text = context.getString(
            when (prefs.dateTimeVisibility) {
                Constants.DateTime.DATE_ONLY -> R.string.date
                Constants.DateTime.ON -> R.string.on
                else -> R.string.off
            }
        )
        binding.dateTimeRow.bindValueRow(binding.dateTime.text)
    }

    private fun updateHomeBottomAlignment() {
        if (viewModel.isParemDefault.value != true) {
            context.showToast(context.getString(R.string.please_set_parem_as_default_first), Toast.LENGTH_LONG)
            return
        }
        prefs.homeBottomAlignment = !prefs.homeBottomAlignment
        populateAlignment()
        viewModel.updateHomeAlignment(prefs.homeAlignment)
    }

    // START is the right edge under RTL; the labels name the side the user sees
    private val isRtl get() = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    private val leftGravity get() = if (isRtl) Gravity.END else Gravity.START
    private val rightGravity get() = if (isRtl) Gravity.START else Gravity.END

    private fun populateAlignment() {
        when (prefs.homeAlignment) {
            leftGravity -> binding.alignment.text = context.getString(R.string.left)
            Gravity.CENTER -> binding.alignment.text = context.getString(R.string.center)
            rightGravity -> binding.alignment.text = context.getString(R.string.right)
        }
        if (prefs.homeBottomAlignment)
            binding.alignment.text = context.getString(R.string.alignment_at_bottom, binding.alignment.text)
        binding.alignmentRow.bindValueRow(binding.alignment.text)
    }

    private fun toggleShowIcons() {
        if (!prefs.showIcons) {
            // Turning on - check for icon packs
            val packs = IconPackManager.getAvailableIconPacks(context)
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
        viewModel.refreshHome.postValue(false)
    }

    private fun showIconPackPicker(packs: List<Pair<String, String>>) {
        val menu = BottomSheetMenu(context)
            .title(context.getString(R.string.select_icon_pack))
        menu.option(context.getString(R.string.system_icons)) { applyIconPack("") }
        for ((pkg, label) in packs) {
            menu.option(label) { applyIconPack(pkg) }
        }
        menu.show().dismissOnDestroy(fragment.viewLifecycleOwner)
    }

    private fun applyIconPack(pkg: String) {
        prefs.showIcons = true
        prefs.iconPackPackage = pkg
        populateShowIcons()
        viewModel.refreshHome.postValue(false)
    }

    private fun populateShowIcons() {
        binding.showIconsToggle?.text = if (prefs.showIcons) context.getString(R.string.on) else context.getString(R.string.off)
        binding.showIconsRow?.bindSwitchRow(prefs.showIcons)
    }

    // Long-press reads the pref live; the refresh only updates empty slots' TalkBack hint
    private fun toggleHomeLayoutLock() {
        prefs.homeLayoutLocked = !prefs.homeLayoutLocked
        populateHomeLayoutLock()
        viewModel.refreshHome.postValue(false)
    }

    private fun populateHomeLayoutLock() {
        binding.homeLayoutLockToggle.text =
            if (prefs.homeLayoutLocked) context.getString(R.string.on) else context.getString(R.string.off)
        binding.homeLayoutLockRow.bindSwitchRow(prefs.homeLayoutLocked)
    }

    private fun populateSortByUsage() {
        if (context.appUsagePermissionGranted()) {
            binding.sortByUsageLayout?.visibility = View.VISIBLE
            binding.sortByUsage?.text = if (prefs.appDrawerSortByUsage) context.getString(R.string.on) else context.getString(R.string.off)
            binding.sortByUsageLayout?.bindSwitchRow(prefs.appDrawerSortByUsage)
        } else {
            binding.sortByUsageLayout?.visibility = View.GONE
        }
    }

    private fun toggleSortByUsage() {
        prefs.appDrawerSortByUsage = !prefs.appDrawerSortByUsage
        populateSortByUsage()
    }

    // Contact search is the only place READ_CONTACTS is ever requested: turning
    // the toggle on shows the disclosure, then the prompt; every other path (launch,
    // onboarding, the drawer itself) leaves it untouched.
    private fun toggleContactSearch() {
        if (ContactSearchManager.isEnabled(context)) {
            ContactSearchManager.setEnabled(context, false)
            populateContactSearch()
        } else if (ContactSearchManager.hasPermission(context)) {
            ContactSearchManager.setEnabled(context, true)
            populateContactSearch()
        } else {
            showContactSheet(R.string.contact_search_disclosure, R.string.agree) {
                requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    private fun showContactSheet(message: Int, action: Int, onAction: () -> Unit) {
        BottomSheetMenu(context)
            .title(context.getString(R.string.contact_search))
            .message(context.getString(message))
            .option(context.getString(action), onClick = onAction)
            .option(context.getString(R.string.not_now), dimmed = true) {}
            .show()
            .dismissOnDestroy(fragment.viewLifecycleOwner)
    }

    /** Called by the fragment when the READ_CONTACTS prompt returns. */
    fun onContactsPermissionResult(granted: Boolean) {
        // A first denial stays quiet; once Android stops asking, the toggle would look broken
        if (granted) ContactSearchManager.setEnabled(context, true)
        else if (!fragment.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
            showContactSheet(R.string.contact_search_denied, R.string.open_app_settings) {
                openAppInfo(context, Process.myUserHandle(), BuildConfig.APPLICATION_ID)
            }
        }
        populateContactSearch()
    }

    private fun populateContactSearch() {
        // If the permission was revoked in system settings while enabled, reset
        // the pref so the shown state matches reality (graceful degradation).
        if (ContactSearchManager.isEnabled(context) && !ContactSearchManager.hasPermission(context)) {
            ContactSearchManager.setEnabled(context, false)
        }
        binding.contactSearchToggle?.text =
            if (ContactSearchManager.isEnabled(context)) context.getString(R.string.on)
            else context.getString(R.string.off)
        binding.contactSearchLayout?.bindSwitchRow(ContactSearchManager.isEnabled(context))
    }

    private fun populateSearchHistory() {
        val on = prefs.omniboxHistoryEnabled
        binding.searchHistoryToggle?.text = context.onOff(on)
        binding.searchHistoryClear?.visibility = if (on) View.VISIBLE else View.GONE
        binding.searchHistoryRow?.bindSwitchRow(on)
    }

    private fun populateAutoLaunch() {
        val on = prefs.autoLaunchSingleMatch
        binding.autoLaunchToggle.text = context.onOff(on)
        binding.autoLaunchRow.bindSwitchRow(on)
    }

    private fun showAlignmentPicker() {
        val menu = BottomSheetMenu(context).title(context.getString(R.string.home_layout_alignment))
        listOf(R.string.left to leftGravity, R.string.center to Gravity.CENTER, R.string.right to rightGravity).forEach { (label, gravity) ->
            menu.option(context.getString(label), checked = prefs.homeAlignment == gravity, singleChoice = true) {
                viewModel.updateHomeAlignment(gravity)
            }
        }
        menu.option(context.getString(R.string.align_at_bottom), checked = prefs.homeBottomAlignment) {
            updateHomeBottomAlignment()
        }
        menu.show().dismissOnDestroy(fragment.viewLifecycleOwner)
    }

    private fun showWidgetPlacementPicker() {
        val menu = BottomSheetMenu(context).title(context.getString(R.string.widget_placement))
        listOf(R.string.above_apps to Constants.WidgetPlacement.ABOVE, R.string.below_apps to Constants.WidgetPlacement.BELOW).forEach { (label, placement) ->
            menu.option(context.getString(label), checked = prefs.widgetPlacement == placement, singleChoice = true) {
                prefs.widgetPlacement = placement
                populateWidgetPlacement()
                viewModel.refreshHome.postValue(false)
            }
        }
        menu.show().dismissOnDestroy(fragment.viewLifecycleOwner)
    }

    private fun populateWidgetPlacement() {
        binding.widgetPlacement?.text = if (prefs.widgetPlacement == Constants.WidgetPlacement.ABOVE)
            context.getString(R.string.above_apps)
        else
            context.getString(R.string.below_apps)
        binding.widgetPlacementRow?.bindValueRow(binding.widgetPlacement?.text)
    }
}
