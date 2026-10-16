package com.parem.launcher.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.parem.launcher.R
import com.parem.launcher.helper.FocusModeManager
import com.parem.launcher.helper.FocusSchedule
import com.parem.launcher.helper.GrayscaleController
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr
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
            setTextColor(primaryColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
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
            setTextColor(ctx.getColorFromAttr(R.attr.textSecondaryColor))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, 0, 0, 16.dpToPx())
        }
        container.addView(timeText)

        menu.option(ctx.getString(R.string.disable_focus), dismissOnClick = false) {
            FocusModeManager.disable(ctx)
            FocusModeManager.pauseSchedule(ctx)
            GrayscaleController.cancelFocusEnd(ctx)
            GrayscaleController.reconcile(ctx)
            dialog.dismiss()
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

        // Radio group for timer options
        val radioGroup = RadioGroup(ctx).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(0, 0, 0, 12.dpToPx())
        }

        data class DurationOption(val label: String, val minutes: Int)

        val options = listOf(
            DurationOption(ctx.getString(R.string.focus_25_min), 25),
            DurationOption(ctx.getString(R.string.focus_1_hour), 60),
            DurationOption(ctx.getString(R.string.focus_2_hours), 120),
            DurationOption(ctx.getString(R.string.focus_until_disable), -1)
        )

        options.forEachIndexed { index, option ->
            val radioButton = RadioButton(ctx).apply {
                id = index + 1
                text = option.label
                setTextColor(primaryColor)
                // Default widget tint is the Material accent, which clashes
                // with the launcher's mono palette
                buttonTintList = ColorStateList.valueOf(primaryColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(8.dpToPx(), 4.dpToPx(), 0, 4.dpToPx())
                minimumHeight = resources.getDimensionPixelSize(R.dimen.touch_target_min)
            }
            radioGroup.addView(radioButton)
        }
        radioGroup.check(1) // Default: 25 minutes
        container.addView(radioGroup)

        // Allowed apps section
        var pickerAdapter: AppPickerAdapter? = null
        // Set once the schedule section exists; the picker calls it on every check change
        var updateEmptyWarning: () -> Unit = {}

        if (allApps.isNotEmpty()) {
            container.addView(createSectionLabel(ctx, ctx.getString(R.string.focus_allowed_apps)))

            val currentWhitelist = FocusModeManager.getWhitelist(ctx)
            val adapter = AppPickerAdapter(
                textColor = primaryColor,
                maxSelected = 5,
                entries = allApps.map { (packageName, appLabel) ->
                    AppPickerAdapter.Entry(packageName, appLabel)
                },
                initiallySelected = allApps.mapNotNull { (packageName, _) ->
                    packageName.takeIf { it in currentWhitelist }
                },
                onSelectionChanged = { updateEmptyWarning() }
            )
            pickerAdapter = adapter

            container.addAppPicker(adapter)

        }

        // Schedule section: edits stay local until Save or Enable
        val windows = FocusModeManager.getSchedule(ctx).toMutableList()
        var scheduleOn = FocusModeManager.isScheduleEnabled(ctx)

        val scheduleToggle = TextView(ctx).apply {
            setTextColor(primaryColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(8.dpToPx(), 8.dpToPx(), 0, 8.dpToPx())
            setBackgroundResource(ctx.selectableBackgroundRes())
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
            val noApps = pickerAdapter?.selectedIds?.isEmpty() ?: true
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
            val selectedId = radioGroup.checkedRadioButtonId
            val durationMinutes = if (selectedId in 1..options.size) {
                options[selectedId - 1].minutes
            } else {
                25
            }

            saveSettings()
            FocusModeManager.enable(ctx, durationMinutes)
            // Also queues the focus-end job when grey goes on
            GrayscaleController.reconcile(ctx)
            dialog.dismiss()
        }
    }

    /**
     * Days → start → end, each step a platform dialog. [onDone] runs only if
     * all three are confirmed while this sheet is still showing.
     */
    private fun editWindow(ctx: Context, initial: FocusSchedule.Window?, onDone: (FocusSchedule.Window) -> Unit) {
        val labels = (0..6).map { weekdayShortName(it) }.toTypedArray()
        val checked = BooleanArray(7) { initial == null || initial.days and (1 shl it) != 0 }
        val is24h = android.text.format.DateFormat.is24HourFormat(ctx)
        val daysDialog = AlertDialog.Builder(ctx)
            .setTitle(R.string.focus_schedule_days)
            // The dialog writes each tap into [checked] before calling back
            .setMultiChoiceItems(labels, checked) { dialog, _, _ ->
                (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = checked.any { it }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (!dialog.isShowing) return@setPositiveButton
                val days = checked.foldIndexed(0) { i, acc, on -> if (on) acc or (1 shl i) else acc }
                if (days == 0) return@setPositiveButton
                val start = initial?.startMin ?: 9 * 60
                TimePickerDialog(ctx, { _, h, m ->
                    if (!dialog.isShowing) return@TimePickerDialog
                    val end = initial?.endMin ?: 17 * 60
                    TimePickerDialog(ctx, { _, h2, m2 ->
                        if (!dialog.isShowing) return@TimePickerDialog
                        onDone(FocusSchedule.Window(days, h * 60 + m, h2 * 60 + m2))
                    }, end / 60, end % 60, is24h).apply { setTitle(R.string.focus_schedule_end) }.show()
                }, start / 60, start % 60, is24h).apply { setTitle(R.string.focus_schedule_start) }.show()
            }
            .create()
        daysDialog.show()
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
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(8.dpToPx(), 10.dpToPx(), 0, 10.dpToPx())
            setBackgroundResource(ctx.selectableBackgroundRes())
            setOnClickListener { onClick() }
            if (onLongClick != null) setOnLongClickListener { onLongClick(); true }
        }
    }

    private fun createMutedLine(ctx: Context, text: String): TextView {
        return TextView(ctx).apply {
            this.text = text
            setTextColor(ctx.getColorFromAttr(R.attr.textSecondaryColor))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(8.dpToPx(), 0, 0, 8.dpToPx())
        }
    }

    private fun createSectionLabel(ctx: Context, text: String): TextView {
        return TextView(ctx).apply {
            this.text = text
            setTextColor(ctx.getColorFromAttr(R.attr.textSecondaryColor))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, 8.dpToPx(), 0, 4.dpToPx())
        }
    }
}
