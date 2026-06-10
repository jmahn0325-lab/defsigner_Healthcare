package com.example.healthcare

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.healthcare.data.FCMTokenManager
import com.example.healthcare.data.HealthState
import com.example.healthcare.ui.screens.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // 기기 ID 기반 userId 획득
        val healthState = HealthState.getInstance(this)
        
        // FCM 토큰 갱신 및 업데이트 (요구사항 1)
        FCMTokenManager.updateTokenForUser(healthState.userId)

        // 인텐트 처리 (요구사항 4)
        handleIntent(intent)

        setContent {
            // 앱이 켜져 있는 동안 플래그 설정
            LaunchedEffect(Unit) {
                MyFirebaseMessagingService.isAppInForeground = true
            }
            
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

    override fun onResume() {
        super.onResume()
        MyFirebaseMessagingService.isAppInForeground = true
    }

    override fun onPause() {
        super.onPause()
        MyFirebaseMessagingService.isAppInForeground = false
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.getStringExtra("action") ?: return
        val senderId = intent.getStringExtra("senderId") ?: ""
        
        // 특정 액션에 따른 로직 수행
        if (action == "OPEN_CHAT") {
            Toast.makeText(this, "$senderId 님과의 채팅을 엽니다.", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun HealthApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val navController = rememberNavController()
    // 싱글톤 인스턴스를 가져오고 초기화합니다.
    val healthState = remember { HealthState.getInstance(context) }

    // 알림 권한 요청 (안드로이드 13 이상)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "알림 권한이 거부되었습니다. 중요 알림을 받지 못할 수 있습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val startDestination = when {
        healthState.userName.isBlank() -> "nameSetting"
        !healthState.isOnboardingCompleted -> "onboarding"
        else -> "main"
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("nameSetting") {
            UserNameSettingScreen(healthState = healthState) {
                // 사용자 정보를 받은 후 앱 재시작 (FCM 토큰 갱신 보장)
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                val mainIntent = Intent.makeRestartActivityTask(intent?.component)
                context.startActivity(mainIntent)
                Runtime.getRuntime().exit(0)
            }
        }
        composable("onboarding") {
            OnboardingUserScreen(healthState = healthState) {
                // 사용자 정보를 받은 후 앱 재시작 (FCM 토큰 갱신 보장)
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                val mainIntent = Intent.makeRestartActivityTask(intent?.component)
                context.startActivity(mainIntent)
                Runtime.getRuntime().exit(0)
            }
        }
        composable("main") {
            MainHealthSpectrumScreen(
                healthState = healthState,
                onNavigateToDetail = { itemName -> navController.navigate("detail/$itemName") },
                onNavigateToSocial = { navController.navigate("social") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("social") {
            SocialPartyScreen(
                healthState = healthState,
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                healthState = healthState,
            ) { navController.popBackStack() }
        }
        composable("detail/{itemName}") { backStackEntry ->
            val itemName = backStackEntry.arguments?.getString("itemName") ?: "상세"
            DetailScreen(
                itemName = itemName,
                healthState = healthState,
            ) { navController.popBackStack() }
        }
    }
}
