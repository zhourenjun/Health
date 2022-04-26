package com.lepu.health.db

import com.lepu.health.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *
 * 手机gps数据相关vm
 * zrj 2020/7/16
 */
class DataViewModel(private val repository: Repository) : BaseViewModel() {

    fun insertTraces(traces: List<Trace>) {
        launch {
            withContext(Dispatchers.IO) { repository.insertTraces(traces) }
        }
    }
}


