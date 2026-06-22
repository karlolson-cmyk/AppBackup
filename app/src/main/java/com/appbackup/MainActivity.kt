package com.appbackup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.appbackup.data.pref.PreferencesManager
import com.appbackup.ui.screen.AppListScreen
import com.appbackup.ui.screen.WebDavConfigScreen
import com.appbackup.ui.theme.AppBackupTheme
import com.appbackup.viewmodel.AppListViewModel
import com.appbackup.viewmodel.WebDavViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppBackupTheme {
                val navController = rememberNavController()
                val webDavViewModel: WebDavViewModel = viewModel()
                val appListViewModel: AppListViewModel = viewModel()

                val savedConfig = PreferencesManager(this@MainActivity).loadWebDavConfig()
                val startDest = if (savedConfig != null) "app_list" else "webdav_config"

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
