package com.parem.launcher.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ImageView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.widget.TextViewCompat
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.parem.launcher.R
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.helper.onOff
import com.parem.launcher.helper.skipAnimations
import com.parem.launcher.helper.isEinkDisplay

/**
 * The sheet frame behind a rounded-corner content view must be transparent,
 * or the frame's own background fills the corners back in as square.
 */
fun BottomSheetDialog.transparentSheetFrame() {
    findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        ?.setBackgroundColor(Color.TRANSPARENT)
}

/**
 * Drops the sheet's slide-in on e-ink (ghosting) and when any system animation
 * scale is 0. The system zeroes window animations only at *window* scale 0, so
 * without this a user who set only animator scale 0 would still see sheets slide.
 * Call before show(); sheets built outside [BottomSheetMenu] must call it themselves.
 */
fun BottomSheetDialog.disableAnimationsOnEink() {
    if (context.skipAnimations()) window?.setWindowAnimations(0)
}

/**
 * Dismisses the dialog when [owner] is destroyed. Dialogs live in the
 * activity's window, so a sheet opened from a fragment would otherwise stay
 * up after Home pops the fragment, and its callbacks would then reach a
 * fragment that is no longer attached.
 */
/** True while the fragment's view exists: the guard every sheet callback runs before touching it. */
val Fragment.isAlive: Boolean get() = isAdded && view != null

fun <T : Dialog> T.dismissOnDestroy(owner: LifecycleOwner): T {
    owner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY) dismiss()
    })
    return this
}

/** Resolves the theme's selectableItemBackground for tap ripple on sheet rows. */
internal fun Context.selectableBackgroundRes(): Int {
    val tv = TypedValue()
    theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
    return tv.resourceId
}

/**
 * Builder for the app's standard bottom-sheet menus: drag handle, optional
 * title/message, and tappable text rows. Every bottom sheet in the app should go
 * through this instead of hand-assembling the same LinearLayout pattern.
 */
class BottomSheetMenu(private val context: Context) {

    private val dialog = BottomSheetDialog(context)
    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_bottom_sheet)
        setPadding(0, 12.dpToPx(), 0, 24.dpToPx())
        addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(40.dpToPx(), 4.dpToPx()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 16.dpToPx()
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setBackgroundColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
        })
    }

    fun title(text: String, prominent: Boolean = false): BottomSheetMenu {
        container.addView(TextView(context).apply {
            this.text = text
            textSize = 14f
            setTextColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
            setTypeface(null, Typeface.BOLD)
            if (prominent) {
                TextViewCompat.setTextAppearance(this, R.style.TextLarge)
                setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
                setTextColor(context.getColorFromAttr(R.attr.primaryColor))
            }
            ViewCompat.setAccessibilityHeading(this, true)
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

    /** A whole tappable row; input validation may opt out of automatic dismissal. */
    fun option(
        text: String,
        dimmed: Boolean = false,
        summary: String? = null,
        checked: Boolean? = null,
        dismissOnClick: Boolean = true,
        onClick: () -> Unit,
    ): BottomSheetMenu {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.resources.getDimensionPixelSize(R.dimen.touch_target_min)
            setPadding(24.dpToPx(), 14.dpToPx(), 24.dpToPx(), 14.dpToPx())
            isFocusable = true
            if (context.isEinkDisplay()) {
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(context.getColorFromAttr(R.attr.primaryShadeDarkColor)))
                    addState(intArrayOf(android.R.attr.state_focused), ColorDrawable(context.getColorFromAttr(R.attr.primaryShadeDarkColor)))
                    addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
                }
            } else setBackgroundResource(context.selectableBackgroundRes())
            contentDescription = listOfNotNull(text, summary).joinToString(", ")
            ViewCompat.setScreenReaderFocusable(this, true)
            isSelected = checked == true
            if (checked != null) ViewCompat.setStateDescription(this, context.onOff(checked))
            ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = if (checked == null) "android.widget.Button" else "android.widget.CheckBox"
                    if (checked != null) info.isSelected = false
                    info.isCheckable = checked != null
                    info.isChecked = checked == true
                }
            })
            setOnClickListener {
                if (dismissOnClick) dialog.dismiss()
                onClick()
            }
        }
        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        labels.addView(TextView(context).apply {
            TextViewCompat.setTextAppearance(this, R.style.TextSmall)
            this.text = text
            setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            setTextColor(context.getColorFromAttr(if (dimmed) R.attr.primaryColorTrans60 else R.attr.primaryColor))
        })
        if (summary != null) labels.addView(TextView(context).apply {
            TextViewCompat.setTextAppearance(this, androidx.appcompat.R.style.TextAppearance_AppCompat_Small)
            this.text = summary
            setTextColor(context.getColorFromAttr(R.attr.primaryColorTrans80))
            setPadding(0, 4.dpToPx(), 0, 0)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (checked != null) row.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_check)
            visibility = if (checked) View.VISIBLE else View.INVISIBLE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(24.dpToPx(), 24.dpToPx()).apply { marginStart = 8.dpToPx() })
        container.addView(row)
        return this
    }

    fun gap(): BottomSheetMenu = customView(View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, 24.dpToPx())
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    })

    fun sectionLabel(text: String): BottomSheetMenu {
        gap()
        return customView(TextView(context).apply {
            TextViewCompat.setTextAppearance(this, androidx.appcompat.R.style.TextAppearance_AppCompat_Small)
            this.text = text
            setTextColor(context.getColorFromAttr(R.attr.primaryColorTrans80))
            setPadding(24.dpToPx(), 0, 24.dpToPx(), 8.dpToPx())
            ViewCompat.setAccessibilityHeading(this, true)
        })
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
        // Nested-scroll wrapper + expanded state: on landscape heights the
        // taller sheets (screen-time graph + top apps) exceed the screen, and
        // the default landscape peek opens sheets half-hidden. Short menus are
        // unaffected — they still open at content height.
        val scroller = androidx.core.widget.NestedScrollView(context).apply {
            addView(container)
        }
        // Edge-to-edge dialog window: keep the last row above the navigation bar
        val basePaddingBottom = container.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, basePaddingBottom + bottom)
            insets
        }
        dialog.setContentView(scroller)
        dialog.transparentSheetFrame()
        dialog.disableAnimationsOnEink()
        dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
        return dialog
    }
}
