package com.lepu.health.db

import androidx.lifecycle.MutableLiveData
import com.lepu.health.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *
 * zrj 2020/7/16
 */
class ExerciseListViewModel(private val repository: Repository) : BaseViewModel() {

    val mTraces: MutableLiveData<List<Trace>?> = MutableLiveData()
    val mModeTraces: MutableLiveData<List<Trace>?> = MutableLiveData()
    val mMsg: MutableLiveData<String> = MutableLiveData()

    fun getTraceById(from: Long, to: Long) {
        launch {
            mTraces.value = withContext(Dispatchers.IO) { repository.getTraceById(from, to) }
        }
    }

    fun getTraceById() {
        launch {
            mTraces.value = withContext(Dispatchers.IO) { repository.getTraceById() }
        }
    }

    fun getModeTraceById(mode: Int, from: Long, to: Long) {
        launch {
            mModeTraces.value =
                withContext(Dispatchers.IO) { repository.getModeTraceById(mode, from, to) }
        }
    }
}


