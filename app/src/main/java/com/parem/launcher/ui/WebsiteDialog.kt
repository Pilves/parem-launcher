package com.parem.launcher.ui

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.parem.launcher.R
import com.parem.launcher.helper.WebShortcut
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.helper.showToast

/**
 * "Add website" sheet: URL and optional label, styled like [CreateFolderDialog].
 * Pure UI — [onSave] only fires with a normalized http(s) URL and a non-blank
 * label (the host when the user left it empty); the caller persists it.
 */
class WebsiteDialog(
    private val context: Context,
    private val onSave: (url: String, label: String) -> Unit,
) {

    fun show() {
        val dialog = BottomSheetDialog(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_bottom_sheet)
            setPadding(24.dpToPx(), 12.dpToPx(), 24.dpToPx(), 24.dpToPx())
        }
        container.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(40.dpToPx(), 4.dpToPx()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 16.dpToPx()
            }
            setBackgroundColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
        })

        container.addView(TextView(context).apply {
            text = context.getString(R.string.add_website)
            textSize = 14f
            setTextColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12.dpToPx())
        })

        fun input(hintRes: Int, type: Int) = EditText(context).apply {
            textSize = 16f
            setTextColor(context.getColorFromAttr(R.attr.primaryColor))
            setHintTextColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
            hint = context.getString(hintRes)
            background = null
            inputType = type
            isSingleLine = true
            setPadding(0, 8.dpToPx(), 0, 16.dpToPx())
        }
        val urlInput = input(R.string.website_url_hint, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val labelInput = input(R.string.website_label_hint, InputType.TYPE_CLASS_TEXT)
        container.addView(urlInput)
        container.addView(labelInput)

        container.addView(TextView(context).apply {
            text = context.getString(R.string.save)
            textSize = 16f
            setTextColor(context.getColorFromAttr(R.attr.primaryColor))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 16.dpToPx(), 0, 0)
            setOnClickListener {
                val url = WebShortcut.normalize(urlInput.text.toString())
                if (url == null) {
                    context.showToast(context.getString(R.string.invalid_url))
                    return@setOnClickListener
                }
                onSave(url, labelInput.text.toString().trim().ifBlank { WebShortcut.defaultLabel(url) })
                dialog.dismiss()
            }
        })

        dialog.setContentView(container)
        dialog.transparentSheetFrame()
        dialog.disableAnimationsOnEink()
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
    }
}
