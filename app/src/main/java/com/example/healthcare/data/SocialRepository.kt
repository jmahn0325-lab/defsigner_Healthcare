package com.example.healthcare.data

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.*

class SocialRepository {
    private val db = FirebaseFirestore.getInstance()

    // --- 1. 파티 형성 및 초대 기능 ---

    // 6자리 랜덤 초대 코드 생성 로직 (중복 최소화를 위해 문자 혼합)
    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    // 새로운 파티 생성
    suspend fun createParty(partyName: String, leaderUid: String): String {
        Log.d("SocialRepository", "createParty called for leaderUid: $leaderUid")
        return try {
            val inviteCode = generateInviteCode()
            val partyId = UUID.randomUUID().toString()
            
            val partyData = hashMapOf(
                "partyId" to partyId,
                "partyName" to partyName,
                "inviteCode" to inviteCode,
                "memberUids" to listOf(leaderUid),
                "createdAt" to FieldValue.serverTimestamp()
            )
            
            db.collection("Parties").document(partyId).set(partyData).await()
            Log.d("SocialRepository", "Party created successfully: $partyId")
            inviteCode
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error creating party", e)
            "ERROR"
        }
    }

    // 내가 속한 파티 목록 실시간 감시 (Flow)
    fun getMyPartiesFlow(myUid: String): Flow<List<Party>> = callbackFlow {
        Log.d("SocialRepository", "getMyPartiesFlow started for myUid: $myUid")
        
        val query = db.collection("Parties")
            .whereArrayContains("memberUids", myUid)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("SocialRepository", "Listen failed", error)
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val parties = snapshot.toObjects(Party::class.java)
                Log.d("SocialRepository", "Real-time update: ${parties.size} parties found")
                // createdAt 정보가 서버에서 아직 오지 않았을 경우를 고려해 안전하게 정렬
                val sortedParties = parties.sortedByDescending { it.createdAt }
                trySend(sortedParties)
            }
        }

        awaitClose { 
            Log.d("SocialRepository", "getMyPartiesFlow closed")
            listener.remove()
        }
    }

    // 내 파티 목록 가져오기 (단회성)
    suspend fun getMyParties(myUid: String): List<Party> {
        return try {
            val query = db.collection("Parties")
                .whereArrayContains("memberUids", myUid)
                .get()
                .await()
            query.toObjects(Party::class.java).sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error getting my parties", e)
            emptyList()
        }
    }

    // 파티 이름 수정
    suspend fun updatePartyName(partyId: String, newName: String): Boolean {
        return try {
            db.collection("Parties").document(partyId)
                .update("partyName", newName)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // 초대 코드로 파티 참여하기
    suspend fun joinParty(inviteCode: String, myUid: String): Boolean {
        Log.d("SocialRepository", "joinParty attempt: Code=$inviteCode, User=$myUid")
        return try {
            val query = db.collection("Parties")
                .whereEqualTo("inviteCode", inviteCode.trim().uppercase())
                .limit(1)
                .get()
                .await()

            if (!query.isEmpty) {
                val document = query.documents[0]
                // 문서 ID를 직접 사용하여 업데이트 보장
                db.collection("Parties").document(document.id)
                    .update("memberUids", FieldValue.arrayUnion(myUid))
                    .await()
                Log.d("SocialRepository", "Join Success: ${document.id}")
                true
            } else {
                Log.e("SocialRepository", "Join Fail: Code not found")
                false
            }
        } catch (e: Exception) {
            Log.e("SocialRepository", "Join Error", e)
            false
        }
    }

    // --- 2. 파티원 리더보드 조회 ---

    suspend fun getPartyLeaderboard(partyId: String): List<UserScore> {
        return try {
            // 1. 파티 문서에서 멤버 UID 리스트 가져오기
            val partyDoc = db.collection("Parties").document(partyId).get().await()
            val memberUids = partyDoc.get("memberUids") as? List<String> ?: return emptyList()

            if (memberUids.isEmpty()) return emptyList()

            // 2. 멤버들의 건강 점수 정보 쿼리 (whereIn은 최대 30명까지 지원)
            db.collection("Users")
                .whereIn("uid", memberUids)
                .orderBy("totalScore", Query.Direction.DESCENDING)
                .get()
                .await()
                .toObjects(UserScore::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- 3. 멤버별 상세 로그 및 프라이버시 필터링 ---

    suspend fun getMemberDetailLogs(memberUid: String): MemberDetails? {
        return try {
            // 1. 해당 유저의 프라이버시 설정 및 기본 정보 조회
            val userDoc = db.collection("Users").document(memberUid).get().await()
            if (!userDoc.exists()) return null
            
            val displayName = userDoc.getString("displayName") ?: "알 수 없음"
            val privacy = userDoc.get("privacySettings", PrivacySettings::class.java) ?: PrivacySettings()

            // 2. 가장 최근의 일일 로그 1개 가져오기
            val logsQuery = db.collection("Users").document(memberUid)
                .collection("DailyLogs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            if (logsQuery.isEmpty) {
                return MemberDetails(memberUid, displayName, emptyMap())
            }

            val logDoc = logsQuery.documents[0]
            val rawScores = logDoc.get("scores") as? Map<String, Double> ?: emptyMap()
            
            // 3. 프라이버시 설정에 따른 필터링 분기 처리
            val filteredScores = mutableMapOf<String, Float>()
            rawScores.forEach { (factor, score) ->
                val isVisible = when (factor) {
                    "알코올" -> privacy.alcoholVisible
                    "카페인" -> privacy.caffeineVisible
                    "흡연" -> privacy.smokingVisible
                    else -> privacy.healthVisible // 수면, 활동량 등
                }
                if (isVisible) {
                    filteredScores[factor] = score.toFloat()
                }
            }

            MemberDetails(memberUid, displayName, filteredScores)
        } catch (e: Exception) {
            null
        }
    }

    // --- 4. 항목별 콕 찌르기 (Nudge) 기록 ---

    suspend fun sendNudge(senderId: String, receiverId: String, factor: String): Boolean {
        return try {
            val nudgeData = hashMapOf(
                "senderId" to senderId,
                "receiverId" to receiverId,
                "targetFactor" to factor,
                "timestamp" to FieldValue.serverTimestamp()
            )
            
            // Interactions 컬렉션에 기록 (FCM 트리거용)
            db.collection("Interactions").add(nudgeData).await()
            true
        } catch (e: Exception) {
            false
        }
    }
}
