package com.lepu.health.widget

import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.core.graphics.toColorInt
import com.lepu.health.util.dp
import com.lepu.health.util.sp

/**
  *
  * 公里标记
  * zrj 2020/11/19
 */
class NumDrawable(private val num: Int) : Drawable() {

    private val paint = Paint()
    private var mWidth = 12.dp

    init {
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL_AND_STROKE
        paint.color = "#B3000000".toColorInt()
    }

    override fun draw(canvas: Canvas) {
        canvas.drawCircle(mWidth / 2f, mWidth / 2f, mWidth / 2f - 1, paint)
        paint.color = "#ffffff".toColorInt()
        paint.textSize = 8.sp.toFloat()
        paint.textAlign = Paint.Align.CENTER
        var w = paint.measureText("$num") / 2
        if (w > mWidth / 4f) {
            w = mWidth / 4f
        }
        canvas.drawText("$num", mWidth / 2f, mWidth / 2f + w, paint)
    }

    override fun getIntrinsicWidth() = mWidth

    override fun getIntrinsicHeight() = mWidth

    override fun setAlpha(alpha: Int) {}

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setColorFilter(colorFilter: ColorFilter?) {}
}