package com.lepu.health.widget

import android.content.Context
import com.lepu.health.R
import com.lepu.health.databinding.PopupFitnessGoalBinding
import com.lepu.health.util.Constant
import com.lepu.health.util.Preference
import com.lepu.health.util.click
import com.lepu.health.util.setVisible
import com.lxj.xpopup.core.BottomPopupView

/**
 *
 * 语音播报设置
 * zrj 2020/11/5
 */
class ReminderPopup(context: Context) : BottomPopupView(context) {
    private lateinit var  binding: PopupFitnessGoalBinding
    override fun getImplLayoutId() = R.layout.popup_fitness_goal
    private var units: Int by Preference(Constant.UNITS, 0)
    private val s0 = listOf(
        context.getString(R.string.by_distance),
        context.getString(R.string.by_time)
    )

    private val s1 = listOf(
        "1 ${context.getString(if (units == 0) R.string.km else R.string.mile)}",
        "2 ${context.getString(if (units == 0) R.string.km else R.string.mile)}",
        "3 ${context.getString(if (units == 0) R.string.km else R.string.mile)}"
    )
    private val value1 = listOf(1, 2, 3)

    private val s2 = listOf(
        "5 ${context.getString(R.string.min)}",
        "10 ${context.getString(R.string.min)}",
        "15 ${context.getString(R.string.min)}",
        "20 ${context.getString(R.string.min)}"
    )
    private val value2 = listOf(5, 10, 15, 20)

    private var index1 = 0
    private var index2 = 0

    override fun onCreate() {
        super.onCreate()
        binding = PopupFitnessGoalBinding.bind(this)
        binding.tv.text = context.getString(R.string.reminders_interval)
        binding.p1.setData(s0)
            .setSelected(index1)
            .setOnSelectListener {
                val temp2 = if (it == index1) {
                    when (index1) {
                        0 -> value1.indexOf(index2)
                        else -> value2.indexOf(index2)
                    }
                } else {
                    0
                }
                binding.p2.setData(if (it == 0) s1 else s2).setSelected(temp2)
            }
        binding.p2.setVisible(true)
        binding.p2.setData(if (index1 == 0) s1 else s2).setSelected(
            when (index1) {
                0 -> value1.indexOf(index2)
                else -> value2.indexOf(index2)
            }
        )
        binding.tvCancel.click { dismiss() }
        binding.tvSure.click {
            dismissWith {
                val temp1 = binding.p1.getSelected()
                var temp2 = binding.p2.getSelected()
                if (temp2 < 0) {
                    temp2 = 0
                }
                val value = if (temp1 == 0) value1[temp2] else value2[temp2]
                onSelectListener?.invoke(temp1, value)
            }
        }
    }

    fun setData(index1: Int, index2: Int): ReminderPopup {
        this.index1 = index1
        this.index2 = index2
        return this
    }

    private var onSelectListener: ((reminderType: Int, reminderValue: Int) -> Unit)? = null

    fun setOnSelectListener(l: ((reminderType: Int, reminderValue: Int) -> Unit)): ReminderPopup {
        this.onSelectListener = l
        return this
    }
}