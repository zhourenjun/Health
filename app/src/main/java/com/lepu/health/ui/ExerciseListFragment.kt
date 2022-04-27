package com.lepu.health.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.MergeAdapter
import com.gyf.immersionbar.ImmersionBar
import com.gyf.immersionbar.ktx.immersionBar
import com.lepu.health.R
import com.lepu.health.base.BaseFragment
import com.lepu.health.base.bindView
import com.lepu.health.base.toBinding
import com.lepu.health.databinding.FragmentExerciseListBinding
import com.lepu.health.databinding.ItemExerciseContentBinding
import com.lepu.health.databinding.ItemExerciseHeaderBinding
import com.lepu.health.db.ExerciseListViewModel
import com.lepu.health.db.Trace
import com.lepu.health.util.*
import com.lepu.health.widget.ExerciseModePopup
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.enums.PopupType
import org.joda.time.DateTime
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

/**
 *
 * 运动数据列表
 * zrj 2020/9/8
 */
class ExerciseListFragment : BaseFragment(R.layout.fragment_exercise_list) {

    private val binding: FragmentExerciseListBinding by bindView()
    private val model: ExerciseListViewModel by viewModel()
    private var nowYear = DateTime.now()
    private var list = mutableListOf<ItemsExpandableAdapter>()
    private var pos = 0
    private val mergeAdapter: MergeAdapter by lazy {
        MergeAdapter(
            MergeAdapter.Config.Builder()
                .setIsolateViewTypes(false)
                .build()
        )
    }
    private val footerAdapter: FooterAdapter by lazy { FooterAdapter() }

    //当前用户所有的数据
    private val data = mutableListOf<Trace>()

    override fun initData() {
        mergeAdapter.addAdapter(mergeAdapter.itemCount, footerAdapter)
        model.apply {
            //全部数据
            mTraces.observe(viewLifecycleOwner, {
                if (it == null || it.isEmpty()) { //加载更多
                    footerAdapter.updateFooterState(FooterAdapter.STATE_NO_DATA)
                    return@observe
                }
                data.clear()
                data.addAll(it)
                nowYear = DateTime.now()
                list.forEach { adapter ->
                    mergeAdapter.removeAdapter(adapter)
                }
                list.clear()
                footerAdapter.updateFooterState(FooterAdapter.STATE_LOADING)
                setData()
            })

            mMsg.observe(viewLifecycleOwner, {
                toast(it)
            })
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        findNavController().navigateUp()
    }

    override fun initView(savedInstanceState: Bundle?) {
        immersionBar {
            navigationBarColor(android.R.color.transparent)
        }
        ImmersionBar.setTitleBar(this, binding.toolbar)
        binding.ivBack.click { onBackPressed() }
        model.getTraceById()

        binding.spinner.click {
            val pop = XPopup.Builder(requireContext())
                .atView(it)
                .popupType(PopupType.AttachView)
                .hasShadowBg(true)
                .asCustom(ExerciseModePopup(requireContext())) as ExerciseModePopup
            pop.setOnSelectListener { position, text ->
                //清除之前重新加载
                binding.spinner.text = text
                nowYear = DateTime.now()
                list.forEach { adapter ->
                    mergeAdapter.removeAdapter(adapter)
                }
                list.clear()
                footerAdapter.updateFooterState(FooterAdapter.STATE_LOADING)
                pos = position
                setData()
            }.show()
        }

        val manager = LinearLayoutManager(requireContext())
        var isSlidingUpward = false
        with(binding.rv) {
            layoutManager = manager
            itemAnimator = ExpandableItemAnimator()
            adapter = mergeAdapter

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE
                        && manager.findLastVisibleItemPosition() == mergeAdapter.itemCount - 1
                        && isSlidingUpward
                    ) {
                        nowYear = nowYear.minusYears(1)
                        footerAdapter.updateFooterState(FooterAdapter.STATE_LOADING)
                        setData() //加载前一年的所有数据
                    }
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    isSlidingUpward = dy > 0
                }
            })
        }
    }

    private fun setData() {

        if (data.isNotEmpty()) {
            //当前模式所有数据
            val current = if (pos == 0) data else data.filter { it.mode == pos }
            if (current.isEmpty()) {
                footerAdapter.updateFooterState(FooterAdapter.STATE_NO_MORE)
                return
            }

            val year = nowYear.dayOfYear().withMinimumValue().withMillisOfDay(0)
            val currentList = mutableListOf<ItemsExpandableAdapter>()
            for (i in 0..11) {
                val startTime =
                    year.withMonthOfYear(i + 1).withMillisOfDay(0).millis / 1000
                val endTime =
                    year.withMonthOfYear(i + 1).plusMonths(1).withMillisOfDay(0).millis / 1000 - 1
                //过滤出每月数据
                val temp = current.filter { trace -> trace.date in startTime until endTime }
                if (temp.isNotEmpty()) {
                    currentList.add(
                        ItemsExpandableAdapter(
                            ItemsGroup((startTime * 1000L).toDateString("yyyy/MM"), temp)
                        ).setOnItemClickListener { trace ->
                            findNavController().navigate(
                                R.id.exerciseRouteFragment,
                                bundleOf("trace" to trace)
                            )
                        })
                }
            }
            if (currentList.isEmpty()) {
                val temp = nowYear.minusYears(1)
                if (temp.year < DateTime(data.first().date * 1000).year) {
                    footerAdapter.updateFooterState(FooterAdapter.STATE_COMPLETE)
                    return
                }
                nowYear = temp
                setData()
                return
            }
            list.addAll(currentList)
            currentList.forEach { adapter ->
                mergeAdapter.addAdapter(0, adapter)
            }
            footerAdapter.updateFooterState(FooterAdapter.STATE_COMPLETE)
            binding.rv.scrollToPosition(0)
        } else {
            footerAdapter.updateFooterState(FooterAdapter.STATE_NO_DATA)
        }
    }
}

