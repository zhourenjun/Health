package com.lepu.health.base

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import com.lepu.health.util.LogUtil

/**
 * java类作用描述
 * zrj
 * 2021/8/11 10:44
 */
abstract class BaseFragment(@LayoutRes contentLayoutId: Int) : Fragment(contentLayoutId) {

    private var hasInitData = false
    private var hasInitView = false

    private lateinit var callback: OnBackPressedCallback

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!hasInitView) {
            hasInitView = true
            initView(savedInstanceState)
        }
        setBackButtonDispatcher()
    }

    override fun onResume() {
        super.onResume()
        LogUtil.e("${this::class.java.simpleName}  onResume")
        if (!hasInitData) {
            hasInitData = true
            initData()
        }
    }

    override fun onPause() {
        super.onPause()
        LogUtil.e("${this::class.java.simpleName}  onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        LogUtil.e("${this::class.java.simpleName}  onDestroy")
    }

    protected abstract fun initView(savedInstanceState: Bundle?)
    protected abstract fun initData()


    private fun setBackButtonDispatcher() {
        callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBackPressed()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, callback)
    }

    open fun onBackPressed() {

    }
}