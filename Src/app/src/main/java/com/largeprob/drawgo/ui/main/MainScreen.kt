package com.largeprob.drawgo.ui.main

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.largeprob.drawgo.ProfilePage

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    Scaffold(
        containerColor =  Color.White,
//        topBar = {
//            Box(modifier = Modifier.background(color = Color.White)){
//                Text(text = "top")
//            }
//        },
//        snackbarHost={
//            Box(modifier = Modifier.background(color = Color.Blue)){
//                Text(text = "66666")
//            }
//        },
//        floatingActionButton={
//            Box(modifier = Modifier.background(color = Color.Blue)){
//                Text(text = "77777")
//            }
//        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFFF0F0F1)
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                val items = listOf(
                    Screen.Home,
//                    Screen.Profile
                )
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.route) },
                        selected = currentDestination?.route == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
//                        modifier = Modifier.background(color = Color(0xFFF0F0F1)),
                        colors = NavigationBarItemColors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color(0xFF3264F4),
                            selectedIndicatorColor = Color(0xFF3264F4),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            disabledIconColor = Color.Gray,
                            disabledTextColor = Color.Gray
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { slideInHorizontally(initialOffsetX = { 1000 }) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -1000 }) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -1000 }) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { 1000 }) }
        ) {
            composable(Screen.Home.route) { MainPage() }
//            composable(Screen.Profile.route) { ProfilePage() }
        }
    }
}

//路由地址
sealed class Screen(val route: String, val icon: ImageVector) {
    object Home : Screen("Home", Icons.Default.Home)
//    object Profile : Screen("Profile", Icons.Default.Person)
}