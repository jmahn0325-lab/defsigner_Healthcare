package com.example.healthcare.data

import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
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
     * 현재 유저의 토큰을 갱신하고 Firestore에 저장합니다.
     * 앱 시작 시 또는 로그인 성공 시 호출 권장.
     */
    fun updateTokenForCurrentUser() {
        val currentUser = Firebase.auth.currentUser ?: return
        
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d(TAG, "Current Token: $token")
            
            // IO 스레드에서 Firestore 업데이트
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    FirebaseFirestore.getInstance()
                        .collection("Users")
                        .document(currentUser.uid)
                        .update("fcmToken", token)
                        .await()
                    Log.d(TAG, "Token updated successfully for user ${currentUser.uid}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating token", e)
                }
            }
        }
    }
}
