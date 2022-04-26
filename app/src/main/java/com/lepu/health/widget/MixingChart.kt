package com.lepu.health.widget

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.annotation.IntDef
import com.lepu.health.R
import com.lepu.health.util.colorCompat
import com.lepu.health.util.sp
import kotlin.math.abs

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
    private var mPercent = 0f

    @Type.Project
    private var type = 0  //对应的项目

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
        when (type) {
            Type.EXERCISE_RECORDS -> drawPoints(canvas)
            Type.OTHER -> drawOther(canvas)
        }
    }

    private fun drawPoints(canvas: Canvas) {
        val latitudeMax = points.map { it[0] }.maxOrNull() ?: 0.0
        val latitudeMin = points.map { it[0] }.minOrNull() ?: 0.0
        val longitudeMax = points.map { it[1] }.maxOrNull() ?: 0.0
        val longitudeMin = points.map { it[1] }.minOrNull() ?: 0.0
        val temp0 = latitudeMax - latitudeMin
        val temp1 = longitudeMax - longitudeMin
        val pointX = points.map { scrWidth * (1 - (longitudeMax - it[1]) / temp1) }
        val pointY = points.map { scrHeight * (latitudeMax - it[0]) / temp0 }
        val path = Path()
        paintPolyline.color = Color.parseColor("#54c054")
        paintPolyline.style = Paint.Style.STROKE
        //画折线

        var offsetX: Float
        var offsetY: Float

        points.forEachIndexed { index, _ ->
            if (index < points.size * mPercent) {
                if (index < points.size - 1) {
                    path.moveTo(pointX[index].toFloat(), pointY[index].toFloat())
                    path.lineTo(pointX[index + 1].toFloat(), pointY[index + 1].toFloat())
                }
                if (index == 0 || index == points.size - 1) {
                    //单点圆
                    offsetX = if (abs(pointX[index].toFloat() - scrWidth) < 10) -5f else 5f
                    offsetY = if (abs(pointY[index].toFloat() - scrHeight) < 10) -5f else 5f
                    canvas.drawCircle(
                        pointX[index].toFloat() + offsetX,
                        pointY[index].toFloat() + offsetY,
                        4f,
                        paintPolyline
                    )
                }
            }
        }
        canvas.drawPath(path, paintPolyline)
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

    private fun startAnim() {
        val mAnimator = ValueAnimator.ofFloat(0f, 1f)
        mAnimator.duration = 1000L
        mAnimator.addUpdateListener {
            mPercent = it.animatedValue as Float
            postInvalidate()
        }
        mAnimator.start()
    }

    private var points = mutableListOf<List<Double>>()
    fun setExercise(value: List<List<Double>>) {
        type = Type.EXERCISE_RECORDS
        points.clear()
        points.addAll(value)
        startAnim()
    }

    private var max = 0
    private var min = 0
    fun setOther(min: Int, max: Int) {
        type = Type.OTHER
        this.min = min
        this.max = max
        postInvalidate()
    }
}


class Type {
    companion object {
        //先定义 常量
        const val EXERCISE_RECORDS = 0  //运动记录
        const val HEART_RATE = 1    //心率
        const val SLEEP = 2    //睡眠
        const val SPO2 = 3    //血氧
        const val BLOOD_PRESSURE = 4  //血压
        const val WEIGHT = 5  // 体重
        const val STRESS = 6  //压力
        const val BLOOD_SUGAR = 7 //血糖
        const val CYCLE_CALENDAR = 8 //生理周期
        const val OTHER = 9
    }

    //注解枚举
    @IntDef(
        EXERCISE_RECORDS, HEART_RATE, SLEEP, SPO2, BLOOD_PRESSURE, WEIGHT, STRESS, BLOOD_SUGAR,
        CYCLE_CALENDAR, OTHER
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class Project
}
