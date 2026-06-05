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
import com.example.healthcare.ui.screens.OnboardingScreen
import com.example.healthcare.ui.screens.SocialPartyScreen

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
    val context = androidx.compose.ui.platform.LocalContext.current
    val navController = rememberNavController()
    // 싱글톤 인스턴스를 가져오고 초기화합니다.
    val healthState = remember { HealthState.getInstance(context) }

    val startDestination = if (healthState.isOnboardingCompleted) "main" else "onboarding"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("onboarding") {
            OnboardingScreen(
                healthState = healthState,
                onComplete = {
                    navController.navigate("main") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }
        composable("main") {
            MainHealthSpectrumScreen(
                healthState = healthState,
                onNavigateToDetail = { itemName -> navController.navigate("detail/$itemName") },
                onNavigateToSocial = { navController.navigate("social") }
            )
        }
        composable("social") {
            SocialPartyScreen(
                myUid = healthState.userId, // HealthState에 userId가 있다고 가정하거나 Onboarding에서 저장된 값 사용
                onBack = { navController.popBackStack() }
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
