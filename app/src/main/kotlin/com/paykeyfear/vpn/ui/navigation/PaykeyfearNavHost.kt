package com.paykeyfear.vpn.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import com.paykeyfear.vpn.ui.theme.AccentGreen
import com.paykeyfear.vpn.ui.theme.SurfaceCard
import com.paykeyfear.vpn.ui.theme.TextMuted

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
        containerColor = com.paykeyfear.vpn.ui.theme.SurfaceBg,
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceCard,
                tonalElevation = 0.dp,
            ) {
                Destination.entries.filter { it.inBottomBar }.forEach { dest ->
                    val isSelected = backStack?.destination?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            if (currentRoute != dest.route) {
                                navController.navigate(dest.route) {
                                    popUpTo(Destination.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Box(contentAlignment = Alignment.TopCenter) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .width(28.dp)
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(AccentGreen),
                                    )
                                }
                                Icon(
                                    imageVector = dest.icon(),
                                    contentDescription = dest.label,
                                    modifier = Modifier.size(22.dp).padding(top = 4.dp),
                                )
                            }
                        },
                        label = null,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentGreen,
                            selectedTextColor = AccentGreen,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Home.route) {
                HomeScreen(onSplitTunnelClick = { navController.navigate(Destination.SplitTunnel.route) })
            }
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
            composable(Destination.SplitTunnel.route) { SplitTunnelScreen(onBack = navController::popBackStack) }
            composable(Destination.Privacy.route) { PrivacyPolicyScreen(onBack = navController::popBackStack) }
            composable(Destination.Logs.route) { LogsScreen(onBack = navController::popBackStack) }
            composable(Destination.About.route) { AboutScreen(onBack = navController::popBackStack) }
        }
    }
}

private fun Destination.icon() = when (this) {
    Destination.Home -> Icons.Filled.Home
    Destination.Servers -> Icons.Filled.Storage
    Destination.Import -> Icons.Filled.Download
    Destination.Settings -> Icons.Filled.Settings
    else -> Icons.Filled.Settings
}
