package com.lepu.health.widget

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ScaleXSpan
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class LastSpacingTextView @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AppCompatTextView(
    context!!, attrs, defStyle
) {
    private var originalText: CharSequence = ""
    private val xScale = 1.0f // x轴缩放比例
    override fun setText(text: CharSequence, type: BufferType) {
        originalText = text
        applyLastLetterSpacing()
    }

    override fun getText(): CharSequence {
        return originalText
    }

    private fun applyLastLetterSpacing() {
        val builder = StringBuilder()
        for (element in originalText) {
            val c = "" + element
            builder.append(c)
        }
        builder.append("\u00A0") // 末端新增一个空格
        val finalText = SpannableString(builder.toString())
        // 为了让空格看起来很明显，我们对空格进行一定的缩放
        finalText.setSpan(
            ScaleXSpan(xScale),
            builder.toString().length - 1, builder.toString().length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        super.setText(finalText, BufferType.SPANNABLE)
    }
}