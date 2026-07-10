package com.parem.launcher.ui.home

import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.parem.launcher.MainViewModel
import com.parem.launcher.R
import com.parem.launcher.data.AppModel
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.databinding.FragmentHomeBinding
import com.parem.launcher.helper.AppIconCache
import com.parem.launcher.helper.AppLimitManager
import com.parem.launcher.helper.FolderManager
import com.parem.launcher.helper.IconPackManager
import com.parem.launcher.helper.UsageStatsHelper
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getAppsList
import com.parem.launcher.helper.getUserHandleFromString
import com.parem.launcher.helper.isPackageInstalledCached
import com.parem.launcher.helper.showToast
import com.parem.launcher.ui.BadHabitDialogs
import com.parem.launcher.ui.BottomSheetMenu
import com.parem.launcher.ui.CreateFolderDialog
import com.parem.launcher.ui.HomeFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the 8 home app slots: populating them (including the dynamic fitting
 * pass that publishes [MainViewModel.homeAppsCapacity]), launch and
 * limit-check flows, the per-slot long-press menu, folders, and home
 * alignment.
 *
 * Extracted from HomeFragment; mirrors HomeWidgetController's shape. Created
 * in HomeFragment.onViewCreated and released via [cleanup] in onDestroyView;
 * [isActive] guards every posted/async callback against running after release.
 */
