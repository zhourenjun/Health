package com.lepu.health.widget

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import com.lepu.health.R
import com.lepu.health.databinding.PopupCustomBinding
import com.lepu.health.util.*
import com.lxj.xpopup.core.BottomPopupView
import java.util.*


/**
 *
 * 运动目标自定义
 * zrj 2020/11/5
 */
class CustomPopup(context: Context) : BottomPopupView(context) {
    private lateinit var  binding: PopupCustomBinding

    override fun getImplLayoutId() = R.layout.popup_custom
    private var goalValue: Int by Preference(Constant.GOAL_VALUE, 0)
    private var units: Int by Preference(Constant.UNITS, 0)
    private var index1 = 0

    @SuppressLint("SetTextI18n")
    override fun onCreate() {
        super.onCreate()
        binding = PopupCustomBinding.bind(popupImplView)
        if (goalValue > 0) {
            binding.et.hint = "$goalValue"
        }
        binding.et.filters = arrayOf<InputFilter>(LengthFilter(if (index1 == 1) 3 else 4))
        binding.tv.text = context.getString(
            when (index1) {
                1 -> R.string.distance
                2 -> R.string.time3
                else -> R.string.calories
            }
        )
        binding.tvHint.text = context.getString(
            when (index1) {
                1 -> if (units == 0) R.string.distance_km_goal_hint else R.string.distance_mile_goal_hint
                2 -> R.string.time_goal_hint
                else -> R.string.calories_goal_hint
            }
        )
        binding.et.setTextChangeListener {
            binding.mask.setVisible(it.isEmpty())
        }
        binding.tvC.click { dismiss() }
        binding.tvS.click {
            val txt = binding.et.text.toString().trim()
            if (txt.isEmpty()) {
                return@click
            }
            val value = txt.trim().toInt()
            when (index1) {
                1 -> { //公里
                    if (value < 1 || value > 100) {
                        context.toast(R.string.input_out_range)
                        return@click
                    }
                }
                2 -> { //时间
                    if (value < 10 || value > 1400) {
                        context.toast(R.string.input_out_range)
                        return@click
                    }
                }
                3 -> { //卡路里
                    if (value < 100 || value > 5000) {
                        context.toast(R.string.input_out_range)
                        return@click
                    }
                }
            }
            dismissWith {
                onSelectListener?.invoke(index1, value)
            }
        }
    }

    fun setData(index1: Int): CustomPopup {
        this.index1 = index1
        return this
    }

    private var onSelectListener: ((goalType: Int, goalValue: Int) -> Unit)? = null

    fun setOnSelectListener(l: ((goalType: Int, goalValue: Int) -> Unit)): CustomPopup {
        this.onSelectListener = l
        return this
    }
}