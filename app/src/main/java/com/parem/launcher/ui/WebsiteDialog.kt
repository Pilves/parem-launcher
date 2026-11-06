package com.parem.launcher.ui

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
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

    fun show(): BottomSheetDialog {
        lateinit var dialog: BottomSheetDialog
        val menu = BottomSheetMenu(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 0, 24.dpToPx(), 0)
        }
        menu.title(context.getString(R.string.add_website))

        fun input(hintRes: Int, type: Int) = EditText(context).apply {
            textSize = 16f
            setTextColor(context.getColorFromAttr(R.attr.textColor))
            setHintTextColor(context.getColorFromAttr(R.attr.textSecondaryColor))
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

        menu.customView(container).option(context.getString(R.string.save), dismissOnClick = false) {
            val url = WebShortcut.normalize(urlInput.text.toString())
            if (url == null) {
                context.showToast(context.getString(R.string.invalid_url))
                return@option
            }
            onSave(url, labelInput.text.toString().trim().ifBlank { WebShortcut.defaultLabel(url) })
            dialog.dismiss()
        }

        dialog = menu.show(resizeForKeyboard = true)
        return dialog
    }
}
