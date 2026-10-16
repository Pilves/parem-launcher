package com.parem.launcher.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Process
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.text.BidiFormatter
import com.google.android.material.snackbar.Snackbar
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
import com.parem.launcher.helper.FolderManager
import com.parem.launcher.helper.Haptics
import com.parem.launcher.helper.IconPackManager
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getAppsList
import com.parem.launcher.helper.getUserHandleFromString
import com.parem.launcher.helper.isPackageInstalledCached
import com.parem.launcher.helper.showToast
import com.parem.launcher.helper.SlotSnapshot
import com.parem.launcher.helper.SwipeUpAppManager
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.helper.skipAnimations
import com.parem.launcher.ui.AppMenu
import com.parem.launcher.ui.dismissOnDestroy
import com.parem.launcher.helper.openSearch
import com.parem.launcher.ui.BadHabitDialogs
import com.parem.launcher.ui.BottomSheetMenu
import com.parem.launcher.ui.CreateFolderDialog
import com.parem.launcher.ui.HomeFragment
import com.parem.launcher.ui.WebsiteDialog
import kotlinx.coroutines.launch

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
    private var undoBar: Snackbar? = null
    private var noticeBar: Snackbar? = null
    private var pendingRemoval: Pair<Int, SlotSnapshot>? = null

    private fun isActive(): Boolean = fragment.isAdded && !released

    fun cleanup() {
        invalidateUndo()
        noticeBar?.dismiss()
        noticeBar = null
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

        fragment.clockController?.populateScreenTime()

        val homeAppsNum = prefs.homeAppsNum.coerceAtMost(homeAppViews.size)

        for (i in 0 until homeAppsNum) {
            val slot = i + 1
            val view = homeAppViews[i]
            view.visibility = View.VISIBLE

            if (folderManager.isFolderSlot(slot)) {
                val folder = folderManager.getFolderGroup(slot)
                view.text = folder?.name ?: ""
                view.contentDescription = folder?.name ?: emptySlotHint()
            } else if (prefs.getHomeAppUrl(slot).isNotEmpty()) {
                // A website has no package: skip the installed check, and clear
                // the icon setHomeAppText would otherwise have reset
                view.text = prefs.getHomeAppName(slot)
                view.setCompoundDrawablesRelative(null, null, null, null)
                view.contentDescription = view.text
            } else {
                val appName = prefs.getHomeAppName(slot)
                if (!setHomeAppText(view, appName, prefs.getHomeAppPackage(slot), prefs.getHomeAppUser(slot))) {
                    prefs.setHomeAppName(slot, "")
                    prefs.setHomeAppPackage(slot, "")
                }
                view.contentDescription = appName.ifEmpty { emptySlotHint() }
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

                    // The touch-exploration "All apps" row shares the column too
                    val allAppsHeight = binding.homeAllApps.takeIf { it.isVisible }?.height ?: 0

                    val usableHeight = availableHeight - widgetHeight - allAppsHeight
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
        if (prefs.getHomeAppName(location).isEmpty()) {
            if (prefs.homeLayoutLocked) showLockedLayout() else showLongPressToast()
        }
        else launchApp(
            prefs.getHomeAppName(location),
            prefs.getHomeAppPackage(location),
            prefs.getHomeAppActivityClassName(location),
            prefs.getHomeAppUser(location),
            prefs.getHomeAppUrl(location).ifEmpty { null }
        )
    }

    /**
     * Every home launch route (slots, folders, gesture letters, swipe apps,
     * double-tap, clock/calendar) comes through here so focus, app limits and
     * the mindful pause apply to all of them. A website slot is gated on the
     * app that handles its URL, not on its empty package.
     */
    fun launchApp(appName: String, packageName: String, activityClassName: String?, userString: String, url: String? = null) {
        val ctx = fragment.context ?: return
        val open = { openApp(appName, packageName, activityClassName, userString, url) }
        if (url != null) {
            BadHabitDialogs.gateWebOpen(
                ctx, fragment.viewLifecycleOwner.lifecycleScope, { fragment.isAdded },
                Intent(Intent.ACTION_VIEW, Uri.parse(url)), open,
            )
            return
        }
        BadHabitDialogs.gateLaunch(
            ctx, fragment.viewLifecycleOwner.lifecycleScope, { fragment.isAdded },
            appName, packageName, open,
        )
    }

    /** The search gestures (swipe-down, OPEN_SEARCH): gated on the app that handles web search. */
    fun launchSearch() {
        val ctx = fragment.context ?: return
        BadHabitDialogs.gateWebOpen(
            ctx, fragment.viewLifecycleOwner.lifecycleScope, { fragment.isAdded },
            Intent(Intent.ACTION_WEB_SEARCH), open = { ctx.openSearch() },
        )
    }

    private fun openApp(appName: String, packageName: String, activityClassName: String?, userString: String, url: String?) {
        viewModel.selectedApp(
            AppModel(
                appName,
                null,
                packageName,
                activityClassName,
                false,
                getUserHandleFromString(context, userString),
                url
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun toggleFolderExpansion(slot: Int) {
        val folder = folderManager.getFolderGroup(slot) ?: return
        if (folder.apps.isEmpty()) return

        val menu = BottomSheetMenu(context).title(folder.name)
        for (app in folder.apps) {
            menu.option(app.appName) {
                launchApp(app.appName, app.packageName, app.activityClassName, app.userString, app.url.ifEmpty { null })
            }
        }
        menu.show().dismissOnDestroy(fragment.viewLifecycleOwner)
    }

    fun showHomeSlotMenu(slot: Int) {
        if (!isActive()) return
        if (prefs.homeLayoutLocked) { showLockedLayout(); return }
        val snapshot = captureSlot(slot)
        val user = getUserHandleFromString(context, snapshot.user)
        // Never turn a missing/private stored profile into actions on its personal copy.
        if (snapshot.folder == null && snapshot.packageName.isNotEmpty() && snapshot.url.isEmpty() && user.toString() == snapshot.user) {
            val app = AppModel(snapshot.name, null, snapshot.packageName, snapshot.activity,
                user = user)
            AppMenu.show(fragment, app, prefs, viewModel, homeSlot = slot,
                homeActions = { addSlotActions(it.sectionLabel(context.getString(R.string.app_menu_home_slot)), slot, snapshot) },
                onChanged = {
                    if (isActive()) { invalidateUndo(); populateHomeScreen(false) }
                })
        } else {
            val title = snapshot.folder?.name ?: snapshot.name.ifEmpty { context.getString(R.string.app_menu_add_home) }
            val menu = BottomSheetMenu(context).title(BidiFormatter.getInstance().unicodeWrap(title), prominent = true)
            if (snapshot.url.isNotEmpty() || snapshot.packageName.isNotEmpty()) menu.option(context.getString(R.string.rename)) {
                if (isActive()) AppMenu.rename(fragment, snapshot.name, onHome = true) { name ->
                    invalidateUndo()
                    prefs.setHomeAppName(slot, name)
                    populateHomeScreen(false)
                }
            }
            addSlotActions(menu, slot, snapshot)
            menu.show().dismissOnDestroy(fragment.viewLifecycleOwner)
        }
    }

    private fun addSlotActions(menu: BottomSheetMenu, slot: Int, snapshot: SlotSnapshot) {
        val occupied = snapshot.folder != null || snapshot.name.isNotEmpty() || snapshot.url.isNotEmpty() || snapshot.packageName.isNotEmpty()
        menu.option(context.getString(if (occupied) R.string.change_app else R.string.select_app)) {
            if (isActive() && !prefs.homeLayoutLocked) fragment.showAppList(slot, occupied, true)
        }
        menu.option(context.getString(R.string.add_website)) {
            if (isActive() && !prefs.homeLayoutLocked) WebsiteDialog(context) { url, label ->
                if (!isActive() || prefs.homeLayoutLocked) return@WebsiteDialog
                invalidateUndo()
                viewModel.selectedApp(AppModel(label, null, "", null, user = Process.myUserHandle(), url = url), slot)
            }.show().dismissOnDestroy(fragment.viewLifecycleOwner)
        }
        menu.option(context.getString(R.string.create_folder), summary =
            if (snapshot.folder != null) context.getString(R.string.app_menu_replace_folder) else null) {
            if (isActive() && !prefs.homeLayoutLocked) showCreateFolderDialog(slot)
        }
        if (occupied) menu.gap().option(context.getString(
            if (snapshot.folder != null) R.string.app_menu_remove_folder else R.string.app_menu_remove_home)) {
            if (isActive() && !prefs.homeLayoutLocked) removeSlot(slot)
        }
    }

    private fun captureSlot(slot: Int) = SlotSnapshot(
        prefs.getHomeAppName(slot), prefs.getHomeAppPackage(slot), prefs.getHomeAppActivityClassName(slot),
        prefs.getHomeAppUser(slot), prefs.getHomeAppUrl(slot),
        SwipeUpAppManager.getSwipeUpAppName(context, slot), SwipeUpAppManager.getSwipeUpAppPackage(context, slot),
        SwipeUpAppManager.getSwipeUpAppActivity(context, slot), SwipeUpAppManager.getSwipeUpAppUser(context, slot),
        folderManager.getFolderGroup(slot)?.let { it.copy(apps = it.apps.toList()) },
    )

    private fun writeSlot(slot: Int, value: SlotSnapshot) {
        prefs.setHomeAppName(slot, value.name)
        prefs.setHomeAppPackage(slot, value.packageName)
        prefs.setHomeAppActivityClassName(slot, value.activity)
        prefs.setHomeAppUser(slot, value.user)
        prefs.setHomeAppUrl(slot, value.url)
        SwipeUpAppManager.setSwipeUpApp(context, slot, value.swipeName, value.swipePackage, value.swipeActivity, value.swipeUser)
        if (value.folder == null) folderManager.removeFolder(slot)
        else folderManager.createFolder(slot, value.folder.name, value.folder.apps)
    }

    private fun invalidateUndo() {
        pendingRemoval = null
        undoBar?.dismiss()
        undoBar = null
    }

    private fun feedback(message: String): Snackbar {
        // The installed Material Snackbar reads these two durations from its context.
        val themed = if (context.skipAnimations()) ContextThemeWrapper(context, R.style.ParemInstantFeedback) else context
        return Snackbar.make(themed, binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(context.getColorFromAttr(R.attr.primaryInverseColor))
            .setTextColor(context.getColorFromAttr(R.attr.primaryColor))
            .setActionTextColor(context.getColorFromAttr(R.attr.primaryColor))
            .setTextMaxLines(4)
    }

    private fun removeSlot(slot: Int) {
        invalidateUndo()
        val before = captureSlot(slot)
        val removal = slot to before
        writeSlot(slot, SlotSnapshot())
        populateHomeScreen(false)
        pendingRemoval = removal
        val message = if (before.folder != null) context.getString(R.string.app_menu_folder_removed)
            else context.getString(R.string.app_menu_removed, BidiFormatter.getInstance().unicodeWrap(before.name))
        val bar = feedback(message).setAction(R.string.app_menu_undo) {
            if (isActive() && pendingRemoval === removal && !prefs.homeLayoutLocked) {
                before.restoreAfterRemoval(captureSlot(slot))?.let { writeSlot(slot, it) }
                invalidateUndo()
                populateHomeScreen(false)
            }
        }
        bar.addCallback(object : Snackbar.Callback() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                if (undoBar === transientBottomBar) { undoBar = null; pendingRemoval = null }
            }
        })
        undoBar = bar
        bar.show()
    }

    private fun showLockedLayout() {
        homeAppViews.firstOrNull()?.let(Haptics::reject)
        noticeBar?.dismiss()
        noticeBar = feedback(context.getString(R.string.app_menu_layout_locked)).setAction(R.string.app_menu_unlock) {
            if (isActive()) { prefs.homeLayoutLocked = false; populateHomeScreen(false) }
        }.also { it.show() }
    }

    private fun showCreateFolderDialog(slot: Int) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            // Same source as the app drawer: all user profiles, non-hidden apps, no cap.
            val apps = getAppsList(context, prefs, includeRegularApps = true, includeHiddenApps = false)

            if (!isActive()) return@launch

            CreateFolderDialog(context, apps) { folderName, selectedApps ->
                if (!isActive() || prefs.homeLayoutLocked) return@CreateFolderDialog
                invalidateUndo()
                prefs.setHomeAppName(slot, "")
                prefs.setHomeAppPackage(slot, "")
                prefs.setHomeAppUrl(slot, "")
                folderManager.createFolder(slot, folderName, selectedApps)
                populateHomeScreen(false)
            }.show().dismissOnDestroy(fragment.viewLifecycleOwner)
        }
    }

    private fun emptySlotHint(): String = context.getString(
        if (prefs.homeLayoutLocked) R.string.home_layout_locked else R.string.long_press_to_select_app
    )

    private fun showLongPressToast() = context.showToast(emptySlotHint())
}
