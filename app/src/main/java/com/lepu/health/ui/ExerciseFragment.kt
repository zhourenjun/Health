@file:Suppress("DEPRECATION")

package com.lepu.health.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.*
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.LinearInterpolator
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.*
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.lepu.health.R
import com.lepu.health.base.App
import com.lepu.health.base.BaseFragment
import com.lepu.health.databinding.FragmentExerciseBinding
import com.lepu.health.db.RealData
import com.lepu.health.util.*
import com.lepu.health.util.CommonUtil.drawableToBitmap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.*
import com.lepu.health.base.bindView
import com.lepu.health.widget.GoalPopup
import com.lxj.xpopup.XPopup

/**
 * 运动
 * zrj 2020/7/8
 */
@ExperimentalCoroutinesApi
class ExerciseFragment : BaseFragment(R.layout.fragment_exercise), TickListener {
    private var units: Int by Preference(Constant.UNITS, 0)
    private var goalType: Int by Preference(Constant.GOAL_TYPE, 0)
    private var goalValue: Int by Preference(Constant.GOAL_VALUE, 0)
    private var position = 0
    private lateinit var mParams: FrameLayout.LayoutParams
    private lateinit var mAMapView: TextureMapView
    private lateinit var mGoogleMapView: MapView
    private var latitude = 0e10
    private var longitude = 0e10
    private var mIsAMapDisplay = true
    private var mLocationClient: AMapLocationClient? = null
    private lateinit var mLocationOption: AMapLocationClientOption
    private var map: Int by Preference(Constant.MAP, 4)
    private var start: Long = 0L
    private val interpolator1: LinearInterpolator = LinearInterpolator()
    private var mTimerTask: TimerTask? = null
    private val mTimer = Timer()
    private var mGpsStatus: GpsStatus? = null
    private var accuracy = 0f
    private var googleMap: GoogleMap? = null
    private val binding: FragmentExerciseBinding by bindView()

