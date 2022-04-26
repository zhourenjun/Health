@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.lepu.health.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.PendingIntent.getService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationClientOption.AMapLocationMode
import com.amap.api.maps.AMapUtils
import com.amap.api.maps.model.LatLng
import com.lepu.health.R
import com.lepu.health.db.RealData
import com.lepu.health.util.CommonUtil.getFormattedStopWatchTIme
import com.lepu.health.util.LogUtil
import com.lepu.health.util.Constant.ACTION_PAUSE_SERVICE
import com.lepu.health.util.Constant.ACTION_START_OR_RESUME_SERVICE
import com.lepu.health.util.Constant.ACTION_STOP_SERVICE
import com.lepu.health.util.Constant.NOTIFICATION_CHANNEL_ID
import com.lepu.health.util.Constant.NOTIFICATION_CHANNEL_NAME
import com.lepu.health.util.Constant.NOTIFICATION_ID
import com.lepu.health.util.Constant.UPDATE_TIME_INTERVAL
import com.lepu.health.util.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.collections.ArrayList
import kotlin.properties.Delegates

/**
 *
 * 高德 定位服务
 * zrj 2020/7/29
 */
class AMapService : LifecycleService() {

    private var isFirstRun = true
    private var serviceKilled = false
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var mLocationClient: AMapLocationClient

    private var baseNotificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setAutoCancel(false)   //无法通过单击取消
        .setOngoing(true)
        .setSmallIcon(R.drawable.ic_exercise_grey_24dp)
        .setContentTitle("Running")
        .setContentText("00:00:00")

    private lateinit var currentNotificationBuilder: NotificationCompat.Builder

    companion object {
        val timeRunInSeconds = MutableLiveData<Long>()  //通知
        var timeRunInMillis = MutableLiveData<Long>() //用于RunningActivity
        var isTracking = MutableLiveData<Boolean>()
        var distanceInMeters = MutableLiveData<MutableList<Float>>()
        var pathPoints = MutableLiveData<MutableList<RealData>>()
    }

    private fun postInitialValue() {
        isTracking.postValue(false)
        pathPoints.postValue(mutableListOf())
        distanceInMeters.postValue(mutableListOf())
        timeRunInMillis.postValue(0L)
        timeRunInSeconds.postValue(0L)
    }

    override fun onCreate() {
        super.onCreate()
        currentNotificationBuilder = baseNotificationBuilder
        postInitialValue()
        mLocationClient = AMapLocationClient(this)
        isTracking.observe(this, {
            updateLocationTracking(it)
            updateNotificationTrackingState(it)
        })
    }

