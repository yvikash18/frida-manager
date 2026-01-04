package com.prapps.fridaserverinstaller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.prapps.fridaserverinstaller.ui.*
import com.prapps.fridaserverinstaller.ui.theme.FridaServerInstallerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefsManager = remember { PreferencesManager(this@MainActivity) }
            var isDarkTheme by remember { mutableStateOf(prefsManager.isDarkTheme) }
            
            FridaServerInstallerTheme(darkTheme = isDarkTheme) {
                MainApp(
                    context = this@MainActivity,
                    onThemeChange = { dark ->
                        isDarkTheme = dark
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    context: ComponentActivity,
    onThemeChange: (Boolean) -> Unit,
    viewModel: FridaInstallerViewModel = viewModel { FridaInstallerViewModel(context) }
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationItem.items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = currentRoute == item.route,
                        onClick = {
                            if (currentRoute != item.route) {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavigationItem.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavigationItem.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToLogs = {
                        navController.navigate(NavigationItem.Logs.route)
                    }
                )
            }
            
            composable(NavigationItem.Detection.route) {
                DetectionScreen()
            }
            
            composable(NavigationItem.Logs.route) {
                LogsScreen(
                    messages = uiState.messages,
                    onClear = { viewModel.clearLogs() }
                )
            }
            
            composable(NavigationItem.Settings.route) {
                SettingsScreen(
                    serverPort = uiState.serverPort,
                    isAutoStartEnabled = uiState.isAutoStartEnabled,
                    isDarkTheme = uiState.isDarkTheme,
                    savedVersions = uiState.savedVersions,
                    currentVersion = uiState.currentServerType,
                    onPortChange = { port -> viewModel.setServerPort(port) },
                    onAutoStartChange = { enabled -> viewModel.setAutoStartEnabled(enabled) },
                    onDarkThemeChange = { enabled ->
                        viewModel.setDarkTheme(enabled)
                        onThemeChange(enabled)
                    },
                    onSwitchVersion = { version -> viewModel.switchToSavedVersion(version) },
                    onDeleteVersion = { version -> viewModel.deleteSavedVersion(version) }
                )
            }
        }
    }
}