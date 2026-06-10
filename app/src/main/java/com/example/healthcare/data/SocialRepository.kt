package com.example.healthcare.data

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.*
import java.util.Locale

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
            
            val partyData = mapOf<String, Any>(
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
                val sortedParties = parties.sortedByDescending { it.createdAt?.seconds ?: 0L }
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
            query.toObjects(Party::class.java).sortedByDescending { it.createdAt?.seconds ?: 0L }
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

            // 2. 멤버들의 정보 가져오기 (whereIn 사용)
            val usersSnapshot = db.collection("Users")
                .whereIn("uid", memberUids)
                .get()
                .await()
            
            // 3. 점수 순으로 내림차순 정렬 (서버 인덱스 오류 방지를 위해 클라이언트 정렬)
            val users = usersSnapshot.documents.mapNotNull { doc ->
                val user = doc.toObject(UserScore::class.java)
                // isPublic이 Boolean 기본값 false로 오작동하는 것을 방지하기 위해 명시적으로 가져옴
                user?.copy(isPublic = doc.getBoolean("isPublic") ?: true)
            }
            users.sortedByDescending { it.totalScore }
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error fetching leaderboard", e)
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
            val isPublic = userDoc.getBoolean("isPublic") ?: true
            
            // 전체 공개가 아니면 빈 점수 맵 반환 (UI에서 '비공개'로 처리됨)
            if (!isPublic) {
                return MemberDetails(memberUid, displayName, emptyMap())
            }

            // 개별 항목별 공개 설정 (privacySettings 맵/객체 읽기)
            val privacyMap = userDoc.get("privacySettings") as? Map<String, Any>
            val privacy = if (privacyMap != null) {
                PrivacySettings(
                    alcoholVisible = privacyMap["alcoholVisible"] as? Boolean ?: true,
                    caffeineVisible = privacyMap["caffeineVisible"] as? Boolean ?: true,
                    smokingVisible = privacyMap["smokingVisible"] as? Boolean ?: true,
                    healthVisible = privacyMap["healthVisible"] as? Boolean ?: true
                )
            } else {
                PrivacySettings()
            }

            // 2. 최근 7일간의 일일 로그 가져오기
            val logsQuery = db.collection("Users").document(memberUid)
                .collection("DailyLogs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(7)
                .get()
                .await()

            if (logsQuery.isEmpty) {
                return MemberDetails(memberUid, displayName, emptyMap())
            }

            // 3. 점수 합산 및 로그 통합
            val totalScores = mutableMapOf<String, Float>()
            val totalIntake24h = mutableMapOf<String, Float>()
            val detailedLogs = mutableMapOf<String, MutableList<String>>()

            // 24시간 계산을 위한 기준점
            val now = java.time.LocalDateTime.now()
            val twentyFourHoursAgo = now.minusHours(24)

            // 최신 로그(첫 번째 문서)에서 현재 감점 정보를 가져옴 (리더보드와 일관성)
            val latestDoc = logsQuery.documents[0]
            val latestScores = latestDoc.get("scores") as? Map<String, Double> ?: emptyMap()
            latestScores.forEach { (factor, score) ->
                // 알코올, 카페인, 흡연 항목만 필터링
                if (factor == "알코올" || factor == "카페인" || factor == "흡연") {
                    totalScores[factor] = score.toFloat()
                }
            }

            // 7일간의 모든 로그 문서를 순회하며 레코드 수집 및 24시간 섭취량 계산
            // 최신 기록이 위로 가도록 하기 위해 수집 후 정렬
            val allRawRecords = mutableListOf<Map<String, Any>>()
            logsQuery.documents.forEach { logDoc ->
                val rawRecords = logDoc.get("records") as? List<Map<String, Any>> ?: return@forEach
                allRawRecords.addAll(rawRecords)
            }

            allRawRecords.forEach { recordMap ->
                val type = recordMap["type"] as? String ?: return@forEach

                // recordDateTime 복구 (date, hour, minute, second 활용)
                val dateStr = recordMap["date"] as? String ?: ""
                val hour = (recordMap["hour"] as? Number)?.toInt() ?: 0
                val minute = (recordMap["minute"] as? Number)?.toInt() ?: 0
                val second = (recordMap["second"] as? Number)?.toInt() ?: 0

                val recordDateTime = try {
                    java.time.LocalDate.parse(dateStr).atTime(hour, minute, second)
                } catch (e: Exception) {
                    now
                }

                val isVisible = when (type) {
                    "알코올" -> privacy.alcoholVisible
                    "카페인" -> privacy.caffeineVisible
                    "흡연" -> privacy.smokingVisible
                    else -> false
                }
                
                if (isVisible) {
                    val itemName = recordMap["itemName"] as? String
                    val unit = recordMap["unit"] as? String
                    val value = (recordMap["value"] as? Number)?.toFloat() ?: 0f
                    val isBingeStored = recordMap["isBinge"] as? Boolean ?: false // 서버에 저장된 정확한 패널티 정보 사용
                    
                    // 24시간 내 섭취량 합산
                    if (recordDateTime.isAfter(twentyFourHoursAgo)) {
                        totalIntake24h[type] = (totalIntake24h[type] ?: 0f) + value
                    }

                    if (itemName != null && unit != null) {
                        val contentUnit = when(type) {
                            "알코올" -> "g"
                            "카페인" -> "mg"
                            "흡연" -> "mg"
                            else -> ""
                        }

                        // 상세 형식 포맷팅 (시간 포함)
                        val timeStr = String.format(Locale.getDefault(), "%02d:%02d", recordDateTime.hour, recordDateTime.minute)
                        val dateStr = recordDateTime.format(java.time.format.DateTimeFormatter.ofPattern("MM/dd"))
                        val fullTimePrefix = "$dateStr $timeStr\n"

                        // 비율 추론 (기존 로직 유지)
                        val ratio = when(itemName) {
                            "소주" -> 6.3f
                            "맥주" -> 7.1f
                            "와인" -> 15.4f
                            "아메리카노" -> 150f
                            "에너지 드링크" -> 100f
                            "녹차" -> 30f
                            else -> 1f
                        }
                        
                        val displayIntake = if (type == "흡연") "${value.toInt()}mg" else "${value.toInt()}$contentUnit"

                        val count = value / ratio
                        val countStr = if (count % 1f == 0f) "${count.toInt()}" else String.format(Locale.getDefault(), "%.1f", count)
                        
                        // 서버에서 직접 가져온 패널티 여부 적용
                        val penaltyTag = if (isBingeStored) " (폭주 패널티)" else ""

                        val logString = "$fullTimePrefix$itemName ${countStr}$unit / $displayIntake$penaltyTag"
                        
                        val sortKey = "${recordMap["date"]}_${String.format(Locale.getDefault(), "%02d:%02d:%02d", recordDateTime.hour, recordDateTime.minute, recordDateTime.second)}"
                        
                        val list = detailedLogs.getOrPut(type) { mutableListOf() }
                        list.add("$sortKey|$logString")
                    }
                }
            }

            // 각 항목별 로그를 시간순(최신순)으로 정렬
            val sortedDetailedLogs = detailedLogs.mapValues { (_, list) ->
                list.sortedByDescending { it.split("|")[0] }
                    .map { it.split("|")[1] }
            }

            MemberDetails(memberUid, displayName, totalScores, totalIntake24h, sortedDetailedLogs)
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error fetching member details", e)
            null
        }
    }

    // --- 4. 항목별 콕 찌르기 (Nudge) 기록 ---

    /**
     * 콕 찌르기 수행: DB 기록 및 알림 큐 등록
     * 기존 Interactions 기록 외에 Notifications 컬렉션에 추가하여 
     * 클라우드 함수 등이 이를 감지하고 실제 FCM을 발송할 수 있게 합니다.
     */
    suspend fun sendNudge(senderId: String, receiverId: String, factor: String): Boolean {
        return try {
            val timestamp = FieldValue.serverTimestamp()
            
            // 발신자 이름 조회 (알림 내용 구성용)
            val senderDoc = db.collection("Users").document(senderId).get().await()
            val senderName = senderDoc.getString("displayName") ?: "누군가"
            
            // 1. Interactions 기록 (기존 기능 유지)
            val nudgeData = mapOf<String, Any>(
                "senderId" to senderId,
                "senderName" to senderName,
                "receiverId" to receiverId,
                "targetFactor" to factor,
                "timestamp" to timestamp
            )
            db.collection("Interactions").add(nudgeData).await()

            // 2. 실제 푸시 알림 발송을 위한 대기열(Queue) 등록
            val receiverToken = getUserFcmToken(receiverId)
            if (receiverToken != null) {
                // 요구사항에 맞춘 타이틀 구성: "A님에게 콕 찌르기(항목이름)가 왔습니다"
                val notificationTitle = "${senderName}님에게 콕 찌르기(${factor})가 왔습니다"
                val nudgeType = NudgeType.fromString(factor)
                
                val notificationData = mapOf(
                    "to" to receiverToken,
                    "title" to notificationTitle,
                    "body" to NudgeMessageProvider.getRandomBody(nudgeType),
                    "senderId" to senderId,
                    "receiverId" to receiverId,
                    "status" to "pending",
                    "timestamp" to timestamp
                )
                db.collection("Notifications").add(notificationData).await()
            }
            
            true
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error sending nudge", e)
            false
        }
    }

    /**
     * 콕 찌르기 알림을 위한 유저 토큰 조회
     */
    suspend fun getUserFcmToken(uid: String): String? {
        return try {
            val doc = db.collection("Users").document(uid).get().await()
            doc.getString("fcmToken")
        } catch (e: Exception) {
            null
        }
    }

    // 신규 유저 등록
    suspend fun createUser(uid: String, userName: String, gender: String): Boolean {
        return try {
            val userData = mapOf<String, Any>(
                "uid" to uid,
                "displayName" to userName,
                "gender" to gender,
                "totalScore" to 0,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("Users").document(uid).set(userData).await()
            true
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error creating user", e)
            false
        }
    }

    // 유저 점수 및 상세 로그 동기화
    suspend fun updateUserScore(uid: String, score: Int, records: List<HealthRecord> = emptyList(), penalties: Map<String, Float> = emptyMap()): Boolean {
        return try {
            val userRef = db.collection("Users").document(uid)
            
            // 1. 기본 점수 업데이트
            userRef.update("totalScore", score).await()
            
            // 2. 오늘의 상세 로그 업로드 (DailyLogs 서브 컬렉션)
            val today = java.time.LocalDate.now().toString()
            val logData = mapOf(
                "date" to today,
                "timestamp" to FieldValue.serverTimestamp(),
                "scores" to penalties, // 실제 감점량 (예: "알코올" -> -12.5f)
                "records" to records.map { record ->
                    mapOf(
                        "type" to record.type,
                        "value" to record.value,
                        "itemName" to record.itemName,
                        "unit" to record.unit,
                        "hour" to record.hour,
                        "minute" to record.minute,
                        "second" to record.second,
                        "date" to record.date.toString(),
                        "isBinge" to record.isBinge // 패널티 여부 서버 동기화
                    )
                }
            )
            
            userRef.collection("DailyLogs").document(today).set(logData).await()
            
            true
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error updating score and logs", e)
            false
        }
    }

    // 유저 프로필 업데이트 (이름, 소개글, 공개여부)
    suspend fun updateUserProfile(uid: String, name: String, bio: String, isPublic: Boolean): Boolean {
        return try {
            db.collection("Users").document(uid).update(
                "displayName", name,
                "bio", bio,
                "isPublic", isPublic
            ).await()
            true
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error updating profile", e)
            false
        }
    }

    // FCM 토큰 업데이트
    suspend fun updateFcmToken(uid: String, token: String): Boolean {
        return try {
            db.collection("Users").document(uid).update("fcmToken", token).await()
            true
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error updating FCM token", e)
            false
        }
    }

    // --- 5. 1:1 개인 쪽지 발송 (mailbox) ---

    suspend fun sendPrivateMail(senderId: String, receiverId: String, title: String, body: String, action: String): Boolean {
        return try {
            val mailData = mapOf(
                "senderId" to senderId,
                "receiverId" to receiverId,
                "title" to title,
                "body" to body,
                "action" to action,
                "timestamp" to FieldValue.serverTimestamp()
            )
            db.collection("mailbox").add(mailData).await()
            true
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error sending private mail", e)
            false
        }
    }

    // --- 6. 실시간 공용 채팅 (messages) ---

    suspend fun sendMessage(senderId: String, text: String): Boolean {
        return try {
            val messageData = mapOf(
                "senderId" to senderId,
                "text" to text,
                "timestamp" to FieldValue.serverTimestamp()
            )
            db.collection("messages").add(messageData).await()
            true
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error sending message", e)
            false
        }
    }

    fun getRealtimeMessagesFlow(startTime: Date): Flow<List<ChatMessage>> = callbackFlow {
        val query = db.collection("messages")
            .whereGreaterThan("timestamp", com.google.firebase.Timestamp(startTime))
            .orderBy("timestamp", Query.Direction.ASCENDING)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val messages = snapshot.toObjects(ChatMessage::class.java)
                trySend(messages)
            }
        }
        awaitClose { listener.remove() }
    }
}
