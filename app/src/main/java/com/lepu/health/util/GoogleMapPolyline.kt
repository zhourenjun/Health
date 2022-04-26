package com.lepu.health.util

import android.graphics.Color
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 *
 * 负责在Google Maps上进行渐变的类[PolylineOptions]。 *计算颜色并绘制渐变的主要功能是[drawPolyline]
 */
class GoogleMapPolyline(private val map: GoogleMap, private val job: CompletableJob) :
    CoroutineScope {

    private var startColor = 0

    private var endColor = 0

    private var delayTime = 10L

    private var polylineOptions = PolylineOptions()
        .width(10F)

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    fun setPolylineOptions(polylineOptions: PolylineOptions): GoogleMapPolyline {
        this.polylineOptions = polylineOptions
        return this
    }

    fun setDelayTime(delayTime: Long): GoogleMapPolyline {
        this.delayTime = delayTime
        return this
    }

    fun setStartColor(startColor: Int): GoogleMapPolyline {
        this.startColor = startColor
        return this
    }

    fun setEndColor(endColor: Int): GoogleMapPolyline {
        this.endColor = endColor
        return this
    }

    fun clear() {
        map.clear()
    }


    /**
     * 根据起始和结束颜色生成渐变线，从而负责在[GoogleMap]多段线上绘制渐变。
     */
    fun drawPolyline(latLngRouteList: List<LatLng>, onDrawFinished: () -> (Unit)) {
        val gradientPoly: ArrayList<PolylineOptions> = ArrayList()

        /**
         * 从[startColor]中提取RGD颜色
         */
        val startRed = Color.red(startColor)
        val startGreen = Color.green(startColor)
        val startBlue = Color.blue(startColor)

        val endRed = Color.red(endColor)
        val endGreen = Color.green(endColor)
        val endBlue = Color.blue(endColor)

        launch {
            /**
             * PolyUtil.simplify（）函数是一个实用程序函数，它以与原始数组相同的条件和相同的行为返回简化的LatLng数组，
             * 只是为了在时间和处理上更快地绘制点。
             * https://developers.google.com/maps/documentation/android-sdk/utility
             */
            val simplifiedList: List<LatLng> = PolyUtil.simplify(latLngRouteList, 1.0)
            val simplifiedListSize = simplifiedList.size.toFloat()

            /**
             *计算每种RGB颜色的步骤。
             */
            val redSteps = (endRed - startRed).toFloat() / 255 / simplifiedListSize
            val greenSteps = (endGreen - startGreen).toFloat() / 255 / simplifiedListSize
            val blueSteps = (endBlue - startBlue).toFloat() / 255 / simplifiedListSize
            val builder = LatLngBounds.Builder()
            /**
             * 循环访问简化列表以包括预定义的* LatLngBounds构建器上的每个点，并开始为每个点提取其渐变色点。
             */
            for (index in 0 until simplifiedList.size - 1) {
                builder.include(simplifiedList[index])
                /**
                 *通过获取每种开始的RGB颜色并将它们除以255，来生成用于渐变的RGB颜色。然后获取结果并将其加到RGB阶跃颜色与for循环索引（简化列表）的乘积中。
                 */
                val redGradientColor = (startRed.toFloat() / 255) + (redSteps * index)
                val greenGradientColor = (startGreen.toFloat() / 255) + (greenSteps * index)
                val blueGradientColor = (startBlue.toFloat() / 255) + (blueSteps * index)

                /**
                 * 然后生成全彩。
                 */
                val gradientColor = getRGBColor(
                    red = redGradientColor,
                    green = greenGradientColor,
                    blue = blueGradientColor
                )
                /**
                 *并将其添加到[PolylineOptions]的gradientPoly数组中
                 */
                gradientPoly.add(
                    copyPolylineOptions(polylineOptions)
                        .color(gradientColor)
                        .add(simplifiedList[index])
                        .add(simplifiedList[index + 1])
                )
            }
            /**
             *然后，将每个[polylineOptions]（它们存储在* [map]上的gradientPoly中）加上设置的延迟。
             */
            withContext(Dispatchers.Main) {
                setZoomingOnMap(false)
                gradientPoly.forEach { polylineOption ->
                    map.addPolyline(polylineOption)
                    delay(delayTime)
                }
                setZoomingOnMap(true)
                onDrawFinished()
            }
        }
    }

    /**
     *getRGBColor（）与[Color.rgb]的功能相同。 *我在这里执行它，因为它是在API级别26中添加的。
     * 从[0..1]范围内的红色，绿色，蓝色浮点组成的彩色int。
     */
    private fun getRGBColor(red: Float, green: Float, blue: Float): Int {
        return -0x1000000 or
                ((red * 255.0f + 0.5f).toInt() shl 16) or
                ((green * 255.0f + 0.5f).toInt() shl 8) or
                (blue * 255.0f + 0.5f).toInt()
    }

    /**
     * 从[PolylineOptions]创建一个对象，集成商将其设置为添加到* gradientPoly数组中。
     */
    private fun copyPolylineOptions(originPolylineOptions: PolylineOptions): PolylineOptions {
        with(originPolylineOptions) {
            val copyPolylineOptions = PolylineOptions()
            copyPolylineOptions.width(width)
            copyPolylineOptions.color(color)
            copyPolylineOptions.zIndex(zIndex)
            copyPolylineOptions.visible(isVisible)
            copyPolylineOptions.geodesic(isGeodesic)
            copyPolylineOptions.clickable(isClickable)
            copyPolylineOptions.startCap(startCap)
            copyPolylineOptions.endCap(endCap)
            copyPolylineOptions.jointType(jointType)
            copyPolylineOptions.pattern(pattern)
            return copyPolylineOptions
        }

    }

    /**
     *这是一种可选方法，可禁用与[map]的用户交互*直到在[map]上成功绘制渐变，然后所有用户交互*将恢复启用状态。
     */
    private fun setZoomingOnMap(isEnabled: Boolean) {
        map.uiSettings.apply {
            isScrollGesturesEnabled = isEnabled
            isScrollGesturesEnabledDuringRotateOrZoom = isEnabled
            isZoomGesturesEnabled = isEnabled
        }
    }
}


inline fun GoogleMap.zoomCameraWithAnimation(
    cameraUpdate: CameraUpdate,
    crossinline onFinishAnimation: () -> Unit
) {
    setOnMapLoadedCallback {
        animateCamera(cameraUpdate, 1000, object : GoogleMap.CancelableCallback {
            override fun onFinish() {
                onFinishAnimation.invoke()
            }

            override fun onCancel() {

            }
        }
        )
    }
}