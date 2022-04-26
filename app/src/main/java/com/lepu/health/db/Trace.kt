package com.lepu.health.db

import android.os.Parcelable
import androidx.annotation.NonNull
import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.android.parcel.Parcelize
import java.text.SimpleDateFormat
import java.util.*


@Dao
interface TraceDao {

    @Query("select * from trace_table WHERE userId = :userId order by date desc limit 1")
    suspend fun getLastTraceById(userId: Long): Trace?

    @Query("SELECT * FROM trace_table Where date = :date AND userId = :userId AND deviceMac = :mac")
    suspend fun getTraceByDate(date: Long, userId: Long, mac: String): Trace?

    @Query("select * from trace_table WHERE userId = :userId order by date desc")
    suspend fun getTraceById(userId: Long): List<Trace>

    @Query("SELECT * FROM trace_table Where mode = :mode AND date > :from AND date < :to AND userId = :userId")
    suspend fun getModeTraceById(mode: Int, from: Long, to: Long, userId: Long): List<Trace>

    @Query("SELECT * FROM trace_table Where date BETWEEN :from AND :to AND userId = :userId AND deviceMac = :mac")
    suspend fun getTraceById(from: Long, to: Long, userId: Long, mac: String): List<Trace>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTraces(traces: List<Trace>)

    @Query("DELETE FROM trace_table WHERE userId = :userId")
    suspend fun deleteTraceById(userId: Long)
}


@Parcelize
@Entity(tableName = "trace_table", primaryKeys = ["date", "deviceMac"])
@TypeConverters(RealDataConverters::class, KmInfoConverters::class, IntConverters::class)
data class Trace(
    @NonNull
    var deviceMac: String,
    //每次运动汇总统计
    var mode: Int,   //模式    0-健走  1-户外跑  2-室内跑  3-户外骑行  4-室内骑行  5-徒步  6-爬山  7-羽毛球
    // 8-足球  9-篮球  10-网球  11-跳舞  12-瑜伽  13-自由训练  14-越野跑  15-室内游泳
    //16-室内健走  17-划船机  18-椭圆机  19- 开放水域
    var averageHr: Int,  //平均心率
    var maxHr: Int,   //最大心率
    var timezone: Int,  //时区
    var maxCadence: Int,  //最大步频  个/分钟
    var calorie: Int,    //能量消耗 卡
    var bestPace: Int,   //最快配速  秒/公里（骑行：米/小时）
    @NonNull
    var date: Long,  //起始时间戳  Unix时间戳
    var accomplishTime: Int,  //运动用时  秒（不包含暂停时间）
    var pauseTime: Int,     //暂停用时  秒
    var distance: Int,    //距离  米
    var calibrateDistance: Int,  //校准距离  米（室内跑用户校准，0则用距离)
    var step: Int,    //步数
    var oLongitude: Double,  //起始经度
    var oLatitude: Double,   //起始纬度
    var tLongitude: Double,  //结束经度
    var tLatitude: Double,   //结束纬度
    var oLongitude_ew: String,  //起始经度标识  室内0，定位成功‘E’ or ‘W’   69  87
    var oLatitude_ns: String,   //起始纬度标识  室内0，定位成功‘N’ or ‘S’   78  83
    var tLongitude_ew: String,  //结束经度标识
    var tLatitude_ns: String,   //结束纬度标识
    var maxOxygenUptake: Int,  //最大摄氧量
    var hrDistributed: List<Int>,  //心率分布  [0, 100]，百分比数值，5个区间值加起来100  区间1表示能量消耗最低的区间
    var swimStyle: Int,  //泳姿 [0, 4]，0:unknown 1:free style 2:Breast stroke(蛙式) 3:Back stroke 4:Butterfly stroke
    var minCadence: Int,  //最小步频 个/分钟
    var worstPace: Int, //最低配速
    var strokes: Int, //划水次数
    var swimLaps: Int, //室内泳池趟数
    var averageSwolf: Int,  //平均游泳效率
    var averageStrokes: Int,  //平均划水頻率  次/分钟
    val kmInfoList: List<KmInfo>,
    val realDataList: List<RealData>
) : Parcelable {
    // 返回路径的GPX表示形式
    fun getGPX(): String {
        val body = StringBuilder()
        realDataList.forEachIndexed { i, realData ->
            body.append(realData.getGPX(timezone, date * 1000L + i * 5))
        }
        return String.format(
            Locale.ENGLISH, "%s <trk> <name>%s</name><time>%s</time>" +
                    "<trkseg>%s</trkseg></trk></gpx>", header, "OyeFit",
            timeFormat(timezone, date * 1000L), body.toString()
        )
    }

    var userId = 0L
}

