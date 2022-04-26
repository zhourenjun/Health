package com.lepu.health.base

import com.lepu.health.db.AppDatabase
import com.lepu.health.db.DataViewModel
import com.lepu.health.db.ExerciseListViewModel
import com.lepu.health.db.Repository
import com.lepu.health.service.AMapService
import com.lepu.health.service.GoogleMapService
import com.lepu.health.service.LocationService
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 *
 * java类作用描述
 * zrj 2021/6/26 10:44
 * 更新者 2021/6/26 10:44
 */

val viewModelModule = module {
    viewModel { DataViewModel(get()) }
    viewModel { ExerciseListViewModel(get()) }
}
val localModule = module {
    single { AppDatabase.getDatabase(androidContext()) }
    single { get<AppDatabase>().traceDao() }
}
val serviceModule = module {
    single { GoogleMapService() }
    single { AMapService() }
    single { LocationService() }
}

val repositoryModule = module {
    single { Repository(get()) }
}

val appModule = listOf(viewModelModule, localModule, serviceModule, repositoryModule)