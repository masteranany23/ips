package com.example.indoormaps.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.indoormaps.ui.LocationViewModelProvider
import com.example.indoormaps.ui.screens.HomeScreen
import com.example.indoormaps.ui.map.MapScreen

/**
 * Main navigation with shared LocationViewModel
 * for continuous background scanning
 */
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    
    // Get shared singleton instance
    val locationViewModel = LocationViewModelProvider.getInstance(
        context.applicationContext as android.app.Application
    )
    
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = locationViewModel,
                onRequestPermissions = onRequestPermissions,
                onNavigateToMap = {
                    navController.navigate(Screen.Map.route)
                }
            )
        }
        
        composable(Screen.Map.route) {
            MapScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
