package com.example.healthcare.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * FCM 토큰 발급 및 Firestore 업데이트를 담당하는 매니저
 */
object FCMTokenManager {
    private const val TAG = "FCMTokenManager"

    /**
     * 특정 유저의 토큰을 갱신하고 Firestore에 저장합니다. (기기 ID 기반 uid 지원)
     */
    fun updateTokenForUser(userId: String) {
        if (userId.isBlank() || userId == "guest_user") {
            Log.w(TAG, "Invalid userId for token update: $userId")
            return
        }
        
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d(TAG, "Current Token: $token for user: $userId")
            
            // IO 스레드에서 Firestore 업데이트
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 문서가 없을 수도 있으므로 merge() 옵션으로 안전하게 저장/갱신
                    FirebaseFirestore.getInstance()
                        .collection("Users")
                        .document(userId)
                        .set(mapOf("fcmToken" to token), SetOptions.merge())
                        .await()
                    Log.d(TAG, "Token updated successfully for user $userId")
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating token for user $userId", e)
                }
            }
        }
    }
}
