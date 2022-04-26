package com.lepu.health.db

import com.lepu.health.util.Constant
import com.lepu.health.util.Preference


/**
 *
 *  说明: 仓库
 *  zrj 2022/4/26 10:22
 *
 */
class Repository(private val db: AppDatabase) {

    private var userId: Long by Preference(Constant.USER_ID, 0L)

    private var currentWatch: String by Preference(Constant.CURRENT_WATCH, "")

    suspend fun insertTraces(traces: List<Trace>) {
        db.traceDao().insertTraces(traces)
    }

    suspend fun getLastTraceById() =
        db.traceDao().getLastTraceById(userId)

    suspend fun getTraceById(id: Long = userId) =
        db.traceDao().getTraceById(id)

    suspend fun deleteTraceById(id: Long) = db.traceDao().deleteTraceById(id)

    suspend fun getModeTraceById(mode: Int, from: Long, to: Long) =
        db.traceDao()
            .getModeTraceById(mode, from, to, userId)

    suspend fun getTraceById(from: Long, to: Long, mac: String = currentWatch) =
        db.traceDao().getTraceById(from, to, userId, mac)

    suspend fun getTraceById() =
        db.traceDao().getTraceById(userId)

    suspend fun getTraceByDate(date: Long, mac: String = currentWatch) =
        db.traceDao().getTraceByDate(date, userId, mac)

}