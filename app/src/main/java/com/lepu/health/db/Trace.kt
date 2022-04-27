package com.lepu.health.db

import android.os.Parcelable
import androidx.annotation.NonNull
import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.android.parcel.Parcelize


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
    var mode: Int,   //模式     1-户外跑步  2-步行  3-骑行
    var calorie: Int,    //能量消耗 卡
    var bestPace: Int,   //最快配速  秒/公里（骑行：米/小时）
    @NonNull
    var date: Long,  //起始时间戳  Unix时间戳
    var accomplishTime: Int,  //运动用时  秒（不包含暂停时间）
    var distance: Int,    //距离  米
    var oLongitude_ew: String,  //起始经度标识  室内0，定位成功‘E’ or ‘W’   69  87
    var oLatitude_ns: String,   //起始纬度标识  室内0，定位成功‘N’ or ‘S’   78  83
    var tLongitude_ew: String,  //结束经度标识
    var tLatitude_ns: String,   //结束纬度标识
    var worstPace: Int, //最低配速
    val realDataList: List<RealData>
) : Parcelable {
    var userId = 0L
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
) : Parcelable

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