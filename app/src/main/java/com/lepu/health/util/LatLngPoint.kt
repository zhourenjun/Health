package com.lepu.health.util

import com.amap.api.maps.AMapUtils
import com.amap.api.maps.model.LatLng
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.sqrt


class LatLngPoint(
    /**
     * 用于记录每一个点的序号
     */
    var id: Int,
    /**
     * 每一个点的经纬度
     */
    var latLng: LatLng
) : Comparable<LatLngPoint> {
    override fun compareTo(other: LatLngPoint): Int {
        if (id < other.id) {
            return -1
        } else if (id > other.id) return 1
        return 0
    }
}


/**
 * 压缩经纬度点
 */
fun douglasCompress(mLineInit: List<LatLng>?, dMax: Double): List<LatLng> {
    requireNotNull(mLineInit) { "传入的经纬度坐标list == null" }

    val list = arrayListOf<LatLngPoint>()
    val size = mLineInit.size
    for (i in 0 until size) {
        list.add(LatLngPoint(i, mLineInit[i]))
    }

    val latLngPoints = compressLine(
        list.toArray(arrayOfNulls<LatLngPoint>(size)),
        arrayListOf<LatLngPoint>(),
        0,
        size - 1,
        dMax
    )
    latLngPoints.add(list[0])
    latLngPoints.add(list[size - 1])
    //对抽稀之后的点进行排序
    latLngPoints.sortWith { o1, o2 -> o1.compareTo(o2) }
    return latLngPoints.map { it.latLng }
}

/**
 * 根据最大距离限制，采用DP方法递归的对原始轨迹进行采样，得到压缩后的轨迹
 * @param original 原始经纬度坐标点数组
 * @param new      保持过滤后的点坐标数组
 * @param start    起始下标
 * @param end      结束下标
 * @param dMax     预先指定好的最大距离误差
 */
private fun compressLine(
    original: Array<LatLngPoint>, new: ArrayList<LatLngPoint>,
    start: Int, end: Int, dMax: Double
): ArrayList<LatLngPoint> {
    if (start < end) {
        //递归进行调教筛选
        var maxDist = 0.0
        var currentIndex = 0
        for (i in start + 1 until end) {
            val currentDist = distToSegment(original[start], original[end], original[i])
            if (currentDist > maxDist) {
                maxDist = currentDist
                currentIndex = i
            }
        }
        //若当前最大距离大于最大距离误差
        if (maxDist >= dMax) {
            //将当前点加入到过滤数组中
            new.add(original[currentIndex])
            //将原来的线段以当前点为中心拆成两段，分别进行递归处理
            compressLine(original, new, start, currentIndex, dMax)
            compressLine(original, new, currentIndex, end, dMax)
        }
    }
    return new
}

/**
 * 使用三角形面积（使用海伦公式求得）相等方法计算点pX到点pA和pB所确定的直线的距离
 * @param start  起始经纬度
 * @param end    结束经纬度
 * @param center 前两个点之间的中心点
 * @return 中心点到 start和end所在直线的距离
 */
private fun distToSegment(start: LatLngPoint, end: LatLngPoint, center: LatLngPoint): Double {
    val a = abs(AMapUtils.calculateLineDistance(start.latLng, end.latLng)).toDouble()
    val b = abs(AMapUtils.calculateLineDistance(start.latLng, center.latLng)).toDouble()
    val c = abs(AMapUtils.calculateLineDistance(end.latLng, center.latLng)).toDouble()
    val p = (a + b + c) / 2.0
    val s = sqrt(abs(p * (p - a) * (p - b) * (p - c)))
    return s * 2.0 / a
}

