package com.parem.launcher.ui

import android.content.Context
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.parem.launcher.R
import com.parem.launcher.data.Prefs
import com.parem.launcher.helper.appLabelOrNull
import com.parem.launcher.helper.FocusModeManager
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getAppsList
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.helper.notifications.QuietNotificationListener
import com.parem.launcher.helper.notifications.QuietNotificationsManager
import com.parem.launcher.helper.notifications.QuietNotificationsManager.State
import com.parem.launcher.helper.showToast
import kotlinx.coroutines.launch

/**
 * Sheets for "Hide from shade, keep for later": the turn-on flow (allowed apps,
 * then the disclosure, then the system access screen), the "needs access" sheet,
 * turning off, and the quiet list itself.
 *
 * Callers pass [isAlive] for their own binding guard; it is checked before any
 * sheet shows after async work.
 */
object QuietListSheet {

    /** The QUIET_LIST gesture: what opens depends on the derived state. */
    fun openFromGesture(fragment: Fragment, prefs: Prefs, isAlive: () -> Boolean) {
        val context = fragment.context ?: return
        when (QuietNotificationsManager.state(context)) {
            State.OFF -> startTurnOn(fragment, prefs, isAlive, onChanged = {})
            State.NEEDS_ACCESS -> showNeedsAccess(fragment, starting = false, onChanged = {})
            State.ON -> {
                val listener = QuietNotificationListener.instance
                // Never show an empty list unless the listener can actually see the snoozed set
                if (listener == null) showNeedsAccess(fragment, starting = true, onChanged = {})
                else showList(fragment, isAlive, listener)
            }
        }
    }

    /**
     * Pick allowed apps, then read the disclosure, then grant access. Cancelling
     * the picker changes nothing; "Not now" keeps the picked apps but leaves the
     * listener off.
     */
    fun startTurnOn(fragment: Fragment, prefs: Prefs, isAlive: () -> Boolean, onChanged: () -> Unit) {
        withAppEntries(fragment, prefs, isAlive) { context, entries ->
            val preselected = QuietNotificationsManager.getAllowed(context) + listOfNotNull(
                QuietNotificationsManager.defaultDialer(context),
                QuietNotificationsManager.defaultSms(context),
            )
            showAllowedPicker(context, fragment.viewLifecycleOwner, entries, preselected) { selected ->
                if (!fragment.isAdded || fragment.view == null || !isAlive()) return@showAllowedPicker
                QuietNotificationsManager.setAllowed(context, selected)
                showDisclosure(context, fragment.viewLifecycleOwner, onChanged)
            }
        }
    }

    /** Edits the allowlist only; used from settings once the filter is on. */
    fun editAllowed(fragment: Fragment, prefs: Prefs, isAlive: () -> Boolean) {
        withAppEntries(fragment, prefs, isAlive) { context, entries ->
            showAllowedPicker(context, fragment.viewLifecycleOwner, entries, QuietNotificationsManager.getAllowed(context)) { selected ->
                if (!fragment.isAdded || fragment.view == null || !isAlive()) return@showAllowedPicker
                QuietNotificationsManager.setAllowed(context, selected)
            }
        }
    }

