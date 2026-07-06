package com.parem.launcher.ui.settings

import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.parem.launcher.MainViewModel
import com.parem.launcher.R
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.databinding.FragmentSettingsBinding
import com.parem.launcher.helper.IconPackManager
import com.parem.launcher.helper.appUsagePermissionGranted
import com.parem.launcher.helper.showToast
import com.parem.launcher.ui.BottomSheetMenu
import com.parem.launcher.ui.SettingsFragment

/**
 * Card 2 ("Home Screen"): number of home apps, date/time visibility, home
 * layout alignment, show-icons, sort-by-usage, widget placement.
 *
 * Extracted from SettingsFragment; mirrors HomeWidgetController's shape.
 */
class HomeScreenSettingsCard(
    private val fragment: SettingsFragment,
    private val binding: FragmentSettingsBinding,
    private val prefs: Prefs,
    private val viewModel: MainViewModel,
) : View.OnClickListener, View.OnLongClickListener {

    private val context get() = binding.root.context

    fun bind() {
        binding.homeAppsNum.text = prefs.homeAppsNum.toString()
        populateSortByUsage()
        populateWidgetPlacement()
        populateShowIcons()
        populateAlignment()
        populateDateTime()

        initClickListeners()
        initObservers()
    }

    private fun initClickListeners() {
        binding.homeAppsNum.setOnClickListener(this)
        binding.sortByUsage?.setOnClickListener(this)
        binding.widgetPlacement?.setOnClickListener(this)
        binding.showIconsToggle?.setOnClickListener(this)
        binding.alignment.setOnClickListener(this)
        binding.alignmentLeft.setOnClickListener(this)
        binding.alignmentCenter.setOnClickListener(this)
        binding.alignmentRight.setOnClickListener(this)
        binding.alignmentBottom.setOnClickListener(this)
        binding.dateTime.setOnClickListener(this)
        binding.dateTimeOn.setOnClickListener(this)
        binding.dateTimeOff.setOnClickListener(this)
        binding.dateOnly.setOnClickListener(this)

        binding.maxApps0.setOnClickListener(this)
        binding.maxApps1.setOnClickListener(this)
        binding.maxApps2.setOnClickListener(this)
        binding.maxApps3.setOnClickListener(this)
        binding.maxApps4.setOnClickListener(this)
        binding.maxApps5.setOnClickListener(this)
        binding.maxApps6.setOnClickListener(this)
        binding.maxApps7.setOnClickListener(this)
        binding.maxApps8.setOnClickListener(this)

        binding.alignment.setOnLongClickListener(this)
    }

    private fun initObservers() {
        viewModel.homeAppAlignment.observe(fragment.viewLifecycleOwner) {
            populateAlignment()
        }
    }

    override fun onClick(view: View) {
        fragment.resetOpenPickers(view.id)
        when (view.id) {
            R.id.homeAppsNum -> binding.appsNumSelectLayout.visibility = View.VISIBLE
            R.id.alignment -> binding.alignmentSelectLayout.visibility = View.VISIBLE
            R.id.alignmentLeft -> viewModel.updateHomeAlignment(Gravity.START)
            R.id.alignmentCenter -> viewModel.updateHomeAlignment(Gravity.CENTER)
            R.id.alignmentRight -> viewModel.updateHomeAlignment(Gravity.END)
            R.id.alignmentBottom -> updateHomeBottomAlignment()
            R.id.dateTime -> binding.dateTimeSelectLayout.visibility = View.VISIBLE
            R.id.dateTimeOn -> toggleDateTime(Constants.DateTime.ON)
            R.id.dateTimeOff -> toggleDateTime(Constants.DateTime.OFF)
            R.id.dateOnly -> toggleDateTime(Constants.DateTime.DATE_ONLY)

            R.id.maxApps0 -> updateHomeAppsNum(0)
            R.id.maxApps1 -> updateHomeAppsNum(1)
            R.id.maxApps2 -> updateHomeAppsNum(2)
            R.id.maxApps3 -> updateHomeAppsNum(3)
            R.id.maxApps4 -> updateHomeAppsNum(4)
            R.id.maxApps5 -> updateHomeAppsNum(5)
            R.id.maxApps6 -> updateHomeAppsNum(6)
            R.id.maxApps7 -> updateHomeAppsNum(7)
            R.id.maxApps8 -> updateHomeAppsNum(8)

            R.id.widgetPlacement -> toggleWidgetPlacement()
            R.id.showIconsToggle -> toggleShowIcons()
            R.id.sortByUsage -> toggleSortByUsage()
        }
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.alignment -> {
                prefs.appLabelAlignment = prefs.homeAlignment
                fragment.findNavController().navigate(R.id.action_settingsFragment_to_appListFragment)
                context.showToast(context.getString(R.string.alignment_changed))
            }
        }
        return true
    }

    private fun updateHomeAppsNum(num: Int) {
        binding.homeAppsNum.text = num.toString()
        binding.appsNumSelectLayout.visibility = View.GONE
        prefs.homeAppsNum = num
        viewModel.refreshHome(true)
    }

    private fun toggleDateTime(selected: Int) {
        prefs.dateTimeVisibility = selected
        populateDateTime()
        viewModel.toggleDateTime()
    }

    private fun populateDateTime() {
        binding.dateTime.text = context.getString(
            when (prefs.dateTimeVisibility) {
                Constants.DateTime.DATE_ONLY -> R.string.date
                Constants.DateTime.ON -> R.string.on
                else -> R.string.off
            }
        )
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

    private fun populateAlignment() {
        when (prefs.homeAlignment) {
            Gravity.START -> binding.alignment.text = context.getString(R.string.left)
            Gravity.CENTER -> binding.alignment.text = context.getString(R.string.center)
            Gravity.END -> binding.alignment.text = context.getString(R.string.right)
        }
        binding.alignmentBottom.text = if (prefs.homeBottomAlignment)
            context.getString(R.string.bottom_on)
        else context.getString(R.string.bottom_off)
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
        viewModel.refreshHome(false)
    }

    private fun showIconPackPicker(packs: List<Pair<String, String>>) {
        val menu = BottomSheetMenu(context)
            .title(context.getString(R.string.select_icon_pack))
        menu.option(context.getString(R.string.system_icons)) { applyIconPack("") }
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
        binding.showIconsToggle?.text = if (prefs.showIcons) context.getString(R.string.on) else context.getString(R.string.off)
    }

    private fun populateSortByUsage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && context.appUsagePermissionGranted()) {
            binding.sortByUsageLayout?.visibility = View.VISIBLE
            binding.sortByUsage?.text = if (prefs.appDrawerSortByUsage) context.getString(R.string.on) else context.getString(R.string.off)
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
            context.getString(R.string.above_apps)
        else
            context.getString(R.string.below_apps)
    }
}
