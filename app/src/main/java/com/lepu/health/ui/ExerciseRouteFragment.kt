package com.lepu.health.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.*
import com.amap.api.maps.model.animation.ScaleAnimation
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.RoundCap
import com.gyf.immersionbar.ImmersionBar
import com.lepu.health.R
import com.lepu.health.base.BaseFragment
import com.lepu.health.base.bindView
import com.lepu.health.databinding.FragmentExerciseRouteBinding
import com.lepu.health.db.KmInfo
import com.lepu.health.db.Trace
import com.lepu.health.util.*
import com.lepu.health.util.CommonUtil.compressImage
import com.lepu.health.widget.NumDrawable
import kotlinx.coroutines.*
import org.joda.time.DateTime
import kotlin.coroutines.CoroutineContext


/**
 *
 * 轨迹
 * zrj 2020/9/8
 */
@Suppress("EXPERIMENTAL_OVERRIDE", "EXPERIMENTAL_API_USAGE")
class ExerciseRouteFragment : BaseFragment(R.layout.fragment_exercise_route), CoroutineScope {
    private val binding: FragmentExerciseRouteBinding by bindView()
    private var units: Int by Preference(Constant.UNITS, 0)
    private var map: Int by Preference(Constant.MAP, 4)

    private var isCycling = false
    private var latitude = 0e10
    private var longitude = 0e10

    private lateinit var mParams: LinearLayout.LayoutParams
    private var mAMapView: TextureMapView? = null
    private var mGoogleMapView: MapView? = null
    private lateinit var mAMap: AMap
    private lateinit var mGoogleMap: GoogleMap
    private var isRunning = false
    private var isGoogleMap = false
    private var isMapTypeNormal = true
    private var isKMShow = true
    private val kmInfoList = mutableListOf<KmInfo>()
    private val aMapPoints = mutableListOf<LatLng>()
    private lateinit var aMapPolyline: AMapPolyline

    private val googlePoints = mutableListOf<com.google.android.gms.maps.model.LatLng>()
    private lateinit var googleMapPolyline: GoogleMapPolyline

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onBackPressed() {
        super.onBackPressed()
        findNavController().navigateUp()
    }

