package com.prapps.fridaserverinstaller.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavigationItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Home : NavigationItem("home", "Home", Icons.Default.Home)
    object Detection : NavigationItem("detection", "Detection", Icons.Default.Security)
    object Logs : NavigationItem("logs", "Logs", Icons.Default.Terminal)
    object Settings : NavigationItem("settings", "Settings", Icons.Default.Settings)
    
    companion object {
        val items = listOf(Home, Detection, Logs, Settings)
    }
}
