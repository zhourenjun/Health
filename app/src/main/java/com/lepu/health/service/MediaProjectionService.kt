@file:Suppress("EXPERIMENTAL_API_USAGE", "DEPRECATION")

package com.lepu.health.service

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.Parcelable
import android.util.DisplayMetrics
import com.lepu.health.R
import com.lepu.health.util.send
import com.lepu.health.util.sendTag
import com.lepu.health.util.windowManager
import com.lepu.health.ui.AMapScreenRecorderFragment
import kotlinx.android.parcel.Parcelize
import java.io.IOException


open class MediaProjectionService : Service() {

    private var mVideoWidth = 0
    private var mVideoHeight = 0
    private lateinit var mediaProjection: MediaProjection
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var mMediaRecorder: MediaRecorder

    companion object {

        private const val CHANNEL_ID = "MediaProjectionService"

        fun newService(context: Context) =
            Intent(context, MediaProjectionService::class.java).apply {
                action = ACTION_INIT
            }

        fun newStartMediaProjection(context: Context) = newService(context).apply {
            action = ACTION_START
        }

        fun newPermissionInitMediaProjection(
            context: Context,
            resultCode: Int,
            requestData: Intent,
            path: String
        ) = newService(context).apply {
            putExtra(EXTRA_RESULT_CODE, resultCode)
            putExtra(EXTRA_REQUEST_DATA, requestData)
            putExtra(PATH, path)
            action = ACTION_PERMISSION_INIT
        }

        fun newStopMediaProjection(context: Context) = newService(context).apply {
            action = ACTION_STOP
        }

        fun newRejectService(context: Context) = newService(context).apply {
            action = ACTION_REJECT
        }

        fun newStopService(context: Context) = newService(context).apply {
            action = ACTION_SELF_STOP
        }
    }

    private val mediaProjectionManager: MediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    override fun onCreate() {
        super.onCreate()
        val mMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(mMetrics)
        mVideoWidth = mMetrics.widthPixels
        mVideoHeight = mMetrics.heightPixels
        startForegroundService()
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    open fun startForegroundService() {
        createNotificationChannel()
        val intent = Intent(this, AMapScreenRecorderFragment::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }.setContentTitle(getString(R.string.screen_recording))
            .setSmallIcon(R.drawable.ic_stat_ic_notification)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1000, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_INIT -> sendTag("getPermission")
            ACTION_PERMISSION_INIT -> permissionInitMediaProjection(intent)
            ACTION_REJECT -> sendEvent(MediaProjectionStatus.OnReject)
            ACTION_START -> startMediaProjection()
            ACTION_STOP -> stopMediaProjection()
            ACTION_SELF_STOP -> {
                stopForeground(true)
                stopSelf()
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun permissionInitMediaProjection(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data = intent.getParcelableExtra<Intent>(EXTRA_REQUEST_DATA)
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data!!)
        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                sendEvent(MediaProjectionStatus.OnStop)
            }
        }, null)

        mMediaRecorder = MediaRecorder()
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mMediaRecorder.setOutputFile(intent.getStringExtra(PATH) ?: "")
        mMediaRecorder.setVideoSize(mVideoWidth, mVideoHeight)
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mMediaRecorder.setVideoEncodingBitRate(12 * 1024 * 1024)
        mMediaRecorder.setVideoFrameRate(60)
        try {
            mMediaRecorder.prepare()
            sendEvent(MediaProjectionStatus.OnInitialized)
        } catch (e: IOException) {
        }
    }

    private fun startMediaProjection() {
        if (::mediaProjection.isInitialized) {

            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                mVideoWidth,
                mVideoHeight,
                application.resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mMediaRecorder.surface,
                null,
                null
            )
            mMediaRecorder.start()
            sendEvent(MediaProjectionStatus.OnStarted)
        } else {
            sendEvent(MediaProjectionStatus.OnFail)
        }
    }

    private fun stopMediaProjection() {
        try {
            if (::mMediaRecorder.isInitialized) {
                mMediaRecorder.stop()
                mMediaRecorder.release()
            }
            if (::virtualDisplay.isInitialized) {
                virtualDisplay.release()
            }
            if (::mediaProjection.isInitialized) {
                mediaProjection.stop()
            }
        } catch (e: Exception) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).apply {
                createNotificationChannel(serviceChannel)
            }
        }
    }

    private fun sendEvent(status: MediaProjectionStatus) {
        val data = MediaProjectionStatusData(status)
        onChangeStatus(data)
    }

    open fun onChangeStatus(statusData: MediaProjectionStatusData) {
        send(statusData)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMediaProjection()
    }
}


internal const val ACTION_INIT = "action_init"
internal const val ACTION_PERMISSION_INIT = "action_permission_init"
internal const val ACTION_REJECT = "action_reject"
internal const val ACTION_START = "action_start"
internal const val ACTION_STOP = "action_stop"
internal const val ACTION_SELF_STOP = "self_stop"

internal const val EXTRA_RESULT_CODE = "result_code"
internal const val EXTRA_REQUEST_DATA = "request_data"
internal const val PATH = "path"

@Parcelize
data class MediaProjectionStatusData(
    val status: MediaProjectionStatus
) : Parcelable

enum class MediaProjectionStatus {
    OnInitialized,
    OnStarted,
    OnStop,
    OnFail,
    OnReject
}