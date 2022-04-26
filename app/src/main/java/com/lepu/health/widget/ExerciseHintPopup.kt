package com.lepu.health.widget

import android.content.Context
import com.lepu.health.R
import com.lepu.health.databinding.PopupExerciseHintBinding
import com.lepu.health.util.click
import com.lxj.xpopup.core.BottomPopupView

/**
 *
 * 运动过短提示
 * zrj 2020/11/5
 */
class ExerciseHintPopup(context: Context) : BottomPopupView(context) {
    private lateinit var  binding: PopupExerciseHintBinding
    override fun getImplLayoutId() = R.layout.popup_exercise_hint

    override fun onCreate() {
        super.onCreate()
        binding = PopupExerciseHintBinding.bind(this)
        binding.tvC.click {
            dismissWith {
                onSelectListener?.invoke(false)
            }
        }
        binding.tvE.click {
            dismissWith {
                onSelectListener?.invoke(true)
            }
        }
    }

    private var onSelectListener: ((isEnd: Boolean) -> Unit)? = null

    fun setOnSelectListener(l: ((isEnd: Boolean) -> Unit)): ExerciseHintPopup {
        this.onSelectListener = l
        return this
    }
}