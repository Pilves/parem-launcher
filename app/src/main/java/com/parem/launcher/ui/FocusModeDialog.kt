package com.parem.launcher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.parem.launcher.R
import com.parem.launcher.helper.FocusModeManager
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr
import com.google.android.material.bottomsheet.BottomSheetDialog

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
    context: Context,
    private val allApps: List<Pair<String, String>> = emptyList()
) : BottomSheetDialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_focus_mode)
        transparentSheetFrame()

        val container = findViewById<LinearLayout>(R.id.focusModeContainer) ?: return
        val ctx = container.context

        val primaryColor = ctx.getColorFromAttr(R.attr.primaryColor)

        if (FocusModeManager.isActive(ctx)) {
            buildActiveView(container, ctx, primaryColor)
        } else {
            buildInactiveView(container, ctx, primaryColor)
        }
    }

    /**
     * Builds the UI shown when focus mode is currently active:
     * status text, remaining time, and a disable button.
     */
    private fun buildActiveView(
        container: LinearLayout,
        ctx: Context,
        primaryColor: Int
    ) {
        container.addView(createTitle(ctx, ctx.getString(R.string.focus_mode)))

        // Status text
        val statusText = TextView(ctx).apply {
            text = ctx.getString(R.string.focus_mode_on)
            setTextColor(primaryColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, 8.dpToPx(), 0, 4.dpToPx())
        }
        container.addView(statusText)

        // Remaining time
        val remaining = FocusModeManager.getRemainingTimeFormatted(ctx)
        val timeText = TextView(ctx).apply {
            text = if (remaining != null) ctx.getString(R.string.focus_remaining, remaining)
            else ctx.getString(R.string.focus_until_disabled_status)
            setTextColor(ctx.getColorFromAttr(R.attr.primaryColorTrans50))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, 0, 0, 16.dpToPx())
        }
        container.addView(timeText)

        container.addView(createActionRow(ctx, ctx.getString(R.string.disable_focus)) {
            FocusModeManager.disable(ctx)
            dismiss()
        })
    }

    /**
     * Builds the UI shown when focus mode is inactive:
     * duration radio group, app whitelist checkboxes, and an enable button.
     */
    private fun buildInactiveView(
        container: LinearLayout,
        ctx: Context,
        primaryColor: Int
    ) {
        container.addView(createTitle(ctx, ctx.getString(R.string.focus_mode)))
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
            }
            radioGroup.addView(radioButton)
        }
        radioGroup.check(1) // Default: 25 minutes
        container.addView(radioGroup)

        // Allowed apps section
        var pickerAdapter: AppPickerAdapter? = null

        if (allApps.isNotEmpty()) {
            container.addView(createSectionLabel(ctx, ctx.getString(R.string.focus_allowed_apps)))

            val currentWhitelist = FocusModeManager.getWhitelist(ctx)
            // Whitelist entries for apps no longer offered are dropped on enable,
            // as before (the old code only collected the rendered checkboxes).
            val adapter = AppPickerAdapter(
                textColor = primaryColor,
                maxSelected = 5,
                entries = allApps.map { (packageName, appLabel) ->
                    AppPickerAdapter.Entry(packageName, appLabel)
                },
                initiallySelected = allApps.mapNotNull { (packageName, _) ->
                    packageName.takeIf { it in currentWhitelist }
                }
            )
            pickerAdapter = adapter

            val searchInput = EditText(ctx).apply {
                hint = ctx.getString(R.string.search_apps)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(primaryColor)
                setHintTextColor(ctx.getColorFromAttr(R.attr.primaryColorTrans50))
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
            }
            container.addView(searchInput)

            // Fixed height so the RecyclerView actually recycles inside the outer
            // ScrollView (wrap_content would measure every row); same viewport as
            // the folder picker.
            val appsList = RecyclerView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 300.dpToPx()
                )
                layoutManager = LinearLayoutManager(ctx)
            }
            appsList.adapter = adapter
            container.addView(appsList)

            // Keep the filtered list visible above the keyboard, as the widget picker does
            window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }

        container.addView(createActionRow(ctx, ctx.getString(R.string.enable_focus)) {
            val selectedId = radioGroup.checkedRadioButtonId
            val durationMinutes = if (selectedId in 1..options.size) {
                options[selectedId - 1].minutes
            } else {
                25
            }

            val selectedPackages = pickerAdapter?.selectedIds?.toSet() ?: emptySet()

            FocusModeManager.setWhitelist(ctx, selectedPackages)
            FocusModeManager.enable(ctx, durationMinutes)
            dismiss()
        })
    }

    private fun createTitle(ctx: Context, text: String): TextView {
        return TextView(ctx).apply {
            this.text = text
            setTextColor(ctx.getColorFromAttr(R.attr.primaryColorTrans50))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.START
            setPadding(0, 0, 0, 8.dpToPx())
        }
    }

    private fun createSectionLabel(ctx: Context, text: String): TextView {
        return TextView(ctx).apply {
            this.text = text
            setTextColor(ctx.getColorFromAttr(R.attr.primaryColorTrans50))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, 8.dpToPx(), 0, 4.dpToPx())
        }
    }

    /** Text-button row, replacing the stock Button widgets the sheet used to mix in. */
    private fun createActionRow(ctx: Context, text: String, onClick: () -> Unit): TextView {
        return TextView(ctx).apply {
            this.text = text
            setTextColor(ctx.getColorFromAttr(R.attr.primaryColor))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dpToPx() }
            setPadding(0, 14.dpToPx(), 0, 14.dpToPx())
            setBackgroundResource(ctx.selectableBackgroundRes())
            setOnClickListener { onClick() }
        }
    }
}
