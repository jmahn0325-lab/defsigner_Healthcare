package com.example.healthcare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.healthcare.data.HealthState
import com.example.healthcare.ui.screens.DetailScreen
import com.example.healthcare.ui.screens.MainHealthSpectrumScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    HealthApp()
                }
            }
        }
    }
}

@Composable
fun HealthApp() {
    val navController = rememberNavController()
    // 최상위에서 상태를 한 번만 초기화하여 하위 화면으로 전달 (또는 ViewModel로 대체 가능)
    val healthState = remember { HealthState() }

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainHealthSpectrumScreen(
                healthState = healthState,
                onNavigateToDetail = { itemName -> navController.navigate("detail/$itemName") }
            )
        }
        composable("detail/{itemName}") { backStackEntry ->
            val itemName = backStackEntry.arguments?.getString("itemName") ?: "상세"
            DetailScreen(
                itemName = itemName,
                healthState = healthState,
                onBack = { navController.popBackStack() }
            )
        }
    }
}