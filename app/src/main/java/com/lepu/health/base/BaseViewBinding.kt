package com.lepu.health.base

import android.app.Activity
import android.app.Dialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 *
 * java类作用描述
 * zrj 2021/8/12 14:59
 * 更新者 2021/8/12 14:59
 */
inline fun <reified VB: ViewBinding> Activity.inflate() = lazy {
    inflateBinding<VB>(layoutInflater).apply{
        setContentView(root)
    }
}

inline fun <reified VB : ViewBinding> Dialog.inflate() = lazy {
    inflateBinding<VB>(layoutInflater).apply { setContentView(root) }
}

inline fun  <reified VB:ViewBinding> inflateBinding(layoutInflater: LayoutInflater) = VB::class.java.getMethod("inflate",LayoutInflater::class.java).invoke(null,layoutInflater) as VB

inline fun <reified  VB:ViewBinding> Fragment.bindView() = FragmentBindingDelegate(VB::class.java)

class FragmentBindingDelegate<VB:ViewBinding>(private val clazz: Class<VB>):
    ReadOnlyProperty<Fragment, VB> {

    private var isInitialized = false
    private var _binding: VB? = null
    private val binding: VB get() = _binding!!

    override fun getValue(thisRef: Fragment, property: KProperty<*>): VB {
        if (!isInitialized) {
            thisRef.viewLifecycleOwner.lifecycle.addObserver(object : LifecycleObserver {
                @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                fun onDestroyView() {
                    _binding = null
                }
            })
            _binding = clazz.getMethod("bind", View::class.java)
                .invoke(null, thisRef.requireView()) as VB
            isInitialized = true
        }
        return binding
    }
}

inline fun <reified V : ViewBinding> View.toBinding(): V {
    return V::class.java.getMethod(
        "inflate",
        LayoutInflater::class.java,
        ViewGroup::class.java,
        Boolean::class.java
    ).invoke(null, LayoutInflater.from(context), this, false) as V
}