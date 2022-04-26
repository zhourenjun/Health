@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_OVERRIDE", "DEPRECATION")

package com.lepu.health.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.*
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.LinearInterpolator
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.lepu.health.R
import com.lepu.health.base.BaseFragment
import com.lepu.health.base.bindView
import com.lepu.health.databinding.FragmentGoogleSrBinding
import com.lepu.health.service.MediaProjectionService
import com.lepu.health.service.MediaProjectionStatus
import com.lepu.health.service.MediaProjectionStatusData
import com.lepu.health.util.*
import kotlinx.coroutines.delay
import org.joda.time.DateTime
import java.io.File
import com.lepu.health.db.Trace
import com.lepu.health.widget.MoveMarker

/**
 *
 * 动态轨迹
 * zrj 2020/11/9
 */
class GoogleMapScreenRecorderFragment : BaseFragment(R.layout.fragment_google_sr) {
    private val binding: FragmentGoogleSrBinding by bindView()
    private var photoPath: String by Preference(Constant.PHOTO_PATH, "")
    private var nickname: String by Preference(Constant.NICKNAME, "")
    private var units: Int by Preference(Constant.UNITS, 0)
    private val requestMediaProjectionCode = 1000
    private var path = ""
    private lateinit var mGoogleMap: GoogleMap
    private lateinit var mSaveFile: File
    private lateinit var trace: Trace

    override fun initData() {
        binding.tvNickname.text = nickname
        if (photoPath.isNotEmpty()) {
            binding.ivPerson.load(photoPath) { transformations(CircleCropTransformation()) }
        }
        receive<MediaProjectionStatusData> { onChangeStatus(it) }

        receiveTag(false, "getPermission") {
            val mMediaProjectionManager =
                requireActivity().getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(
                mMediaProjectionManager.createScreenCaptureIntent(), requestMediaProjectionCode
            )
        }
    }

    private val aMapPoints = mutableListOf<LatLng>()
    private val mPointIndex = mutableListOf<Int>()
    private var isMapTypeNormal = true
    private var isSave = false

