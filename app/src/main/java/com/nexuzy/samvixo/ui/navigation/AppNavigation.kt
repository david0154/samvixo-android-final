package com.nexuzy.samvixo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    AppNavGraph(navController = navController)
}
