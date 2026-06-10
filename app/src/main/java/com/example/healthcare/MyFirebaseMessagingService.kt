package com.example.healthcare

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import android.widget.Toast
import com.example.healthcare.data.SocialRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * FCM 알림 수신 및 처리 서비스
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM"
        private const val CHANNEL_ID = "nudge_channel"

        // 포그라운드 상태 확인을 위한 간이 플래그 (실제 상용 앱에선 LifecycleObserver 권장)
        var isAppInForeground = false
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        // 데이터 페이로드 추출
        val data = remoteMessage.data
        val title = data["title"] ?: remoteMessage.notification?.title ?: "건강 알림"
        val body = data["body"] ?: remoteMessage.notification?.body ?: "메시지가 도착했습니다."
        val action = data["action"] ?: ""
        val senderId = data["senderId"] ?: ""

        if (isAppInForeground) {
            // 포그라운드: Toast를 메인 스레드에서 실행하여 실기기에서도 안전하게 표시
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(applicationContext, "[$title] $body", Toast.LENGTH_SHORT).show()
            }
        } else {
            // 백그라운드: 시스템 알림 바
            sendNotification(title, body, action, senderId)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New token: $token")

        val healthState = com.example.healthcare.data.HealthState.getInstance(applicationContext)
        val userId = healthState.userId

        if (userId != "guest_user") {
            CoroutineScope(Dispatchers.IO).launch {
                SocialRepository().updateFcmToken(userId, token)
            }
        }
    }

    private fun sendNotification(title: String, body: String, action: String, senderId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // 인텐트 데이터 전달 (요구사항 4)
            putExtra("action", action)
            putExtra("senderId", senderId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "건강 서비스 알림",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
