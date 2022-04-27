package com.lepu.health

import android.Manifest
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import com.gyf.immersionbar.ktx.immersionBar
import com.lepu.health.base.KeepStateNavigator
import com.lepu.health.util.permissionX


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val mode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        immersionBar {
            statusBarDarkFont(mode != Configuration.UI_MODE_NIGHT_YES, 0.2f)
            navigationBarColor(R.color.f5f5f5_121212)
        }
        val navController = findNavController(R.id.my_nav_host_fragment)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.my_nav_host_fragment) as NavHostFragment
        val navigator =
            KeepStateNavigator(this, navHostFragment.childFragmentManager, navHostFragment.id)
        navController.navigatorProvider.addNavigator(navigator)
        val navGraph = navController.navInflater.inflate(R.navigation.mobile_navigation)
        navController.graph = navGraph

        permissionX(
            listOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        ) { allGranted, _, _ ->
            if (allGranted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    permissionX(
                        listOf(
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        )
                    ) { all, _, _ ->
                        if (!all) finish()
                    }
                }
            }else{
                finish()
            }
        }
    }
}