data class ItemsGroup(val title: String, val items: List<Trace>)

class ItemsExpandableAdapter(private val itemsGroup: ItemsGroup) :
    RecyclerView.Adapter<ItemsExpandableAdapter.ViewHolder>() {

    private var units: Int by Preference(Constant.UNITS, 0)

    companion object {
        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_HEADER = 2
        private const val IC_EXPANDED_ROTATION_DEG = 0F
        private const val IC_COLLAPSED_ROTATION_DEG = 180F
    }

    //initialValue: 初始值
    //onChange: 属性值被修改时的回调处理器，回调有三个参数property,oldValue,newValue，分别为: 被赋值的属性、旧值与新值
    private var isExpanded: Boolean by Delegates.observable(true) { _: KProperty<*>, _: Boolean, newExpandedValue: Boolean ->
        if (newExpandedValue) {
            notifyItemRangeInserted(1, itemsGroup.items.size)
            //To update the header expand icon
            notifyItemChanged(0)
        } else {
            notifyItemRangeRemoved(1, itemsGroup.items.size)
            //To update the header expand icon
            notifyItemChanged(0)
        }
    }

    private val onHeaderClickListener = View.OnClickListener {
        isExpanded = !isExpanded
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM
    }

    override fun getItemCount(): Int = if (isExpanded) itemsGroup.items.size + 1 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> ViewHolder.HeaderVH(
                inflater.inflate(R.layout.item_exercise_header, parent, false).toBinding()
            )
            else -> ViewHolder.ItemVH(
                inflater.inflate(R.layout.item_exercise_content, parent, false).toBinding()
            )
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (holder) {
            is ViewHolder.ItemVH -> holder.bind(
                itemsGroup.items[position - 1],
                position != itemsGroup.items.size,
                View.OnClickListener {
                    onItemClickListener?.invoke(itemsGroup.items[position - 1])
                })
            is ViewHolder.HeaderVH -> {
                val run =
                    itemsGroup.items.filter { it.mode == 1 || it.mode == 2 }.map { it.distance }
                        .sum()
                val walk =
                    itemsGroup.items.filter { it.mode == 0 || it.mode == 5 }.map { it.distance }
                        .sum()
                val cycling =
                    itemsGroup.items.filter { it.mode == 3 || it.mode == 4 }.map { it.distance }
                        .sum()

                holder.bind(
                    itemsGroup.title,
                    String.format("%.2f", if (units == 0) run / 1000f else run * 0.62 / 1000f),
                    String.format("%.2f", if (units == 0) walk / 1000f else walk * 0.62 / 1000f),
                    String.format(
                        "%.2f",
                        if (units == 0) cycling / 1000f else cycling * 0.62 / 1000f
                    ),
                    isExpanded, onHeaderClickListener
                )
            }
        }
    }

    private var onItemClickListener: ((trace: Trace) -> Unit)? = null

    fun setOnItemClickListener(l: ((trace: Trace) -> Unit)): ItemsExpandableAdapter {
        this.onItemClickListener = l
        return this
    }

    sealed class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var units: Int by Preference(Constant.UNITS, 0)

        class ItemVH(private val binding: ItemExerciseContentBinding) : ViewHolder(binding.root) {

            @SuppressLint("SetTextI18n")
            fun bind(
                trace: Trace,
                lineVisible: Boolean,
                onClickListener: View.OnClickListener
            ) {
                binding.iv.setImageResource(
                    when (trace.mode) {
                        1 -> R.drawable.ic_outdoor_run_white_24dp
                        2 -> R.drawable.ic_walk_white_24dp
                        else -> R.drawable.ic_ride_white_24dp
                    }
                )
                binding.tvDistance.text = String.format(
                    "%.2f",
                    if (units == 0) trace.distance / 1000f else trace.distance * 0.62 / 1000f
                )
                binding.tvDistanceUnit.text =
                    binding.tvDistance.context.getString(if (units == 0) R.string.km else R.string.mile)

                val sec =
                    trace.accomplishTime * 1000 / (if (units == 0) trace.distance else trace.distance * 0.62).toInt()
                binding.tvPace.text =
                    "${sec / 60}'${sec % 60}''"
                binding.tvPaceUnit.text =
                    binding.tvPace.context.getString(if (units == 0) R.string.km2 else R.string.mile2)
                binding.tvTime.text =
                    CommonUtil.getFormattedStopWatchTIme(trace.accomplishTime * 1000L, false)
                binding.tvData.text = (trace.date * 1000L).toDateString("MM/dd")
                itemView.apply {
                    binding.line.setInVisible(lineVisible)
                    this.setOnClickListener(onClickListener)
                }
            }
        }

        class HeaderVH(private val binding: ItemExerciseHeaderBinding) : ViewHolder(binding.root) {

            internal val icExpand = binding.icExpand

            fun bind(
                data: String,
                run: String,
                walk: String,
                cycling: String,
                expanded: Boolean,
                onClickListener: View.OnClickListener
            ) {
                binding.tvTitle.text = data
                binding.tvRunning.text =
                    binding.tvRunning.context.getString(if (units == 0) R.string.running else R.string.running2)
                binding.tvWalking.text =
                    binding.tvWalking.context.getString(if (units == 0) R.string.walking else R.string.walking2)
                binding.tvCycling.text =
                    binding.tvCycling.context.getString(if (units == 0) R.string.cycling else R.string.cycling2)
                binding.tvRunningValue.text = run
                binding.tvWalkingValue.text = walk
                binding.tvCyclingValue.text = cycling
                icExpand.rotation =
                    if (expanded) IC_EXPANDED_ROTATION_DEG else IC_COLLAPSED_ROTATION_DEG
                icExpand.setOnClickListener(onClickListener)
            }
        }
    }
}