    override fun initView(savedInstanceState: Bundle?) {
        mAMapView = TextureMapView(context)
        mParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        binding.mapContainer.addView(mAMapView, mParams)

        mAMapView.onCreate(savedInstanceState)
        mAMapView.map.uiSettings.isZoomControlsEnabled = false
        mAMapView.map.uiSettings.isZoomGesturesEnabled = false
        mAMapView.map.uiSettings.isRotateGesturesEnabled = false
        mAMapView.map.uiSettings.isScaleControlsEnabled = false
        mAMapView.map.uiSettings.isScrollGesturesEnabled = false
        mAMapView.map.uiSettings.isTiltGesturesEnabled = false

        binding.tvDistanceUnit.text = getString(if (units == 0) R.string.km else R.string.mile)

        mGpsStatus = GpsStatus(App.context)
        mGpsStatus?.let { if (!it.isLogging()) it.start(this) }
        setGoal()
        binding.ivSettings.click {
            findNavController().navigate(
                R.id.exerciseSettingFragment,
                bundleOf("position" to position)
            )
        }

        binding.ivGoal.click {
            val pop = XPopup.Builder(requireContext())
                .asCustom(GoalPopup(requireContext())) as GoalPopup
            pop.setData(goalType, goalValue).setOnSelectListener { goalType, goalValue ->
                this.goalType = goalType
                this.goalValue = goalValue
                setGoal()
            }.show()
        }

        binding.ivStatistics.click {
            findNavController().navigate(
                R.id.exerciseListFragment, bundleOf(
                    "position" to when (position) {  //string.xml actionArray
                        0 -> 2
                        1 -> 1
                        else -> 4
                    }
                )
            )
        }

        binding.tabLayout.addOnTabChangeListener {
            animation(
                binding.tvDistanceValue,
                binding.tvDistanceUnit,
                binding.ctlStart,
                binding.ivGoal,
                binding.ivStatistics,
                binding.ctlFlag
            )
            position = it?.position ?: 0
        }

        //初始化client
        mLocationClient = AMapLocationClient(App.context)
        // 设置定位监听
        mLocationClient?.setLocationListener {
            accuracy = it.accuracy
            if (mTimerTask != null) {
                mTimerTask?.cancel()
                mTimerTask = null
            }
            if (it != null && it.errorCode == 0) {
                longitude = it.longitude
                latitude = it.latitude
                if (map == 1 || (!GPSConverterUtils.out_of_china(
                        it.longitude,
                        it.latitude
                    ) && map == 4)
                ) {   //0 google  1 amap  4 auto
                    mAMapView.map.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), 15f)
                    )
                    addAmapMarker(it.accuracy)
                } else {
                    changeToGoogleMapView(it.accuracy)
                }
            } else {
                LogUtil.e("定位失败," + it.errorCode.toString() + ": " + it.errorInfo)
            }
        }
        //定位参数
        mLocationOption = AMapLocationClientOption()
        //设置为高精度定位模式
        mLocationOption.isOnceLocation = true
        mLocationOption.locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
        //设置定位参数
        mLocationClient?.setLocationOption(mLocationOption)
        mLocationClient?.startLocation()

        binding.ctlStart.click {
            val mode = when (position) {
                0 -> 1
                1 -> 0
                else -> 3
            }
            val bundle = bundleOf("LatLng" to LatLng(latitude, longitude), "mode" to mode)
            findNavController().navigate(
                if (map == 4 || (mIsAMapDisplay && map == 1)) R.id.aMapRunningFragment else R.id.googleRunningFragment,
                bundle
            )
        }
    }

    /**
     * 切换为google地图显示
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    private fun changeToGoogleMapView(accuracy: Float) {
        mIsAMapDisplay = false
        mGoogleMapView = MapView(
            context, GoogleMapOptions().camera(
                com.google.android.gms.maps.model.CameraPosition(
                    com.google.android.gms.maps.model.LatLng(
                        latitude,
                        longitude
                    ), 15f, 0f, 0f
                )
            )
        )
        mGoogleMapView.onCreate(null)
        mGoogleMapView.onResume()
        binding.mapContainer.addView(mGoogleMapView, mParams)
        mGoogleMapView.getMapAsync {
            googleMap = it
            it.uiSettings.isZoomControlsEnabled = false
            it.uiSettings.isZoomGesturesEnabled = false
            it.uiSettings.isRotateGesturesEnabled = false
            it.uiSettings.isScrollGesturesEnabledDuringRotateOrZoom = false
            it.uiSettings.isScrollGesturesEnabled = false
            it.uiSettings.isTiltGesturesEnabled = false
            addGoogleMarker(accuracy)
        }
        handler.sendEmptyMessageDelayed(0, 500)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun addGoogleMarker(accuracy: Float) {
        val bMap =
            drawableToBitmap(resources.getDrawable(R.drawable.ic_baseline_place_24, null))
        val myLocation = com.google.android.gms.maps.model.LatLng(latitude, longitude)
        val des = com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bMap)
        googleMap?.let {
            it.addMarker(
                com.google.android.gms.maps.model.MarkerOptions().position(myLocation).icon(des)
                    .anchor(0.5f, 0.5f)
            )
            val c = it.addCircle(
                com.google.android.gms.maps.model.CircleOptions().center(myLocation)
                    .fillColor(Color.argb(50, 255, 136, 72))
                    .radius(accuracy.toDouble()).strokeColor(Color.argb(255, 255, 228, 185))
                    .strokeWidth(0f)
            )
            start = SystemClock.uptimeMillis()
            mTimerTask = GoogleCircleTask(c, 1000)
            mTimer.schedule(mTimerTask, 0, 30)
        }
    }

    private val handler: Handler = @SuppressLint("HandlerLeak")
    object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            if (mIsAMapDisplay) {
                if (::mGoogleMapView.isInitialized) {
                    mGoogleMapView.visibility = View.GONE
                    binding.mapContainer.removeView(mGoogleMapView)
                    mGoogleMapView.onDestroy()
                }
            } else {
                if (::mAMapView.isInitialized) {
                    mAMapView.visibility = View.GONE
                    binding.mapContainer.removeView(mAMapView)
                    mAMapView.onDestroy()
                }
            }
        }
    }

    private fun changeToAMapView(accuracy: Float) {
        mIsAMapDisplay = true
        mAMapView = TextureMapView(context)
        mAMapView.map.uiSettings.isZoomControlsEnabled = false
        mAMapView.map.uiSettings.isZoomGesturesEnabled = false
        mAMapView.map.uiSettings.isRotateGesturesEnabled = false
        mAMapView.map.uiSettings.isScaleControlsEnabled = false
        mAMapView.map.uiSettings.isScrollGesturesEnabled = false
        mAMapView.map.uiSettings.isTiltGesturesEnabled = false
        mAMapView.map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(latitude, longitude),
                15f
            )
        )
        mAMapView.onCreate(null)
        mAMapView.onResume()
        binding.mapContainer.addView(mAMapView, mParams)
        mAMapView.map.setOnMapLoadedListener {
            addAmapMarker(accuracy)
        }
        handler.sendEmptyMessageDelayed(0, 500)
    }

    override fun onResume() {
        super.onResume()
        if (mIsAMapDisplay) {
            mAMapView.onResume()
        } else {
            mGoogleMapView.onResume()
        }
    }

    private fun setGoal() {
        val goal = if (goalType > 0) "$goalValue ${
            getString(
                when (goalType) {
                    1 -> if (units == 0) R.string.km else R.string.mile
                    2 -> R.string.min
                    else -> R.string.kcal
                }
            )
        }" else ""
        if (goal.isNotEmpty()) {
            binding.tvGoal.text = goal
            binding.ctlFlag.setVisible(true)
        } else {
            binding.ctlFlag.setVisible(false)
        }
    }

    override fun onPause() {
        super.onPause()
        if (mIsAMapDisplay) {
            mAMapView.onPause()
        } else {
            mGoogleMapView.onPause()
        }
        mGpsStatus?.stop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (mIsAMapDisplay) {
            mAMapView.onSaveInstanceState(outState)
        } else {
            mGoogleMapView.onSaveInstanceState(outState)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mLocationClient?.stopLocation()
        mLocationClient?.onDestroy()
        mLocationClient = null
        if (mIsAMapDisplay) {
            mAMapView.onDestroy()
        } else {
            mGoogleMapView.onDestroy()
        }
        if (mTimerTask != null) {
            mTimerTask!!.cancel()
            mTimerTask = null
        }
        try {
            mTimer.cancel()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }


    override fun initData() {
        receiveTag(false, "units") {
            binding.tvDistanceUnit.text = getString(if (units == 0) R.string.km else R.string.mile)
        }

        receiveTag(false, "map") {
            if (map == 1 || (!GPSConverterUtils.out_of_china(
                    longitude,
                    latitude
                ) && map == 4)
            ) {   //0 google  1 amap  4 auto
                if (!mIsAMapDisplay) {
                    changeToAMapView(accuracy)
                }
            } else {
                if (mIsAMapDisplay) {
                    changeToGoogleMapView(accuracy)
                }
            }
        }

        receive<RealData>(false, "end_location") {
            if (mTimerTask != null) {
                mTimerTask?.cancel()
                mTimerTask = null
            }
            longitude = it.longitude
            latitude = it.latitude
            if (mIsAMapDisplay) {
                addAmapMarker(accuracy)
            } else {
                addGoogleMarker(accuracy)
            }
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun addAmapMarker(accuracy: Float) {
        mAMapView.map.clear()
        val bMap = drawableToBitmap(resources.getDrawable(R.drawable.ic_baseline_place_24, null))
        val myLocation = LatLng(latitude, longitude)
        val des = BitmapDescriptorFactory.fromBitmap(bMap)
        mAMapView.map.addMarker(MarkerOptions().position(myLocation).icon(des).anchor(0.5f, 0.5f))
        val c = mAMapView.map.addCircle(
            CircleOptions().center(myLocation)
                .fillColor(Color.argb(60, 255, 136, 72))
                .radius(accuracy.toDouble()).strokeColor(Color.argb(255, 255, 228, 185))
                .strokeWidth(0f)
        )
        start = SystemClock.uptimeMillis()
        mTimerTask = AMapCircleTask(c, 1000)
        mTimer.schedule(mTimerTask, 0, 30)
    }

    private inner class AMapCircleTask(private val circle: Circle, rate: Long) : TimerTask() {
        private val r: Double = circle.radius
        private var duration: Long = 500
        override fun run() {
            try {
                val elapsed = SystemClock.uptimeMillis() - start
                val input = elapsed.toFloat() / duration
                // 外圈放大后消失
                val t = interpolator1.getInterpolation(input)
                val r1 = (t + 1) * r
                circle.radius = r1
                if (input > 2) {
                    start = SystemClock.uptimeMillis()
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        init {
            if (rate > 0) {
                duration = rate
            }
        }
    }

    private inner class GoogleCircleTask(
        private val circle: com.google.android.gms.maps.model.Circle,
        rate: Long
    ) : TimerTask() {
        private val r = circle.radius
        private var duration: Long = 500
        override fun run() {
            try {
                val elapsed = SystemClock.uptimeMillis() - start
                val input = elapsed.toFloat() / duration
                val t = interpolator1.getInterpolation(input)
                val r1 = (t + 1) * r
                activity?.runOnUiThread {
                    circle.radius = r1  //Not on the main thread
                }
                if (input > 2) {
                    start = SystemClock.uptimeMillis()
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        init {
            if (rate > 0) {
                duration = rate
            }
        }
    }

    override fun onTick() {
        mGpsStatus?.let {
            if (it.isEnabled() && it.isLogging()) {
                if (it.isFixed) {
                    binding.tvGps.text = getString(
                        when {
                            it.satellitesFixed > 7 -> R.string.GPS_level_good
                            it.satellitesFixed > 4 -> R.string.GPS_level_acceptable
                            else -> R.string.GPS_level_poor
                        }
                    )
                }
            }
        }
    }

    private fun animation(vararg views: View) {
        views.forEach {
            val animation = TranslateAnimation(
                Animation.RELATIVE_TO_SELF, -1f,
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0f
            )
            animation.repeatCount = 0
            animation.duration = 100
            val animationSet = AnimationSet(true)
            animationSet.addAnimation(animation)
            it.startAnimation(animationSet)
        }
    }
}