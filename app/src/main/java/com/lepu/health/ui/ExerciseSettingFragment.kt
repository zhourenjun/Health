@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.lepu.health.ui

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.navigation.fragment.findNavController
import com.gyf.immersionbar.ImmersionBar
import com.lepu.health.R
import com.lepu.health.base.BaseFragment
import com.lepu.health.base.bindView
import com.lepu.health.databinding.FragmentExerciseSettingBinding
import com.lepu.health.util.Constant
import com.lepu.health.util.Preference
import com.lepu.health.util.click
import com.lepu.health.util.setVisible
import com.lepu.health.widget.ReminderPopup
import com.lxj.xpopup.XPopup

/**
 * 运动目标设置
 * zrj 2020/10/30
 */
class ExerciseSettingFragment : BaseFragment(R.layout.fragment_exercise_setting) {
    private val binding: FragmentExerciseSettingBinding by bindView()
    private var isReminder: Boolean by Preference(Constant.IS_REMINDER, false)
    private var reminderType: Int by Preference(Constant.REMINDER_TYPE, 0)
    private var reminderValue: Int by Preference(Constant.REMINDER_VALUE, 1)
    private var units: Int by Preference(Constant.UNITS, 0)
    override fun initData() {
        binding.sc.isChecked = isReminder
        binding.ctl2.setVisible(isReminder)
        setReminders()
    }

    @SuppressLint("SetTextI18n")
    private fun setReminders() {
        binding.tvRemindersValue.text =
            "$reminderValue ${
                getString(
                    when (reminderType) {
                        0 -> if (units == 0) R.string.km else R.string.mile
                        else -> R.string.min
                    }
                )
            }"
    }

    override fun initView(savedInstanceState: Bundle?) {
        ImmersionBar.setTitleBar(this, binding.toolbar)
        binding.ivBack.click { findNavController().navigateUp() }

        binding.sc.setOnCheckedChangeListener { _, isChecked ->
            isReminder = isChecked
            binding.ctl2.setVisible(isChecked)
        }

        binding.ctl2.click {
            val pop = XPopup.Builder(requireContext())
                .asCustom(ReminderPopup(requireContext())) as ReminderPopup
            pop.setData(reminderType, reminderValue)
                .setOnSelectListener { reminderType, reminderValue ->
                    this.reminderType = reminderType
                    this.reminderValue = reminderValue
                    setReminders()
                }
            pop.show()
        }
    }
}