    private fun killService() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        serviceKilled = true
        isFirstRun = true
        mLocationClient.onDestroy()
        pauseService()
        postInitialValue()
        stopForeground(true)
        stopSelf()
    }

    private var mode by Delegates.notNull<Int>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            mode = it.extras?.getInt("mode") ?: 0
            when (it.action) {
                ACTION_START_OR_RESUME_SERVICE -> {
                    if (isFirstRun) {
                        startForegroundService()
                        isFirstRun = false
                    } else {
                        startTimer()
                    }
                }
                ACTION_PAUSE_SERVICE -> {
                    pauseService()
                }
                ACTION_STOP_SERVICE -> {
                    killService()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private var isTimeEnabled = false
    private var lapTime = 0L
    private var timeRun = 0L
    private var timeStarted = 0L
    private var lastSeconTimeStamp = 0L

    private fun startTimer() {
        addEmptyPolyLines()
        isTracking.postValue(true)
        timeStarted = System.currentTimeMillis()
        isTimeEnabled = true
        timeRun += lapTime
        CoroutineScope(Dispatchers.Main).launch {
            while (isTracking.value!!) {
                //时差
                lapTime = System.currentTimeMillis() - timeStarted
                //发布新的lapTime
                timeRunInMillis.postValue(timeRun + lapTime)
                if (timeRunInMillis.value!! >= lastSeconTimeStamp + 1000L) {
                    timeRunInSeconds.postValue(timeRunInSeconds.value!! + 1)
                    send(timeRunInSeconds.value!! + 1,"timeRunInSeconds")
                    lastSeconTimeStamp += 1000L
                }
                delay(UPDATE_TIME_INTERVAL)
            }
        }
    }

    private fun pauseService() {
        isTracking.postValue(false)
        isTimeEnabled = false
    }

    private fun updateNotificationTrackingState(isTracking: Boolean) {
        val notificationActionText =
            if (isTracking) getString(R.string.pause) else getString(R.string.resume)
        val pendingIntent = if (isTracking) {
            val pauseIntent = Intent(this, AMapService::class.java).apply {
                action = ACTION_PAUSE_SERVICE
            }
            getService(this, 1, pauseIntent, FLAG_UPDATE_CURRENT)
        } else {
            val resumeIntent = Intent(this, AMapService::class.java).apply {
                action = ACTION_START_OR_RESUME_SERVICE
            }
            getService(this, 2, resumeIntent, FLAG_UPDATE_CURRENT)

        }
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        currentNotificationBuilder.javaClass.getDeclaredField("mActions").apply {
            isAccessible = true
            set(currentNotificationBuilder, ArrayList<NotificationCompat.Action>())
        }
        if (!serviceKilled) {
            currentNotificationBuilder = baseNotificationBuilder.addAction(
                R.drawable.ic_baseline_pause_24, notificationActionText, pendingIntent
            )
            notificationManager.notify(NOTIFICATION_ID, currentNotificationBuilder.build())
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateLocationTracking(isTracking: Boolean) {

        if (isTracking) {
            val mLocationOption = AMapLocationClientOption()
            mLocationOption.locationMode = AMapLocationMode.Hight_Accuracy
            mLocationOption.interval = 2000L
            mLocationClient.setLocationOption(mLocationOption)
            mLocationClient.setLocationListener {
                if (it.errorCode == 0) {
                    pathPoints.value?.apply {
                        if (it.speed > 0) {
                            add(
                                RealData(
                                    -1,
                                    0,
                                    if (it.longitude > 0) "E" else "W",
                                    if (it.latitude > 0) "N" else "S",
                                    0,
                                    (1000 / it.speed).toInt(), //speed 米/秒
                                    it.latitude,
                                    it.longitude
                                )
                            )
                            LogUtil.e("speed:  ${it.speed}")
                            LogUtil.e("speed:  ${(1000 / it.speed).toInt()}")
                            pathPoints.postValue(this)

                            if (this.size > 2) {
                                val preLastLatLng = this[this.size - 2]
                                val lastLatLng = this.last()
                                val temp = AMapUtils.calculateLineDistance(
                                    LatLng(
                                        preLastLatLng.latitude,
                                        preLastLatLng.longitude
                                    ), LatLng(lastLatLng.latitude, lastLatLng.longitude)
                                )

                                distanceInMeters.value?.apply {
                                    add(temp)
                                    send(this,"distanceInMeters")
                                }
                            }
                        }
                    }
                    LogUtil.d("NEW LOCATION ${it.latitude},${it.longitude}")
                }
            }
            mLocationClient.startLocation()
        } else {
            mLocationClient.stopLocation()
        }
    }

    private fun addEmptyPolyLines() = pathPoints.value?.apply {
        pathPoints.postValue(this)
    } ?: pathPoints.postValue(mutableListOf())

    @SuppressLint("WakelockTimeout")
    private fun startForegroundService() {
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AMapService::lock").apply {
                    acquire()
                }
            }
        startTimer()
        isTracking.postValue(true)
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(notificationManager)
        }
        timeRunInSeconds.observe(this, {
            if (!serviceKilled) {
                val notification =
                    currentNotificationBuilder.setContentText(getFormattedStopWatchTIme(it * 1000L))
                        .setContentTitle(
                            getString(
                                when (mode) {
                                    1 -> R.string.outdoor_run
                                    0 -> R.string.walk
                                    else -> R.string.cycle
                                }
                            )
                        )

                notificationManager.notify(NOTIFICATION_ID, notification.build())
            }
        })
        startForeground(NOTIFICATION_ID, baseNotificationBuilder.build())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(notificationManager: NotificationManager) {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }
}