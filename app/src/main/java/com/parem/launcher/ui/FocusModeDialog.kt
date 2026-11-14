package com.parem.launcher.ui

import android.app.TimePickerDialog
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.parem.launcher.R
import com.parem.launcher.helper.FocusModeManager
import com.parem.launcher.helper.FocusSchedule
import com.parem.launcher.helper.GrayscaleController
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.helper.isEinkDisplay
import com.parem.launcher.helper.showToast
import androidx.core.widget.TextViewCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.parem.launcher.helper.onOff
import java.text.DateFormatSymbols
import java.util.Calendar

/**
 * Bottom sheet dialog for enabling/disabling Focus Mode.
 *
 * When focus mode is active, shows current status with remaining time and a disable button.
 * When inactive, shows duration options, app whitelist checkboxes, and an enable button.
 *
 * @param context The activity context.
 * @param allApps List of (packageName, appLabel) pairs for whitelist selection. All installed
 * apps (all profiles) are offered; only [FocusModeManager]'s selection cap limits how many can
 * be checked at once.
 */
class FocusModeDialog(
    private val context: Context,
    private val allApps: List<Pair<String, String>> = emptyList()
) {

    private lateinit var dialog: BottomSheetDialog

    fun show(): BottomSheetDialog {
        val menu = BottomSheetMenu(context).title(context.getString(R.string.focus_mode))
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
        }
        val ctx = context
        val primaryColor = ctx.getColorFromAttr(R.attr.textColor)

        menu.customView(container)
        val active = FocusModeManager.isActive(ctx)
        if (active) {
            buildActiveView(container, ctx, primaryColor, menu)
        } else {
            buildInactiveView(container, ctx, primaryColor, menu)
        }
        dialog = menu.show(
            resizeForKeyboard = !active && allApps.isNotEmpty(),
        )
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        )
        return dialog
    }

    /**
     * Builds the UI shown when focus mode is currently active:
     * status text, remaining time, and a disable button.
     */
    private fun buildActiveView(
        container: LinearLayout,
        ctx: Context,
        primaryColor: Int,
        menu: BottomSheetMenu,
    ) {
        // Status text
        val statusText = TextView(ctx).apply {
            text = ctx.getString(R.string.focus_mode_on)
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Parem_SheetBody)
            setTextColor(primaryColor)
            setPadding(0, 8.dpToPx(), 0, 4.dpToPx())
        }
        container.addView(statusText)

        val timeText = TextView(ctx).apply {
            text = when (val label = FocusModeManager.getActiveLabel(ctx)) {
                is FocusModeManager.ActiveLabel.Timed -> ctx.getString(R.string.focus_remaining, label.remaining)
                is FocusModeManager.ActiveLabel.ScheduledUntil -> ctx.getString(
                    R.string.focus_schedule_until,
                    FocusModeManager.formatScheduledEnd(ctx, label.epochMs)
                )
                FocusModeManager.ActiveLabel.ScheduledNoEnd -> ctx.getString(R.string.focus_schedule_no_end)
                FocusModeManager.ActiveLabel.Unlimited, null -> ctx.getString(R.string.focus_until_disabled_status)
            }
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Parem_SheetCaption)
            setTextColor(ctx.getColorFromAttr(R.attr.textSecondaryColor))
            setPadding(0, 0, 0, 16.dpToPx())
        }
        container.addView(timeText)

        menu.option(ctx.getString(R.string.disable_focus), dismissOnClick = false) {
            BadHabitDialogs.showPause(
                ctx,
                question = ctx.getString(R.string.focus_end_early),
                note = timeText.text.toString(),
                waiting = { left -> ctx.getString(R.string.focus_end_wait, left) },
                ready = ctx.getString(R.string.focus_end_now),
                onProceed = {
                    FocusModeManager.disable(ctx)
                    FocusModeManager.pauseSchedule(ctx)
                    GrayscaleController.cancelFocusEnd(ctx)
                    GrayscaleController.reconcile(ctx)
                    dialog.dismiss()
                },
                onCancel = {},
            )
        }
    }

    /**
     * Builds the UI shown when focus mode is inactive:
     * duration radio group, app whitelist checkboxes, and an enable button.
     */
    private fun buildInactiveView(
        container: LinearLayout,
        ctx: Context,
        primaryColor: Int,
        menu: BottomSheetMenu,
    ) {
        container.addView(createSectionLabel(ctx, ctx.getString(R.string.focus_duration)))

        data class DurationOption(val label: String, val minutes: Int)
        val options = listOf(
            DurationOption(ctx.getString(R.string.focus_25_min), 25),
            DurationOption(ctx.getString(R.string.focus_1_hour), 60),
            DurationOption(ctx.getString(R.string.focus_2_hours), 120),
            DurationOption(ctx.getString(R.string.focus_until_disable), -1)
        )
        var durationMinutes = 25
        val durationRows = options.map { option ->
            SheetCheckRow(ctx).apply {
                singleChoice = true
                text = option.label
                checked = option.minutes == durationMinutes
                container.addView(this)
            }
        }
        durationRows.forEachIndexed { index, row ->
            row.setOnClickListener {
                durationMinutes = options[index].minutes
                durationRows.forEachIndexed { i, item -> item.checked = i == index }
            }
        }

        // Allowed apps section
        var pickerAdapter: AppPickerAdapter? = null
        // Set once the schedule section exists; the picker calls it on every check change
        var updateEmptyWarning: () -> Unit = {}
        var updateAllowedCount: () -> Unit = {}

        if (allApps.isNotEmpty()) {
            val currentWhitelist = FocusModeManager.getWhitelist(ctx)
            val knownPackages = allApps.mapTo(mutableSetOf()) { it.first }
            val hiddenCount = currentWhitelist.count { it !in knownPackages }
            val adapter = AppPickerAdapter(
                textColor = primaryColor,
                maxSelected = (5 - hiddenCount).coerceAtLeast(0),
                entries = allApps.map { (packageName, appLabel) ->
                    AppPickerAdapter.Entry(packageName, appLabel)
                },
                initiallySelected = allApps.mapNotNull { (packageName, _) ->
                    packageName.takeIf { it in currentWhitelist }
                },
                onSelectionChanged = { updateEmptyWarning(); updateAllowedCount() }
            )
            pickerAdapter = adapter
            val allowedRow = createWindowRow(ctx, primaryColor, ctx.getString(R.string.focus_allowed_apps),
                onClick = {
                    val staged = AppPickerAdapter(
                        textColor = primaryColor,
                        maxSelected = (5 - hiddenCount).coerceAtLeast(0),
                        entries = allApps.map { (pkg, label) -> AppPickerAdapter.Entry(pkg, label) },
                        initiallySelected = adapter.selectedIds,
                        onLimitReached = { it.showToast(it.getString(R.string.a11y_picker_cap, 5)) },
                    )
                    val pickerContent = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        addAppPicker(staged)
                    }
                    val picker = BottomSheetMenu(ctx)
                        .title(ctx.getString(R.string.focus_allowed_apps))
                        .customView(pickerContent)
                        .pickerActions {
                            adapter.replaceSelection(staged.selectedIds)
                            updateEmptyWarning()
                            updateAllowedCount()
                        }
                        .show(resizeForKeyboard = true, scrollContent = false, fitPicker = true)
                    picker.dismissWithParent(dialog)
                }, onLongClick = null)
            updateAllowedCount = {
                val count = FocusModeManager.mergeWhitelist(
                    adapter.selectedIds, FocusModeManager.getWhitelist(ctx),
                    allApps.mapTo(mutableSetOf()) { it.first },
                ).size
                allowedRow.text = ctx.getString(R.string.picker_selected_count,
                    ctx.getString(R.string.focus_allowed_apps), count)
            }
            updateAllowedCount()
            container.addView(allowedRow)
        }

        // Schedule section: edits stay local until Save or Enable
        val windows = FocusModeManager.getSchedule(ctx).toMutableList()
        var scheduleOn = FocusModeManager.isScheduleEnabled(ctx)

        val scheduleToggle = TextView(ctx).apply {
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Parem_SheetCaption)
            setTextColor(primaryColor)
            setPadding(8.dpToPx(), 8.dpToPx(), 0, 8.dpToPx())
            setBackgroundResource(if (ctx.isEinkDisplay()) R.drawable.bg_eink_control else ctx.selectableBackgroundRes())
        }
        val scheduleHeader = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                createSectionLabel(ctx, ctx.getString(R.string.focus_schedule)),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(scheduleToggle)
        }
        container.addView(scheduleHeader)

        val windowsList = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        container.addView(windowsList)
        val hint = createMutedLine(ctx, ctx.getString(R.string.focus_schedule_hint))
        container.addView(hint)
        val emptyWarning = createMutedLine(ctx, ctx.getString(R.string.focus_schedule_empty_whitelist))

        fun renderSchedule() {
            scheduleToggle.text = ctx.onOff(scheduleOn)
            windowsList.removeAllViews()
            windows.forEachIndexed { index, window ->
                windowsList.addView(createWindowRow(ctx, primaryColor, formatWindow(ctx, window),
                    onClick = {
                        editWindow(ctx, window) { edited ->
                            windows[index] = edited
                            renderSchedule()
                        }
                    },
                    onLongClick = {
                        windows.removeAt(index)
                        renderSchedule()
                    }
                ))
            }
            hint.visibility = if (windows.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            updateEmptyWarning()
        }
        updateEmptyWarning = {
            val visible = pickerAdapter?.selectedIds.orEmpty()
            val noApps = FocusModeManager.mergeWhitelist(
                visible, FocusModeManager.getWhitelist(ctx), allApps.mapTo(mutableSetOf()) { it.first },
            ).isEmpty()
            emptyWarning.visibility =
                if (scheduleOn && noApps) android.view.View.VISIBLE else android.view.View.GONE
        }
        scheduleToggle.setOnClickListener {
            scheduleOn = !scheduleOn
            renderSchedule()
        }

        container.addView(createWindowRow(ctx, primaryColor, ctx.getString(R.string.focus_schedule_add),
            onClick = {
                editWindow(ctx, null) { added ->
                    windows += added
                    // Adding a window means the user wants a schedule
                    scheduleOn = true
                    renderSchedule()
                }
            },
            onLongClick = null
        ))
        container.addView(emptyWarning)
        renderSchedule()

        fun saveSettings() {
            // Entries the picker could not offer (locked Private Space, paused
            // profile) survive a save instead of being blocked from then on
            FocusModeManager.setWhitelist(ctx, FocusModeManager.mergeWhitelist(
                pickerAdapter?.selectedIds ?: emptySet(),
                FocusModeManager.getWhitelist(ctx),
                allApps.mapTo(mutableSetOf()) { it.first },
            ))
            FocusModeManager.setSchedule(ctx, windows)
            FocusModeManager.setScheduleEnabled(ctx, scheduleOn)
        }

        menu.option(ctx.getString(R.string.save), dismissOnClick = false) {
            saveSettings()
            dialog.dismiss()
        }

        menu.option(ctx.getString(R.string.enable_focus), dismissOnClick = false) {
            saveSettings()
            FocusModeManager.enable(ctx, durationMinutes)
            // Also queues the focus-end job when grey goes on
            GrayscaleController.reconcile(ctx)
            dialog.dismiss()
        }
    }

    /**
     * Days sheet → start → end picker. [onDone] runs only if
     * all three are confirmed while this sheet is still showing.
     */
    private fun editWindow(ctx: Context, initial: FocusSchedule.Window?, onDone: (FocusSchedule.Window) -> Unit) {
        val labels = (0..6).map { weekdayShortName(it) }.toTypedArray()
        val is24h = android.text.format.DateFormat.is24HourFormat(ctx)
        val boxes = (0..6).map { day ->
            SheetCheckRow(ctx).apply {
                text = labels[day]
                checked = initial == null || initial.days and (1 shl day) != 0
                setOnClickListener { this.checked = !this.checked }
            }
        }
        val dayList = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            boxes.forEach { addView(it) }
        }
        lateinit var daysSheet: BottomSheetDialog
        daysSheet = BottomSheetMenu(ctx)
            .title(ctx.getString(R.string.focus_schedule_days))
            .customView(dayList)
            .option(ctx.getString(android.R.string.ok), dismissOnClick = false) {
                if (!dialog.isShowing) return@option
                val days = boxes.foldIndexed(0) { i, acc, box -> if (box.checked) acc or (1 shl i) else acc }
                if (days == 0) {
                    ctx.showToast(ctx.getString(R.string.focus_schedule_pick_day))
                    return@option
                }
                daysSheet.dismiss()
                val start = initial?.startMin ?: 9 * 60
                Motion.showPopup(TimePickerDialog(ctx, { _, h, m ->
                    if (!dialog.isShowing) return@TimePickerDialog
                    val end = initial?.endMin ?: 17 * 60
                    Motion.showPopup(TimePickerDialog(ctx, { _, h2, m2 ->
                        if (!dialog.isShowing) return@TimePickerDialog
                        onDone(FocusSchedule.Window(days, h * 60 + m, h2 * 60 + m2))
                    }, end / 60, end % 60, is24h).apply { setTitle(R.string.focus_schedule_end) })
                }, start / 60, start % 60, is24h).apply { setTitle(R.string.focus_schedule_start) })
            }
            .option(ctx.getString(android.R.string.cancel), dimmed = true) {}
            .show()
    }

    /** 0 = Monday … 6 = Sunday, in the platform's short weekday names. */
    private fun weekdayShortName(day: Int): String {
        val calendarDay = if (day == 6) Calendar.SUNDAY else Calendar.MONDAY + day
        return DateFormatSymbols.getInstance().shortWeekdays[calendarDay]
    }

    private fun formatWindow(ctx: Context, w: FocusSchedule.Window): String {
        val days = if (w.days == FocusSchedule.ALL_DAYS) ctx.getString(R.string.focus_schedule_every_day)
        else (0..6).filter { w.days and (1 shl it) != 0 }.joinToString(" ") { weekdayShortName(it) }
        val timeFormat = android.text.format.DateFormat.getTimeFormat(ctx)
        fun time(min: Int) = timeFormat.format(Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, min / 60)
            set(Calendar.MINUTE, min % 60)
        }.time)
        return "$days  ${time(w.startMin)}–${time(w.endMin)}"
    }

    private fun createWindowRow(
        ctx: Context,
        color: Int,
        text: String,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)?
    ): TextView {
        return TextView(ctx).apply {
            this.text = text
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Parem_SheetBody)
            setTextColor(color)
            setPadding(8.dpToPx(), 10.dpToPx(), 0, 10.dpToPx())
            minimumHeight = resources.getDimensionPixelSize(R.dimen.touch_target_min)
            setBackgroundResource(if (ctx.isEinkDisplay()) R.drawable.bg_eink_control else ctx.selectableBackgroundRes())
            setOnClickListener { onClick() }
            if (onLongClick != null) setOnLongClickListener { onLongClick(); true }
        }
    }

    private fun createMutedLine(ctx: Context, text: String): TextView {
        return TextView(ctx).apply {
            this.text = text
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Parem_SheetCaption)
            setTextColor(ctx.getColorFromAttr(R.attr.textSecondaryColor))
            setPadding(8.dpToPx(), 0, 0, 8.dpToPx())
        }
    }

    private fun createSectionLabel(ctx: Context, text: String): TextView {
        return TextView(ctx).apply {
            this.text = text
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Parem_SheetCaption)
            setTextColor(ctx.getColorFromAttr(R.attr.textSecondaryColor))
            setPadding(0, 8.dpToPx(), 0, 4.dpToPx())
        }
    }
}
