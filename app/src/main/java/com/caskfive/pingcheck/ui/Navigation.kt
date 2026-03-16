package com.caskfive.pingcheck.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.caskfive.pingcheck.ui.history.HistoryListScreen
import com.caskfive.pingcheck.ui.ping.PingScreen
import com.caskfive.pingcheck.ui.settings.SettingsScreen
import com.caskfive.pingcheck.ui.traceroute.TracerouteScreen

enum class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    Ping("ping", "Ping", Icons.Default.NetworkPing),
    Traceroute("traceroute", "Traceroute", Icons.Default.Route),
    History("history", "History", Icons.Default.History),
    Settings("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun PingCheckNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomNavItem.entries.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Ping.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Ping.route) { PingScreen() }
            composable(BottomNavItem.Traceroute.route) { TracerouteScreen() }
            composable(BottomNavItem.History.route) { HistoryListScreen() }
            composable(BottomNavItem.Settings.route) { SettingsScreen() }
        }
    }
}
