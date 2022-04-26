package com.lepu.health.widget

import android.content.Context
import android.util.AttributeSet
import com.lepu.health.R
import com.ycuwq.datepicker.WheelPicker

/**
 *
 * java类作用描述
 * zrj 2020/9/15
 */
class TextPicker @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : WheelPicker<String>(context, attrs, defStyleAttr) {
    private var mSelected = 0

    fun setSelected(selected: Int) :TextPicker{
        setSelected(selected, true)
        return this
    }

    private fun setSelected(selected: Int, smoothScroll: Boolean) {
        setCurrentPosition(selected, smoothScroll)
        mSelected = selected
    }

    private var onSelectListener: ((pos: Int) -> Unit)? = null

    fun setOnSelectListener(l: ((pos: Int) -> Unit)) :TextPicker{
        this.onSelectListener = l
        return this
    }

    fun setData(list: List<String>):TextPicker {
        dataList = list
        return this
    }

    init {
        dataList = arrayListOf(context.getString(R.string.male),context.getString(R.string.female))
        setSelected(mSelected, false)
        setOnWheelChangeListener { _: String, pos: Int ->
            mSelected = pos
            onSelectListener?.invoke(pos)
        }
    }

    fun getSelected() = mSelected
}