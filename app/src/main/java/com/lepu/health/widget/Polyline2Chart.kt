package com.lepu.health.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.lepu.health.R
import com.lepu.health.util.colorCompat
import com.lepu.health.util.sp
import kotlin.math.absoluteValue

/**
 *
 * 图表
 * zrj 2020/11/9
 */
class Polyline2Chart(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    //屏幕宽高
    private var scrWidth = 0f
    private var scrHeight = 0f
    private var data = mutableListOf<Float>()
    private lateinit var paintLine: Paint
    private lateinit var paintPolyline: Paint //折线
    private lateinit var paintPolyShadow: Paint //折线阴影
    private var mLinePath: Path  //折线路径
    private var mPercent = 0f //动画进度

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        mLinePath = Path()
        initPaint()
    }

    /**
     * 初始化画笔
     */
    private fun initPaint() {
        paintLine = Paint()
        paintLine.isAntiAlias = true
        paintLine.style = Paint.Style.FILL
        paintLine.strokeWidth = 1f
        paintLine.textSize = 12f.sp

        paintPolyShadow = Paint()
        paintPolyShadow.style = Paint.Style.FILL

        paintPolyline = Paint()
        paintPolyline.style = Paint.Style.FILL

        paintPolyline.strokeWidth = 2f
        paintPolyline.isAntiAlias = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        scrWidth = width.toFloat()
        scrHeight = height.toFloat()
        ySpacing = scrHeight / 6f //y轴分6份
        xSpacing = scrWidth / (data.size - 1)
    }


    private var ySpacing = 0f //高度分割份数后间距
    private var xSpacing = 0f //x轴柱子分割份数后间距

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawGreyPolyline(canvas)
        drawAnimPolyline(canvas)
    }

    private fun drawGreyPolyline(canvas: Canvas) {
        paintPolyline.color = context.colorCompat(R.color.ffffff_1c293c)
        var x0: Float
        var x1: Float
        var y0: Float
        var y1: Float
        val yMin = yMax - yStep * 4
        //画折线
        data.forEachIndexed { index, i ->
            if (index < data.size - 1) {
                x0 = xSpacing * index
                y0 = scrHeight - ySpacing * ((i - yMin).absoluteValue / yStep)
                x1 = xSpacing * (index + 1)
                y1 = scrHeight - ySpacing * ((data[index + 1] - yMin).absoluteValue / yStep)
                if (data[index + 1] > 0) {
                    canvas.drawLine(x0, y0, x1, y1, paintPolyline)
                } else {
                    if (index == 0 || data[index - 1] < 0) {
                        //单点圆
                        canvas.drawCircle(x0, y0, 4f, paintPolyline)
                    }
                }
            }
        }
    }

    private fun drawAnimPolyline(canvas: Canvas) {
        var x0: Float
        var x1: Float
        var y0: Float
        var y1: Float
        val yMin = yMax - yStep * 4
        //画折线阴影
        data.forEachIndexed { index, i ->
            if (index < (data.size - 1) * mPercent) {
                x0 = xSpacing * index
                y0 = scrHeight - ySpacing * ((i - yMin).absoluteValue / yStep)
                x1 = xSpacing * (index + 1)
                y1 = scrHeight - ySpacing * ((data[index + 1] - yMin).absoluteValue / yStep)
                if (data[index + 1] > 0) {
                    drawPolyShadow(x0, y0, x1, y1, canvas)
                } else {
                    //单点圆
                    if (index == 0 || data[index - 1] < 0) {
                        drawPolyShadow(x0 - 4f, y0, x0 + 4f, y0, canvas)
                    }
                }
            }
        }

        paintPolyline.color = context.colorCompat(R.color.feba26_f3b42b)
        //画折线
        data.forEachIndexed { index, i ->
            if (index < (data.size - 1) * mPercent) {
                x0 = xSpacing * index
                y0 = scrHeight - ySpacing * ((i - yMin).absoluteValue / yStep)
                x1 = xSpacing * (index + 1)
                y1 = scrHeight - ySpacing * ((data[index + 1] - yMin).absoluteValue / yStep)

                if (data[index + 1] > 0) {
                    canvas.drawLine(x0, y0, x1, y1, paintPolyline)

                } else {
                    if (index == 0 || data[index - 1] < 0) {
                        //单点圆
                        canvas.drawCircle(x0, y0, 4f, paintPolyline)
                    }
                }
                if (index == ((data.size - 1) * mPercent).toInt()) {
                    canvas.drawLine(x1, y1, x1, scrHeight, paintPolyline)
                    paintPolyline.color = context.colorCompat(R.color.color_on_primary)
                    canvas.drawCircle(x1, y1, 10f, paintPolyline)
                    paintPolyline.color = context.colorCompat(R.color.feba26_f3b42b)
                    canvas.drawCircle(x1, y1, 6f, paintPolyline)
                }
            }
        }
    }


    private fun drawPolyShadow(x0: Float, y0: Float, x1: Float, y1: Float, canvas: Canvas) {
        mLinePath.reset()
        mLinePath.moveTo(x0, scrHeight)
        mLinePath.lineTo(x0, y0)
        mLinePath.lineTo(x1, y1)
        mLinePath.lineTo(x1, scrHeight)
        mLinePath.lineTo(x0, scrHeight)
        mLinePath.close()
        val mLinearGradient = LinearGradient(
            0f,
            ySpacing * 3f - ySpacing * ((data.maxOrNull()?.minus(yMax - yStep * 4))?.div(yStep)
                ?: 0f),
            0f,
            scrHeight,
            intArrayOf(
                context.colorCompat(R.color.translucent_white_60),
                context.colorCompat(R.color.translucent_white_1A)
            ),
            null,
            Shader.TileMode.MIRROR
        )
        paintPolyShadow.shader = mLinearGradient
        canvas.drawPath(mLinePath, paintPolyShadow)
    }


    private var yMax = 0
    private var yStep = 0
    private var xStep = 0
    fun setValue(value: MutableList<Float>): Polyline2Chart {
        data.clear()
        data.addAll(value)
        yMax = ((value.maxOrNull() ?: 0f) + 10f).toInt()
        yStep = (1 + (yMax - (value.filter { it > 0 }.minOrNull() ?: 0f)) / 4).toInt()
        val temp = value.size / 12 / 5   //一分钟12个值（5秒采集一次） x轴最小间隔5分钟
        xStep = if (temp < 5) {
            5
        } else {
            (temp / 5) * 5
        }
        return this
    }

     fun setProgress(mPercent:Float){
         this.mPercent = mPercent
         postInvalidate()
     }
}

