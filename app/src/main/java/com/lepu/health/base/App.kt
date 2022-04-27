package com.lepu.health.base

import android.app.Application
import com.tencent.bugly.Bugly
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import kotlin.properties.Delegates


/**
 *
 * java类作用描述
 * zrj 2021/8/11 10:08
 * 更新者 2021/8/11 10:08
 */
class App : Application() {

    companion object {
        var context: App by Delegates.notNull()
            private set

    }

    override fun onCreate() {
        super.onCreate()
        context = this
        startKoin {
            androidContext(context)
            modules(appModule)
        }
        Bugly.init(applicationContext, "1dc31cf615", false)
    }
}