const val header =
    "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" " +
            "xmlns:gpxx=\"http://www.garmin.com/xmlschemas/GpxExtensions/v3\" " +
            "xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\" " +
            "creator=\"OyeFit GPX\" " +
            "version=\"1.1\" " +
            "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
            "xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd http://www.garmin.com/xmlschemas/GpxExtensions/v3 http://www.garmin.com/xmlschemas/GpxExtensionsv3.xsd http://www.garmin.com/xmlschemas/TrackPointExtension/v1 http://www.garmin.com/xmlschemas/TrackPointExtensionv1.xsd\">"


fun timeFormat(zone: Int, time: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSS'Z'", Locale.getDefault())
    sdf.timeZone = SimpleTimeZone(zone, "UTC")
    return sdf.format(Date(time))
}

//每公里打点信息
@Parcelize
data class KmInfo(
    var pace: Int,   //配速   秒/公里（骑行：米/小时）
    var elapseTime: Int,  //运动时间  单位：秒（包含暂停时间）
    var longitude: Double,  //当前经度   室内0，经度 * 1000000（放大最后6位小数）
    var latitude: Double,   //当前纬度   室内0，纬度 * 1000000（放大最后6位小数）
    var longitudeEW: String,  //经度标识  室内0，定位成功‘E’ or ‘W’
    var latitudeNS: String = ""   //纬度标识  室内0，定位成功‘N’ or ‘S’
) : Parcelable

//每5秒的实时数据
@Parcelize
data class RealData(
    var pause: Int,   //暂停  0为运动状态  -1 -2手机运动数据
    var hr: Int,  //实时心率
    var longitudeEW: String,
    var latitudeNS: String,
    var cadence: Int,   //实时步频   个/分钟（骑行：0）
    var pace: Int,      //实时配速
    var latitude: Double, //当前纬度
    var longitude: Double//当前经度
) : Parcelable {
    //返回此对象的GPX XML表示形式
    fun getGPX(timezone: Int, time: Long): String {
        //手表正负实值
        val tempLat = if (latitudeNS == "N") {
            latitude
        } else {
            -latitude
        }
        val tempLon = if (longitudeEW == "E") {
            longitude
        } else {
            -longitude
        }
        return String.format(
            Locale.ENGLISH,
            "<trkpt lat=\"%.6f\" lon=\"%.6f\">" +
                    " <time>%s</time> " +
                    " <extensions> " +
                    " <gpxtpx:TrackPointExtension> " +
                    " <gpxtpx:hr>%s</gpxtpx:hr> " +
                    " <gpxtpx:cad>%s</gpxtpx:cad> " +
                    " </gpxtpx:TrackPointExtension> " +
                    " </extensions> " +
                    "</trkpt>\n",
            tempLat, tempLon, timeFormat(timezone, time), hr, cadence
        )
    }
}

class RealDataConverters {
    @TypeConverter
    fun stringToObject(value: String): List<RealData> {
        val listType = object : TypeToken<List<RealData>>() {

        }.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun objectToString(list: List<RealData>): String {
        val gSon = Gson()
        return gSon.toJson(list)
    }
}

class KmInfoConverters {
    @TypeConverter
    fun stringToObject(value: String): List<KmInfo> {
        val listType = object : TypeToken<List<KmInfo>>() {

        }.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun objectToString(list: List<KmInfo>?): String {
        if (list == null) {
            return "[]"
        }
        val gSon = Gson()
        return gSon.toJson(list)
    }
}

class IntConverters {
    @TypeConverter
    fun stringToObject(value: String): List<Int> {
        val listType = object : TypeToken<List<Int>>() {

        }.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun objectToString(list: List<Int>): String {
        val gSon = Gson()
        return gSon.toJson(list)
    }
}