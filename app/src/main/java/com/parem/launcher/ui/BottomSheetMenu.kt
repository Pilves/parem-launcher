package com.parem.launcher.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
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
    private val horizontalPadding = context.resources.getDimensionPixelSize(R.dimen.sheet_padding_horizontal)
    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_bottom_sheet)
        setPadding(0, 12.dpToPx(), 0, 24.dpToPx())
        addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(32.dpToPx(), 4.dpToPx()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 16.dpToPx()
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = GradientDrawable().apply {
                setColor(context.getColorFromAttr(R.attr.outlineColor))
                cornerRadius = 2.dpToPx().toFloat()
            }
        })
    }

    fun title(text: String): BottomSheetMenu {
        container.addView(TextView(context).apply {
            this.text = text
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Parem_Title)
            ViewCompat.setAccessibilityHeading(this, true)
            setPadding(horizontalPadding, 8.dpToPx(), horizontalPadding, 8.dpToPx())
        })
        return this
    }

    fun message(text: String): BottomSheetMenu {
        container.addView(TextView(context).apply {
            this.text = text
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Parem_Body)
            setTextColor(context.getColorFromAttr(R.attr.textColor))
            setPadding(horizontalPadding, 8.dpToPx(), horizontalPadding, 16.dpToPx())
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
        destructive: Boolean = false,
        primaryAction: Boolean = false,
        onClick: () -> Unit,
    ): BottomSheetMenu {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.resources.getDimensionPixelSize(R.dimen.touch_target_min)
            setPadding(horizontalPadding, 14.dpToPx(), horizontalPadding, 14.dpToPx())
            isFocusable = true
            if (context.isEinkDisplay()) {
                setBackgroundResource(R.drawable.bg_eink_control)
            } else if (primaryAction) {
                background = RippleDrawable(
                    ColorStateList.valueOf(context.getColorFromAttr(R.attr.onAccentColor) and 0x00ffffff or 0x33000000),
                    GradientDrawable().apply {
                        setColor(context.getColorFromAttr(R.attr.accentColor))
                        cornerRadius = context.resources.getDimension(R.dimen.corner_control)
                    }, null,
                )
            } else setBackgroundResource(context.selectableBackgroundRes())
            if (primaryAction) layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(horizontalPadding, 16.dpToPx(), horizontalPadding, 0)
            }
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
            TextViewCompat.setTextAppearance(this, if (primaryAction) R.style.TextAppearance_Parem_Label else R.style.TextAppearance_Parem_Body)
            this.text = text
            setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            setTextColor(context.getColorFromAttr(when {
                primaryAction && !context.isEinkDisplay() -> R.attr.onAccentColor
                destructive -> R.attr.destructiveColor
                dimmed -> R.attr.textSecondaryColor
                else -> R.attr.textColor
            }))
            setTypeface(typeface, if (checked == true) Typeface.BOLD else Typeface.NORMAL)
        })
        if (summary != null) labels.addView(TextView(context).apply {
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Parem_Caption)
            this.text = summary
            setTextColor(context.getColorFromAttr(R.attr.textSecondaryColor))
            setPadding(0, 4.dpToPx(), 0, 0)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (checked != null) row.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_check)
            imageTintList = android.content.res.ColorStateList.valueOf(context.getColorFromAttr(R.attr.accentColor))
            visibility = if (checked) View.VISIBLE else View.INVISIBLE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(24.dpToPx(), 24.dpToPx()).apply { marginStart = 8.dpToPx() })
        container.addView(row)
        return this
    }

    fun gap(): BottomSheetMenu = customView(View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, context.resources.getDimensionPixelSize(R.dimen.section_gap))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    })

    fun sectionLabel(text: String): BottomSheetMenu {
        gap()
        return customView(TextView(context).apply {
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Parem_Caption)
            this.text = text
            setTextColor(context.getColorFromAttr(R.attr.textSecondaryColor))
            setPadding(horizontalPadding, 0, horizontalPadding, 8.dpToPx())
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

    fun show(resizeForKeyboard: Boolean = false, scrollContent: Boolean = true): BottomSheetDialog {
        // Nested-scroll wrapper + expanded state: on landscape heights the
        // taller sheets (screen-time graph + top apps) exceed the screen, and
        // the default landscape peek opens sheets half-hidden. Short menus are
        // unaffected — they still open at content height.
        val content = if (scrollContent) androidx.core.widget.NestedScrollView(context).apply {
            addView(container)
        } else container
        // Edge-to-edge dialog window: keep the last row above the navigation bar
        val basePaddingBottom = container.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, basePaddingBottom + bottom)
            insets
        }
        dialog.setContentView(content)
        dialog.transparentSheetFrame()
        dialog.disableAnimationsOnEink()
        dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.window?.setDimAmount(0.5f)
        dialog.behavior.maxWidth = context.resources.getDimensionPixelSize(R.dimen.sheet_max_width)
        if (resizeForKeyboard) dialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
        )
        dialog.show()
        return dialog
    }
}
