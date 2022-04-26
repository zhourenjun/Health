package com.lepu.health.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.customview.widget.ViewDragHelper
import com.lepu.health.R


/**
 *
 * 滑动解锁
 * zrj 2020/8/1
 */
class SlideLockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var mLockBtn: View

    private lateinit var mViewDragHelper: ViewDragHelper

    private fun init() {
        mViewDragHelper = ViewDragHelper.create(this, 1.0f, object : ViewDragHelper.Callback() {

            override fun tryCaptureView(child: View, pointerId: Int): Boolean {
                //判断能拽托的View，这里会遍历内部子控件来决定是否可以拽托，我们只需要滑块可以滑动
                return child == mLockBtn
            }

            override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
                //拽托子View横向滑动时回调，回调的left，则是可以滑动的左上角x坐标
                val min = paddingStart
                if (left < min) {
                    return min
                }
                val max = (measuredWidth - paddingEnd - mLockBtn.measuredWidth)
                if (left > max) {
                    return max
                }
                return left
            }

            override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
                //拽托子View纵向滑动时回调，锁定顶部padding距离即可，不能不复写，否则少了顶部的padding，位置就偏去上面了
                return paddingTop
            }

            override fun onViewCaptured(capturedChild: View, activePointerId: Int) {
                super.onViewCaptured(capturedChild, activePointerId)
                val parent = capturedChild.parent
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
                super.onViewReleased(releasedChild, xvel, yvel)

                if (releasedChild == mLockBtn) {
                    if (releasedChild.left <= measuredWidth * 2 / 3) {
                        mViewDragHelper.smoothSlideViewTo(mLockBtn, paddingStart, paddingTop)

                    } else {
                        //否则去到右边（宽度，减去padding和滑块宽度）
                        mViewDragHelper.smoothSlideViewTo(
                            mLockBtn,
                            measuredWidth - paddingEnd - mLockBtn.measuredWidth,
                            paddingTop
                        )
                        mCallback?.invoke()
                    }
                    invalidate()
                }
            }
        })
    }

    fun closeToggle() {
        mViewDragHelper.smoothSlideViewTo(mLockBtn, paddingLeft, paddingTop)
        invalidate()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        //找到需要拽托的滑块
        mLockBtn = findViewById(R.id.lock_btn)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        //将onInterceptTouchEvent委托给ViewDragHelper
        return mViewDragHelper.shouldInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        //将onTouchEvent委托给ViewDragHelper
        mViewDragHelper.processTouchEvent(event)
        return true
    }

    override fun computeScroll() {
        //判断是否移动到头了，未到头则继续
        if (mViewDragHelper.continueSettling(true)) {
            invalidate()
        } else {
            super.computeScroll()
        }
    }


    private var mCallback: (() -> Unit)? = null

    fun setCallback(callback: (() -> Unit)?) {
        mCallback = callback
    }

    init {
        init()
    }
}