    /** (package, label) for every launchable app, one row per package across profiles. */
    private fun withAppEntries(
        fragment: Fragment,
        prefs: Prefs,
        isAlive: () -> Boolean,
        block: (Context, List<Pair<String, String>>) -> Unit,
    ) {
        val context = fragment.context ?: return
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            // Hidden apps still post notifications, so they are offered too
            val apps = getAppsList(context, prefs, includeRegularApps = true, includeHiddenApps = true)
            if (!fragment.isAdded || fragment.view == null || !isAlive()) return@launch
            val entries = apps
                .filter { it.appPackage.isNotEmpty() }
                .map { it.appPackage to it.appLabel }
                .distinctBy { it.first }
            block(context, entries)
        }
    }

    private fun showAllowedPicker(
        context: Context,
        owner: LifecycleOwner,
        entries: List<Pair<String, String>>,
        preselected: Set<String>,
        onDone: (Set<String>) -> Unit,
    ) {
        val textColor = context.getColorFromAttr(R.attr.textColor)
        val adapter = AppPickerAdapter(
            textColor = textColor,
            maxSelected = Int.MAX_VALUE,
            entries = entries.map { (pkg, label) -> AppPickerAdapter.Entry(pkg, label) },
            initiallySelected = entries.map { it.first }.filter { it in preselected },
        )
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 0, 24.dpToPx(), 0)
            addAppPicker(adapter)
        }
        BottomSheetMenu(context)
            .title(context.getString(R.string.quiet_pick_title))
            .customView(content)
            .option(context.getString(R.string.quiet_continue)) { onDone(adapter.selectedIds.toSet()) }
            .option(context.getString(android.R.string.cancel), dimmed = true) {}
            .show(resizeForKeyboard = true, scrollContent = false, fitPicker = true).dismissOnDestroy(owner)
    }

    /** Shown every time the filter is turned on, before the system access screen. */
    private fun showDisclosure(context: Context, owner: LifecycleOwner, onChanged: () -> Unit) {
        val body = context.withRestrictedNote(context.getString(R.string.quiet_disclosure_body))
        val hasAccess = QuietNotificationsManager.hasAccess(context)
        BottomSheetMenu(context)
            .title(context.getString(R.string.quiet_disclosure_title))
            .message(body)
            // With access already granted nothing opens, so the label must not promise it
            .option(context.getString(if (hasAccess) R.string.agree else R.string.lock_consent_accept)) {
                QuietNotificationsManager.setEnabled(context, true)
                if (!hasAccess) QuietNotificationsManager.openAccessSettings(context)
                onChanged()
            }
            .option(context.getString(R.string.quiet_not_now), dimmed = true) {}
            .show().dismissOnDestroy(owner)
    }

    /** Access revoked, or (with [starting]) granted but the system has not bound us yet. */
    fun showNeedsAccess(fragment: Fragment, starting: Boolean, onChanged: () -> Unit) {
        val context = fragment.context ?: return
        val body = context.getString(if (starting) R.string.quiet_starting else R.string.quiet_needs_access_body)
        BottomSheetMenu(context)
            .title(context.getString(R.string.quiet_notif))
            .message(body)
            .option(context.getString(R.string.quiet_open_settings)) {
                QuietNotificationsManager.openAccessSettings(context)
            }
            .option(context.getString(R.string.quiet_turn_off), dimmed = true) {
                turnOff(context, onChanged)
            }
            .show().dismissOnDestroy(fragment.viewLifecycleOwner)
    }

    /** Turning off from settings or the sheet above: hidden notifications come back to the shade now. */
    fun turnOff(context: Context, onChanged: () -> Unit) {
        QuietNotificationsManager.turnOff(context)
        // Released keys are forgotten. One left was not released (no connected listener,
        // or since a restart its app has not posted it again), so it comes back later
        if (QuietNotificationsManager.tracked(context).snoozedAt.isNotEmpty())
            context.showToast(R.string.quiet_keep_info)
        onChanged()
    }

    /** Opening an entry is a Parem launch route: same focus check and limit gate as a home slot. */
    private fun showList(fragment: Fragment, isAlive: () -> Boolean, listener: QuietNotificationListener) {
        val context = fragment.context ?: return
        val waiting = listener.waiting()
        val menu = BottomSheetMenu(context).title(context.getString(R.string.quiet_list))
        if (waiting.isEmpty()) {
            menu.message(context.getString(R.string.quiet_list_empty))
        } else {
            val labels = HashMap<String, String>()
            for (sbn in waiting) {
                val app = labels.getOrPut(sbn.packageName) { context.appLabelOrNull(sbn.packageName) ?: sbn.packageName }
                val title = sbn.notification.extras
                    ?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()?.trim()
                val text = if (title.isNullOrEmpty()) app else "$app: $title"
                menu.option(text) {
                    if (!fragment.isAdded || fragment.view == null || !isAlive()) return@option
                    if (!FocusModeManager.isAppAllowed(context, sbn.packageName)) {
                        FocusModeManager.showBlocked(context)
                        return@option
                    }
                    BadHabitDialogs.gateLaunch(
                        context, fragment.viewLifecycleOwner.lifecycleScope, isAlive,
                        app, sbn.packageName,
                        open = { listener.open(context, sbn.key) },
                    )
                }
            }
            menu.option(context.getString(R.string.quiet_list_dismiss_all), dimmed = true) {
                listener.dismissAll()
            }
        }
        menu.show().dismissOnDestroy(fragment.viewLifecycleOwner)
    }
}
