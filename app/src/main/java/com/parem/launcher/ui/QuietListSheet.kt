package com.parem.launcher.ui

import android.content.Context
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.parem.launcher.R
import com.parem.launcher.data.Prefs
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
 * the turn-off question, and the quiet list itself.
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
            State.NEEDS_ACCESS -> showNeedsAccess(context, starting = false, onChanged = {})
            State.ON -> {
                val listener = QuietNotificationListener.instance
                // Never show an empty list unless the listener can actually see the snoozed set
                if (listener == null) showNeedsAccess(context, starting = true, onChanged = {})
                else showList(context, listener)
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
            showAllowedPicker(context, entries, preselected) { selected ->
                QuietNotificationsManager.setAllowed(context, selected)
                showDisclosure(context, onChanged)
            }
        }
    }

    /** Edits the allowlist only; used from settings once the filter is on. */
    fun editAllowed(fragment: Fragment, prefs: Prefs, isAlive: () -> Boolean) {
        withAppEntries(fragment, prefs, isAlive) { context, entries ->
            showAllowedPicker(context, entries, QuietNotificationsManager.getAllowed(context)) { selected ->
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
            if (!fragment.isAdded || !isAlive()) return@launch
            val entries = apps
                .filter { it.appPackage.isNotEmpty() }
                .map { it.appPackage to it.appLabel }
                .distinctBy { it.first }
            block(context, entries)
        }
    }

    private fun showAllowedPicker(
        context: Context,
        entries: List<Pair<String, String>>,
        preselected: Set<String>,
        onDone: (Set<String>) -> Unit,
    ) {
        val textColor = context.getColorFromAttr(R.attr.primaryColor)
        val adapter = AppPickerAdapter(
            textColor = textColor,
            maxSelected = Int.MAX_VALUE,
            entries = entries.map { (pkg, label) -> AppPickerAdapter.Entry(pkg, label) },
            initiallySelected = entries.map { it.first }.filter { it in preselected },
        )
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 0, 24.dpToPx(), 0)
            addView(EditText(context).apply {
                hint = context.getString(R.string.search_apps)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(textColor)
                setHintTextColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
                background = null
                inputType = InputType.TYPE_CLASS_TEXT
                isSingleLine = true
                setPadding(8.dpToPx(), 4.dpToPx(), 0, 8.dpToPx())
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        adapter.filter(s?.toString() ?: "")
                    }
                })
            })
            // Fixed height so the RecyclerView recycles inside the sheet's scroller,
            // same viewport as the focus-mode picker
            addView(RecyclerView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 300.dpToPx())
                layoutManager = LinearLayoutManager(context)
                this.adapter = adapter
            })
        }
        BottomSheetMenu(context)
            .title(context.getString(R.string.quiet_pick_title))
            .customView(content)
            .option(context.getString(R.string.quiet_continue)) { onDone(adapter.selectedIds.toSet()) }
            .option(context.getString(android.R.string.cancel), dimmed = true) {}
            .show()
            .window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    /** Shown every time the filter is turned on, before the system access screen. */
    private fun showDisclosure(context: Context, onChanged: () -> Unit) {
        var body = context.getString(R.string.quiet_disclosure_body)
        // Sideloaded installs get a greyed-out switch on 13+; the line is shown to every install source
        if (Build.VERSION.SDK_INT >= 33)
            body += "\n\n" + context.getString(R.string.lock_consent_restricted)
        BottomSheetMenu(context)
            .title(context.getString(R.string.quiet_disclosure_title))
            .message(body)
            .option(context.getString(R.string.quiet_continue)) {
                QuietNotificationsManager.setEnabled(context, true)
                if (!QuietNotificationsManager.hasAccess(context))
                    QuietNotificationsManager.openAccessSettings(context)
                onChanged()
            }
            .option(context.getString(R.string.quiet_not_now), dimmed = true) {}
            .show()
    }

    /** Access revoked, or (with [starting]) granted but the system has not bound us yet. */
    fun showNeedsAccess(context: Context, starting: Boolean, onChanged: () -> Unit) {
        val body = context.getString(if (starting) R.string.quiet_starting else R.string.quiet_needs_access_body)
        BottomSheetMenu(context)
            .title(context.getString(R.string.quiet_notif))
            .message(body)
            .option(context.getString(R.string.quiet_open_settings)) {
                QuietNotificationsManager.openAccessSettings(context)
            }
            .option(context.getString(R.string.quiet_turn_off), dimmed = true) {
                turnOff(context, dismissWaiting = false)
                onChanged()
            }
            .show()
    }

    /** Turning off from settings: asks what to do with notifications still hidden. */
    fun confirmTurnOff(context: Context, onChanged: () -> Unit) {
        val waiting = QuietNotificationListener.instance?.waiting().orEmpty()
        if (waiting.isEmpty()) {
            turnOff(context, dismissWaiting = false)
            onChanged()
            return
        }
        BottomSheetMenu(context)
            .title(context.getString(R.string.quiet_turn_off_title))
            .option(context.getString(R.string.quiet_dismiss)) {
                turnOff(context, dismissWaiting = true)
                onChanged()
            }
            .option(context.getString(R.string.quiet_keep)) {
                turnOff(context, dismissWaiting = false)
                onChanged()
            }
            .show()
    }

    private fun turnOff(context: Context, dismissWaiting: Boolean) {
        QuietNotificationsManager.turnOff(context, dismissWaiting)
        // Without access we cannot see what is snoozed, so any stored key means items may come back
        if (!dismissWaiting && QuietNotificationsManager.getOurKeys(context).isNotEmpty())
            context.showToast(R.string.quiet_keep_info)
    }

    private fun showList(context: Context, listener: QuietNotificationListener) {
        val waiting = listener.waiting()
        val menu = BottomSheetMenu(context).title(context.getString(R.string.quiet_list))
        if (waiting.isEmpty()) {
            menu.message(context.getString(R.string.quiet_list_empty))
        } else {
            val labels = HashMap<String, String>()
            for (sbn in waiting) {
                val app = labels.getOrPut(sbn.packageName) { appLabel(context, sbn.packageName) }
                val title = sbn.notification.extras
                    ?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()?.trim()
                val text = if (title.isNullOrEmpty()) app else "$app: $title"
                menu.option(text) { listener.open(context, sbn.key) }
            }
            menu.option(context.getString(R.string.quiet_list_dismiss_all), dimmed = true) {
                listener.dismissAll()
            }
        }
        menu.show()
    }

    private fun appLabel(context: Context, pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }
}
