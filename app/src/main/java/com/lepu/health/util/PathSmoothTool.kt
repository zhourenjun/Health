@file:Suppress("unused")

package com.lepu.health.util

import com.amap.api.maps.AMapUtils
import com.amap.api.maps.model.LatLng
import java.util.*
import kotlin.math.sqrt

/**
 * 轨迹优化工具类
 */
class PathSmoothTool {
    var intensity = 4
    var threshhold = 0.3f
    private var mNoiseThreshHold = 10f
    fun setNoiseThreshHold(mNoiseThreshHold: Float) {
        this.mNoiseThreshHold = mNoiseThreshHold
    }

    /**
     * 轨迹平滑优化
     */
    fun pathOptimize(originList: List<LatLng>): List<LatLng> {
        val list = removeNoisePoint(originList) //去噪
        val afterList = kalManFilterPath(list, intensity) //滤波
        return reducerVerticalThreshold(afterList, threshhold)
    }

    /**
     * 轨迹线路滤波
     */
    fun kalManFilterPath(originList: List<LatLng>): List<LatLng> {
        return kalManFilterPath(originList, intensity)
    }

    /**
     * 轨迹去噪，删除垂距大于20m的点
     */
    private fun removeNoisePoint(originList: List<LatLng>): List<LatLng> {
        return reduceNoisePoint(originList, mNoiseThreshHold)
    }

    /**
     * 单点滤波
     * @param lastLoc 上次定位点坐标
     * @param curLoc 本次定位点坐标
     * @return 滤波后本次定位点坐标值
     */
    fun kalManFilterPoint(lastLoc: LatLng, curLoc: LatLng): LatLng {
        return kalManFilterPoint(lastLoc, curLoc, intensity)
    }

    /**
     * 轨迹抽稀
     * @param inPoints 待抽稀的轨迹list，至少包含两个点，删除垂距小于mThreshhold的点
     * @return 抽稀后的轨迹list
     */
    fun reducerVerticalThreshold(inPoints: List<LatLng>): List<LatLng> {
        return reducerVerticalThreshold(inPoints, threshhold)
    }
    /** */
    /**
     * 轨迹线路滤波
     * @param originList 原始轨迹list,list.size大于2
     * @param intensity 滤波强度（1—5）
     */
    private fun kalManFilterPath(originList: List<LatLng>, intensity: Int): List<LatLng> {
        val kalManFilterList: MutableList<LatLng> = ArrayList()
        if (originList.size <= 2) return kalManFilterList
        initial() //初始化滤波参数
        var latLng: LatLng?
        var lastLoc = originList[0]
        kalManFilterList.add(lastLoc)
        for (i in 1 until originList.size) {
            val curLoc = originList[i]
            latLng = kalManFilterPoint(lastLoc, curLoc, intensity)
            kalManFilterList.add(latLng)
            lastLoc = latLng
        }
        return kalManFilterList
    }

    /**
     * 单点滤波
     * @param lastLoc 上次定位点坐标
     * @param curLoc 本次定位点坐标
     * @param intensity 滤波强度（1—5）
     * @return 滤波后本次定位点坐标值
     */
    private fun kalManFilterPoint(lastLoc: LatLng, curLoc: LatLng, intensity: Int): LatLng {
        var tempLoc = curLoc
        var temp = intensity
        if (pdelt_x == 0.0 || pdelt_y == 0.0) {
            initial()
        }
        var kalManLatLng = tempLoc
        if (temp < 1) {
            temp = 1
        } else if (temp > 5) {
            temp = 5
        }
        for (j in 0 until temp) {
            kalManLatLng = kalManFilter(
                lastLoc.longitude,
                tempLoc.longitude,
                lastLoc.latitude,
                curLoc.latitude
            )
            tempLoc = kalManLatLng
        }
        return kalManLatLng
    }

    /***************************卡尔曼滤波开始 */
    private var lastLocation_x //上次位置
            = 0.0
    private var currentLocation_x //这次位置
            = 0.0
    private var lastLocation_y //上次位置
            = 0.0
    private var currentLocation_y //这次位置
            = 0.0
    private var estimate_x //修正后数据
            = 0.0
    private var estimate_y //修正后数据
            = 0.0
    private var pdelt_x //自预估偏差
            = 0.0
    private var pdelt_y //自预估偏差
            = 0.0
    private var mdelt_x //上次模型偏差
            = 0.0
    private var mdelt_y //上次模型偏差
            = 0.0
    private var gauss_x //高斯噪音偏差
            = 0.0
    private var gauss_y //高斯噪音偏差
            = 0.0
    private var kalmanGain_x //卡尔曼增益
            = 0.0
    private var kalmanGain_y //卡尔曼增益
            = 0.0
    private val m_R = 0.0
    private val m_Q = 0.0

