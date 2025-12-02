package com.example.indoormaps.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Map : Screen("map")
}
