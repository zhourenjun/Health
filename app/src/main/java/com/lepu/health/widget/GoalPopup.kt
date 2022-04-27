package com.lepu.health.widget

import android.content.Context
import com.lepu.health.R
import com.lepu.health.databinding.PopupFitnessGoalBinding
import com.lepu.health.util.Constant
import com.lepu.health.util.Preference
import com.lepu.health.util.click
import com.lepu.health.util.setVisible
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.core.BottomPopupView

/**
 *
 * 运动目标设置
 * zrj 2020/11/5
 */
class GoalPopup(context: Context) : BottomPopupView(context) {

    private lateinit var  binding: PopupFitnessGoalBinding

    override fun getImplLayoutId() = R.layout.popup_fitness_goal

    private var units: Int by Preference(Constant.UNITS, 0)

    private val s0 = listOf(
        context.getString(R.string.none),
        context.getString(R.string.distance),
        context.getString(R.string.time3),
        context.getString(R.string.calories)
    )

    private val s1 = listOf(
        context.getString(R.string.custom),
        "1 ${context.getString(if (units == 0) R.string.km else R.string.mile)}",
        "3 ${context.getString(if (units == 0) R.string.km else R.string.mile)}",
        "5 ${context.getString(if (units == 0) R.string.km else R.string.mile)}",
        "10 ${context.getString(if (units == 0) R.string.km else R.string.mile)}",
        context.getString(R.string.half_marathon),
        context.getString(R.string.marathon)
    )

    private val value1 =
        listOf(0, 1, 3, 5, 10, if (units == 0) 21 else 13, if (units == 0) 42 else 26)

    private val s2 = listOf(
        context.getString(R.string.custom),
        "10 ${context.getString(R.string.min)}",
        "20 ${context.getString(R.string.min)}",
        "30 ${context.getString(R.string.min)}",
        "60 ${context.getString(R.string.min)}",
        "120 ${context.getString(R.string.min)}",
        "180 ${context.getString(R.string.min)}"
    )

    private val value2 = listOf(0, 10, 20, 30, 60, 120, 180)

    private val s3 = listOf(
        context.getString(R.string.custom),
        "100 ${context.getString(R.string.kcal)}",
        "200 ${context.getString(R.string.kcal)}",
        "300 ${context.getString(R.string.kcal)}",
        "500 ${context.getString(R.string.kcal)}",
        "600 ${context.getString(R.string.kcal)}",
        "800 ${context.getString(R.string.kcal)}"
    )

    private val value3 = listOf(0, 100, 200, 300, 500, 600, 800)

    private var index1 = 0
    private var index2 = 0

    override fun onCreate() {
        super.onCreate()
        binding = PopupFitnessGoalBinding.bind(popupImplView)
        binding.p1.setData(s0)
            .setOnSelectListener {
                binding.p2.setVisible(it > 0)
                if (it > 0) {
                    var temp2 = if (it == index1) {
                        when (index1) {
                            1 -> value1.indexOf(index2)
                            2 -> value2.indexOf(index2)
                            else -> value3.indexOf(index2)
                        }
                    } else {
                        2
                    }
                    if (temp2 < 0) {
                        temp2 = 0
                    }
                    binding.p2.setData(
                        when (it) {
                            1 -> s1
                            2 -> s2
                            else -> s3
                        }
                    ).setSelected(temp2)
                }
            }.setSelected(index1)

        binding.tvCancel.click { dismiss() }
        binding.tvSure.click {
            dismissWith {
                val temp1 = binding.p1.getSelected()
                if (temp1 > 0) {
                    val temp2 = binding.p2.getSelected()
                    if (temp2 == 0) { // 值自定义
                        val pop = XPopup.Builder(context)
                            .asCustom(CustomPopup(context)) as CustomPopup
                        pop.setData(temp1).setOnSelectListener { goalType, goalValue ->
                            onSelectListener?.invoke(goalType, goalValue)
                        }.show()
                    } else {
                        val value = when (temp1) {
                            1 -> value1[temp2]
                            2 -> value2[temp2]
                            else -> value3[temp2]
                        }
                        onSelectListener?.invoke(temp1, value)
                    }
                } else {
                    onSelectListener?.invoke(0, 0)
                }
            }
        }
    }

    fun setData(index1: Int, index2: Int): GoalPopup {
        this.index1 = index1
        this.index2 = index2
        return this
    }

    private var onSelectListener: ((goalType: Int, goalValue: Int) -> Unit)? = null

    fun setOnSelectListener(l: ((goalType: Int, goalValue: Int) -> Unit)): GoalPopup {
        this.onSelectListener = l
        return this
    }
}