class ExpandableItemAnimator : DefaultItemAnimator() {

    override fun recordPreLayoutInformation(
        state: RecyclerView.State,
        viewHolder: RecyclerView.ViewHolder,
        changeFlags: Int,
        payloads: MutableList<Any>
    ): ItemHolderInfo {
        return if (viewHolder is ItemsExpandableAdapter.ViewHolder.HeaderVH) {
            HeaderItemInfo().also {
                it.setFrom(viewHolder)
            }
        } else {
            super.recordPreLayoutInformation(state, viewHolder, changeFlags, payloads)
        }
    }

    override fun recordPostLayoutInformation(
        state: RecyclerView.State,
        viewHolder: RecyclerView.ViewHolder
    ): ItemHolderInfo {
        return if (viewHolder is ItemsExpandableAdapter.ViewHolder.HeaderVH) {
            HeaderItemInfo().also {
                it.setFrom(viewHolder)
            }
        } else {
            super.recordPostLayoutInformation(state, viewHolder)
        }
    }

    override fun animateChange(
        oldHolder: RecyclerView.ViewHolder,
        holder: RecyclerView.ViewHolder,
        preInfo: ItemHolderInfo,
        postInfo: ItemHolderInfo
    ): Boolean {
        if (preInfo is HeaderItemInfo && postInfo is HeaderItemInfo && holder is ItemsExpandableAdapter.ViewHolder.HeaderVH) {
            ObjectAnimator
                .ofFloat(
                    holder.icExpand,
                    View.ROTATION,
                    preInfo.arrowRotation,
                    postInfo.arrowRotation
                )
                .also {
                    it.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator?) {
                            holder.icExpand.rotation = postInfo.arrowRotation
                            dispatchAnimationFinished(holder)
                        }
                    })
                    it.start()
                }
        }
        return super.animateChange(oldHolder, holder, preInfo, postInfo)
    }

    //这意味着对于动画，我们不需要将ViewHolder的对象（旧的和新的支架）分开
    override fun canReuseUpdatedViewHolder(viewHolder: RecyclerView.ViewHolder): Boolean {
        return true
    }
}

class HeaderItemInfo : RecyclerView.ItemAnimator.ItemHolderInfo() {

    internal var arrowRotation: Float = 0F

    override fun setFrom(holder: RecyclerView.ViewHolder): RecyclerView.ItemAnimator.ItemHolderInfo {
        if (holder is ItemsExpandableAdapter.ViewHolder.HeaderVH) {
            arrowRotation = holder.icExpand.rotation
        }
        return super.setFrom(holder)
    }
}


class FooterAdapter : RecyclerView.Adapter<FooterAdapter.BaseViewHolder>() {
    companion object {
        const val STATE_LOADING = 3
        const val STATE_COMPLETE = 4
        const val STATE_NO_MORE = 5
        const val STATE_NO_DATA = 6
    }

    var state = STATE_LOADING

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return BaseViewHolder(
            when (viewType) {
                STATE_LOADING ->
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_footer_loading, parent, false)
                STATE_COMPLETE ->
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_footer_complete, parent, false)
                STATE_NO_MORE ->
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_footer_no_more, parent, false)
                else ->
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_footer_no_data, parent, false)
            }
        )
    }

    override fun getItemCount(): Int {
        return 1
    }

    override fun getItemViewType(position: Int): Int {
        return state
    }

    fun updateFooterState(state: Int) {
        this.state = state
        notifyItemChanged(0)
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {

    }

    class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    }
}