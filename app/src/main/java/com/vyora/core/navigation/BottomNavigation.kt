package com.vyora.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.vyora.core.designsystem.theme.*

enum class BottomNavigationItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    MAP(AppRoute.Map.route, "Map", Icons.Default.Map),
    ASSISTANT(AppRoute.Assistant.route, "AI Assistant", Icons.Default.SmartToy),
    MESSAGES(AppRoute.Messages.route, "Messages", Icons.Default.Chat)
}

@Composable
fun VyoraBottomNavigation(
    currentRoute: String?,
    onNavigateToRoute: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = VyoraNavyCard,
        contentColor = VyoraTextPrimary
    ) {
        BottomNavigationItem.values().forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigateToRoute(item.route) },
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = { Text(item.title, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = VyoraCyanSecondary,
                    selectedTextColor = VyoraCyanSecondary,
                    unselectedIconColor = VyoraTextSecondary,
                    unselectedTextColor = VyoraTextSecondary,
                    indicatorColor = VyoraNavyHighlight
                )
            )
        }
    }
}
