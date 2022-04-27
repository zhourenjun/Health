package com.lepu.health.widget

import android.content.Context
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.lepu.health.R
import com.lepu.health.databinding.PopupExerciseModeBinding
import com.lxj.xpopup.core.AttachPopupView
import com.lxj.xpopup.util.XPopupUtils

/**
 *
 * 模式选择
 * zrj 2020/11/7
 */
class ExerciseModePopup(context: Context) : AttachPopupView(context) {

    override fun getImplLayoutId() = R.layout.popup_exercise_mode
    private lateinit var  binding: PopupExerciseModeBinding

    private val modeAdapter: ModeAdapter by lazy { ModeAdapter() }

    private var isAll = true

    override fun onCreate() {
        super.onCreate()
        binding = PopupExerciseModeBinding.bind(popupImplView)
        binding.rv.adapter = modeAdapter.apply {
            setOnItemClickListener { _, _, position ->
                dismissWith {
                    onSelectListener?.invoke(position, this.data[position])
                }
            }
        }
        val mode = context.resources.getStringArray(R.array.actionArray)
        val data = if (isAll) {
            mode.toList()
        } else {
            val newArr = mutableListOf<String>()
            for (i in 1 until mode.size) {
                newArr.add(mode[i])
            }
            newArr
        }
        modeAdapter.setList(data)
    }

    private var onSelectListener: ((position: Int, s: String) -> Unit)? = null

    fun setOnSelectListener(l: ((position: Int, s: String) -> Unit)): ExerciseModePopup {
        this.onSelectListener = l
        return this
    }

    override fun getMaxHeight() = (XPopupUtils.getAppHeight(context) * 0.7f).toInt()

    inner class ModeAdapter : BaseQuickAdapter<String, BaseViewHolder>(R.layout.item_mode_list) {

        override fun convert(holder: BaseViewHolder, item: String) {
            holder.setText(R.id.tv, item)
                .setGone(R.id.view, holder.absoluteAdapterPosition == modeAdapter.data.size - 1)
        }
    }
}