class HomeSlotsController(
    private val fragment: HomeFragment,
    private val binding: FragmentHomeBinding,
    private val prefs: Prefs,
    private val viewModel: MainViewModel,
) {

    private val context get() = binding.root.context
    private val folderManager = FolderManager(context)

    // The 8 slot views, gathered once per view lifecycle so slot logic loops
    // instead of repeating per-view lines eight times
    var homeAppViews: List<TextView> = listOf(
        binding.homeApp1, binding.homeApp2, binding.homeApp3, binding.homeApp4,
        binding.homeApp5, binding.homeApp6, binding.homeApp7, binding.homeApp8
    )
        private set

    private var released = false

    private fun isActive(): Boolean = fragment.isAdded && !released

    fun cleanup() {
        released = true
        homeAppViews = emptyList()
    }

    fun setHomeAlignment(horizontalGravity: Int = prefs.homeAlignment) {
        val verticalGravity = if (prefs.homeBottomAlignment) Gravity.BOTTOM else Gravity.CENTER_VERTICAL
        binding.homeAppsLayout.gravity = horizontalGravity or verticalGravity
        binding.dateTimeLayout.gravity = horizontalGravity
        homeAppViews.forEach { it.gravity = horizontalGravity }
    }

    fun populateHomeScreen(appCountUpdated: Boolean) {
        if (appCountUpdated) hideHomeApps()
        fragment.clockController?.populateDateTime()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            fragment.clockController?.populateScreenTime()

        val homeAppsNum = prefs.homeAppsNum.coerceAtMost(homeAppViews.size)

        for (i in 0 until homeAppsNum) {
            val slot = i + 1
            val view = homeAppViews[i]
            view.visibility = View.VISIBLE

            if (folderManager.isFolderSlot(slot)) {
                val folder = folderManager.getFolderGroup(slot)
                view.text = folder?.name ?: ""
                view.contentDescription = folder?.name ?: context.getString(R.string.long_press_to_select_app)
            } else {
                val appName = prefs.getHomeAppName(slot)
                if (!setHomeAppText(view, appName, prefs.getHomeAppPackage(slot), prefs.getHomeAppUser(slot))) {
                    prefs.setHomeAppName(slot, "")
                    prefs.setHomeAppPackage(slot, "")
                }
                view.contentDescription = appName.ifEmpty { context.getString(R.string.long_press_to_select_app) }
            }
        }

        // Dynamic fitting: adjust padding to clear date/time, then hide overflow apps
        if (homeAppsNum > 0) {
            binding.homeAppsLayout.post {
                if (!isActive()) return@post
                val layout = binding.homeAppsLayout

                // Adjust top padding to clear the dateTimeLayout
                val dtLayout = binding.dateTimeLayout
                val minTopPadding = if (dtLayout.isVisible) {
                    (dtLayout.top + dtLayout.height + (8 * context.resources.displayMetrics.density).toInt())
                } else {
                    (56 * context.resources.displayMetrics.density).toInt()
                }
                if (layout.paddingTop != minTopPadding) {
                    layout.setPadding(layout.paddingLeft, minTopPadding, layout.paddingRight, layout.paddingBottom)
                }

                // Landscape: the widget column clears the clock dynamically too,
                // so widgets start straight below it instead of at a fixed guess.
                // In portrait the scroll views live inside homeAppsLayout and the
                // guard skips this.
                (binding.widgetScrollViewAbove?.parent as? ViewGroup)
                    ?.takeIf { it !== layout }
                    ?.let { column ->
                        if (column.paddingTop != minTopPadding)
                            column.setPadding(column.paddingLeft, minTopPadding, column.paddingRight, column.paddingBottom)
                    }

                // Run fitting after the padding change has been laid out
                layout.post fitApps@{
                    if (!isActive()) return@fitApps

                    val availableHeight = layout.height - layout.paddingTop - layout.paddingBottom
                    if (availableHeight <= 0) return@fitApps

                    // Subtract widget container height — but only when the widgets
                    // actually share this vertical column (portrait). The landscape
                    // layout puts them in their own column beside the apps, where
                    // their height must not shrink the app rows.
                    val activeWidgetScroll =
                        if (prefs.widgetPlacement == Constants.WidgetPlacement.ABOVE)
                            binding.widgetScrollViewAbove
                        else
                            binding.widgetScrollViewBelow
                    val widgetHeight = activeWidgetScroll
                        ?.takeIf { it.isVisible && it.parent === layout }
                        ?.height ?: 0

                    val usableHeight = availableHeight - widgetHeight
                    if (usableHeight <= 0) return@fitApps

                    // Measure height of a single app view
                    val firstVisible = homeAppViews.firstOrNull { it.isVisible }
                    val singleAppHeight = firstVisible?.height ?: return@fitApps
                    if (singleAppHeight <= 0) return@fitApps

                    val maxFitting = usableHeight / singleAppHeight
                    viewModel.homeAppsCapacity = maxFitting.coerceIn(1, 8)
                    if (maxFitting < homeAppsNum) {
                        // Hide excess apps from the bottom
                        for (j in homeAppViews.indices.reversed()) {
                            if (j >= maxFitting && homeAppViews[j].isVisible) {
                                homeAppViews[j].visibility = View.GONE
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setHomeAppText(textView: TextView, appName: String, packageName: String, userString: String): Boolean {
        // Cached check: up to 8 per-slot getActivityList IPCs per resume before PAREM-117
        if (isPackageInstalledCached(context, packageName, userString)) {
            textView.text = appName
            if (prefs.showIcons && packageName.isNotEmpty()) {
                val iconSize = 20.dpToPx()
                val icon = if (prefs.iconPackPackage.isNotEmpty()) {
                    IconPackManager.getIconForApp(context, prefs.iconPackPackage, packageName, null)
                } else null
                val drawable = icon ?: AppIconCache.get(context, packageName)
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
        homeAppViews.forEach { it.visibility = View.GONE }
    }

    fun homeAppClicked(location: Int) {
        if (folderManager.isFolderSlot(location)) {
            toggleFolderExpansion(location)
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

    fun launchApp(appName: String, packageName: String, activityClassName: String?, userString: String) {
        viewModel.selectedApp(
            AppModel(
                appName,
                null,
                packageName,
                activityClassName,
                false,
                getUserHandleFromString(context, userString)
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun checkBadHabitAndLaunch(name: String, pkg: String, activity: String?, user: String) {
        val ctx = fragment.context ?: return
        if (!AppLimitManager.hasLimit(ctx, pkg)) {
            launchApp(name, pkg, activity, user)
            return
        }
        val limitMinutes = AppLimitManager.getLimit(ctx, pkg) ?: run {
            launchApp(name, pkg, activity, user)
            return
        }
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val usageMs = withContext(Dispatchers.IO) {
                UsageStatsHelper.getUsageForApp(ctx, pkg)
            }
            if (!fragment.isAdded) return@launch
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
        val ctx = fragment.context ?: return
        BadHabitDialogs.showLimitWarning(ctx, name.ifEmpty { pkg }, usageMinutes, limitMinutes) {
            launchApp(name, pkg, activity, user)
        }
    }

    private fun toggleFolderExpansion(slot: Int) {
        val folder = folderManager.getFolderGroup(slot) ?: return
        if (folder.apps.isEmpty()) return

        val menu = BottomSheetMenu(context).title(folder.name)
        for (app in folder.apps) {
            menu.option(app.appName) {
                launchApp(app.appName, app.packageName, app.activityClassName, app.userString)
            }
        }
        menu.show()
    }

    fun showHomeSlotMenu(slot: Int) {
        val hasApp = prefs.getHomeAppName(slot).isNotEmpty()
        val isFolder = folderManager.isFolderSlot(slot)

        val menu = BottomSheetMenu(context)

        // Set / change app
        menu.option(if (hasApp) context.getString(R.string.change_app) else context.getString(R.string.select_app)) {
            fragment.showAppList(slot, hasApp, true)
        }

        // Create folder
        menu.option(context.getString(R.string.create_folder)) {
            showCreateFolderDialog(slot)
        }

        // App limit toggle (only for regular apps, not folders)
        if (hasApp && !isFolder) {
            val pkg = prefs.getHomeAppPackage(slot)
            if (pkg.isNotEmpty()) {
                val hasLimit = AppLimitManager.hasLimit(context, pkg)
                menu.option(if (hasLimit) context.getString(R.string.remove_time_limit) else context.getString(R.string.set_time_limit)) {
                    if (hasLimit) {
                        AppLimitManager.removeLimit(context, pkg)
                    } else {
                        val ctx = fragment.context ?: return@option
                        BadHabitDialogs.showTimeLimitPicker(ctx, pkg) { populateHomeScreen(false) }
                    }
                }
            }
        }

        // Remove (if occupied)
        if (hasApp || isFolder) {
            menu.option(context.getString(R.string.delete)) {
                if (isFolder) folderManager.removeFolder(slot)
                prefs.setHomeAppName(slot, "")
                prefs.setHomeAppPackage(slot, "")
                prefs.setHomeAppActivityClassName(slot, "")
                prefs.setHomeAppUser(slot, "")
                populateHomeScreen(false)
            }
        }

        menu.show()
    }

    private fun showCreateFolderDialog(slot: Int) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            // Same source as the app drawer: all user profiles, non-hidden apps, no cap.
            val apps = getAppsList(context, prefs, includeRegularApps = true, includeHiddenApps = false)

            if (!isActive()) return@launch

            CreateFolderDialog(context, apps) { folderName, selectedApps ->
                // Clear any existing app at this slot
                prefs.setHomeAppName(slot, "")
                prefs.setHomeAppPackage(slot, "")
                folderManager.createFolder(slot, folderName, selectedApps)
                populateHomeScreen(false)
            }.show()
        }
    }

    private fun showLongPressToast() = context.showToast(context.getString(R.string.long_press_to_select_app))
}
