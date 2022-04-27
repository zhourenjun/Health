package com.lepu.health.widget

import android.content.Context
import com.lepu.health.R
import com.lepu.health.databinding.PopupMapBinding
import com.lepu.health.util.click
import com.lxj.xpopup.core.BottomPopupView

/**
  *
  * 地图选择
  * zrj 2020/11/7
 */
class MapPopup(context: Context) : BottomPopupView(context) {

    private lateinit var  binding: PopupMapBinding
    override fun getImplLayoutId() = R.layout.popup_map

    private var index = 0

    override fun onCreate() {
        super.onCreate()
        binding = PopupMapBinding.bind(popupImplView)

        binding.rbGoogleMap.isChecked = index == 0
        binding.rbAmap.isChecked = index == 1
        binding.rbAuto.isChecked = index == 4
        binding.ctlAmap.click {
            dismissWith {
                if (index != 1) {
                    onSelectListener?.invoke(1)
                }
            }
        }
        binding.ctlGoogleMap.click {
            dismissWith {
                if (index != 0) {
                    onSelectListener?.invoke(0)
                }
            }
        }
        binding.ctlAuto.click {
            dismissWith {
                if (index != 4) {
                    onSelectListener?.invoke(4)
                }
            }
        }
        binding.tvCancel.click { dismiss() }
    }

    fun setData(index: Int) : MapPopup{
        this.index = index
        return this
    }

    private var onSelectListener: ((map: Int) -> Unit)? = null

    fun setOnSelectListener(l: ((map: Int) -> Unit)): MapPopup {
        this.onSelectListener = l
        return this
    }
}