    @SuppressLint("SetTextI18n")
    override fun initView(savedInstanceState: Bundle?) {
        ImmersionBar.setTitleBar(this, binding.toolbar)
        binding.ivBack.click { onBackPressed() }
        mParams = LinearLayout.LayoutParams(-1, -1)
        arguments?.getParcelable<Trace>("trace")?.let { trace ->
            send(false, "share")
            if (trace.kmInfoList.isEmpty()) {
                binding.ivKm.setVisible(false)
            } else {
                kmInfoList.addAll(trace.kmInfoList)
                binding.ivKm.setVisible(units == 0)
            }
            isCycling = trace.mode == 3 || trace.mode == 4
            var isChina = false
            if (trace.realDataList.isNotEmpty()) {
                isChina = if (trace.averageHr == 0){
                    !GPSConverterUtils.out_of_china(trace.realDataList[0].longitude, trace.realDataList[0].latitude)
                }else{
                    !GPSConverterUtils.out_of_china(trace.tLongitude, trace.tLatitude)
                }
            }

            if (map == 1 || (isChina && map == 4)) {
                val temp = loadAMapPoints(trace)
                aMapPoints.addAll(PathSmoothTool().pathOptimize(temp))
                aMapMapView(savedInstanceState)
            } else {
                isGoogleMap = true
                val temp = loadGooglePoints(trace)
                val list =
                    PathSmoothTool().pathOptimize(temp.map { LatLng(it.latitude, it.longitude) })
                googlePoints.addAll(list.map {
                    com.google.android.gms.maps.model.LatLng(
                        it.latitude,
                        it.longitude
                    )
                })
                googleMapView(savedInstanceState)
            }
            binding.tvDynamic.click {
                if (isGoogleMap) {
                    findNavController().navigate(R.id.googleMapScreenRecorderFragment, bundleOf("trace" to trace))
                } else {
                    findNavController().navigate(R.id.aMapScreenRecorderFragment, bundleOf("trace" to trace))
                }
            }
            binding.ivLocation.click { location() }
            binding.ivRestart.click { start() }
            binding.ivKm.click { showKm() }
            binding.ivLayer.click { layer() }
            binding.tvKm.text = getString(if (units == 0) R.string.km else R.string.mile)
            val m =
                if (trace.mode == 2 || trace.mode == 4 || trace.mode == 15 || trace.mode == 16) (if (trace.calibrateDistance == 0) trace.distance else trace.calibrateDistance) else trace.distance
            binding.tvSteps.text = String.format(
                "%.2f",
                if (units == 0) m / 1000f else m * 0.62 / 1000f
            )
            val day = DateTime(trace.date * 1000L)
            val temp = day.minuteOfDay % 60
            binding.tvData.text =
                "${day.monthOfYear().asShortText} ${day.dayOfMonth().asText}, ${day.year}  ${day.hourOfDay}:${if (temp < 10) "0$temp" else temp}"
            binding.tvDurationValue.text =
                CommonUtil.getFormattedStopWatchTIme(trace.accomplishTime * 1000L, false)
            val sec = trace.accomplishTime * 1000 / (if (units == 0) m else m * 0.62).toInt()
            binding.tvAvgPaceValue.text = "${sec / 60}'${sec % 60}''"
            binding.tvKcalValue.text = "${trace.calorie / 1000}"

            // pace  //配速   秒/公里（骑行：米/小时）
            val worst = if ((trace.mode == 3 || trace.mode == 4) && trace.worstPace > 0) {
                1000 * 3600 / trace.worstPace
            } else {
                trace.worstPace
            }
            val worstPace = if (units == 0) worst else (worst / 0.62).toInt()

            val best = if (trace.mode == 3 || trace.mode == 4) {
                3600 * 1000 / trace.bestPace
            } else {
                trace.bestPace
            }
            val bestPace = if (units == 0) best else (best / 0.62).toInt()

            binding.mc.setOther(worstPace, bestPace)
        }
    }

