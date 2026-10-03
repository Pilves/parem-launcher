package com.parem.launcher.ui

import android.animation.Animator
import android.animation.ValueAnimator
import androidx.fragment.app.Fragment
import com.parem.launcher.helper.isSystemAnimationsDisabled

open class BaseFragment : Fragment() {

    // With system animations turned off, view Animation end callbacks (draw-driven) can
    // stall and leave the outgoing fragment's view stuck on screen (Olauncher #713).
    // An Animator completes via Choreographer even at duration 0, so substitute one.
    override fun onCreateAnimator(transit: Int, enter: Boolean, nextAnim: Int): Animator? {
        if (nextAnim != 0 && requireContext().isSystemAnimationsDisabled())
            return ValueAnimator.ofFloat(0f, 1f).setDuration(0)
        return null
    }
}
