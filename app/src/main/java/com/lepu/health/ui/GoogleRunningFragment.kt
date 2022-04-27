package com.lepu.health.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.renderscript.Int2
import android.view.View
import androidx.navigation.fragment.findNavController
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.lepu.health.R
import com.lepu.health.base.App
import com.lepu.health.base.BaseFragment
import com.lepu.health.base.bindView
import com.lepu.health.databinding.FragmentGoogleRunningBinding
import com.lepu.health.db.DataViewModel
import com.lepu.health.db.RealData
import com.lepu.health.db.Trace
import com.lxj.xpopup.XPopup
import com.lepu.health.util.Constant.ACTION_PAUSE_SERVICE
import com.lepu.health.util.Constant.ACTION_START_OR_RESUME_SERVICE
import com.lepu.health.util.Constant.ACTION_STOP_SERVICE
import com.lepu.health.service.LocationService
import com.lepu.health.service.LocationService.Companion.isTracking
import com.lepu.health.service.LocationService.Companion.pathPoints
import com.lepu.health.util.*
import com.lepu.health.util.CommonUtil.getFormattedStopWatchTIme
import com.lepu.health.widget.ExerciseHintPopup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.math.abs
import kotlin.properties.Delegates
import org.koin.android.ext.android.get
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.math.roundToInt

/**
 *
 * google 轨迹
 * zrj 2020/7/29
 */
@ExperimentalCoroutinesApi
class GoogleRunningFragment : BaseFragment(R.layout.fragment_google_running), TickListener {
    private val binding: FragmentGoogleRunningBinding by bindView()

    private lateinit var navbarDim: Int2
    private lateinit var navbarPosition: NavBarPosition
    private var goalType: Int by Preference(Constant.GOAL_TYPE, 0)
    private var goalValue: Int by Preference(Constant.GOAL_VALUE, 0)
    private var isReminder: Boolean by Preference(Constant.IS_REMINDER, false)
    private var reminderType: Int by Preference(Constant.REMINDER_TYPE, 0)
    private var reminderValue: Int by Preference(Constant.REMINDER_VALUE, 1)
    private var weight: Int by Preference(Constant.WEIGHT, 50)
    private var lbs: Int by Preference(Constant.LBS, 110)
    private var units: Int by Preference(Constant.UNITS, 0)
    private val model: DataViewModel by viewModel()
    private val service: LocationService = get()
    private var isRunning = false
    private var pathPoint = mutableListOf<RealData>()
    private var timeStarted = 0L
    private var map: GoogleMap? = null
    private var timeInSeconds = 0L
    private lateinit var mMarkerOptions: MarkerOptions
    private var mode by Delegates.notNull<Int>()
    private var index = 1
    private var mGpsStatus: GpsStatus? = null
    private var fcmId: String by Preference(Constant.FCM_ID, "")
    private var distance = 0e10
    private var maker: Marker? = null

