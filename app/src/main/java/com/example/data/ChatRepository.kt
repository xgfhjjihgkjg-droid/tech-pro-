package com.example.data

import android.content.Context
import com.example.R
import com.example.crypto.CryptoEngine
import com.example.crypto.IdentityKeyBundle
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    // --- 1. DIRECTORY & REAL-TIME PRESENCE ---

    fun observeDirectoryUsers(): Flow<List<DirectoryUser>> = flow {
        requireUserId()
        val path = "directory"
        emitAll(
            db.collection("directory")
                .whereEqualTo("discoverable", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DirectoryUser::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.lastSeenAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logAndRethrowFlowError(error, OperationType.LIST, path)
                }
        )
    }

    fun observeDirectoryUser(userId: String): Flow<DirectoryUser?> = flow {
        requireUserId()
        require(userId.isNotBlank())
        val path = "directory/$userId"
        emitAll(
            db.collection("directory").document(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        DirectoryUser::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logAndRethrowFlowError(error, OperationType.GET, path)
                }
        )
    }

    suspend fun getDirectoryUsersByIds(uids: List<String>): Map<String, DirectoryUser> {
        val cleanIds = uids.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(60)
        if (cleanIds.isEmpty()) return emptyMap()
        if (auth.currentUser == null) return emptyMap()
        return coroutineScope {
            cleanIds.map { targetUid ->
                async {
                    runCatching {
                        val snap = db.collection("directory").document(targetUid).get().await()
                        snap.toObject(
                            DirectoryUser::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )?.let { targetUid to it }
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull().toMap()
        }
    }

    fun observePrivateAccount(userId: String): Flow<PrivateAccount?> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$userId/private/account"
        emitAll(
            db.collection("users").document(userId)
                .collection("private").document("account")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        PrivateAccount::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logAndRethrowFlowError(error, OperationType.GET, path)
                }
        )
    }

    suspend fun ensureUserProfileInitialized(
        userId: String,
        displayName: String,
        email: String,
        phoneNumber: String?,
        photoUrl: String?,
        keyBundle: IdentityKeyBundle
    ): Result<Unit> {
        val dirPath = "directory/$userId"
        val privPath = "users/$userId/private/account"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val safeName = displayName.trim().ifBlank { "ToKerUser" }.take(60)
            val cleanHandleBase = safeName.lowercase().replace(Regex("[^a-z0-9]"), "_").take(20).ifBlank { "toker" }
            val handle = "@${cleanHandleBase}_${uid.takeLast(4)}".take(40)

            val dirRef = db.collection("directory").document(uid)
            val existingDir = dirRef.get().await()
            if (!existingDir.exists()) {
                val payload = mapOf(
                    "uid" to uid,
                    "displayName" to safeName,
                    "handle" to handle,
                    "photoUrl" to photoUrl?.take(500),
                    "publicKey" to keyBundle.publicKeyBase64,
                    "keyFingerprint" to keyBundle.keyFingerprint,
                    "presence" to PresenceStatus.ONLINE.name,
                    "statusMessage" to "End-to-end encrypted on ToKer",
                    "statusMood" to "Available",
                    "discoverable" to true,
                    "lastSeenVisibility" to PrivacyAudience.EVERYONE.name,
                    "photoVisibility" to PrivacyAudience.EVERYONE.name,
                    "statusVisibility" to PrivacyAudience.EVERYONE.name,
                    "statusUpdatedAt" to FieldValue.serverTimestamp(),
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                dirRef.set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "displayName" to (existingDir.getString("displayName")?.takeIf { it.isNotBlank() } ?: safeName),
                    "handle" to (existingDir.getString("handle") ?: handle),
                    "publicKey" to keyBundle.publicKeyBase64,
                    "keyFingerprint" to keyBundle.keyFingerprint,
                    "presence" to PresenceStatus.ONLINE.name,
                    "statusMessage" to (existingDir.getString("statusMessage") ?: "End-to-end encrypted on ToKer"),
                    "statusMood" to (existingDir.getString("statusMood") ?: "Available"),
                    "discoverable" to (existingDir.getBoolean("discoverable") ?: true),
                    "lastSeenVisibility" to (existingDir.getString("lastSeenVisibility") ?: PrivacyAudience.EVERYONE.name),
                    "photoVisibility" to (existingDir.getString("photoVisibility") ?: PrivacyAudience.EVERYONE.name),
                    "statusVisibility" to (existingDir.getString("statusVisibility") ?: PrivacyAudience.EVERYONE.name),
                    "statusUpdatedAt" to (existingDir.getTimestamp("statusUpdatedAt") ?: FieldValue.serverTimestamp()),
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val storedPhoto = existingDir.getString("photoUrl")
                if (!photoUrl.isNullOrBlank() && photoUrl.take(500) != storedPhoto) {
                    updates["photoUrl"] = photoUrl.take(500)
                }
                dirRef.update(updates).await()
            }

            val privRef = db.collection("users").document(uid).collection("private").document("account")
            val existingPriv = privRef.get().await()
            if (!existingPriv.exists()) {
                val privPayload = mapOf(
                    "userId" to uid,
                    "email" to email.take(200),
                    "phoneNumber" to phoneNumber?.take(32),
                    "verifiedPeerIds" to emptyList<String>(),
                    "savedContactIds" to emptyList<String>(),
                    "blockedUserIds" to emptyList<String>(),
                    "readReceiptsEnabled" to true,
                    "lastSeenVisibility" to PrivacyAudience.EVERYONE.name,
                    "photoVisibility" to PrivacyAudience.EVERYONE.name,
                    "statusVisibility" to PrivacyAudience.EVERYONE.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                privRef.set(privPayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "$dirPath | $privPath")
            Result.failure(e)
        }
    }

    suspend fun updatePresence(presence: PresenceStatus): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val path = "directory/$uid"
        return try {
            db.collection("directory").document(uid).update(
                mapOf(
                    "presence" to presence.name,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMyStatusBanner(
        presence: PresenceStatus,
        mood: String,
        statusMessage: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val path = "directory/$uid"
        return try {
            val cleanMood = mood.trim().ifBlank { "Available" }.take(32)
            val cleanMsg = statusMessage.trim().take(140)
            db.collection("directory").document(uid).update(
                mapOf(
                    "presence" to presence.name,
                    "statusMood" to cleanMood,
                    "statusMessage" to cleanMsg,
                    "statusUpdatedAt" to FieldValue.serverTimestamp(),
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleVerifiedPeer(peerId: String, currentVerifiedIds: List<String>): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val path = "users/$uid/private/account"
        return try {
            val updated = if (peerId in currentVerifiedIds) {
                currentVerifiedIds - peerId
            } else {
                (currentVerifiedIds + peerId).distinct().take(200)
            }
            db.collection("users").document(uid)
                .collection("private").document("account")
                .update(
                    mapOf(
                        "verifiedPeerIds" to updated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMyProfileControls(
        displayName: String,
        handle: String,
        statusMessage: String,
        statusMood: String,
        presence: PresenceStatus,
        discoverable: Boolean,
        avatarBase64: String?,
        clearAvatar: Boolean,
        readReceiptsEnabled: Boolean,
        lastSeenVisibility: PrivacyAudience = PrivacyAudience.EVERYONE,
        photoVisibility: PrivacyAudience = PrivacyAudience.EVERYONE,
        statusVisibility: PrivacyAudience = PrivacyAudience.EVERYONE
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val dirPath = "directory/$uid"
        val privPath = "users/$uid/private/account"
        return try {
            val cleanName = displayName.trim().ifBlank { "ToKerUser" }.take(60)
            val rawHandle = handle.trim().let { if (it.startsWith("@")) it else "@$it" }
            val cleanHandle = rawHandle.replace(Regex("[^a-zA-Z0-9_@]"), "_").take(40)
                .let { if (it.length < 2) "@toker_${uid.takeLast(4)}" else it }
            val cleanMsg = statusMessage.trim().take(140)
            val cleanMood = statusMood.trim().ifBlank { "Available" }.take(32)

            val dirUpdates = mutableMapOf<String, Any?>(
                "displayName" to cleanName,
                "handle" to cleanHandle,
                "statusMessage" to cleanMsg,
                "statusMood" to cleanMood,
                "presence" to presence.name,
                "discoverable" to discoverable,
                "lastSeenVisibility" to lastSeenVisibility.name,
                "photoVisibility" to photoVisibility.name,
                "statusVisibility" to statusVisibility.name,
                "statusUpdatedAt" to FieldValue.serverTimestamp(),
                "lastSeenAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (clearAvatar) {
                dirUpdates["avatarBase64"] = null
                dirUpdates["photoUrl"] = null
            } else if (!avatarBase64.isNullOrBlank()) {
                dirUpdates["avatarBase64"] = avatarBase64.take(160_000)
            }
            db.collection("directory").document(uid).update(dirUpdates).await()

            db.collection("users").document(uid)
                .collection("private").document("account")
                .update(
                    mapOf(
                        "readReceiptsEnabled" to readReceiptsEnabled,
                        "lastSeenVisibility" to lastSeenVisibility.name,
                        "photoVisibility" to photoVisibility.name,
                        "statusVisibility" to statusVisibility.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "$dirPath | $privPath")
            Result.failure(e)
        }
    }

    suspend fun updatePrivacySettings(
        discoverable: Boolean,
        readReceiptsEnabled: Boolean,
        lastSeenVisibility: PrivacyAudience,
        photoVisibility: PrivacyAudience,
        statusVisibility: PrivacyAudience
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        return try {
            db.collection("directory").document(uid).update(
                mapOf(
                    "discoverable" to discoverable,
                    "lastSeenVisibility" to lastSeenVisibility.name,
                    "photoVisibility" to photoVisibility.name,
                    "statusVisibility" to statusVisibility.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection("users").document(uid)
                .collection("private").document("account")
                .update(
                    mapOf(
                        "readReceiptsEnabled" to readReceiptsEnabled,
                        "lastSeenVisibility" to lastSeenVisibility.name,
                        "photoVisibility" to photoVisibility.name,
                        "statusVisibility" to statusVisibility.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "directory/$uid | users/$uid/private/account")
            Result.failure(e)
        }
    }

    suspend fun toggleSavedContact(peerId: String, currentSaved: List<String>): Result<Boolean> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val path = "users/$uid/private/account"
        return try {
            val isRemoving = peerId in currentSaved
            val updated = if (isRemoving) {
                currentSaved - peerId
            } else {
                (currentSaved + peerId).distinct().take(500)
            }
            db.collection("users").document(uid)
                .collection("private").document("account")
                .update(
                    mapOf(
                        "savedContactIds" to updated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(!isRemoving)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun findUserAndSaveContact(
        query: String,
        currentSaved: List<String>
    ): Result<DirectoryUser> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val clean = query.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Enter a handle (@user) or user UID."))
        return try {
            var matched: DirectoryUser? = null
            if (!clean.startsWith("@") && clean.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) {
                val byId = db.collection("directory").document(clean).get().await()
                if (byId.exists()) {
                    matched = byId.toObject(DirectoryUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }
            if (matched == null) {
                val dirDocs = db.collection("directory")
                    .whereEqualTo("discoverable", true)
                    .get()
                    .await()
                    .toObjects(DirectoryUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                val normalized = clean.lowercase()
                matched = dirDocs.firstOrNull {
                    it.uid != uid && (
                        it.handle.lowercase() == normalized ||
                            it.handle.lowercase() == "@$normalized" ||
                            it.displayName.lowercase() == normalized ||
                            it.handle.lowercase().contains(normalized)
                        )
                }
            }
            if (matched == null || matched.uid == uid) {
                return Result.failure(IllegalArgumentException("No ToKer contact found matching \"$clean\"."))
            }
            val updated = (currentSaved + matched.uid).distinct().take(500)
            db.collection("users").document(uid)
                .collection("private").document("account")
                .update(
                    mapOf(
                        "savedContactIds" to updated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(matched)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "directory")
            Result.failure(e)
        }
    }

    suspend fun toggleBlockedUser(peerId: String, currentBlocked: List<String>): Result<Boolean> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val path = "users/$uid/private/account"
        return try {
            val isUnblocking = peerId in currentBlocked
            val updated = if (isUnblocking) {
                currentBlocked - peerId
            } else {
                (currentBlocked + peerId).distinct().take(200)
            }
            db.collection("users").document(uid)
                .collection("private").document("account")
                .update(
                    mapOf(
                        "blockedUserIds" to updated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(!isUnblocking)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun applyRestoredContactAndPrivacyLists(
        savedContactIds: List<String>,
        blockedUserIds: List<String>,
        verifiedPeerIds: List<String>,
        readReceiptsEnabled: Boolean,
        lastSeenVisibility: PrivacyAudience,
        photoVisibility: PrivacyAudience,
        statusVisibility: PrivacyAudience,
        discoverable: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        return try {
            db.collection("directory").document(uid).update(
                mapOf(
                    "discoverable" to discoverable,
                    "lastSeenVisibility" to lastSeenVisibility.name,
                    "photoVisibility" to photoVisibility.name,
                    "statusVisibility" to statusVisibility.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection("users").document(uid)
                .collection("private").document("account")
                .update(
                    mapOf(
                        "savedContactIds" to savedContactIds.distinct().take(500),
                        "blockedUserIds" to blockedUserIds.distinct().take(200),
                        "verifiedPeerIds" to verifiedPeerIds.distinct().take(200),
                        "readReceiptsEnabled" to readReceiptsEnabled,
                        "lastSeenVisibility" to lastSeenVisibility.name,
                        "photoVisibility" to photoVisibility.name,
                        "statusVisibility" to statusVisibility.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid/private/account")
            Result.failure(e)
        }
    }

    suspend fun saveCloudBackup(backupJson: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val path = "users/$uid/private/backup"
        return try {
            require(backupJson.length in 2..500_000) { "Backup payload exceeds 500 KB cloud limit." }
            val ref = db.collection("users").document(uid).collection("private").document("backup")
            val existing = ref.get().await()
            if (existing.exists()) {
                ref.update(
                    mapOf(
                        "backupJson" to backupJson,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.set(
                    mapOf(
                        "userId" to uid,
                        "backupJson" to backupJson,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun loadCloudBackup(): Result<String?> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val path = "users/$uid/private/backup"
        return try {
            val snap = db.collection("users").document(uid)
                .collection("private").document("backup").get().await()
            Result.success(snap.getString("backupJson"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    // --- 2. REAL-TIME STATUS UPDATES FEED ---

    fun observePublicStatuses(): Flow<List<StatusUpdate>> = flow {
        requireUserId()
        val path = "status_updates"
        emitAll(
            db.collection("status_updates")
                .whereEqualTo("visibility", "PUBLIC")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StatusUpdate::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logAndRethrowFlowError(error, OperationType.LIST, path)
                }
        )
    }

    suspend fun publishStatusUpdate(
        authorName: String,
        authorPhotoUrl: String?,
        mood: String,
        text: String
    ): Result<Unit> {
        val path = "status_updates"
        return try {
            val uid = requireUserId()
            val cleanText = text.trim()
            require(cleanText.isNotEmpty() && cleanText.length <= 180) {
                "Status text must be between 1 and 180 characters."
            }
            val cleanMood = mood.trim().ifBlank { "Available" }.take(32)
            val statusId = "st_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val docRef = db.collection("status_updates").document(statusId)
            val payload = mapOf(
                "authorId" to uid,
                "authorName" to authorName.trim().ifBlank { "CipherUser" }.take(60),
                "authorPhotoUrl" to authorPhotoUrl?.take(500),
                "mood" to cleanMood,
                "text" to cleanText,
                "visibility" to "PUBLIC",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            updateMyStatusBanner(PresenceStatus.ONLINE, cleanMood, cleanText.take(140))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteStatusUpdate(statusId: String): Result<Unit> {
        val path = "status_updates/$statusId"
        return try {
            requireUserId()
            require(statusId.isNotBlank())
            db.collection("status_updates").document(statusId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- 2B. WHATSAPP-STYLE LIVE STORIES ---

    fun observeLiveStories(): Flow<List<LiveStory>> = flow {
        requireUserId()
        val path = "stories"
        val cutoffMillis = System.currentTimeMillis() - 86_400_000L
        emitAll(
            db.collection("stories")
                .whereEqualTo("visibility", "PUBLIC")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        LiveStory::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).filter { story ->
                        val ts = story.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                        ts >= cutoffMillis
                    }.sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logAndRethrowFlowError(error, OperationType.LIST, path)
                }
        )
    }

    suspend fun publishLiveStory(
        authorName: String,
        authorPhotoUrl: String?,
        storyType: StoryType,
        caption: String,
        gradient: StoryGradient,
        imageBase64: String?,
        videoBase64: String? = null,
        mentionedUserIds: List<String> = emptyList(),
        mentionedHandles: List<String> = emptyList()
    ): Result<Unit> {
        val storyId = "story_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "stories/$storyId"
        return try {
            val uid = requireUserId()
            val cleanCaption = caption.trim().take(280)
            if (storyType == StoryType.TEXT) {
                require(cleanCaption.isNotEmpty()) { "Story text cannot be empty." }
            }
            if (storyType == StoryType.PHOTO) {
                require(!imageBase64.isNullOrBlank() && imageBase64.length <= 350_000) {
                    "Selected photo is missing or exceeds size budget."
                }
            }
            if (storyType == StoryType.VIDEO) {
                require(!videoBase64.isNullOrBlank() && videoBase64.length <= 680_000) {
                    "Selected video is missing or exceeds size budget."
                }
            }
            val cleanMentionIds = mentionedUserIds.filter { it.isNotBlank() }.distinct().take(25)
            val cleanMentionHandles = mentionedHandles.filter { it.isNotBlank() }.map { it.take(48) }.distinct().take(25)
            val payload = mapOf(
                "authorId" to uid,
                "authorName" to authorName.trim().ifBlank { "CipherUser" }.take(60),
                "authorPhotoUrl" to authorPhotoUrl?.take(500),
                "storyType" to storyType.name,
                "caption" to cleanCaption,
                "gradientId" to gradient.name,
                "imageBase64" to imageBase64?.take(350_000),
                "videoBase64" to videoBase64?.take(680_000),
                "mentionedUserIds" to cleanMentionIds,
                "mentionedHandles" to cleanMentionHandles,
                "visibility" to "PUBLIC",
                "viewedBy" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            db.collection("stories").document(storyId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun markStoryViewed(story: LiveStory): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        if (story.id.isBlank() || uid in story.viewedBy) return Result.success(Unit)
        val path = "stories/${story.id}"
        return try {
            val updated = (story.viewedBy + uid).distinct().take(200)
            db.collection("stories").document(story.id).update(
                mapOf(
                    "viewedBy" to updated,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteStory(storyId: String): Result<Unit> {
        val path = "stories/$storyId"
        return try {
            requireUserId()
            require(storyId.isNotBlank())
            db.collection("stories").document(storyId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- 3. CONVERSATIONS & E2EE MESSAGES ---

    fun observeConversation(conversationId: String): Flow<Conversation?> = flow {
        requireUserId()
        require(conversationId.isNotBlank())
        val path = "conversations/$conversationId"
        emitAll(
            db.collection("conversations").document(conversationId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        Conversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logAndRethrowFlowError(error, OperationType.GET, path)
                }
        )
    }

    fun observeConversations(userId: String): Flow<List<Conversation>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "conversations"
        emitAll(
            db.collection("conversations")
                .whereArrayContains("participantIds", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Conversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.lastMessageAt ?: it.updatedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logAndRethrowFlowError(error, OperationType.LIST, path)
                }
        )
    }

    fun deterministicConversationId(uid1: String, uid2: String): String {
        val sorted = listOf(uid1, uid2).sorted()
        return "${sorted[0]}_${sorted[1]}"
    }

    suspend fun getOrCreateConversation(
        myUser: DirectoryUser,
        peerUser: DirectoryUser
    ): Result<String> {
        val convId = deterministicConversationId(myUser.uid, peerUser.uid)
        val path = "conversations/$convId"
        return try {
            val uid = requireUserId()
            check(uid == myUser.uid)
            val docRef = db.collection("conversations").document(convId)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val sorted = listOf(myUser, peerUser).sortedBy { it.uid }
                val peerA = sorted[0]
                val peerB = sorted[1]
                val payload = mapOf(
                    "participantIds" to listOf(peerA.uid, peerB.uid),
                    "peerAId" to peerA.uid,
                    "peerAName" to peerA.displayName.trim().ifBlank { "ToKerUser" }.take(60),
                    "peerAPhotoUrl" to peerA.photoUrl?.take(500),
                    "peerBId" to peerB.uid,
                    "peerBName" to peerB.displayName.trim().ifBlank { "ToKerUser" }.take(60),
                    "peerBPhotoUrl" to peerB.photoUrl?.take(500),
                    "lastMessageCiphertext" to "",
                    "lastMessageIv" to "",
                    "lastMessageSenderId" to uid,
                    "lastMessageSenderPublicKey" to myUser.publicKey.take(300),
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            }
            Result.success(convId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun observeMessages(conversationId: String, currentUserId: String): Flow<List<EncryptedMessage>> = flow {
        val uid = requireUserId()
        check(uid == currentUserId)
        val path = "conversations/$conversationId/messages"
        emitAll(
            db.collection("conversations").document(conversationId)
                .collection("messages")
                .whereArrayContains("participantIds", currentUserId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        EncryptedMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logAndRethrowFlowError(error, OperationType.LIST, path)
                }
        )
    }

    suspend fun sendEncryptedMessage(
        conversationId: String,
        participantIds: List<String>,
        senderPublicKey: String,
        ciphertextBase64: String,
        ivBase64: String,
        messageType: String = "TEXT",
        previewCiphertextBase64: String = ciphertextBase64,
        previewIvBase64: String = ivBase64,
        expiresAtMillis: Long? = null
    ): Result<Unit> {
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val path = "conversations/$conversationId/messages/$msgId"
        return try {
            val uid = requireUserId()
            require(participantIds.size == 2 && uid in participantIds)
            require(ciphertextBase64.length <= 700_000) {
                "Encrypted attachment exceeds message size budget."
            }
            val convRef = db.collection("conversations").document(conversationId)
            val msgRef = convRef.collection("messages").document(msgId)

            val msgPayload = mutableMapOf<String, Any>(
                "conversationId" to conversationId,
                "participantIds" to participantIds,
                "senderId" to uid,
                "senderPublicKey" to senderPublicKey,
                "ciphertext" to ciphertextBase64.take(700_000),
                "iv" to ivBase64.take(64),
                "encryptionAlg" to CryptoEngine.ALG_NAME,
                "messageType" to messageType,
                "readBy" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (expiresAtMillis != null && expiresAtMillis > 0L) {
                msgPayload["expiresAtMillis"] = expiresAtMillis
            }
            msgRef.set(msgPayload).await()

            convRef.update(
                mapOf(
                    "lastMessageCiphertext" to previewCiphertextBase64.take(4096),
                    "lastMessageIv" to previewIvBase64.take(128),
                    "lastMessageSenderId" to uid,
                    "lastMessageSenderPublicKey" to senderPublicKey,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun voteOnConversationPoll(
        conversationId: String,
        messageId: String,
        optionIndex: Int
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val path = "conversations/$conversationId/messages/$messageId"
        return try {
            db.collection("conversations").document(conversationId)
                .collection("messages").document(messageId)
                .update(
                    mapOf(
                        "pollVotes.$uid" to optionIndex.toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun reactToConversationMessage(
        conversationId: String,
        messageId: String,
        emoji: String,
        currentReactions: Map<String, String>
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val path = "conversations/$conversationId/messages/$messageId"
        return try {
            val value: Any = if (currentReactions[uid] == emoji) {
                FieldValue.delete()
            } else {
                emoji.take(16)
            }
            db.collection("conversations").document(conversationId)
                .collection("messages").document(messageId)
                .update(
                    mapOf(
                        "reactions.$uid" to value,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markMessagesAsRead(
        conversationId: String,
        unreadMessages: List<EncryptedMessage>
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        if (unreadMessages.isEmpty()) return Result.success(Unit)
        return try {
            unreadMessages.forEach { msg ->
                if (uid !in msg.readBy && msg.id.isNotBlank()) {
                    val updatedReadBy = (msg.readBy + uid).distinct().take(2)
                    db.collection("conversations").document(conversationId)
                        .collection("messages").document(msg.id)
                        .update(
                            mapOf(
                                "readBy" to updatedReadBy,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "conversations/$conversationId/messages")
            Result.failure(e)
        }
    }

    suspend fun editEncryptedMessage(
        conversationId: String,
        messageId: String,
        senderPublicKey: String,
        ciphertextBase64: String,
        ivBase64: String,
        messageType: String,
        updateConversationPreview: Boolean,
        previewCiphertextBase64: String = ciphertextBase64,
        previewIvBase64: String = ivBase64
    ): Result<Unit> {
        val path = "conversations/$conversationId/messages/$messageId"
        return try {
            val uid = requireUserId()
            require(messageId.isNotBlank())
            require(ciphertextBase64.length <= 700_000) {
                "Edited message exceeds size budget."
            }
            val convRef = db.collection("conversations").document(conversationId)
            convRef.collection("messages").document(messageId)
                .update(
                    mapOf(
                        "ciphertext" to ciphertextBase64.take(700_000),
                        "iv" to ivBase64.take(64),
                        "senderPublicKey" to senderPublicKey,
                        "messageType" to messageType,
                        "edited" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

            if (updateConversationPreview) {
                convRef.update(
                    mapOf(
                        "lastMessageCiphertext" to previewCiphertextBase64.take(4096),
                        "lastMessageIv" to previewIvBase64.take(128),
                        "lastMessageSenderId" to uid,
                        "lastMessageSenderPublicKey" to senderPublicKey,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteEncryptedMessage(
        conversationId: String,
        messageId: String,
        updateConversationPreview: Boolean,
        newLastCiphertext: String = "",
        newLastIv: String = "",
        newLastSenderId: String = "",
        newLastSenderPublicKey: String = ""
    ): Result<Unit> {
        val path = "conversations/$conversationId/messages/$messageId"
        return try {
            requireUserId()
            require(messageId.isNotBlank())
            val convRef = db.collection("conversations").document(conversationId)
            convRef.collection("messages").document(messageId).delete().await()

            if (updateConversationPreview) {
                convRef.update(
                    mapOf(
                        "lastMessageCiphertext" to newLastCiphertext.take(4096),
                        "lastMessageIv" to newLastIv.take(128),
                        "lastMessageSenderId" to newLastSenderId.take(128),
                        "lastMessageSenderPublicKey" to newLastSenderPublicKey.take(300),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun updateConversationNickname(
        conversationId: String,
        nickname: String
    ): Result<Unit> {
        val path = "conversations/$conversationId"
        return try {
            val uid = requireUserId()
            require(conversationId.isNotBlank())
            val clean = nickname.trim().take(60)
            val value: Any = if (clean.isEmpty()) FieldValue.delete() else clean
            db.collection("conversations").document(conversationId)
                .update(
                    mapOf(
                        "nicknames.$uid" to value,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun pinConversationMessage(
        conversationId: String,
        messageId: String?
    ): Result<Unit> {
        val path = "conversations/$conversationId"
        return try {
            val uid = requireUserId()
            require(conversationId.isNotBlank())
            db.collection("conversations").document(conversationId)
                .update(
                    mapOf(
                        "pinnedMessageId" to messageId?.take(128),
                        "pinnedBy" to if (messageId != null) uid else null,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateConversationDisappearingTimer(
        conversationId: String,
        disappearingSeconds: Long
    ): Result<Unit> {
        val path = "conversations/$conversationId"
        return try {
            requireUserId()
            require(conversationId.isNotBlank())
            val bounded = disappearingSeconds.coerceIn(0L, 31_536_000L)
            db.collection("conversations").document(conversationId)
                .update(
                    mapOf(
                        "disappearingSeconds" to bounded,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteExpiredConversationMessageSilently(
        conversationId: String,
        messageId: String
    ) {
        if (conversationId.isBlank() || messageId.isBlank()) return
        runCatching {
            db.collection("conversations").document(conversationId)
                .collection("messages").document(messageId).delete().await()
        }
    }

    suspend fun deleteConversation(conversationId: String): Result<Unit> {
        val path = "conversations/$conversationId"
        return try {
            val uid = requireUserId()
            require(conversationId.isNotBlank())
            val convRef = db.collection("conversations").document(conversationId)
            val msgsSnap = convRef.collection("messages")
                .whereArrayContains("participantIds", uid)
                .get()
                .await()
            val typingSnap = convRef.collection("typing")
                .whereArrayContains("participantIds", uid)
                .get()
                .await()

            convRef.delete().await()
            msgsSnap.documents.forEach { doc ->
                runCatching { doc.reference.delete().await() }
            }
            typingSnap.documents.forEach { doc ->
                runCatching { doc.reference.delete().await() }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeTypingStates(conversationId: String, currentUserId: String): Flow<List<TypingState>> = flow {
        val uid = requireUserId()
        check(uid == currentUserId)
        val path = "conversations/$conversationId/typing"
        emitAll(
            db.collection("conversations").document(conversationId)
                .collection("typing")
                .whereArrayContains("participantIds", currentUserId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TypingState::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logAndRethrowFlowError(error, OperationType.LIST, path)
                }
        )
    }

    suspend fun setTypingState(
        conversationId: String,
        participantIds: List<String>,
        isTypingNow: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val path = "conversations/$conversationId/typing/$uid"
        return try {
            val payload = mapOf(
                "userId" to uid,
                "participantIds" to participantIds,
                "typing" to isTypingNow,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("conversations").document(conversationId)
                .collection("typing").document(uid)
                .set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- 3B. ENCRYPTED MULTI-CONTACT GROUP CHATS ---

    fun observeMyGroups(userId: String): Flow<List<GroupChat>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "groups"
        emitAll(
            db.collection("groups")
                .whereArrayContains("memberIds", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        GroupChat::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.lastMessageAt ?: it.updatedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logAndRethrowFlowError(error, OperationType.LIST, path)
                }
        )
    }

    fun observeGroup(groupId: String): Flow<GroupChat?> = flow {
        requireUserId()
        val path = "groups/$groupId"
        emitAll(
            db.collection("groups").document(groupId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        GroupChat::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logAndRethrowFlowError(error, OperationType.GET, path)
                }
        )
    }

    suspend fun createGroupChat(
        groupId: String,
        name: String,
        description: String,
        avatarBase64: String?,
        memberIds: List<String>,
        creatorName: String,
        wrappedMemberKeys: Map<String, Pair<String, String>>, // memberId -> (ciphertext, iv)
        granterPublicKey: String
    ): Result<String> {
        val path = "groups/$groupId"
        return try {
            val uid = requireUserId()
            val cleanMembers = (memberIds + uid).distinct().take(50)
            require(cleanMembers.size >= 2) { "Select at least 1 contact to create a group." }
            val cleanName = name.trim().take(60)
            require(cleanName.isNotEmpty()) { "Group name cannot be empty." }

            val nowMillis = System.currentTimeMillis()
            val groupPayload = mapOf(
                "name" to cleanName,
                "description" to description.trim().take(140),
                "avatarBase64" to avatarBase64?.take(160_000),
                "createdBy" to uid,
                "adminIds" to listOf(uid),
                "memberIds" to cleanMembers,
                "memberJoinedAt" to cleanMembers.associateWith { nowMillis },
                "lastMessageCiphertext" to "",
                "lastMessageIv" to "",
                "lastMessageSenderId" to uid,
                "lastMessageSenderName" to creatorName.trim().ifBlank { "CipherUser" }.take(60),
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            val groupRef = db.collection("groups").document(groupId)
            groupRef.set(groupPayload).await()

            wrappedMemberKeys.forEach { (memberId, pair) ->
                val keyPayload = mapOf(
                    "memberId" to memberId,
                    "participantIds" to cleanMembers,
                    "granterId" to uid,
                    "granterPublicKey" to granterPublicKey,
                    "wrappedKeyCiphertext" to pair.first,
                    "wrappedKeyIv" to pair.second,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                groupRef.collection("memberKeys").document(memberId).set(keyPayload).await()
            }
            Result.success(groupId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun addMembersToGroup(
        groupId: String,
        updatedMemberIds: List<String>,
        newWrappedKeys: Map<String, Pair<String, String>>,
        granterPublicKey: String
    ): Result<Unit> {
        val path = "groups/$groupId"
        return try {
            val uid = requireUserId()
            val cleanMembers = updatedMemberIds.distinct().take(50)
            val groupRef = db.collection("groups").document(groupId)
            val nowMillis = System.currentTimeMillis()
            val updates = mutableMapOf<String, Any>(
                "memberIds" to cleanMembers,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            newWrappedKeys.keys.forEach { newMemberId ->
                updates["memberJoinedAt.$newMemberId"] = nowMillis
            }
            groupRef.update(updates).await()

            newWrappedKeys.forEach { (memberId, pair) ->
                val keyPayload = mapOf(
                    "memberId" to memberId,
                    "participantIds" to cleanMembers,
                    "granterId" to uid,
                    "granterPublicKey" to granterPublicKey,
                    "wrappedKeyCiphertext" to pair.first,
                    "wrappedKeyIv" to pair.second,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                groupRef.collection("memberKeys").document(memberId).set(keyPayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateGroupProfile(
        groupId: String,
        name: String,
        description: String,
        avatarBase64: String?,
        clearAvatar: Boolean
    ): Result<Unit> {
        val path = "groups/$groupId"
        return try {
            requireUserId()
            val cleanName = name.trim().take(60)
            require(cleanName.isNotEmpty()) { "Group name cannot be empty." }
            val updates = mutableMapOf<String, Any?>(
                "name" to cleanName,
                "description" to description.trim().take(140),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (clearAvatar) {
                updates["avatarBase64"] = null
            } else if (!avatarBase64.isNullOrBlank()) {
                updates["avatarBase64"] = avatarBase64.take(160_000)
            }
            db.collection("groups").document(groupId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun pinGroupMessage(
        groupId: String,
        messageId: String?
    ): Result<Unit> {
        val path = "groups/$groupId"
        return try {
            val uid = requireUserId()
            require(groupId.isNotBlank())
            db.collection("groups").document(groupId)
                .update(
                    mapOf(
                        "pinnedMessageId" to messageId?.take(128),
                        "pinnedBy" to if (messageId != null) uid else null,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun removeMemberFromGroup(
        groupId: String,
        targetMemberId: String,
        remainingMemberIds: List<String>,
        remainingAdminIds: List<String>
    ): Result<Unit> {
        val path = "groups/$groupId"
        return try {
            val uid = requireUserId()
            require(targetMemberId.isNotBlank() && targetMemberId != uid) { "Invalid member to remove." }
            require(remainingMemberIds.isNotEmpty()) { "Group must retain at least 1 member." }
            val groupRef = db.collection("groups").document(groupId)
            groupRef.update(
                mapOf(
                    "memberIds" to remainingMemberIds.distinct().take(50),
                    "adminIds" to remainingAdminIds.distinct().take(50),
                    "memberJoinedAt.$targetMemberId" to FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            runCatching {
                groupRef.collection("memberKeys").document(targetMemberId).delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateGroupAdmins(
        groupId: String,
        updatedAdminIds: List<String>
    ): Result<Unit> {
        val path = "groups/$groupId"
        return try {
            requireUserId()
            require(updatedAdminIds.isNotEmpty()) { "Group must have at least one admin." }
            db.collection("groups").document(groupId)
                .update(
                    mapOf(
                        "adminIds" to updatedAdminIds.distinct().take(50),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateGroupOnlyAdminsCanMessage(
        groupId: String,
        onlyAdminsCanMessage: Boolean
    ): Result<Unit> {
        val path = "groups/$groupId"
        return try {
            requireUserId()
            require(groupId.isNotBlank())
            db.collection("groups").document(groupId)
                .update(
                    mapOf(
                        "onlyAdminsCanMessage" to onlyAdminsCanMessage,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateGroupDisappearingTimer(
        groupId: String,
        disappearingSeconds: Long
    ): Result<Unit> {
        val path = "groups/$groupId"
        return try {
            requireUserId()
            require(groupId.isNotBlank())
            val bounded = disappearingSeconds.coerceIn(0L, 31_536_000L)
            db.collection("groups").document(groupId)
                .update(
                    mapOf(
                        "disappearingSeconds" to bounded,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteExpiredGroupMessageSilently(
        groupId: String,
        messageId: String
    ) {
        if (groupId.isBlank() || messageId.isBlank()) return
        runCatching {
            db.collection("groups").document(groupId)
                .collection("messages").document(messageId).delete().await()
        }
    }

    suspend fun leaveGroup(groupId: String, currentMembers: List<String>): Result<Unit> {
        val path = "groups/$groupId"
        return try {
            val uid = requireUserId()
            val remaining = currentMembers.filter { it != uid }
            if (remaining.isEmpty()) {
                db.collection("groups").document(groupId).delete().await()
            } else {
                db.collection("groups").document(groupId).update(
                    mapOf(
                        "memberIds" to remaining,
                        "memberJoinedAt.$uid" to FieldValue.delete(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeMyGroupMemberKey(groupId: String, userId: String): Flow<GroupMemberKey?> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "groups/$groupId/memberKeys/$userId"
        emitAll(
            db.collection("groups").document(groupId)
                .collection("memberKeys").document(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        GroupMemberKey::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logAndRethrowFlowError(error, OperationType.GET, path)
                }
        )
    }

    fun observeGroupMessages(groupId: String, userId: String): Flow<List<GroupMessage>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "groups/$groupId/messages"
        emitAll(
            db.collection("groups").document(groupId)
                .collection("messages")
                .whereArrayContains("participantIds", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        GroupMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logAndRethrowFlowError(error, OperationType.LIST, path)
                }
        )
    }

    suspend fun sendGroupMessage(
        groupId: String,
        participantIds: List<String>,
        senderName: String,
        ciphertextBase64: String,
        ivBase64: String,
        messageType: String = "TEXT",
        previewCiphertextBase64: String = ciphertextBase64,
        previewIvBase64: String = ivBase64,
        expiresAtMillis: Long? = null
    ): Result<Unit> {
        val msgId = "gmsg_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val path = "groups/$groupId/messages/$msgId"
        return try {
            val uid = requireUserId()
            require(uid in participantIds)
            require(ciphertextBase64.length <= 700_000) {
                "Encrypted attachment exceeds group message size budget."
            }
            val cleanSender = senderName.trim().ifBlank { "CipherUser" }.take(60)
            val groupRef = db.collection("groups").document(groupId)
            val msgPayload = mutableMapOf<String, Any>(
                "groupId" to groupId,
                "participantIds" to participantIds,
                "senderId" to uid,
                "senderName" to cleanSender,
                "ciphertext" to ciphertextBase64.take(700_000),
                "iv" to ivBase64.take(64),
                "encryptionAlg" to CryptoEngine.GROUP_ALG_NAME,
                "messageType" to messageType,
                "readBy" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (expiresAtMillis != null && expiresAtMillis > 0L) {
                msgPayload["expiresAtMillis"] = expiresAtMillis
            }
            groupRef.collection("messages").document(msgId).set(msgPayload).await()

            groupRef.update(
                mapOf(
                    "lastMessageCiphertext" to previewCiphertextBase64.take(4096),
                    "lastMessageIv" to previewIvBase64.take(128),
                    "lastMessageSenderId" to uid,
                    "lastMessageSenderName" to cleanSender,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun voteOnGroupPoll(
        groupId: String,
        messageId: String,
        optionIndex: Int
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val path = "groups/$groupId/messages/$messageId"
        return try {
            db.collection("groups").document(groupId)
                .collection("messages").document(messageId)
                .update(
                    mapOf(
                        "pollVotes.$uid" to optionIndex.toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun reactToGroupMessage(
        groupId: String,
        messageId: String,
        emoji: String,
        currentReactions: Map<String, String>
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val path = "groups/$groupId/messages/$messageId"
        return try {
            val value: Any = if (currentReactions[uid] == emoji) {
                FieldValue.delete()
            } else {
                emoji.take(16)
            }
            db.collection("groups").document(groupId)
                .collection("messages").document(messageId)
                .update(
                    mapOf(
                        "reactions.$uid" to value,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markGroupMessagesAsRead(
        groupId: String,
        unreadMessages: List<GroupMessage>
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        if (unreadMessages.isEmpty()) return Result.success(Unit)
        return try {
            unreadMessages.forEach { msg ->
                if (uid !in msg.readBy && msg.id.isNotBlank()) {
                    val updated = (msg.readBy + uid).distinct().take(50)
                    db.collection("groups").document(groupId)
                        .collection("messages").document(msg.id)
                        .update(
                            mapOf(
                                "readBy" to updated,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "groups/$groupId/messages")
            Result.failure(e)
        }
    }

    suspend fun editGroupMessage(
        groupId: String,
        messageId: String,
        senderName: String,
        ciphertextBase64: String,
        ivBase64: String,
        messageType: String,
        updateGroupPreview: Boolean,
        previewCiphertextBase64: String = ciphertextBase64,
        previewIvBase64: String = ivBase64
    ): Result<Unit> {
        val path = "groups/$groupId/messages/$messageId"
        return try {
            val uid = requireUserId()
            require(messageId.isNotBlank())
            require(ciphertextBase64.length <= 700_000) {
                "Edited group message exceeds size budget."
            }
            val cleanSender = senderName.trim().ifBlank { "CipherUser" }.take(60)
            val groupRef = db.collection("groups").document(groupId)
            groupRef.collection("messages").document(messageId)
                .update(
                    mapOf(
                        "ciphertext" to ciphertextBase64.take(700_000),
                        "iv" to ivBase64.take(64),
                        "messageType" to messageType,
                        "edited" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

            if (updateGroupPreview) {
                groupRef.update(
                    mapOf(
                        "lastMessageCiphertext" to previewCiphertextBase64.take(4096),
                        "lastMessageIv" to previewIvBase64.take(128),
                        "lastMessageSenderId" to uid,
                        "lastMessageSenderName" to cleanSender,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteGroupMessage(
        groupId: String,
        messageId: String,
        updateGroupPreview: Boolean,
        newLastCiphertext: String = "",
        newLastIv: String = "",
        newLastSenderId: String = "",
        newLastSenderName: String = ""
    ): Result<Unit> {
        val path = "groups/$groupId/messages/$messageId"
        return try {
            requireUserId()
            require(messageId.isNotBlank())
            val groupRef = db.collection("groups").document(groupId)
            groupRef.collection("messages").document(messageId).delete().await()

            if (updateGroupPreview) {
                groupRef.update(
                    mapOf(
                        "lastMessageCiphertext" to newLastCiphertext.take(4096),
                        "lastMessageIv" to newLastIv.take(128),
                        "lastMessageSenderId" to newLastSenderId.take(128),
                        "lastMessageSenderName" to newLastSenderName.take(60),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteGroup(groupId: String): Result<Unit> {
        val path = "groups/$groupId"
        return try {
            val uid = requireUserId()
            require(groupId.isNotBlank())
            val groupRef = db.collection("groups").document(groupId)
            val msgsSnap = groupRef.collection("messages")
                .whereArrayContains("participantIds", uid)
                .get()
                .await()
            val keysSnap = groupRef.collection("memberKeys")
                .whereArrayContains("participantIds", uid)
                .get()
                .await()

            groupRef.delete().await()
            msgsSnap.documents.forEach { doc ->
                runCatching { doc.reference.delete().await() }
            }
            keysSnap.documents.forEach { doc ->
                runCatching { doc.reference.delete().await() }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- 4. LIVE AUDIO/VIDEO CALL SIGNALING ---

    fun observeMyCalls(userId: String): Flow<List<CallSession>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "calls"
        emitAll(
            db.collection("calls")
                .whereArrayContains("participantIds", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CallSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logAndRethrowFlowError(error, OperationType.LIST, path)
                }
        )
    }

    fun observeCallSession(callId: String): Flow<CallSession?> = flow {
        requireUserId()
        val path = "calls/$callId"
        emitAll(
            db.collection("calls").document(callId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        CallSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logAndRethrowFlowError(error, OperationType.GET, path)
                }
        )
    }

    suspend fun createCallSession(
        callId: String,
        caller: DirectoryUser,
        callee: DirectoryUser,
        callType: CallType,
        offerSdp: String,
        e2eeFingerprint: String
    ): Result<Unit> {
        val path = "calls/$callId"
        return try {
            val uid = requireUserId()
            check(uid == caller.uid)
            val payload = mapOf(
                "callerId" to caller.uid,
                "callerName" to caller.displayName.take(60),
                "callerPhotoUrl" to caller.photoUrl?.take(500),
                "calleeId" to callee.uid,
                "calleeName" to callee.displayName.take(60),
                "calleePhotoUrl" to callee.photoUrl?.take(500),
                "participantIds" to listOf(caller.uid, callee.uid),
                "callType" to callType.name,
                "status" to CallStatus.RINGING.name,
                "offerSdp" to offerSdp.take(32768),
                "e2eeFingerprint" to e2eeFingerprint.take(128),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            db.collection("calls").document(callId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun acceptCallWithAnswer(callId: String, answerSdp: String): Result<Unit> {
        val path = "calls/$callId"
        return try {
            requireUserId()
            db.collection("calls").document(callId).update(
                mapOf(
                    "status" to CallStatus.ACCEPTED.name,
                    "answerSdp" to answerSdp.take(32768),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateCallStatus(callId: String, newStatus: CallStatus): Result<Unit> {
        val path = "calls/$callId"
        return try {
            requireUserId()
            db.collection("calls").document(callId).update(
                mapOf(
                    "status" to newStatus.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeIceCandidates(callId: String, currentUserId: String): Flow<List<IceCandidateModel>> = flow {
        val uid = requireUserId()
        check(uid == currentUserId)
        val path = "calls/$callId/candidates"
        emitAll(
            db.collection("calls").document(callId)
                .collection("candidates")
                .whereArrayContains("participantIds", currentUserId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        IceCandidateModel::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logAndRethrowFlowError(error, OperationType.LIST, path)
                }
        )
    }

    suspend fun addIceCandidate(
        callId: String,
        participantIds: List<String>,
        sdpMid: String,
        sdpMLineIndex: Int,
        candidate: String
    ): Result<Unit> {
        val candId = "ice_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "calls/$callId/candidates/$candId"
        return try {
            val uid = requireUserId()
            val payload = mapOf(
                "senderId" to uid,
                "participantIds" to participantIds,
                "sdpMid" to sdpMid.take(64),
                "sdpMLineIndex" to sdpMLineIndex.coerceIn(0, 32),
                "candidate" to candidate.take(2048),
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("calls").document(callId)
                .collection("candidates").document(candId)
                .set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCallSession(callId: String): Result<Unit> {
        val path = "calls/$callId"
        return try {
            val uid = requireUserId()
            require(callId.isNotBlank())
            val callRef = db.collection("calls").document(callId)
            val candSnap = callRef.collection("candidates")
                .whereArrayContains("participantIds", uid)
                .get()
                .await()
            candSnap.documents.forEach { doc ->
                runCatching { doc.reference.delete().await() }
            }
            callRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun clearMyCallHistory(calls: List<CallSession>): Result<Int> {
        return try {
            requireUserId()
            var deletedCount = 0
            calls.filter { it.id.isNotBlank() && it.statusEnum != CallStatus.RINGING }.forEach { call ->
                if (deleteCallSession(call.id).isSuccess) {
                    deletedCount++
                }
            }
            Result.success(deletedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun logAndRethrowFlowError(
        error: Throwable,
        operationType: OperationType,
        path: String
    ): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser != null) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }
}
