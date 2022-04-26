package com.lepu.health.widget

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
  *
  * 数字字体
  * zrj 2020/11/23
 */
class NumTextView(context: Context, attrs: AttributeSet?) : AppCompatTextView(context, attrs) {

    init {
        val customFont = FontCache.getTypeface("XType-Bold.otf", context)
        typeface = customFont
    }

}

object FontCache {
    private val fontCache: HashMap<String, Typeface?> = HashMap()
    fun getTypeface(fontName: String, context: Context): Typeface? {
        var typeface: Typeface? = fontCache[fontName]
        if (typeface == null) {
            typeface = try {
                Typeface.createFromAsset(context.assets, fontName)
            } catch (e: Exception) {
                return null
            }
            fontCache[fontName] = typeface
        }
        return typeface
    }
}