    @SuppressLint("SetTextI18n", "InvalidWakeLockTag")
    override fun initData() {
        receive<MutableList<Float>>(false, "distanceInMeters"){
            distance = it.sum().toDouble()
            val temp = (if (units == 0) distance else distance * 0.62).toInt()
            binding.tvDistance.text = String.format("%.2f", temp / 1000f)
            val sec = if (temp > 0) timeInSeconds * 1000 / temp else 0
            binding.tvPaceValue.text = "${sec / 60}'${sec % 60}''"
            //跑步  体重（kg）×距离（公里）×1.036
            //骑行  时速(km/h)×体重(kg)×1.05×运动时间(h)
            val w = if (units == 0) weight else (lbs / 2.2).toInt()
            val kCal = String.format(
                "%.2f", if (mode == 3) //骑行
                    distance * w * 1.05 / 1000
                else
                    distance * w * 1.036 / 1000
            )
            binding.tvKcalValue.text = kCal
            if (goalType > 0) {
                binding.rpb.setProgressPercentage(
                    when (goalType) {
                        1 -> distance / if (units == 0) 1000 else 1069 / goalValue
                        2 -> (timeInSeconds * 100 / 60 / goalValue).toDouble()
                        else -> kCal.toDouble() / goalValue
                    }
                )
            }
        }

        receive<Long>(false, "timeRunInSeconds"){
            timeInSeconds = it
            val formattedTime = getFormattedStopWatchTIme(it * 1000, false)
            binding.tvTimeValue.text = formattedTime
            if (reminderValue > 0) {
                val talk = when (reminderType) {
                    0 -> abs((if (units == 0) 1000 else 1609) * index * reminderValue - distance.toInt()) < 5 //5米误差
                    else -> timeInSeconds.toInt() == 60 * index * reminderValue
                }
                if (talk && isReminder) {
                    val pm = activity?.getSystemService(Context.POWER_SERVICE) as PowerManager
                    val wl =
                        pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "MyWakelock")
                    Gossip(requireContext()).talk(
                        "${getString(R.string.distance)} ${binding.tvDistance.text} ${
                            getString(if (units == 0) R.string.km else R.string.mile)
                        } ${
                            getString(R.string.time3)
                        } ${timeInSeconds / 60} ${getString(R.string.min)}",
                        onStart = suspend { wl.acquire(2000) },
                        onDone = suspend { wl.release() },
                        onError = suspend { wl.release() }
                    )
                    index += 1
                }
            }
        }

    }
    @SuppressLint("MissingPermission", "UseCompatLoadingForDrawables", "SetTextI18n")
    override fun initView(savedInstanceState: Bundle?) {
        mGpsStatus = GpsStatus(App.context)
        mGpsStatus?.let {
            if (!it.isLogging()) it.start(this)
        }
        val (position, navbarHeight) = DisplayAssist.getNavigationBarSize(requireContext())
        this.navbarDim = navbarHeight
        this.navbarPosition = position
        mode = arguments?.getInt("mode") ?: 0
        try {
            MapsInitializer.initialize(App.context) // IBitmapDescriptorFactory is not initialized
        } catch (e: GooglePlayServicesNotAvailableException) {
            e.printStackTrace()
        }
        binding.mapView.onCreate(savedInstanceState)
        binding.tvKm.text = getString(if (units == 0) R.string.km else R.string.mile)
        val bMap = CommonUtil.drawableToBitmap(
            resources.getDrawable(
                R.drawable.ic_baseline_place_24,
                null
            )
        )

        val des = BitmapDescriptorFactory.fromBitmap(bMap)

        mMarkerOptions = MarkerOptions().icon(des).anchor(0.5f, 0.5f)
        binding.mapView.getMapAsync { googleMap ->
            map = googleMap
            googleMap.isMyLocationEnabled = true
            googleMap.uiSettings.isMyLocationButtonEnabled = false
            googleMap.uiSettings.isZoomControlsEnabled = false
            googleMap.uiSettings.isTiltGesturesEnabled = false

           arguments?.getParcelable<com.amap.api.maps.model.LatLng>("LatLng")?.let {
               val latLng = LatLng(it.latitude, it.longitude)
               googleMap.addMarker(mMarkerOptions.position(latLng))
               googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
           }
        }
        timeStarted = System.currentTimeMillis()
        sendCommandToService(ACTION_START_OR_RESUME_SERVICE)

        service.apply {
            isTracking.observe(this@GoogleRunningFragment, {
                isRunning = it
                binding.finish.setVisible(!isRunning)
                if (!isRunning) {
                    binding.ivPauseStop.setImageResource(R.drawable.ic_baseline_stop_24)
                    binding.ivStart.setVisible(true)
                } else {
                    binding.ivPauseStop.setImageResource(R.drawable.ic_baseline_pause_24)
                    binding.ivStart.setVisible(false)
                }
            })

            pathPoints.observe(this@GoogleRunningFragment, {
                if (it.isNotEmpty()) {
                    pathPoint = it
                    if (it.size > 2) {
                        map?.clear()
                        val lastLatLng =
                            LatLng(pathPoint.last().latitude, pathPoint.last().longitude)
                        val polylineOptions = PolylineOptions()
                            .color(Color.RED)
                            .width(15f)
                            .addAll(pathPoint.map { realData ->
                                LatLng(
                                    realData.latitude,
                                    realData.longitude
                                )
                            })
                        map?.addPolyline(polylineOptions)
                        maker = map?.addMarker(
                            mMarkerOptions.position(
                                LatLng(
                                    lastLatLng.latitude,
                                    lastLatLng.longitude
                                )
                            )
                        )
                        map?.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(
                                    lastLatLng.latitude,
                                    lastLatLng.longitude
                                ), 15f
                            )
                        )
                    }
                }
            })
        }

        val sheetBehavior = BottomSheetBehavior.from(binding.nestedScrollView).apply {
            val navbarHeightInverseRatio = -1f + navbarDim.y / peekHeight.toFloat()
            val expandedOffset = 160.dp
            setExpandedOffset(expandedOffset)
            state = BottomSheetBehavior.STATE_HALF_EXPANDED
            addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                private var lastOffset = Float.MIN_VALUE
                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    val updatedOffset = slideOffset.coerceIn(
                        navbarHeightInverseRatio,
                        halfExpandedRatio
                    )
                    if (updatedOffset != lastOffset) {
                        lastOffset = updatedOffset
                        if (updatedOffset >= 0) {
                            val parentHeight = (bottomSheet.parent as View).height
                            val maxHeightDifference = parentHeight - expandedOffset - peekHeight
                            val offset =
                                (peekHeight + updatedOffset * maxHeightDifference).roundToInt()

                            binding.mapView.setPadding(0, 0, 0, offset)

                        } else {
                            val offset = ((1 + updatedOffset) * peekHeight).roundToInt()
                            binding.mapView.setPadding(0, 0, 0, offset)
                        }
                    }
                }

                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_COLLAPSED -> binding.view.setImageResource(R.drawable.ic_baseline_expand_less_24)
                        else -> binding.view.setImageResource(R.drawable.ic_baseline_expand_more_24)
                    }
                }
            })
        }

        binding.clInnerLayout.click {
            sheetBehavior.state = when (sheetBehavior.state) {
                BottomSheetBehavior.STATE_COLLAPSED -> BottomSheetBehavior.STATE_HALF_EXPANDED
                BottomSheetBehavior.STATE_HALF_EXPANDED -> BottomSheetBehavior.STATE_COLLAPSED
                else -> BottomSheetBehavior.STATE_COLLAPSED
            }
        }



        binding.ivStart.click { sendCommandToService(ACTION_START_OR_RESUME_SERVICE) }
        //  长按动画结束
        binding.finish.setOnFinishListener {
            sendCommandToService(ACTION_PAUSE_SERVICE)
            if (distance < 300) {
                (XPopup.Builder(requireContext())
                    .asCustom(ExerciseHintPopup(requireContext())) as ExerciseHintPopup).setOnSelectListener {
                    if (it) { //结束
                        sendCommandToService(ACTION_STOP_SERVICE)
                        if (pathPoint.isNotEmpty()) {
                            send(pathPoint.last(), "end_location")
                        }
                        findNavController().navigateUp()
                    } else {
                        sendCommandToService(ACTION_START_OR_RESUME_SERVICE)
                    }
                }.show()
            } else {
                sendCommandToService(ACTION_STOP_SERVICE)
                val paces = pathPoint.map { it.pace }
                val trace = Trace(
                    fcmId,
                    arguments?.getInt("mode") ?: 0,
                    0,
                    0,
                    0,
                    0,
                    (binding.tvKcalValue.text.toString().toFloat() * 1000).toInt(),
                    paces.maxOrNull() ?: 0,
                    timeStarted / 1000,
                    timeInSeconds.toInt(),
                    0,
                    distance.toInt(),
                    0,
                    0,
                    0e10,
                    0e10,
                    0e10,
                    0e10,
                    pathPoint[0].longitudeEW,
                    pathPoint[0].latitudeNS,
                    pathPoint[pathPoint.size - 1].longitudeEW,
                    pathPoint[pathPoint.size - 1].latitudeNS,
                    0,
                    listOf(),
                    0,
                    0,
                    paces.maxOrNull() ?: 0,
                    0,
                    0,
                    0,
                    0,
                    listOf(),
                    pathPoint
                )
                model.insertTraces(listOf(trace))
                send(trace, "phone_trace")
                send(pathPoint.last(), "end_location")
               findNavController().navigateUp()
            }
        }

        binding.ivPauseStop.click {
            if (isRunning) {
                sendCommandToService(ACTION_PAUSE_SERVICE)
            }
        }

        binding.slideRail.setCallback {
            binding.llSlide.setVisible(false)
            binding.ctl.setVisible(true)
            binding.slideRail.closeToggle()
        }

        binding.ivLock.click {
            binding.llSlide.setVisible(true)
            binding.ctl.setVisible(false)
        }

        binding.tvCalories.text = "${getString(R.string.calories)}(${getString(R.string.kcal)})"
        binding.tvGoal.text = if (goalType > 0) "${getString(R.string.goals)} $goalValue ${
            getString(
                when (goalType) {
                    1 -> R.string.km
                    2 -> R.string.min
                    else -> R.string.kcal
                }
            )
        } ${
            getString(
                when (mode) {
                    1 -> R.string.outdoor_run
                    0 -> R.string.walk
                    else -> R.string.cycle
                }
            )
        }" else getString(R.string.none)
    }

    private fun sendCommandToService(action: String) {
        Intent(activity, LocationService::class.java).also {
            it.action = action
            it.putExtra("mode", mode)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity?.startForegroundService(it)
                return
            }
            activity?.startService(it)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRunning) {
            sendCommandToService(ACTION_STOP_SERVICE)
        }
    }
    override fun onBackPressed() {
        super.onBackPressed()
        if (isRunning) {
            toast(R.string.running_back_hint)
        } else {
            toast(R.string.run_pause_back_hint)
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
}


