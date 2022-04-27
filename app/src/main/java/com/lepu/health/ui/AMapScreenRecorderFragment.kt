@file:Suppress("DEPRECATION", "EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_OVERRIDE")

package com.lepu.health.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationSet
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import coil.transform.CircleCropTransformation
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.model.*
import com.amap.api.maps.model.animation.TranslateAnimation
import com.amap.api.maps.utils.overlay.SmoothMoveMarker
import com.lepu.health.R
import com.lepu.health.base.BaseFragment
import com.lepu.health.base.bindView
import com.lepu.health.databinding.FragmentAmapSrBinding
import com.lepu.health.db.Trace
import com.lepu.health.service.MediaProjectionService
import com.lepu.health.service.MediaProjectionStatus
import com.lepu.health.service.MediaProjectionStatusData
import com.lepu.health.util.*
import kotlinx.coroutines.delay
import org.joda.time.DateTime
import java.io.File
import kotlin.math.sqrt


/**
 *
 * 动态轨迹
 * zrj 2020/11/9
 */
class AMapScreenRecorderFragment : BaseFragment(R.layout.fragment_amap_sr) {
    private val binding: FragmentAmapSrBinding by bindView()
    private var photoPath: String by Preference(Constant.PHOTO_PATH, "")
    private var nickname: String by Preference(Constant.NICKNAME, "")
    private var units: Int by Preference(Constant.UNITS, 0)
    private val requestMediaProjectionCode = 1000
    private var path = ""
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
        binding.mapView.map.uiSettings.isMyLocationButtonEnabled = false
        binding.mapView.map.uiSettings.isZoomControlsEnabled = false
        binding.mapView.map.uiSettings.isScaleControlsEnabled = false
        binding.mapView.map.uiSettings.isScrollGesturesEnabled = false
        binding.mapView.map.uiSettings.isTiltGesturesEnabled = false
        binding.mapView.map.uiSettings.setLogoBottomMargin(-50)
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
                when (trace.mode) {
                    1 -> R.drawable.ic_outdoor_run_white_24dp
                    2 -> R.drawable.ic_walk_white_24dp
                    else -> R.drawable.ic_ride_white_24dp
                }
            )
            binding.tvTime.text =
                CommonUtil.getFormattedStopWatchTIme(it.accomplishTime * 1000L, false)
            val m = it.distance
            binding.tvDistance.text =
                String.format("%.2f", if (units == 0) m / 1000f else m * 0.62 / 1000f)

            binding.tvDistanceUnit.text =
                getString(if (units == 0) R.string.km else R.string.mile)
            binding.tvStrideValue.text = "0" //平均步频
            binding.tvKcalValue.text = "${it.calorie / 1000}"
            aMapPoints.addAll(PathSmoothTool().pathOptimize(loadAMapPoints(it)))
            binding.mapView.map.setOnMapLoadedListener {
                addStartAndStop()
            }
        }

        binding.ivBack.click { onBackPressed() }
        binding.ivLayer.click {
            binding.mapView.map.mapType =
                if (isMapTypeNormal) AMap.MAP_TYPE_SATELLITE else AMap.MAP_TYPE_NORMAL
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
            resources.getDrawable(R.drawable.ic_exercise_start_12dp, null)
        )
        val startDes = BitmapDescriptorFactory.fromBitmap(start)
        var mMarkerOptions = MarkerOptions().icon(startDes).anchor(0.5f, 0.5f)
        binding.mapView.map.addMarker(mMarkerOptions.position(aMapPoints[0]))

        val polylineOptions = PolylineOptions().color(Color.BLACK).width(15f).addAll(aMapPoints)
        binding.mapView.map.addPolyline(polylineOptions)

        val end = CommonUtil.drawableToBitmap(
            resources.getDrawable(R.drawable.ic_exercise_stop_12dp, null)
        )

        val endDes = BitmapDescriptorFactory.fromBitmap(end)
        mMarkerOptions = MarkerOptions().icon(endDes).anchor(0.5f, 0.5f)
        binding.mapView.map.addMarker(mMarkerOptions.position(aMapPoints[aMapPoints.size - 1]))

        val latitudeMax = aMapPoints.maxByOrNull { p -> p.latitude }?.latitude ?: 0.0
        val latitudeMin = aMapPoints.minByOrNull { p -> p.latitude }?.latitude ?: 0.0
        val longitudeMax = aMapPoints.maxByOrNull { p -> p.longitude }?.longitude ?: 0.0
        val longitudeMin = aMapPoints.minByOrNull { p -> p.longitude }?.longitude ?: 0.0
        val bounds =
            LatLngBounds(LatLng(latitudeMin, longitudeMin), LatLng(latitudeMax, longitudeMax))
        binding.mapView.map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 200))
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

                if (it.pause == -1 || (it.pause == -2 && !GPSConverterUtils.out_of_china(
                        it.longitude,
                        it.latitude
                    ))
                ) {
                    points.add(LatLng(it.latitude, it.longitude))
                } else {
                    val converter = CoordinateConverter(requireContext())
                    converter.from(CoordinateConverter.CoordType.GPS)
                    converter.coord(LatLng(latitude, longitude))
                    val desLatLng: LatLng = converter.convert()
                    points.add(desLatLng)
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
            val point = binding.mapView.map.projection.toScreenLocation(latLng)
            point.y -= requireContext().dip2px(125f)
            val target = binding.mapView.map.projection.fromScreenLocation(point)
            val animation = TranslateAnimation(target)
            animation.setInterpolator { input -> // 模拟重加速度的interpolator
                if (input <= 0.5) {
                    (0.5f - 2 * (0.5 - input) * (0.5 - input)).toFloat()
                } else {
                    (0.5f - sqrt((input - 0.5f) * (1.5f - input).toDouble())).toFloat()
                }
            }
            animation.setDuration(600)
            marker.setAnimation(animation)
            marker.startAnimation()
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
        binding.mapView.map.clear()
        binding.mapView.map.moveCamera(CameraUpdateFactory.newLatLngZoom(aMapPoints[0], 18f))
        //地图上添加标记点
        for (i in 0 until aMapPoints.size) {
            if (mPointIndex.contains(i)) {
                val marker = binding.mapView.map.addMarker(MarkerOptions().position(aMapPoints[i]))
                marker.setObject(i)
                marker.isVisible = false
            }
        }
        //初始化移动点marker
        val routePolyLineOptions = PolylineOptions()
        routePolyLineOptions.width(15F)
        routePolyLineOptions.lineCapType(PolylineOptions.LineCapType.LineCapSquare)
        routePolyLineOptions.lineJoinType(PolylineOptions.LineJoinType.LineJoinRound)
        val mPolyline = binding.mapView.map.addPolyline(routePolyLineOptions)
        val moveMarker = SmoothMoveMarker(binding.mapView.map)
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
            activity?.runOnUiThread {
                if (distance > 0 && !flag) {
                    flag = true
                    binding.cpc.setVisible(true)
                    binding.cpc.setValue(cadences)
                }
                binding.cpc.setProgress((trace.distance - distance.toFloat()) / trace.distance)
                binding.tvDistance.text = String.format("%.2f", (trace.distance - distance) / 1000)
            }
            val center = moveMarker.position
            val index = moveMarker.index
            val markerAngle = moveMarker.marker.rotateAngle
            //画线
            mList.add(center)
            mPolyline.points = mList
            //判断是否到标记点
            val markerList = binding.mapView.map.mapScreenMarkers
            if (mPointIndex.contains(index)) {
                activity?.runOnUiThread {
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
                }

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
                moveMarker.stopMove()
                for (marker in markerList) {
                    if (marker.getObject() != null) {
                        val markerIndex = marker.getObject() as Int
                        if (markerIndex == index) {
                            marker.setIcon(icon)
                            marker.isVisible = true
                            startJumpAnimation(marker)
                        }
                    }
                }
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
                moveMarker.startSmoothMove()
            }

            //地图移动旋转  每20个点移动一次中心点
            if (index % 10 == 0) {
                //需要旋转地图的话，在此改变地图角度  aMapAngle += 10
                val cameraPosition = CameraPosition(center, 16f, 60f, markerAngle)
                binding.mapView.map.animateCamera(
                    CameraUpdateFactory.newCameraPosition(cameraPosition), 1000, null
                )
            } else if (index % 10 == 10) {
                val cameraPosition = CameraPosition(center, 16f, 60f, markerAngle)
                binding.mapView.map.animateCamera(
                    CameraUpdateFactory.newCameraPosition(cameraPosition), 1000, null
                )
            }
            if (distance.toFloat() == 0.0f) { //判断是否到终点
                lifecycleScope.launchWhenStarted {
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
                    binding.mapView.map.clear()
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
        moveMarker.startSmoothMove()
    }

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
