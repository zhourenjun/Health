package com.lepu.health.util

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.*
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.lepu.health.R
import com.permissionx.guolindev.PermissionX
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 *
 * java类作用描述
 * zrj 2021/8/11 19:17
 * 更新者 2021/8/11 19:17
 */


fun Context.share(shareFile: File, isPic: Boolean = true): String {
    val intent = Intent(Intent.ACTION_SEND)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val uri =
            FileProvider.getUriForFile(this, "$packageName.fileProvider", shareFile)
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri(MediaStore.EXTRA_OUTPUT, uri)
    } else {
        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(shareFile))
    }
    intent.type = if (isPic) "image/jpeg" else "video/mp4"
    val chooser = Intent.createChooser(intent, getString(R.string.share))
    if (intent.resolveActivity(packageManager) != null) {
        startActivity(chooser)
    }
    return shareFile.absolutePath
}

fun Context.dip2px(dpValue: Float): Int {
    val scale = this.applicationContext.resources.displayMetrics.density
    return (dpValue * scale + 0.5f).toInt()
}

fun Context.colorCompat(color: Int) = ContextCompat.getColor(this, color)

fun Long.toDateString(format: String = "HH:mm:ss dd/MM/yyyy") = SimpleDateFormat(
    format,
    Locale.getDefault()
).format(Date(this))

val Float.dp: Float                 // [xxhdpi](360 -> 1080)
    get() = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_DIP, this, Resources.getSystem().displayMetrics
    )

val Int.dp: Int
    get() = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        Resources.getSystem().displayMetrics
    ).toInt()


val Float.sp: Float                 // [xxhdpi](360 -> 1080)
    get() = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_SP, this, Resources.getSystem().displayMetrics
    )


val Int.sp: Int
    get() = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_SP,
        this.toFloat(),
        Resources.getSystem().displayMetrics
    ).toInt()


val Context.windowManager: WindowManager
    get() = getSystemService(Context.WINDOW_SERVICE) as WindowManager

fun View.setVisible(visible: Boolean = true) {
    this.visibility = if (visible) View.VISIBLE else View.GONE
}

fun View.setInVisible(visible: Boolean = true) {
    this.visibility = if (visible) View.VISIBLE else View.INVISIBLE
}

fun View.delayOnLifecycle(
    durationInMillis: Long=0L,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    block: () -> Unit
): Job? = findViewTreeLifecycleOwner()?.let { lifecycleOwner ->
    lifecycleOwner.lifecycle.coroutineScope.launch(dispatcher) {
        delay(durationInMillis)
        block()
    }
}

@Suppress("UNCHECKED_CAST")
fun <T : View> T.click(triggerDelay: Long = 400L, click: (view: T) -> Unit) {
    setOnClickListener {
        isClickable = false
        click(it as T)
        postDelayed({
            isClickable = true
        }, triggerDelay)
    }
}

//inline：内联函数的使用增强了高阶函数的性能。内联函数告诉编译器将参数和函数复制到调用站点。
//crossinline：添加crossinline关键字以避免非本地返回。即，如果我们在 lambdas 中添加了返回，它将允许非本地返回并将代码留在其下方。
// 为了避免这种情况，我们在 lambda 函数侦听器之前使用 crossinline 关键字。
inline fun EditText.onTextChange(crossinline listener: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(charSequence: CharSequence?, p1: Int, p2: Int, p3: Int) {
            //NO OP
        }

        override fun onTextChanged(charSequence: CharSequence?, p1: Int, p2: Int, p3: Int) {
            listener(charSequence.toString())
        }

        override fun afterTextChanged(p0: Editable?) {
            //NO OP
        }
    })

}

fun EditText.setTextChangeListener(body: (key: String) -> Unit) {

    addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            body(s.toString())
        }
    })
}

fun AppCompatSeekBar.setOnSeekBarChangeListener(body: (key: Int) -> Unit) {

    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
            body(p1)
        }

        override fun onStartTrackingTouch(p0: SeekBar?) {
        }

        override fun onStopTrackingTouch(p0: SeekBar?) {
        }

    })
}

fun Spinner.onItemSelectedListener(body: (position: Int) -> Unit) {
    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            body(position)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }
}

fun Context.drawable(@DrawableRes id: Int) = ContextCompat.getDrawable(this, id)

fun CheckBox.getString() = if (this.isChecked) "${this.text}  " else ""
fun TextView.getString() = "  ${this.text}  "


fun getTextHeight(paint: Paint, text: String): Int {
    val rect = Rect()
    paint.getTextBounds(text, 0, text.length, rect)
    return rect.height()
}

fun View.forEachChildView(closure: (View) -> Unit) {
    closure(this)
    val groupView = this as? ViewGroup ?: return
    val size = groupView.childCount - 1
    for (i in 0..size) {
        groupView.getChildAt(i).forEachChildView(closure)
    }
}

fun CheckBox.getInt() = if (this.isChecked) 1 else 0

