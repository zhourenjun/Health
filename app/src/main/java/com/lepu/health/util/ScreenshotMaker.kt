package com.lepu.health.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
  *
  * 轨迹截图存储转换
  * zrj 2020/7/28
 */
object ScreenshotMaker {

    fun toBase64(bitmap: Bitmap, quality: Int): String {
        return Base64.encodeToString(getBytes(bitmap, quality), Base64.DEFAULT)
    }

    fun fromBase64(base64: String?): Bitmap {
        val decodedString = Base64.decode(base64, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
    }

    private fun getBytes(bm: Bitmap, quality: Int): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bm.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream)
        return byteArrayOutputStream.toByteArray()
    }
}