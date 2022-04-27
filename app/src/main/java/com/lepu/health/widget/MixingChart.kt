package com.lepu.health.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.lepu.health.R
import com.lepu.health.util.colorCompat
import com.lepu.health.util.sp

/**
 * 混合图表
 * zrj 2020/9/7
 */
class MixingChart(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    //屏幕宽高
    private var scrWidth = 0f
    private var scrHeight = 0f
    private lateinit var paintPolyline: Paint //心率折线
    private lateinit var paintPolyShadow: Paint //心率折线阴影
    private var mLinePath: Path  //折线路径
    private var mRectF: RectF
    private lateinit var mBgCirPaint: Paint
    private var mBgCirWidth = 28f//宽度


    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        mRectF = RectF()
        mLinePath = Path()
        initPaint()
    }

    /**
     * 初始化画笔
     */
    private fun initPaint() {
        paintPolyShadow = Paint()
        paintPolyShadow.style = Paint.Style.FILL

        paintPolyline = Paint()
        paintPolyline.style = Paint.Style.FILL
        paintPolyline.strokeWidth = 2f
        paintPolyline.textSize = 12f.sp
        paintPolyline.isAntiAlias = true
        paintPolyline.color = context.colorCompat(R.color.fc355c_fc3159)

        mBgCirPaint = Paint()
        mBgCirPaint.isAntiAlias = true
        mBgCirPaint.style = Paint.Style.STROKE
        mBgCirPaint.strokeWidth = mBgCirWidth
        mBgCirPaint.strokeCap = Paint.Cap.ROUND

    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        scrWidth = width.toFloat()
        scrHeight = height.toFloat()
        ySpacing = scrHeight / 8f //y轴分8份

        val minWidth =
            (w - paddingLeft - paddingRight - 2 * mBgCirWidth).coerceAtMost(h - paddingBottom - paddingTop - 2 * mBgCirWidth)
        val radius = minWidth / 2
        mRectF.left = w / 2 - radius - mBgCirWidth / 2
        mRectF.top = h / 2 - radius - mBgCirWidth / 2
        mRectF.right = w / 2 + radius + mBgCirWidth / 2
        mRectF.bottom = h / 2 + radius + mBgCirWidth / 2
    }

    private var ySpacing = 0f //高度分割份数后间距

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawOther(canvas)
    }


    private fun drawOther(canvas: Canvas) {
        val mLinearGradient = LinearGradient(
            0f, scrHeight / 2f - 5f, scrWidth, scrHeight / 2f + 5f,
            intArrayOf(
                Color.parseColor("#04d657"),
                Color.parseColor("#ebbd1f"),
                Color.parseColor("#e8361b")
            ), null, Shader.TileMode.MIRROR
        )
        val paintGradientLine = Paint()
        paintGradientLine.style = Paint.Style.FILL
        paintGradientLine.shader = mLinearGradient

        canvas.drawRoundRect(
            RectF(0f, scrHeight / 2f - 5f, scrWidth, scrHeight / 2f + 5f), 5f, 5f, paintGradientLine
        )

        paintPolyline.color = Color.parseColor("#04d657")
        paintPolyline.textAlign = Paint.Align.LEFT
        canvas.drawText(
            "${context.getString(R.string.slowest)} ${min / 60}'${min % 60}''",
            0f,
            scrHeight / 2f - 20f,
            paintPolyline
        )

        paintPolyline.color = Color.parseColor("#e8361b")
        paintPolyline.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            "${context.getString(R.string.fastest)} ${max / 60}'${max % 60}''",
            scrWidth,
            scrHeight / 2f - 20f,
            paintPolyline
        )
    }

    private var max = 0
    private var min = 0
    fun setOther(min: Int, max: Int) {
        this.min = min
        this.max = max
        postInvalidate()
    }
}