    @SuppressLint("SetTextI18n")
    override fun initView(savedInstanceState: Bundle?) {
        binding.mapView.onCreate(savedInstanceState)

        arguments?.getParcelable<Trace>("trace")?.let {
            val hrList = it.realDataList.map { realData -> realData.hr }
            val cadenceList = it.realDataList.map { realData -> realData.cadence }
            val paceList = it.realDataList.map { realData -> realData.pace }
            val hr = hrList.maxOrNull() ?: 0
            binding.tvHr.text = "$hr ${getString(R.string.bpm)}"
            val cadence = cadenceList.maxOrNull() ?: 0
            binding.tvCadence.text = "$cadence ${getString(R.string.steps_min)}"
            val temp = paceList.filter { temp -> temp > 0 }.minOrNull() ?: 0
            val pace = if ((it.mode == 3 || it.mode == 4) && temp > 0) {
                1000 * 3600 / temp
            } else {
                temp
            }
            val sec = if (units == 0) pace else (pace / 0.62).toInt()
            binding.tvPace.text =
                "${sec / 60}'${sec % 60}'' ${getString(if (units == 0) R.string.km2 else R.string.mile2)}"

            mPointIndex.add(hrList.indexOf(hr))
            mPointIndex.add(cadenceList.indexOf(cadence))
            mPointIndex.add(paceList.indexOf(temp))
            trace = it
            val day = DateTime(it.date * 1000L)
            val min = day.minuteOfDay % 60

            binding.tvData.text =
                "${day.monthOfYear().asShortText} ${day.dayOfMonth().asText}, ${day.year}  ${day.hourOfDay}:${if (min < 10) "0$min" else min}"
            binding.ivMode.setImageResource(
                when (it.mode) {
                    0 -> R.drawable.ic_walk_white_24dp
                    1 -> R.drawable.ic_outdoor_run_white_24dp
                    2 -> R.drawable.ic_run_in_24dp
                    3 -> R.drawable.ic_ride_white_24dp
                    4 -> R.drawable.ic_ride_indoor_24dp
                    5 -> R.drawable.ic_onfoot_white_24dp
                    6 -> R.drawable.ic_mountaineering_white_24dp
                    7 -> R.drawable.ic_badminton_white
                    8 -> R.drawable.ic_football_white_24dp
                    9 -> R.drawable.ic_basketball_white_24dp
                    10 -> R.drawable.ic_tennis_white_24dp
                    11 -> R.drawable.ic_dancing_white_24dp
                    12 -> R.drawable.ic_yoga_white_24dp
                    13 -> R.drawable.ic_freetraining_white_24dp
                    14 -> R.drawable.ic_trail_running_white_24dp
                    15 -> R.drawable.ic_swimming_white_24dp
                    16 -> R.drawable.ic_inner_walk_white_24dp
                    17 -> R.drawable.ic_rower_white_24dp
                    18 -> R.drawable.ic_elliptical_white_24dp
                    else -> R.drawable.ic_open_swimming_white_24dp
                }
            )
            binding.tvTime.text = CommonUtil.getFormattedStopWatchTIme(it.accomplishTime * 1000L, false)
            val m =
                if (it.mode == 2 || it.mode == 4 || it.mode == 15 || it.mode == 16) (if (it.calibrateDistance == 0) it.distance else it.calibrateDistance) else it.distance
            binding.tvDistance.text = String.format("%.2f", if (units == 0) m / 1000f else m * 0.62 / 1000f)
            binding.tvDistanceUnit.text = getString(if (units == 0) R.string.km else R.string.mile)
            binding.tvStrideValue.text = "${it.step * 60 / it.accomplishTime}" //平均步频
            binding.tvKcalValue.text = "${it.calorie / 1000}"

            val list =
                PathSmoothTool().pathOptimize(loadAMapPoints(it).map { latlang ->
                    com.amap.api.maps.model.LatLng(
                        latlang.latitude,
                        latlang.longitude
                    )
                })
            aMapPoints.addAll(list.map { latlang ->
                LatLng(
                    latlang.latitude,
                    latlang.longitude
                )
            })
            binding.mapView.getMapAsync { map ->
                mGoogleMap = map
                map.uiSettings.isMyLocationButtonEnabled = false
                map.uiSettings.isZoomControlsEnabled = false
                map.uiSettings.isScrollGesturesEnabled = false
                map.uiSettings.isTiltGesturesEnabled = false
                map.setOnMapLoadedCallback {
                    addStartAndStop()
                }
            }
        }

        binding.ivBack.click { onBackPressed() }
        binding.ivLayer.click {
            mGoogleMap.mapType =
                if (isMapTypeNormal) GoogleMap.MAP_TYPE_SATELLITE else GoogleMap.MAP_TYPE_NORMAL
            binding.ivLayer.setImageResource(if (isMapTypeNormal) R.drawable.ic_layer2_black_24dp else R.drawable.ic_layer1_black_24dp)
            isMapTypeNormal = !isMapTypeNormal
        }
        binding.ivStart.click {
            isSave = false
            mapMove()
        }

        binding.tvSave.click {
            if (path.isNotEmpty()) {
                requireContext().share(File(path), false)
                return@click
            }
            isSave = true
            runService(MediaProjectionService.newService(requireContext()))
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun addStartAndStop() {
        val start = CommonUtil.drawableToBitmap(
            resources.getDrawable(
                R.drawable.ic_exercise_start_12dp,
                null
            )
        )
        val startDes = BitmapDescriptorFactory.fromBitmap(start)
        var mMarkerOptions = MarkerOptions().icon(startDes).anchor(0.5f, 0.5f)
        mGoogleMap.addMarker(mMarkerOptions.position(aMapPoints[0]))

        val polylineOptions = PolylineOptions()
            .color(Color.BLACK)
            .width(15f)
            .addAll(aMapPoints)
        mGoogleMap.addPolyline(polylineOptions)

        val end = CommonUtil.drawableToBitmap(
            resources.getDrawable(
                R.drawable.ic_exercise_stop_12dp,
                null
            )
        )

        val endDes = BitmapDescriptorFactory.fromBitmap(end)
        mMarkerOptions = MarkerOptions().icon(endDes).anchor(0.5f, 0.5f)
        mGoogleMap.addMarker(mMarkerOptions.position(aMapPoints[aMapPoints.size - 1]))

        val builder = LatLngBounds.Builder()
        val latitudeMax = aMapPoints.maxByOrNull { p -> p.latitude }?.latitude ?: 0.0
        val latitudeMin = aMapPoints.minByOrNull { p -> p.latitude }?.latitude ?: 0.0
        val longitudeMax = aMapPoints.maxByOrNull { p -> p.longitude }?.longitude ?: 0.0
        val longitudeMin = aMapPoints.minByOrNull { p -> p.longitude }?.longitude ?: 0.0
        builder.include(LatLng(latitudeMin, longitudeMin))
        builder.include(LatLng(latitudeMax, longitudeMax))
        mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 200))
    }

    private var googleKmMarker = mutableListOf<Marker>()

