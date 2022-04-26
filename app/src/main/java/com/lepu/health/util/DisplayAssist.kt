@file:Suppress("unused")

package com.lepu.health.util

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.renderscript.Int2
import android.view.Surface

object DisplayAssist {
    /**
     * 以下列常数之一返回设备的方向
     * [Surface.ROTATION_0], [Surface.ROTATION_90], [Surface.ROTATION_180], [Surface.ROTATION_270].
     */
    fun orientation(context: Context): Int {
        return context.windowManager.defaultDisplay.rotation
    }

    fun getRealArea(context: Context): Int2 {
        val display = context.windowManager.defaultDisplay
        val realScreenSize = Point()

        display.getRealSize(realScreenSize)
        return Int2(realScreenSize.x, realScreenSize.y)
    }

    fun getUsableArea(context: Context): Int2 {
        val display = context.windowManager.defaultDisplay
        val appUsableSize = Point()

        display.getSize(appUsableSize)
        return Int2(appUsableSize.x, appUsableSize.y)
    }

    fun getDisplayOffsets(context: Context): Int2 {
        val display = context.windowManager.defaultDisplay

        val appUsableSize = Point()
        val realScreenSize = Point()

        display.getRealSize(realScreenSize)
        display.getSize(appUsableSize)

        return Int2(realScreenSize.x - appUsableSize.x, realScreenSize.y - appUsableSize.y)
    }

    /**
     * 计算当前导航栏大小及其当前位置。
     * 大小存储在Point类中。
     */
    fun getNavigationBarSize(context: Context): Pair<NavBarPosition, Int2> {
        val display = context.windowManager.defaultDisplay

        val appUsableSize = Point()
        val realScreenSize = Point()

        display.getRealSize(realScreenSize)
        display.getSize(appUsableSize)
        val rotation = display.rotation

        //右侧的导航栏
        if (appUsableSize.x < realScreenSize.x) {
            //应用仅支持手机，因此不应存在方向为0或180的情况
            val position = if (rotation == Surface.ROTATION_90 || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                NavBarPosition.RIGHT
            } else {
                NavBarPosition.LEFT
            }

            val dimension = Int2(realScreenSize.x - appUsableSize.x, appUsableSize.y)

            return Pair(position, dimension)
        }

        //底部的导航栏
        return if (appUsableSize.y < realScreenSize.y) {
            Pair(NavBarPosition.BOTTOM, Int2(appUsableSize.x, realScreenSize.y - appUsableSize.y))
        } else {
            Pair(NavBarPosition.UNKNOWN, Int2())
        }
    }

    @Suppress("MagicNumber")
    fun getStatusBarHeight(context: Context): Int {
        val resources = context.resources
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return when {
            resourceId > 0 -> resources.getDimensionPixelSize(resourceId)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> 24.dp
            else -> 25.dp
        }
    }

    fun calculateNoOfColumns(context: Context, columnWidthDp: Float): Int {
        val displayMetrics = context.resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        return (screenWidthDp / columnWidthDp + 0.5).toInt()
    }

    fun getDisplayDensity(context: Context): Float {
        return context.resources.displayMetrics.density
    }
}


/**
 * 枚举有关屏幕上导航栏位置的信息
 */
enum class NavBarPosition {
    BOTTOM,
    LEFT,
    RIGHT,
    UNKNOWN
}

