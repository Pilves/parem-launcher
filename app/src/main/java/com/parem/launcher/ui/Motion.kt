package com.parem.launcher.ui

import android.animation.AnimatorSet
import android.app.Dialog
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import com.parem.launcher.R
import com.parem.launcher.helper.skipAnimations

/** Visual feedback only: never owns a touch listener or delays an action. */
internal object Motion {
    fun showPopup(dialog: Dialog) {
        dialog.window?.setWindowAnimations(if (dialog.context.skipAnimations()) 0 else R.style.ParemPopupWindowAnimation)
        dialog.show()
    }

    fun press(view: View) {
        if (view.context.skipAnimations()) return
        fun scale(to: Float) = AnimatorSet().apply {
            playTogether(ObjectAnimator.ofFloat(view, View.SCALE_X, to), ObjectAnimator.ofFloat(view, View.SCALE_Y, to))
            duration = view.resources.getInteger(R.integer.motion_press).toLong()
            interpolator = DecelerateInterpolator()
        }
        view.stateListAnimator = StateListAnimator().apply {
            addState(intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed), scale(.97f))
            addState(intArrayOf(), scale(1f))
        }
    }

    fun pressChildren(parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.isClickable) press(child)
            if (child is ViewGroup) pressChildren(child)
        }
    }

    fun stagger(rows: List<View>) {
        if (rows.isEmpty() || rows[0].context.skipAnimations()) return
        val duration = rows[0].resources.getInteger(R.integer.motion_medium).toLong()
        val interval = rows[0].resources.getInteger(R.integer.motion_stagger).toLong()
        rows.filter { it.visibility == View.VISIBLE }.take(6).forEachIndexed { index, row ->
            row.animate().cancel()
            row.alpha = 0f
            row.animate().alpha(1f).setStartDelay(interval * index).setDuration(duration)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    fun copied(view: View) {
        if (view.context.skipAnimations()) return
        view.animate().cancel()
        view.animate().scaleX(.97f).scaleY(.97f)
            .setDuration(view.resources.getInteger(R.integer.motion_press).toLong())
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f)
                    .setDuration(view.resources.getInteger(R.integer.motion_press).toLong()).start()
            }.start()
    }
}
