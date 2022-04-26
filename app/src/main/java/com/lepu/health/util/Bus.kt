@file:Suppress("unused")

package com.lepu.health.util

import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class Bus<T>(val event: T, val tag: String = "") {
    override fun toString(): String {
        return "event = $event, tag = $tag"
    }
}

internal class TagEvent

@ExperimentalCoroutinesApi
val channel = BroadcastChannel<Bus<Any>>(100000)

//发送消息 + 标签I
@ExperimentalCoroutinesApi
fun send(event: Any, tag: String = "") = runBlocking { channel.send(Bus(event, tag)) } // 阻塞线程直到协程作用域内部所有协程执行完毕

//仅发送标签
@ExperimentalCoroutinesApi
fun sendTag(tag: String) = runBlocking {  channel.send(Bus(TagEvent(), tag)) }


//1.发送事件加标签时使用receive接收其事件, 如果发送时有指定标签,则接收时应该也指定其标签才能接收到其事件
//2.仅发送标签时使用receiveTag可以接收到该事件, 可以同时接收多个标签的事件
//3.active表示只有当Activity或Fragment处于活跃状态时才会收到事件, 如果未活跃时发送则等待到活跃时收到
//4.只有LifecycleOwner扩展函数才会在销毁时自动注销作用域
//活跃状态: 非onPause或者onDestroy都属于正在活跃中

@ExperimentalCoroutinesApi
inline fun <reified T> LifecycleOwner.receive(active: Boolean = false, vararg tags: String = arrayOf(),
                                              lifecycleEvent: Lifecycle.Event = Lifecycle.Event.ON_DESTROY,
                                              noinline block: suspend CoroutineScope.(event: T) -> Unit): ChannelScope {

    val scope = ChannelScope(this, lifecycleEvent)

    return scope.launch {
        for (bus in channel.openSubscription()) {
            if (bus.event is T && (tags.isEmpty() && bus.tag.isBlank() || tags.contains(bus.tag))) {
                if (active) {
                    MutableLiveData<T>().apply {
                        observe(this@receive,  {
                            scope.launch {
                                block(it)
                            }
                        })
                        value = bus.event
                    }
                } else block(bus.event)
            }
        }
    }
}

@ExperimentalCoroutinesApi
fun LifecycleOwner.receiveTag(active: Boolean = false, vararg tags: String,
                              lifecycleEvent: Lifecycle.Event = Lifecycle.Event.ON_DESTROY,
                              block: suspend CoroutineScope.(tag: String) -> Unit): ChannelScope {

    val scope = ChannelScope(this, lifecycleEvent)

    return scope.launch {
        for (bus in channel.openSubscription()) {
            if (bus.event is TagEvent && tags.contains(bus.tag)) {
                if (active) {
                    MutableLiveData<String>().apply {
                        observe(this@receiveTag,  {
                            scope.launch {
                                block(it)
                            }
                        })
                        value = bus.tag
                    }
                } else block(bus.tag)
            }
        }
    }
}


//需要手动注销的接收者
@ExperimentalCoroutinesApi
inline fun <reified T> receive(vararg tags: String = arrayOf(), noinline block: suspend (event: T) -> Unit) = ChannelScope().launch {

    for (bus in channel.openSubscription()) {
        if (bus.event is T && (tags.isEmpty() && bus.tag.isBlank() || tags.contains(bus.tag))) {
            block(bus.event)
        }
    }
}

//需要手动注销的接收者
@ExperimentalCoroutinesApi
fun receiveTag(vararg tags: String, block: suspend CoroutineScope.(tag: String) -> Unit) = ChannelScope().launch {

    for (bus in channel.openSubscription()) {
        if (bus.event is TagEvent && tags.contains(bus.tag)) {
            block(bus.tag)
        }
    }
}


/**
 * 异步协程作用域
 */
@Suppress("MemberVisibilityCanBePrivate", "NAME_SHADOWING")
open class ChannelScope() : CoroutineScope {
    //自定义注销的生命周期
    constructor(lifecycleOwner: LifecycleOwner, lifeEvent: Lifecycle.Event = Lifecycle.Event.ON_DESTROY) : this() {
        lifecycleOwner.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (lifeEvent == event) {
                    cancel()
                }
            }
        })
    }

    protected var catch: (ChannelScope.(Throwable) -> Unit)? = null
    protected var finally: (ChannelScope.(Throwable?) -> Unit)? = null
    protected var auto = true

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        catch(throwable)
    }

    override val coroutineContext: CoroutineContext = Dispatchers.Main + exceptionHandler + SupervisorJob()

    open fun launch(block: suspend CoroutineScope.() -> Unit): ChannelScope {
        start()
        launch(EmptyCoroutineContext, block = block).invokeOnCompletion { finally(it) }
        return this
    }

    protected open fun start() {

    }

    protected open fun catch(e: Throwable) {
        catch?.invoke(this, e) ?: handleError(e)
    }

    protected open fun finally(e: Throwable?) {
        finally?.invoke(this, e)
    }

    /**
     * 当作用域内发生异常时回调
     */
    open fun catch(block: ChannelScope.(Throwable) -> Unit = {}): ChannelScope {
        this.catch = block
        return this
    }

    /**
     * 无论正常或者异常结束都将最终执行
     */
    open fun finally(block: ChannelScope.(Throwable?) -> Unit = {}): ChannelScope {
        this.finally = block
        return this
    }

    /**
     * 错误处理
     */
    open fun handleError(e: Throwable) {
        e.printStackTrace()
    }

    fun autoOff() {
        auto = false
    }
}
