package com.appkitz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.appkitz.data.pref.PreferencesManager
import com.appkitz.ui.screen.AppListScreen
import com.appkitz.ui.screen.WebDavConfigScreen
import com.appkitz.ui.theme.AppkitzTheme
import com.appkitz.viewmodel.AppListViewModel
import com.appkitz.viewmodel.WebDavViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppkitzTheme {
                val navController = rememberNavController()
                val webDavViewModel: WebDavViewModel = viewModel()
                val appListViewModel: AppListViewModel = viewModel()

                val startDest = "app_list"

                NavHost(
                    navController = navController,
                    startDestination = startDest
                ) {
                    composable("webdav_config") {
                        WebDavConfigScreen(
                            viewModel = webDavViewModel,
                            onConfigured = {
                                val config = PreferencesManager(this@MainActivity).loadWebDavConfig()
                                config?.let { appListViewModel.setWebDavConfig(it) }
                                navController.navigate("app_list") {
                                    popUpTo("webdav_config") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("app_list") {
                        val config = PreferencesManager(this@MainActivity).loadWebDavConfig()
                        config?.let { appListViewModel.setWebDavConfig(it) }
                        AppListScreen(
                            viewModel = appListViewModel,
                            onConfigureWebDav = {
                                navController.navigate("webdav_config")
                            }
                        )
                    }
                }
            }
        }
    }
}
