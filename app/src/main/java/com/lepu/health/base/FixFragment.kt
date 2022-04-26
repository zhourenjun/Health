package com.lepu.health.base

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import androidx.navigation.fragment.FragmentNavigator
import java.util.ArrayDeque
import androidx.annotation.Nullable
import androidx.fragment.app.FragmentTransaction
import java.lang.StringBuilder


@Navigator.Name("keep_state_fragment")
class KeepStateNavigator(private val context: Context,
                         private val manager: FragmentManager, private val containerId: Int
) :
    FragmentNavigator(context, manager, containerId) {
    private val mBackStack = ArrayDeque<String>()

    @Nullable
    override fun navigate(
        destination: Destination,
        @Nullable args: Bundle?,
        @Nullable navOptions: NavOptions?,
        @Nullable navigatorExtras: Navigator.Extras?
    ): NavDestination? {
        val tag = destination.id.toString()
        val transaction: FragmentTransaction = manager.beginTransaction()
        var initialNavigate = false
        val currentFragment = manager.primaryNavigationFragment
        if (currentFragment != null) {
            transaction.hide(currentFragment)
        }
        var fragment = manager.findFragmentByTag(tag)
        if (fragment == null) {
            val className = destination.className
            fragment = manager.fragmentFactory.instantiate(context.classLoader, className)
            fragment.arguments = args
            transaction.add(containerId, fragment, tag)
            initialNavigate = true
            mBackStack.add(tag)
        } else {
            fragment.arguments = args
            transaction.show(fragment)
        }
        transaction.setPrimaryNavigationFragment(fragment)
        transaction.setReorderingAllowed(true)
        transaction.commitNow()
        return if (initialNavigate) destination else null
    }

    override fun popBackStack(): Boolean {
        if (mBackStack.isEmpty()) {
            return false
        }
        //        if (manager.getBackStackEntryCount() > 0) {
//            manager.popBackStack(
//                    generateBackStackName(mBackStack.size(), mBackStack.peekLast()),
//                    FragmentManager.POP_BACK_STACK_INCLUSIVE);
//        } // else, we're on the first Fragment, so there's nothing to pop from FragmentManager
        val removeTag = mBackStack.removeLast()
        return doNavigate(removeTag)
    }

    /**
     * 使用场景：A 打开 B, B 打开 C，C 直接或是间接（通过 事件总线传递事件 然后由具体的业务进行操作）对 B 进行关闭。然后从 C 直接返回至 A。
     *
     * @param destId 是在 Navigation 节点中 keep_state_fragment 中的 id 值，不能是 action 的 id！！！
     * @return  true 移除成功，false 移除失败
     */
    fun closeMiddle(destId: Int): Boolean {
        val removeTag = destId.toString()
        val sb = StringBuilder("All stack is : [ ")
        for (s in mBackStack) {
            sb.append(s).append(" ")
        }
        sb.append("]").append(". Waiting for close is ").append(removeTag)
//        LogUtil.e( sb.toString())
        val remove = mBackStack.remove(removeTag)
        return if (remove) {
            doNavigate(removeTag)
        } else {
            false
        }
    }

    /**
     * 移除 Fragment 并把当前栈顶的 Fragment 显示出来。
     *
     * @param removeTag 待移除 Fragment tag
     * @return true 移除成功，false 移除失败
     */
    private fun doNavigate(removeTag: String): Boolean {
        val transaction: FragmentTransaction = manager.beginTransaction()
        val removeFrag = manager.findFragmentByTag(removeTag)
        if (removeFrag != null) {
            transaction.remove(removeFrag)
//            LogUtil.e( "removeFrag :${removeFrag::class.java.simpleName}")
        } else {
            return false
        }
        kotlin.runCatching {
            val showTag = mBackStack.last
            val showFrag = manager.findFragmentByTag(showTag)
            if (showFrag != null) {
//                LogUtil.e( "showFrag :${showFrag::class.java.simpleName}")
                transaction.show(showFrag)
                transaction.setPrimaryNavigationFragment(showFrag)
                transaction.setReorderingAllowed(true)
                val stateSaved = manager.isStateSaved
//                LogUtil.e("popBackStack: 当前是否在进行状态保存$stateSaved")
                if (stateSaved) {
                    transaction.commitNowAllowingStateLoss()
                } else {
                    transaction.commitNow()
                }
            } else {
                return false
            }
        }
        return true
    }
}