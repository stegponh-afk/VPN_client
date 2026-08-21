package com.netbridge.app.ui

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.view.View
import android.view.animation.LinearInterpolator
import com.google.android.material.button.MaterialButton
import com.netbridge.app.vpn.TunnelState

/**
 * Owns the connect button's three visual layers (button + spinner ring + pulse
 * ring) and animates the transition between [TunnelState]s: a rotating arc while
 * connecting, a "breathing" glow ring while connected, and a smooth background
 * color crossfade between the three states.
 */
class ButtonStateAnimator(
    private val button: MaterialButton,
    private val spinnerRing: View,
    private val pulseRing: View,
) {
    private var spinnerAnimator: ObjectAnimator? = null
    private var pulseAnimator: AnimatorSet? = null
    private var colorAnimator: ValueAnimator? = null
    private var currentColor: Int = disconnectedColor()

    fun setState(state: TunnelState) {
        when (state) {
            TunnelState.DISCONNECTED -> {
                stopSpinner()
                stopPulse()
                animateColorTo(disconnectedColor())
            }
            TunnelState.CONNECTING -> {
                stopPulse()
                startSpinner()
                animateColorTo(connectingColor())
            }
            TunnelState.CONNECTED -> {
                stopSpinner()
                startPulse()
                animateColorTo(connectedColor())
            }
        }
    }

    private fun startSpinner() {
        if (spinnerAnimator != null) return
        spinnerRing.visibility = View.VISIBLE
        spinnerRing.alpha = 1f
        spinnerAnimator = ObjectAnimator.ofFloat(spinnerRing, View.ROTATION, 0f, 360f).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopSpinner() {
        spinnerAnimator?.cancel()
        spinnerAnimator = null
        spinnerRing.visibility = View.INVISIBLE
    }

    private fun startPulse() {
        if (pulseAnimator != null) return
        pulseRing.visibility = View.VISIBLE
        pulseRing.scaleX = 0.85f
        pulseRing.scaleY = 0.85f
        pulseRing.alpha = 0.85f

        val scaleX = ObjectAnimator.ofFloat(pulseRing, View.SCALE_X, 0.85f, 1.35f)
        val scaleY = ObjectAnimator.ofFloat(pulseRing, View.SCALE_Y, 0.85f, 1.35f)
        val fade = ObjectAnimator.ofFloat(pulseRing, View.ALPHA, 0.85f, 0f)
        pulseAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, fade)
            duration = 1700
            interpolator = android.view.animation.DecelerateInterpolator()
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationEnd(animation: Animator) {
                    if (pulseAnimator != null) start()
                }
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.let { it.removeAllListeners(); it.cancel() }
        pulseAnimator = null
        pulseRing.visibility = View.INVISIBLE
    }

    private fun animateColorTo(target: Int) {
        colorAnimator?.cancel()
        val from = currentColor
        colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), from, target).apply {
            duration = 350
            addUpdateListener {
                val color = it.animatedValue as Int
                button.backgroundTintList = ColorStateList.valueOf(color)
                currentColor = color
            }
            start()
        }
    }

    private fun disconnectedColor() = android.graphics.Color.parseColor("#1B1F27")
    private fun connectingColor() = android.graphics.Color.parseColor("#2A2113")
    private fun connectedColor() = android.graphics.Color.parseColor("#123024")
}
