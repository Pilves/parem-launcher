package com.parem.launcher.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.parem.launcher.R
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr

/**
 * Builder for the app's standard bottom-sheet menus: drag handle, optional
 * title/message, and tappable text rows. Every bottom sheet in the app should go
 * through this instead of hand-assembling the same LinearLayout pattern.
 */
class BottomSheetMenu(private val context: Context) {

    private val dialog = BottomSheetDialog(context)
    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(context.getColorFromAttr(R.attr.primaryInverseColor))
        setPadding(0, 12.dpToPx(), 0, 24.dpToPx())
        addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(40.dpToPx(), 4.dpToPx()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 16.dpToPx()
            }
            setBackgroundColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
        })
    }

    fun title(text: String): BottomSheetMenu {
        container.addView(TextView(context).apply {
            this.text = text
            textSize = 14f
            setTextColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
            setTypeface(null, Typeface.BOLD)
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 8.dpToPx())
        })
        return this
    }

    fun message(text: String): BottomSheetMenu {
        container.addView(TextView(context).apply {
            this.text = text
            textSize = 16f
            setTextColor(context.getColorFromAttr(R.attr.primaryColor))
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 16.dpToPx())
        })
        return this
    }

    /** A tappable row. The sheet dismisses before [onClick] runs. */
    fun option(text: String, dimmed: Boolean = false, onClick: () -> Unit): BottomSheetMenu {
        container.addView(TextView(context).apply {
            this.text = text
            textSize = 16f
            setTextColor(
                context.getColorFromAttr(if (dimmed) R.attr.primaryColorTrans50 else R.attr.primaryColor)
            )
            setPadding(24.dpToPx(), 14.dpToPx(), 24.dpToPx(), 14.dpToPx())
            setOnClickListener {
                dialog.dismiss()
                onClick()
            }
        })
        return this
    }

    fun customView(view: View): BottomSheetMenu {
        container.addView(view)
        return this
    }

    fun onDismiss(action: () -> Unit): BottomSheetMenu {
        dialog.setOnDismissListener { action() }
        return this
    }

    fun show(): BottomSheetDialog {
        dialog.setContentView(container)
        dialog.show()
        return dialog
    }
}
