package com.parem.launcher.ui.settings

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.parem.launcher.MainViewModel
import com.parem.launcher.R
import com.parem.launcher.helper.GestureLetterManager
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.ui.BottomSheetMenu

/**
 * Bottom sheet listing every supported gesture letter with its current app
 * mapping (or "Not set"), a per-row clear button, and a "disable" option.
 * Extracted from SettingsFragment.showGestureLetterConfigDialog; behavior
 * unchanged, including the dismiss-and-rebuild-self trick used after a
 * mapping is cleared.
 *
 * @param onPickAppForLetter Called (with the dialog already dismissed) after the
 * user taps an unmapped/mapped letter row; sets [MainViewModel.pendingGestureLetter]
 * and navigates to the app list.
 * @param onDisabled Called after gesture letters are turned off, so the caller can
 * refresh its "Wellbeing" row texts.
 */
class GestureLetterConfigDialog(
    private val context: Context,
    private val viewModel: MainViewModel,
    private val onPickAppForLetter: (Char) -> Unit,
    private val onDisabled: () -> Unit,
) {

    fun show(): BottomSheetDialog {
        val menu = BottomSheetMenu(context)
            .title(context.getString(R.string.gesture_letters))
        lateinit var dialog: BottomSheetDialog

        val allMappings = GestureLetterManager.getAllMappings(context)
        for (letter in GestureLetterManager.getSupportedLetters()) {
            val mapping = allMappings[letter]
            val appLabel = if (mapping != null) {
                try {
                    val pm = context.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(mapping.first, 0)).toString()
                } catch (_: Exception) { mapping.first }
            } else null

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24.dpToPx(), 14.dpToPx(), 24.dpToPx(), 14.dpToPx())
            }

            val letterTv = TextView(context).apply {
                text = if (appLabel != null) "$letter  →  $appLabel" else "$letter  —  Not set"
                textSize = 16f
                setTextColor(context.getColorFromAttr(R.attr.primaryColor))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    dialog.dismiss()
                    viewModel.pendingGestureLetter = letter
                    onPickAppForLetter(letter)
                }
            }
            row.addView(letterTv)

            if (mapping != null) {
                val clearBtn = TextView(context).apply {
                    text = "✕"
                    textSize = 16f
                    setTextColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
                    setPadding(12.dpToPx(), 0, 0, 0)
                    setOnClickListener {
                        GestureLetterManager.removeMapping(context, letter)
                        dialog.dismiss()
                        show()
                    }
                }
                row.addView(clearBtn)
            }

            menu.customView(row)
        }

        menu.option(context.getString(R.string.disable), dimmed = true) {
            GestureLetterManager.setEnabled(context, false)
            onDisabled()
        }

        dialog = menu.show()
        return dialog
    }
}
