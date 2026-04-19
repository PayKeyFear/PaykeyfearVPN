package com.paykeyfear.vpn.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.paykeyfear.vpn.ui.screens.home.HomeScreen
import com.paykeyfear.vpn.ui.screens.import_config.ImportScreen
import com.paykeyfear.vpn.ui.screens.servers.ServersScreen
import com.paykeyfear.vpn.ui.screens.settings.AboutScreen
import com.paykeyfear.vpn.ui.screens.settings.LogsScreen
import com.paykeyfear.vpn.ui.screens.settings.PrivacyPolicyScreen
import com.paykeyfear.vpn.ui.screens.settings.SettingsScreen
import com.paykeyfear.vpn.ui.screens.settings.SplitTunnelScreen

enum class Destination(val route: String, val label: String, val inBottomBar: Boolean = true) {
    Home("home", "Home"),
    Servers("servers", "Servers"),
    Import("import", "Import"),
    Settings("settings", "Settings"),
    SplitTunnel("split_tunnel", "Split tunnel", inBottomBar = false),
    Privacy("privacy", "Privacy", inBottomBar = false),
    Logs("logs", "Logs", inBottomBar = false),
    About("about", "About", inBottomBar = false),
}

@Composable
fun PaykeyfearNavHost() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.filter { it.inBottomBar }.forEach { dest ->
                    NavigationBarItem(
                        selected = backStack?.destination?.hierarchy?.any { it.route == dest.route } == true,
                        onClick = {
                            if (currentRoute != dest.route) {
                                navController.navigate(dest.route) {
                                    popUpTo(Destination.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(dest.icon(), contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = androidx.compose.ui.Modifier.padding(padding),
        ) {
            composable(Destination.Home.route) { HomeScreen() }
            composable(Destination.Servers.route) { ServersScreen() }
            composable(Destination.Import.route) { ImportScreen() }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    onSplitTunnelClick = { navController.navigate(Destination.SplitTunnel.route) },
                    onPrivacyClick = { navController.navigate(Destination.Privacy.route) },
                    onLogsClick = { navController.navigate(Destination.Logs.route) },
                    onAboutClick = { navController.navigate(Destination.About.route) },
                )
            }
            composable(Destination.SplitTunnel.route) { SplitTunnelScreen() }
            composable(Destination.Privacy.route) { PrivacyPolicyScreen() }
            composable(Destination.Logs.route) { LogsScreen() }
            composable(Destination.About.route) { AboutScreen() }
        }
    }
}

private fun Destination.icon() =
    when (this) {
        Destination.Home -> Icons.Filled.Home
        Destination.Servers -> Icons.Filled.Dns
        Destination.Import -> Icons.Filled.FileOpen
        Destination.Settings -> Icons.Filled.Settings
        Destination.SplitTunnel -> Icons.Filled.Settings
        Destination.Privacy -> Icons.Filled.Settings
        Destination.Logs -> Icons.Filled.Settings
        Destination.About -> Icons.Filled.Settings
    }