    private fun animation(view: View) {
        val animation = android.view.animation.TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 1f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f
        )
        animation.repeatCount = 0
        animation.duration = 1000
        val animationSet = AnimationSet(true)
        animationSet.addAnimation(animation)
        view.startAnimation(animationSet)
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
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
        binding.mapView.onDestroy()
    }

    private val cadences = mutableListOf<Float>()

    private fun loadAMapPoints(trace: Trace): List<LatLng> {
        val points = mutableListOf<LatLng>()
        trace.realDataList.forEach {
            if (it.latitudeNS == "N" || it.latitudeNS == "S") {
                val latitude = if (it.latitudeNS == "N") {
                    it.latitude
                } else {
                    -it.latitude
                }

                val longitude = if (it.longitudeEW == "E") {
                    it.longitude
                } else {
                    -it.longitude
                }
                //Google Map中国用火星坐标
                if (it.pause == -2) { // google手机数据直接使用
                    points.add(LatLng(it.latitude, it.longitude))
                } else if (it.pause == -1) {
                    //手机高德国外数据(火星坐标)
                    if (GPSConverterUtils.out_of_china(longitude, latitude)) {
                        val gps = GPSConverterUtils.gcj02towgs84(longitude, latitude)
                        points.add(LatLng(gps[1], gps[0]))
                    } else {
                        points.add(LatLng(it.latitude, it.longitude))
                    }
                } else {
                    val gps = GPSConverterUtils.wgs84togcj02(longitude, latitude)
                    points.add(LatLng(gps[1], gps[0]))
                }
                cadences.add(it.cadence.toFloat())
            }
        }
        return points
    }


    /**
     * marker 跳动
     */
    private fun startJumpAnimation(marker: Marker?) {
        if (marker != null) {
            //根据屏幕距离计算需要移动的目标点
            val latLng = marker.position
            val point = mGoogleMap.projection.toScreenLocation(latLng)
            point.y -= requireContext().dip2px(125f)
            val target = mGoogleMap.projection.fromScreenLocation(point)
            val handler = Handler()
            val start = SystemClock.uptimeMillis()
            val duration = 600
            val interpolator = LinearInterpolator()
            handler.post(object : Runnable {
                override fun run() {
                    val elapsed = SystemClock.uptimeMillis() - start
                    val t = interpolator.getInterpolation(elapsed.toFloat() / duration)
                    val lng = t * target.longitude + (1 - t) * latLng.longitude
                    val lat = t * target.latitude + (1 - t) * latLng.latitude
                    marker.position = LatLng(lat, lng)
                    if (t < 1.0) {
                        handler.postDelayed(this, 16)
                    } else {
                        marker.position = latLng
                    }
                }
            })
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data != null && requestCode == requestMediaProjectionCode && resultCode == Activity.RESULT_OK) {
            val path =
                requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
                    ?: requireContext().externalMediaDirs.first().absolutePath
            mSaveFile = File(path, "temp.tmp")
            if (mSaveFile.exists()) {
                mSaveFile.delete()
            }
            runService(
                MediaProjectionService.newPermissionInitMediaProjection(
                    requireContext(),
                    resultCode,
                    data,
                    mSaveFile.absolutePath
                )
            )
        } else {
            runService(MediaProjectionService.newRejectService(requireContext()))
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        if (binding.ivBack.isVisible) {
            if (path.isNotEmpty()) {
                File(path).delete()
            }
            findNavController().navigateUp()
        }
    }

    @SuppressLint("SetTextI18n", "UseCompatLoadingForDrawables")
    private fun mapMove() {
        binding.ivBack.setVisible(false)
        binding.ivStart.setVisible(false)
        binding.ivLayer.setVisible(false)
        binding.tvDistance.text = "0.00"
        binding.tvTime.setInVisible(false)
        binding.tvStrideValue.setInVisible(false)
        binding.tvStride.setInVisible(false)
        binding.tvKcalValue.setInVisible(false)
        binding.tvKcal.setInVisible(false)
        binding.tvSave.setInVisible(false)

        //地图上添加标记点
        for (i in 0 until aMapPoints.size) {
            if (mPointIndex.contains(i)) {
                val marker = mGoogleMap.addMarker(MarkerOptions().position(aMapPoints[i]))
                marker.tag = i
                marker.isVisible = false
                googleKmMarker.add(marker)
            }
        }
        mGoogleMap.clear()
        mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(aMapPoints[0], 18f))
        val routePolyLineOptions = PolylineOptions()
        routePolyLineOptions.width(15F)
        routePolyLineOptions.startCap(RoundCap())
        routePolyLineOptions.endCap(RoundCap())
        routePolyLineOptions.jointType(JointType.ROUND)

        val mPolyline = mGoogleMap.addPolyline(routePolyLineOptions)
        val moveMarker = MoveMarker(mGoogleMap, Handler())
        // 设置滑动的图标
        val start = CommonUtil.drawableToBitmap(
            resources.getDrawable(R.drawable.ic_baseline_place_24, null)
        )
        moveMarker.setDescriptor(BitmapDescriptorFactory.fromBitmap(start))
        moveMarker.setPoints(aMapPoints) //设置平滑移动的轨迹list
        moveMarker.setTotalDuration(10) //设置平滑移动的总时间
        val mList = ArrayList<LatLng>()
        var flag = false
        moveMarker.setMoveListener { distance ->
            if (distance > 0 && !flag) {
                flag = true
                binding.cpc.setVisible(true)
                binding.cpc.setValue(cadences)
            }
            binding.cpc.setProgress((trace.distance - distance.toFloat()) / trace.distance)
            binding.tvDistance.text = String.format("%.2f", (trace.distance - distance) / 1000)
            val center = moveMarker.position
            val index = moveMarker.index
            val markerAngle = moveMarker.marker.rotation
            //画线
            mList.add(center)
            mPolyline.points = mList
            //判断是否到标记点
            if (mPointIndex.contains(index)) {
                when (mPointIndex.indexOf(index)) {
                    0 -> { //心率
                        binding.ctlHr.setVisible(true)
                        animation(binding.ctlHr)
                    }
                    1 -> { //步频
                        binding.ctlCadence.setVisible(true)
                        animation(binding.ctlCadence)
                    }
                    2 -> { //配速
                        binding.ctlPace.setVisible(true)
                        animation(binding.ctlPace)
                    }
                }
                moveMarker.stopMove()
                val bitmap = CommonUtil.drawableToBitmap(
                    resources.getDrawable(
                        when (mPointIndex.indexOf(index)) {
                            0 -> R.drawable.ic_map_hr_24dp
                            1 -> R.drawable.ic_map_cadence_24dp
                            else -> R.drawable.ic_map_pace_24dp
                        }, null
                    )
                )
                val icon = BitmapDescriptorFactory.fromBitmap(bitmap)
                for (marker in googleKmMarker) {
                    if (marker.tag != null) {
                        val markerIndex = marker.tag as Int
                        if (markerIndex == index) {
                            marker.setIcon(icon)
                            marker.isVisible = true
                            startJumpAnimation(marker)
                        }
                    }
                }
                binding.cpc.postDelayed({
                    moveMarker.startMove()
                }, 1000)
            }

            //地图移动旋转  每20个点移动一次中心点
            if (index % 10 == 0) {
                //需要旋转地图的话，在此改变地图角度  aMapAngle += 10
                val cameraPosition = CameraPosition(center, 16f, 60f, markerAngle)
                mGoogleMap.animateCamera(
                    CameraUpdateFactory.newCameraPosition(cameraPosition), 1000, null
                )
            } else if (index % 10 == 10) {
                val cameraPosition = CameraPosition(center, 16f, 60f, markerAngle)
                mGoogleMap.animateCamera(
                    CameraUpdateFactory.newCameraPosition(cameraPosition), 1000, null
                )
            }
            /**
             * 判断是否到终点
             */
            if (distance.toFloat() == 0.0f) {
                lifecycleScope.launchWhenStarted  {
                    binding.tvTime.setVisible(true)
                    binding.cpc.setVisible(false)
                    binding.ctlHr.setInVisible(false)
                    binding.ctlCadence.setInVisible(false)
                    binding.ctlPace.setInVisible(false)
                    animation(binding.tvTime)
                    delay(1000)
                    binding.tvStrideValue.setVisible(true)
                    binding.tvStride.setVisible(true)
                    animation(binding.tvStrideValue)
                    animation(binding.tvStride)
                    delay(1000)
                    binding.tvKcalValue.setVisible(true)
                    binding.tvKcal.setVisible(true)
                    animation(binding.tvKcalValue)
                    animation(binding.tvKcal)
                    delay(1000)
                    mGoogleMap.clear()
                    binding.ivBack.setVisible(true)
                    binding.ivStart.setVisible(true)
                    binding.ivLayer.setVisible(true)
                    binding.tvSave.setVisible(true)
                    addStartAndStop()
                    if (isSave) {
                        runService(MediaProjectionService.newStopMediaProjection(requireContext()))
                    }
                }
            }
        }

        moveMarker.startMove()
    }

    private fun onChangeStatus(statusData: MediaProjectionStatusData) {
        when (statusData.status) {
            MediaProjectionStatus.OnInitialized ->
                runService(MediaProjectionService.newStartMediaProjection(requireContext()))

            MediaProjectionStatus.OnStarted -> mapMove()

            MediaProjectionStatus.OnStop -> {
                PhotoUtils.muxAudio(requireContext(), mSaveFile).let {
                    toast(R.string.saved_to_album)
                    path = requireContext().share(it, false)
                }
                runService(MediaProjectionService.newStopService(requireContext()))
            }
            MediaProjectionStatus.OnFail,
            MediaProjectionStatus.OnReject ->
                runService(MediaProjectionService.newStopService(requireContext()))
        }
    }

    private fun runService(service: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.startForegroundService(service)
        } else {
            activity?.startService(service)
        }
    }
}