    //初始模型
    private fun initial() {
        pdelt_x = 0.001
        pdelt_y = 0.001
        mdelt_x = 5.698402909980532E-4
        mdelt_y = 5.698402909980532E-4
    }

    private fun kalManFilter(
        oldValue_x: Double,
        value_x: Double,
        oldValue_y: Double,
        value_y: Double
    ): LatLng {
        lastLocation_x = oldValue_x
        currentLocation_x = value_x
        gauss_x = sqrt(pdelt_x * pdelt_x + mdelt_x * mdelt_x) + m_Q //计算高斯噪音偏差
        kalmanGain_x =
            sqrt(gauss_x * gauss_x / (gauss_x * gauss_x + pdelt_x * pdelt_x)) + m_R //计算卡尔曼增益
        estimate_x = kalmanGain_x * (currentLocation_x - lastLocation_x) + lastLocation_x //修正定位点
        mdelt_x = sqrt((1 - kalmanGain_x) * gauss_x * gauss_x) //修正模型偏差
        lastLocation_y = oldValue_y
        currentLocation_y = value_y
        gauss_y = sqrt(pdelt_y * pdelt_y + mdelt_y * mdelt_y) + m_Q //计算高斯噪音偏差
        kalmanGain_y =
            sqrt(gauss_y * gauss_y / (gauss_y * gauss_y + pdelt_y * pdelt_y)) + m_R //计算卡尔曼增益
        estimate_y = kalmanGain_y * (currentLocation_y - lastLocation_y) + lastLocation_y //修正定位点
        mdelt_y = sqrt((1 - kalmanGain_y) * gauss_y * gauss_y) //修正模型偏差
        return LatLng(estimate_y, estimate_x)
    }
    /***************************卡尔曼滤波结束 */


    /***************************抽稀算法 */
    private fun reducerVerticalThreshold(inPoints: List<LatLng>, threshHold: Float): List<LatLng> {

        if (inPoints.size <= 2) {
            return inPoints
        }
        val ret: MutableList<LatLng> = ArrayList()
        for (i in inPoints.indices) {
            val pre = getLastLocation(ret)
            val cur = inPoints[i]
            if (pre == null || i == inPoints.size - 1) {
                ret.add(cur)
                continue
            }
            val next = inPoints[i + 1]
            val distance = calculateDistanceFromPoint(cur, pre, next)
            if (distance > threshHold) {
                ret.add(cur)
            }
        }
        return ret
    }

    /***************************抽稀算法结束 */
    private fun reduceNoisePoint(inPoints: List<LatLng>, threshHold: Float): List<LatLng> {
        if (inPoints.size <= 2) {
            return inPoints
        }
        val ret: MutableList<LatLng> = ArrayList()
        for (i in inPoints.indices) {
            val pre = getLastLocation(ret)
            val cur = inPoints[i]
            if (pre == null || i == inPoints.size - 1) {
                ret.add(cur)
                continue
            }
            val next = inPoints[i + 1]
            val distance = calculateDistanceFromPoint(cur, pre, next)
            if (distance < threshHold) {
                ret.add(cur)
            }
        }
        return ret
    }

    companion object {
        private fun getLastLocation(oneGraspList: List<LatLng>?): LatLng? {
            if (oneGraspList == null || oneGraspList.isEmpty()) {
                return null
            }
            val locListSize = oneGraspList.size
            return oneGraspList[locListSize - 1]
        }

        /**
         * 计算当前点到线的垂线距离
         * @param p 当前点
         * @param begin 线的起点
         * @param end 线的终点
         */
        private fun calculateDistanceFromPoint(p: LatLng, begin: LatLng, end: LatLng): Double {
            val a = p.longitude - begin.longitude
            val b = p.latitude - begin.latitude
            val c = end.longitude - begin.longitude
            val d = end.latitude - begin.latitude
            val dot = a * c + b * d
            val lenSq = c * c + d * d
            val param = dot / lenSq
            val xx: Double
            val yy: Double
            if (param < 0 || (begin.longitude == end.longitude && begin.latitude == end.latitude)) {
                xx = begin.longitude
                yy = begin.latitude
            } else if (param > 1) {
                xx = end.longitude
                yy = end.latitude
            } else {
                xx = begin.longitude + param * c
                yy = begin.latitude + param * d
            }
            return AMapUtils.calculateLineDistance(p, LatLng(yy, xx)).toDouble()
        }
    }
}