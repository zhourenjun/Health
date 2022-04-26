@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.lepu.health.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.*
import android.location.GpsStatus
import android.os.Bundle
import androidx.core.content.ContextCompat

/**
 *
 * 确定GPS状态何时良好*足够（isFixed（））
 *
 */
class GpsStatus(ctx: Context) : LocationListener, GpsStatus.Listener {
    var isFixed = false
        private set
    private var context = ctx
    private var mHistory: Array<Location?> = arrayOfNulls(HIST_LEN)
    private var locationManager: LocationManager? = null
    private var listener: TickListener? = null

    /**
     * 如果我们获得的位置准确度<= mFi固定的准确度=> true
     */
    private val mFixAccurancy = 10f

    /**
     * 如果我们得到固定卫星> = mFix卫星mFix => true
     */
    private val mFixSatellites = 2

    /**
     * 如果我们获得时差<= mFixTime mFixed => * true的位置更新
     */
    private val mFixTime = 3
    var satellitesAvailable = 0
        private set
    var satellitesFixed = 0
        private set

    fun start(listener: TickListener) {
        clear(true)
        this.listener = listener
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            var lm: LocationManager? =
                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            try {
                lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1f, this)
            } catch (ex: Exception) {
                lm = null
            }
            locationManager = lm
            locationManager?.addGpsStatusListener(this)
        }
    }

    fun stop() {
        this.listener = null
        locationManager?.removeGpsStatusListener(this)
        try {
            locationManager?.removeUpdates(this)
        } catch (ex: SecurityException) {
            //忽略用户是否关闭GPS
        }
    }

    override fun onLocationChanged(location: Location) {
        LogUtil.d("NEW LOCATION ${location.latitude},${location.longitude}")
        send(location,"location")
        System.arraycopy(mHistory, 0, mHistory, 1, HIST_LEN - 1)
        mHistory[0] = location
        if (location.hasAccuracy() && location.accuracy < mFixAccurancy) {
            isFixed = true
        } else if (mHistory[1] != null
            && location.time - mHistory[1]?.time!! <= 1000 * mFixTime
        ) {
            isFixed = true
        } else if (satellitesAvailable >= mFixSatellites) {
            isFixed = true
        }
        listener?.onTick()
    }

    override fun onProviderDisabled(provider: String) {
        if (provider.equals("gps", ignoreCase = true)) {
            clear(true)
            listener?.onTick()
        }
    }

    override fun onProviderEnabled(provider: String) {
        if (provider.equals("gps", ignoreCase = true)) {
            clear(false)
            listener?.onTick()
        }
    }

    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
        if (provider.equals("gps", ignoreCase = true)) {
            if (status == LocationProvider.OUT_OF_SERVICE
                || status == LocationProvider.TEMPORARILY_UNAVAILABLE
            ) {
                clear(true)
            }
            listener?.onTick()
        }
    }

    override fun onGpsStatusChanged(event: Int) {
        val gpsStatus: GpsStatus = try {
            locationManager?.getGpsStatus(null)
        } catch (ex: SecurityException) {
            null
        }
            ?: return
        var cnt0 = 0
        var cnt1 = 0
        val list = gpsStatus.satellites
        for (satellite in list) {
            cnt0++
            if (satellite.usedInFix()) {
                cnt1++
            }
        }
        satellitesAvailable = cnt0
        satellitesFixed = cnt1
        listener?.onTick()
    }

    private fun clear(resetIsFixed: Boolean) {
        if (resetIsFixed) {
            isFixed = false
        }
        satellitesAvailable = 0
        satellitesFixed = 0
        for (i in 0 until HIST_LEN) mHistory[i] = null
    }

    fun isLogging() = locationManager != null

    fun isEnabled(): Boolean {
        val lm = context
            .getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    companion object {
        private const val HIST_LEN = 3
    }
}

interface TickListener {
    fun onTick()
}
