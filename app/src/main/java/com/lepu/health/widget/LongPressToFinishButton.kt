package com.lepu.health.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs


/**
  *
  * 按钮长按结束动画
  * zrj 2020/8/1
 */
class LongPressToFinishButton : View {
    private var mLastMotionX = 0
    private var mLastMotionY = 0
    private val duration = 1000L
    private var roundWidth = 10 //圆环宽度
    private var isMoved = false
    private var progress = 0
    private lateinit var progressCirclePaint: Paint
    private lateinit var valueAnimator: ValueAnimator
    private var isPress = false

    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        progressCirclePaint = Paint()
        progressCirclePaint.style = Paint.Style.STROKE
        progressCirclePaint.color = Color.parseColor("#fb6522")
        progressCirclePaint.isAntiAlias = true
        progressCirclePaint.strokeWidth = roundWidth.toFloat()
        valueAnimator = ValueAnimator.ofInt(0, 100)
        valueAnimator.duration = duration
        valueAnimator.interpolator = LinearInterpolator()
        valueAnimator.addUpdateListener { valueAnimator: ValueAnimator ->
            progress = valueAnimator.animatedValue as Int
            postInvalidate()
            if (progress == 100) {
                postDelayed({
                    isPress = false
                    postInvalidate()
                    onFinishListener?.invoke()
                }, 50)
            }
        }
        valueAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                isPress = false
                postInvalidate()
            }
        })
    }

    private var onFinishListener: (() -> Unit)? = null

    fun setOnFinishListener(onFinishListener: () -> Unit) {
        this.onFinishListener = onFinishListener
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val center = width / 2
        val radius = center - roundWidth / 2 //圆环的半径
        canvas.save()
        val oval = RectF(
            (center - radius).toFloat(), (center - radius).toFloat(), (center
                    + radius).toFloat(), (center + radius).toFloat()
        )
        if (isPress) {
            canvas.drawArc(oval, -90f, 360 * progress / 100.toFloat(), false, progressCirclePaint)
        }
        canvas.restore()
    }

    private fun startAnim() {
        valueAnimator.start()
    }

    private fun cancelAnimation() {
        isPress = false
        valueAnimator.cancel()
        progress = 0
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val x = ev.x.toInt()
        val y = ev.y.toInt()
        when (ev.action) {
            MotionEvent.ACTION_DOWN ->  {
                mLastMotionX = x
                mLastMotionY = y
                isMoved = false
                isPress = true
                startAnim()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isMoved) {
                    if (abs(mLastMotionX - x) > TOUCH_SLOP || abs(mLastMotionY - y) > TOUCH_SLOP) {
                        isMoved = true
                        cancelAnimation()
                    }
                }
            }
            MotionEvent.ACTION_UP -> cancelAnimation()
        }
        return true
    }

    companion object {
        private const val TOUCH_SLOP = 20
    }
}