inline fun TabLayout.addOnTabChangeListener(
    crossinline onTabUnselected: (tab: TabLayout.Tab?) -> Unit = { _ -> },
    crossinline onTabReselected: (tab: TabLayout.Tab?) -> Unit = { _ -> },
    crossinline onTabSelected: (tab: TabLayout.Tab?) -> Unit = { _ -> }
): TabLayout.OnTabSelectedListener {
    val tabChangeListener = object : TabLayout.OnTabSelectedListener {
        override fun onTabReselected(tab: TabLayout.Tab?) {
            onTabReselected.invoke(tab)
        }

        override fun onTabUnselected(tab: TabLayout.Tab?) {
            onTabUnselected.invoke(tab)
        }

        override fun onTabSelected(tab: TabLayout.Tab?) {
            onTabSelected.invoke(tab)
        }
    }
    addOnTabSelectedListener(tabChangeListener)
    return tabChangeListener
}

fun RadioGroup.disableRadioGroup() {
    for (i in 0 until this.childCount) {
        this.getChildAt(i).isEnabled = false
    }
}

val RecyclerView.canScroll: Boolean?
    get() {
        if (adapter == null) return null
        val manager = layoutManager as? LinearLayoutManager ?: return null
        if (manager.orientation == RecyclerView.HORIZONTAL) {
            return computeHorizontalScrollRange() + paddingLeft + paddingRight > width
        }
        return computeVerticalScrollRange() + paddingTop + paddingBottom > height
    }

@SuppressLint("ClickableViewAccessibility")
fun EditText.setOnTouch() {
    this.setOnTouchListener { v, event ->
        if (v.id == this.id && this.lineCount > this.maxLines) {
            v.parent.requestDisallowInterceptTouchEvent(true)
            // 否则将事件交由其父类处理
            if (event.action == MotionEvent.ACTION_UP) {
                v.parent.requestDisallowInterceptTouchEvent(false)
            }
        }
        false
    }
}

fun Context.toast(text: CharSequence) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

fun Context.toast(@StringRes resId: Int) {
    Toast.makeText(this, getString(resId), Toast.LENGTH_SHORT).show()
}
fun Context.networkBroadcastReceiverFlow(): Flow<Boolean> {
    return callbackFlow {

        val networkBroadcastReceiver = object : BroadcastReceiver() {

            override fun onReceive(context: Context?, intent: Intent) {
                if (intent.action != ConnectivityManager.CONNECTIVITY_ACTION) return
                val activeNetwork = intent.extras?.get(ConnectivityManager.EXTRA_NETWORK_INFO) as NetworkInfo? ?: return
                trySend(activeNetwork.isConnected)
            }
        }
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(networkBroadcastReceiver, filter)
        awaitClose { this@networkBroadcastReceiverFlow.unregisterReceiver(networkBroadcastReceiver) }
    }
}

fun Fragment.toast(text: CharSequence) {
    Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
}

fun Fragment.toast(@StringRes resId: Int) {
    Toast.makeText(requireContext(), getString(resId), Toast.LENGTH_SHORT).show()
}


fun TextView.setDate(
    minDate: Long = 0L,
    maxDate: Long = 0L,
    onSelectListener: (text: String) -> Unit
) {
    val d = Calendar.getInstance()
    var year = d.get(Calendar.YEAR)
    var month = 1 + d.get(Calendar.MONTH)
    var day = d.get(Calendar.DAY_OF_MONTH)
    if (text.isNotEmpty()) {
        val list = text.toString().split("-")
        year = list[0].toInt()
        month = list[1].toInt() - 1
        day = list[2].toInt()
    }
    val dialog = DatePickerDialog(
        context, { _, y, m, dayOfMonth ->
            text =
                String.format("%d-%02d-%02d", y, m + 1, dayOfMonth)  //重构使用时间戳行不行（数据上传后端），兼容通过“_”判断
            onSelectListener.invoke(text.toString())
        }, year, month, day
    )
    if (minDate > 0) {
        dialog.datePicker.minDate = minDate
    }
    if (maxDate > 0) {
        dialog.datePicker.maxDate = maxDate
    }
    dialog.show()
}

fun showSingleChoiceDialog(
    context: Context, title: String, items: Array<String>,
    onSelectListener: (which: Int) -> Unit
) {
    AlertDialog.Builder(context)
        .setTitle(title)
        .setIcon(android.R.drawable.ic_menu_edit)
        .setSingleChoiceItems(items, -1) { dialog, which ->
            onSelectListener.invoke(which)
            dialog.dismiss()
        }.create().show()
}


fun Fragment.permissionX(
    permissions: List<String>,
    callback: (allGranted: Boolean, grantedList: List<String>, deniedList: List<String>) -> Unit
) {
    PermissionX.init(this)
        .permissions(permissions)
        .setDialogTintColor(Color.parseColor("#008577"), Color.parseColor("#83e8dd"))
        .onExplainRequestReason { scope, deniedList ->
            scope.showRequestReasonDialog(
                deniedList,
                getString(R.string.hint),
                getString(R.string.sure),
                getString(R.string.cancel)
            )
        }.onForwardToSettings { scope, deniedList ->
            scope.showForwardToSettingsDialog(
                deniedList,
                getString(R.string.permission),
                getString(R.string.sure),
                getString(R.string.cancel)
            )
        }.request { allGranted, grantedList, deniedList ->
            callback.invoke(allGranted, grantedList, deniedList)
        }
}