    override fun initData() {
        receiveTag(false, "map_screenshot") {
            if (isGoogleMap) {
                mGoogleMap.snapshot { bitmap ->
                    compressImage(bitmap)?.let {
                        send(ScreenshotMaker.toBase64(it, 50), "map_screenshot_pic")
                    }
                }
            } else {
                mAMap.getMapScreenShot(object : AMap.OnMapScreenShotListener {
                    override fun onMapScreenShot(bitmap: Bitmap) {
                    }

                    override fun onMapScreenShot(bitmap: Bitmap, statu: Int) {
                        compressImage(bitmap)?.let {
                            send(ScreenshotMaker.toBase64(it, 50), "map_screenshot_pic")
                        }
                    }
                })
            }
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun googleMapView(savedInstanceState: Bundle?) {
        mGoogleMapView = MapView(context)
        mGoogleMapView?.onCreate(savedInstanceState)
        mGoogleMapView?.onResume()
        binding.mapContainer.addView(mGoogleMapView, mParams)
        mGoogleMapView?.getMapAsync {
            mGoogleMap = it
            it.uiSettings.setAllGesturesEnabled(false)
            if (googlePoints.isEmpty()) return@getMapAsync
            lifecycleScope.launchWhenStarted {
                val builder = com.google.android.gms.maps.model.LatLngBounds.Builder()
                val latitudeMax = googlePoints.maxByOrNull { p -> p.latitude }?.latitude ?: 0.0
                val latitudeMin = googlePoints.minByOrNull { p -> p.latitude }?.latitude ?: 0.0
                val longitudeMax = googlePoints.maxByOrNull { p -> p.longitude }?.longitude ?: 0.0
                val longitudeMin = googlePoints.minByOrNull { p -> p.longitude }?.longitude ?: 0.0
                latitude = (latitudeMax + latitudeMin) / 2
                longitude = (longitudeMax + longitudeMin) / 2
                builder.include(com.google.android.gms.maps.model.LatLng(latitudeMin, longitudeMin))
                builder.include(com.google.android.gms.maps.model.LatLng(latitudeMax, longitudeMax))
                /**
                 * 用200度填充将[google Map]放大到bounds边界*然后，绘制路线
                 */
                it.zoomCameraWithAnimation(
                    com.google.android.gms.maps.CameraUpdateFactory.newLatLngBounds(
                        builder.build(),
                        250
                    )
                ) {
                    /**
                     * 路线的折线的选项。
                     */
                    val routePolyLineOptions = com.google.android.gms.maps.model.PolylineOptions()
                    routePolyLineOptions.width(10F)
                    routePolyLineOptions.startCap(RoundCap())
                    routePolyLineOptions.endCap(RoundCap())
                    routePolyLineOptions.jointType(JointType.ROUND)

                    /**
                     * 使用自定义折线选项初始化，开始*和结束颜色以进行渐变颜色计算。
                     */
                    googleMapPolyline = GoogleMapPolyline(map = it, job = job)
                        .setPolylineOptions(polylineOptions = routePolyLineOptions)
                        .setDelayTime(delayTime = 10L)
                        .setStartColor(startColor = Color.RED)
                        .setEndColor(endColor = Color.YELLOW)

                    googleMapDrawStart()
                    googleMapPolyline.drawPolyline(googlePoints) {
                        googleMapDrawEnd()
                    }
                }
            }
        }
    }


    @SuppressLint("UseCompatLoadingForDrawables")
    private fun googleMapDrawStart() {
        val start = CommonUtil.drawableToBitmap(
            resources.getDrawable(
                R.drawable.ic_exercise_start_12dp,
                null
            )
        )
        val startDes = com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(start)
        mGoogleMap.addMarker(
            com.google.android.gms.maps.model.MarkerOptions().position(googlePoints[0])
                .icon(startDes)
                .anchor(0.5f, 0.5f)
        )
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun googleMapDrawEnd() {
        isRunning = false
        mGoogleMap.uiSettings.setAllGesturesEnabled(true)
        binding.ctl.setVisible(true)
        send(true, "share")
        binding.tvDynamic.setVisible(true)
        val end = CommonUtil.drawableToBitmap(
            resources.getDrawable(
                R.drawable.ic_exercise_stop_12dp,
                null
            )
        )

        val endDes = com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(end)
        mGoogleMap.addMarker(
            com.google.android.gms.maps.model.MarkerOptions().position(googlePoints.last())
                .icon(endDes)
                .anchor(0.5f, 0.5f)
        )
        addGoogleKMMarker()
    }

    private var googleKmMarker = mutableListOf<Marker>()

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun addGoogleKMMarker() {
        googleKmMarker.clear()
        kmInfoList.forEachIndexed { index, kmInfo ->
            if (index < kmInfoList.size - 1) {
                val gps = if (GPSConverterUtils.out_of_china(kmInfo.longitude,  kmInfo.latitude)){ //国外
                    LatLng(kmInfo.latitude, kmInfo.longitude)
                }else{//国内
                    val temp = GPSConverterUtils.wgs84togcj02(kmInfo.longitude, kmInfo.latitude)
                    LatLng(temp[1], temp[0])
                }
                val kmDes =
                    com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(
                        CommonUtil.drawableToBitmap(NumDrawable((index + 1) * (if (isCycling) 5 else 1)))
                    )
                googleKmMarker.add(
                    mGoogleMap.addMarker(
                        com.google.android.gms.maps.model.MarkerOptions()
                            .position(
                                com.google.android.gms.maps.model.LatLng(
                                    gps.latitude,
                                    gps.longitude
                                )
                            ).icon(kmDes)
                            .anchor(0.5f, 0.5f)
                    )
                )
            }
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun aMapMapView(savedInstanceState: Bundle?) {
        mAMapView = TextureMapView(requireContext())
        binding.mapContainer.addView(mAMapView, mParams)
        mAMapView?.let {
            it.onCreate(savedInstanceState)
            mAMap = it.map
            mAMap.uiSettings.isZoomControlsEnabled = false
            mAMap.uiSettings.setAllGesturesEnabled(false)
            mAMap.setOnMapLoadedListener {
                if (aMapPoints.isEmpty()) return@setOnMapLoadedListener
                lifecycleScope.launchWhenStarted  {
                    val latitudeMax = aMapPoints.maxByOrNull { p -> p.latitude }?.latitude ?: 0.0
                    val latitudeMin = aMapPoints.minByOrNull { p -> p.latitude }?.latitude ?: 0.0
                    val longitudeMax = aMapPoints.maxByOrNull { p -> p.longitude }?.longitude ?: 0.0
                    val longitudeMin = aMapPoints.minByOrNull { p -> p.longitude }?.longitude ?: 0.0
                    latitude = (latitudeMax + latitudeMin) / 2
                    longitude = (longitudeMax + longitudeMin) / 2
                    val bounds =
                        LatLngBounds(
                            LatLng(latitudeMin, longitudeMin),
                            LatLng(latitudeMax, longitudeMax)
                        )
                    mAMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 250),
                        object : AMap.CancelableCallback {
                            override fun onFinish() {
                                val routePolyLineOptions = PolylineOptions()
                                routePolyLineOptions.width(10F)
                                routePolyLineOptions.lineCapType(PolylineOptions.LineCapType.LineCapSquare)
                                routePolyLineOptions.lineJoinType(PolylineOptions.LineJoinType.LineJoinRound)

                                aMapPolyline = AMapPolyline(map = mAMap, job = job)
                                    .setPolylineOptions(polylineOptions = routePolyLineOptions)
                                    .setDelayTime(delayTime = 10L)
                                    .setStartColor(startColor = Color.RED)
                                    .setEndColor(endColor = Color.YELLOW)

                                aMapDrawStart()
                                aMapPolyline.drawPolyline(aMapPoints) {
                                    aMapDrawEnd()
                                }
                            }

                            override fun onCancel() = Unit
                        })
                }
            }
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun aMapDrawStart() {
        val start = CommonUtil.drawableToBitmap(
            resources.getDrawable(
                R.drawable.ic_exercise_start_12dp,
                null
            )
        )
        val startDes = BitmapDescriptorFactory.fromBitmap(start)
        val marker = mAMap.addMarker(
            MarkerOptions().position(aMapPoints[0]).icon(startDes)
                .anchor(0.5f, 0.5f)
        )

        val markerAnimation = ScaleAnimation(0f, 1f, 0f, 1f) //初始化生长效果动画
        markerAnimation.setDuration(500L) //设置动画时间 单位毫秒
        marker.setAnimation(markerAnimation)
        marker.startAnimation()
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun aMapDrawEnd() {
        isRunning = false
        mAMap.uiSettings.setAllGesturesEnabled(true)
        binding.ctl.setVisible(true)
        send(true, "share")
        binding.tvDynamic.setVisible(true)

        val end = CommonUtil.drawableToBitmap(
            resources.getDrawable(
                R.drawable.ic_exercise_stop_12dp,
                null
            )
        )

        val endDes = BitmapDescriptorFactory.fromBitmap(end)
        mAMap.addMarker(
            MarkerOptions().position(aMapPoints.last())
                .icon(endDes)
                .anchor(0.5f, 0.5f)
        )
        addAMapKMMarker()
    }


    private var aMapKmMarker = mutableListOf<com.amap.api.maps.model.Marker>()

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun addAMapKMMarker() {
        aMapKmMarker.clear()
        kmInfoList.forEachIndexed { index, kmInfo ->
            if (index < kmInfoList.size - 1) {
                val gps = wgs84ToGcj02(kmInfo.latitude, kmInfo.longitude)
                val kmDes =
                    BitmapDescriptorFactory.fromBitmap(
                        CommonUtil.drawableToBitmap(NumDrawable((index + 1) * (if (isCycling) 5 else 1)))
                    )
                aMapKmMarker.add(
                    mAMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(gps.latitude, gps.longitude)).icon(kmDes)
                            .anchor(0.5f, 0.5f)
                    )
                )
            }
        }
    }

    private fun start() {
        if (!isRunning) {
            binding.ctl.setVisible(false)
            send(false, "share")
            binding.tvDynamic.setVisible(false)
            isRunning = true
            if (isGoogleMap) {
                googleMapPolyline.clear()
                googleMapDrawStart()
                googleMapPolyline.drawPolyline(googlePoints) {
                    isRunning = false
                    googleMapDrawEnd()
                }
            } else {
                aMapPolyline.clear()
                aMapDrawStart()
                aMapPolyline.drawPolyline(aMapPoints) {
                    isRunning = false
                    aMapDrawEnd()
                }
            }
        }
    }

    private fun location() {
        if (!isRunning) {
            if (isGoogleMap) {
                mGoogleMap.animateCamera(
                    com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                        com.google.android.gms.maps.model.LatLng(
                            latitude,
                            longitude
                        ), 8f
                    )
                )
            } else {
                mAMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(
                            latitude,
                            longitude
                        ), 15f
                    )
                )
            }
        }
    }

    private fun layer() {
        if (!isRunning) {
            if (isGoogleMap) {
                mGoogleMap.mapType =
                    if (isMapTypeNormal) GoogleMap.MAP_TYPE_SATELLITE else GoogleMap.MAP_TYPE_NORMAL
            } else {
                mAMap.mapType =
                    if (isMapTypeNormal) AMap.MAP_TYPE_SATELLITE else AMap.MAP_TYPE_NORMAL
            }
            binding.ivLayer.setImageResource(if (isMapTypeNormal) R.drawable.ic_layer2_black_24dp else R.drawable.ic_layer1_black_24dp)
            isMapTypeNormal = !isMapTypeNormal
        }
    }

    private fun showKm() {
        if (!isRunning) {
            isKMShow = !isKMShow
            if (isGoogleMap) {
                googleKmMarker.forEach { it.isVisible = isKMShow }
            } else {
                aMapKmMarker.forEach { it.isVisible = isKMShow }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mAMapView?.onResume()
        mGoogleMapView?.onResume()
    }
    //截图用
//    override fun onPause() {
//        super.onPause()
//        mAMapView?.onPause()
//        mGoogleMapView?.onPause()
//    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mAMapView?.onSaveInstanceState(outState)
        mGoogleMapView?.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        mAMapView?.onDestroy()
        mGoogleMapView?.onDestroy()
        job.cancel()
    }

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
                // 1.Amap手机数据直接使用 gcj02      2. google手机国内数据 gcj02
                if (it.pause == -1 || (it.pause == -2 && !GPSConverterUtils.out_of_china(
                        it.longitude,
                        it.latitude
                    ))
                ) {
                    points.add(LatLng(it.latitude, it.longitude))
                } else {
                    points.add(wgs84ToGcj02(latitude, longitude))
                }
            }
        }
        return points
    }

    //gps 转高德火星坐标
    private fun wgs84ToGcj02(latitude: Double, longitude: Double): LatLng {
        val converter = CoordinateConverter(requireContext())
        converter.from(CoordinateConverter.CoordType.GPS)
        converter.coord(LatLng(latitude, longitude))
        return converter.convert()
    }

    private fun loadGooglePoints(trace: Trace): List<com.google.android.gms.maps.model.LatLng> {
        val points = mutableListOf<com.google.android.gms.maps.model.LatLng>()
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
                if (it.pause == -2) { // 手机google数据直接使用
                    points.add(com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude))
                } else if (it.pause == -1) { //手机高德
                    //国外数据(火星坐标)
                    if (GPSConverterUtils.out_of_china(longitude, latitude)) {
                        val gps = GPSConverterUtils.gcj02towgs84(longitude, latitude)
                        points.add(com.google.android.gms.maps.model.LatLng(gps[1], gps[0]))
                    } else {
                        points.add(
                            com.google.android.gms.maps.model.LatLng(
                                it.latitude,
                                it.longitude
                            )
                        )
                    }
                } else { //手表的数据
                    if (GPSConverterUtils.out_of_china(longitude, latitude)){ //国外
                        points.add(
                            com.google.android.gms.maps.model.LatLng(
                                it.latitude,
                                it.longitude
                            )
                        )
                    }else{//国内
                        val gps = wgs84ToGcj02(latitude, longitude)
                        points.add(com.google.android.gms.maps.model.LatLng(gps.latitude, gps.longitude))
                    }
                }
            }
        }
        return points
    }
}





