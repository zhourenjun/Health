package com.lepu.health.util

import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.widget.TextView

/**
  *
  * 倒计时
  * zrj 2020/9/16
 */
object CountTimerUtil {
    // 默认计时
    private const val DEFAULT_REPEAT_COUNT = 4
    // 最后一秒显示的文本
    private const val LAST_SECOND_TEXT = "Go"
    // 当前的计时
    private var sCurCount = DEFAULT_REPEAT_COUNT
    fun <T : TextView> start(animationViewTv: T, animationState: AnimationState) {
        start(animationViewTv, DEFAULT_REPEAT_COUNT, animationState)
    }

    @Suppress("SameParameterValue")
    private fun <T : TextView> start(animationViewTv: T, repeatCount: Int, animationState: AnimationState) {

        // 设置计时
        sCurCount = repeatCount - 1
        animationViewTv.text = sCurCount.toString()
        animationViewTv.visibility = View.VISIBLE

        // 透明度渐变动画
        val alphaAnimation = AlphaAnimation(1f, 0f)
        alphaAnimation.repeatCount = sCurCount
        alphaAnimation.duration = 500
        // 缩放渐变动画
        val scaleAnimation = ScaleAnimation(
            0.1f, 1.3f, 0.1f, 1.3f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        scaleAnimation.repeatCount = sCurCount
        scaleAnimation.duration = 500
        scaleAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                animationState.start()
            }

            override fun onAnimationEnd(animation: Animation) {
                // 动画结束时，隐藏
                animationViewTv.visibility = View.GONE
                animationState.end()
            }

            override fun onAnimationRepeat(animation: Animation) {
                // 减秒
                --sCurCount
                // 设置文本
                if (sCurCount == 0) animationViewTv.text =
                    LAST_SECOND_TEXT else animationViewTv.text = sCurCount.toString()
                animationState.repeat()
            }
        })

        // 两个动画同时播放
        val animationSet = AnimationSet(true)
        animationSet.addAnimation(alphaAnimation)
        animationSet.addAnimation(scaleAnimation)
        animationViewTv.startAnimation(animationSet)
    }

    interface AnimationState {
        fun start()
        fun repeat()
        fun end()
    }
}