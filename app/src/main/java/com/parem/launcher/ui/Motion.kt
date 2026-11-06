package com.parem.launcher.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.app.Dialog
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
        configureWindow(dialog, R.style.ParemPopupWindowAnimation)
        dialog.show()
        watchWindow(dialog, R.style.ParemPopupWindowAnimation)
    }

    fun configureWindow(dialog: Dialog, style: Int) {
        dialog.window?.setWindowAnimations(if (dialog.context.skipAnimations()) 0 else style)
    }

    /** Window animations do not obey animator scale, so update them while a dialog is open. */
    fun watchWindow(dialog: Dialog, style: Int) {
        val window = dialog.window ?: return
        val resolver = dialog.context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (dialog.isShowing) configureWindow(dialog, style)
            }
        }
        var registered = false
        fun register() {
            if (registered) return
            listOf(Settings.Global.WINDOW_ANIMATION_SCALE, Settings.Global.TRANSITION_ANIMATION_SCALE,
                Settings.Global.ANIMATOR_DURATION_SCALE).forEach {
                resolver.registerContentObserver(Settings.Global.getUriFor(it), false, observer)
            }
            registered = true
            configureWindow(dialog, style)
        }
        val decor = window.decorView
        decor.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = register()
            override fun onViewDetachedFromWindow(v: View) {
                if (registered) resolver.unregisterContentObserver(observer)
                registered = false
                v.removeOnAttachStateChangeListener(this)
            }
        })
        if (decor.isAttachedToWindow) register()
    }

    // Scale non-interactive content only: the row's touch matrix and bounds stay fixed.
    fun press(view: View) {
        if (view !is ViewGroup) return
        fun interactive(child: View): Boolean = child.isClickable || child.isLongClickable || child.isFocusable ||
            (child is ViewGroup && (0 until child.childCount).any { interactive(child.getChildAt(it)) })
        for (index in 0 until view.childCount) {
            val content = view.getChildAt(index)
            if (interactive(content)) continue
            content.isDuplicateParentStateEnabled = true
            fun scale(to: Float) = AnimatorSet().apply {
                playTogether(ObjectAnimator.ofFloat(content, View.SCALE_X, to), ObjectAnimator.ofFloat(content, View.SCALE_Y, to))
                duration = content.resources.getInteger(R.integer.motion_press).toLong()
                interpolator = DecelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        if (content.context.skipAnimations()) {
                            animation.cancel()
                            content.scaleX = 1f
                            content.scaleY = 1f
                        }
                    }
                })
            }
            content.stateListAnimator = StateListAnimator().apply {
                addState(intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed), scale(.97f))
                addState(intArrayOf(), scale(1f))
            }
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
        view.animate().alpha(.8f)
            .setDuration(view.resources.getInteger(R.integer.motion_press).toLong())
            .withEndAction {
                view.animate().alpha(1f)
                    .setDuration(view.resources.getInteger(R.integer.motion_press).toLong()).start()
            }.start()
    }
}
