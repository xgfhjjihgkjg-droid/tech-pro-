package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.CryptoEngine
import com.example.crypto.IdentityKeyBundle
import com.example.data.AuthorStoriesGroup
import com.example.data.BackupManager
import com.example.data.CallSession
import com.example.data.CallStatus
import com.example.data.ChatRepository
import com.example.data.Conversation
import com.example.data.ConversationListItemUi
import com.example.data.DirectoryUser
import com.example.data.GroupChat
import com.example.data.GroupListItemUi
import com.example.data.LiveStory
import com.example.data.PresenceStatus
import com.example.data.PrivacyAudience
import com.example.data.PrivateAccount
import com.example.data.RichMessageCodec
import com.example.data.RichMessagePayload
import com.example.data.RichMessageType
import com.example.data.ScheduledMessageItem
import com.example.data.ScheduledMessageManager
import com.example.data.ScheduledMessageStatus
import com.example.data.StatusUpdate
import com.example.data.StoryGradient
import com.example.data.StoryType
import com.example.data.UiState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: ChatRepository,
    private val cryptoEngine: CryptoEngine,
    private val currentUserId: String,
    private val displayName: String,
    private val email: String,
    private val phoneNumber: String?,
    private val photoUrl: String?,
    private val scheduledMessageManager: ScheduledMessageManager? = null
) : ViewModel() {

    private val _keyBundle = MutableStateFlow<IdentityKeyBundle?>(null)
    val keyBundle: StateFlow<IdentityKeyBundle?> = _keyBundle.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    val myProfile: StateFlow<DirectoryUser?> = repository.observeDirectoryUser(currentUserId)
        .catch { error ->
            unwrapFirestoreOrThrow(error, "observeMyProfile")
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    val privateAccount: StateFlow<PrivateAccount?> = repository.observePrivateAccount(currentUserId)
        .catch { error ->
            unwrapFirestoreOrThrow(error, "observePrivateAccount")
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    private val _hydratedPeers = MutableStateFlow<Map<String, DirectoryUser>>(emptyMap())
    private val fetchedPeerIds = mutableSetOf<String>()

    val hydratedDirectoryMap: StateFlow<Map<String, DirectoryUser>> = combine(
        repository.observeDirectoryUsers(),
        _hydratedPeers,
        myProfile
    ) { discoverable, extra, me ->
        val map = LinkedHashMap<String, DirectoryUser>()
        extra.forEach { (k, v) -> map[k] = v }
        discoverable.forEach { map[it.uid] = it }
        if (me != null) map[me.uid] = me
        map
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyMap()
        )

    private fun ensurePeersHydrated(uids: Collection<String>, knownMap: Map<String, DirectoryUser>) {
        val missing = uids
            .filter { it.isNotBlank() && it !in knownMap && fetchedPeerIds.add(it) }
            .distinct()
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val loaded = repository.getDirectoryUsersByIds(missing)
            if (loaded.isNotEmpty()) {
                _hydratedPeers.value = _hydratedPeers.value + loaded
            }
        }
    }

    val directoryUsers: StateFlow<UiState<List<DirectoryUser>>> = repository.observeDirectoryUsers()
        .map<List<DirectoryUser>, UiState<List<DirectoryUser>>> { users ->
            UiState.Success(users)
        }
        .catch { error ->
            val root = unwrapFirestoreOrThrow(error, "observeDirectoryUsers")
            emit(UiState.Error(root.message ?: "Failed to load directory"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    private fun accountJoinCutoffMillis(profile: DirectoryUser?): Long =
        profile?.createdAt?.toDate()?.time
            ?: FirebaseAuth.getInstance().currentUser?.metadata?.creationTimestamp
            ?: 0L

    val publicStatuses: StateFlow<UiState<List<StatusUpdate>>> = combine(
        repository.observePublicStatuses(),
        myProfile
    ) { list, profile ->
        val joinedAt = accountJoinCutoffMillis(profile)
        val filtered = if (joinedAt > 0L) {
            list.filter { status ->
                status.authorId == currentUserId ||
                    (status.createdAt?.toDate()?.time ?: Long.MAX_VALUE) >= joinedAt
            }
        } else {
            list
        }
        UiState.Success(filtered) as UiState<List<StatusUpdate>>
    }
        .catch { error ->
            val root = unwrapFirestoreOrThrow(error, "observePublicStatuses")
            emit(UiState.Error(root.message ?: "Failed to load status updates"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val liveStoryGroups: StateFlow<UiState<List<AuthorStoriesGroup>>> = combine(
        repository.observeLiveStories(),
        hydratedDirectoryMap,
        myProfile
    ) { stories, dirMap, profile ->
        val joinedAt = accountJoinCutoffMillis(profile)
        val visibleStories = if (joinedAt > 0L) {
            stories.filter { story ->
                story.authorId == currentUserId ||
                    (story.createdAt?.toDate()?.time ?: Long.MAX_VALUE) >= joinedAt
            }
        } else {
            stories
        }
        ensurePeersHydrated(visibleStories.map { it.authorId }, dirMap)
        val grouped = visibleStories.groupBy { it.authorId }.map { (authorId, authorStories) ->
            val dir = dirMap[authorId]
            val latest = authorStories.last()
            AuthorStoriesGroup(
                authorId = authorId,
                authorName = dir?.displayName ?: latest.authorName,
                authorPhotoUrl = dir?.photoUrl ?: latest.authorPhotoUrl,
                authorAvatarBase64 = dir?.avatarBase64,
                stories = authorStories,
                hasUnviewed = authorStories.any { currentUserId !in it.viewedBy }
            )
        }.sortedWith(
            compareByDescending<AuthorStoriesGroup> { it.authorId == currentUserId }
                .thenByDescending { it.hasUnviewed }
                .thenByDescending { it.stories.lastOrNull()?.createdAt?.toDate()?.time ?: 0L }
        )
        UiState.Success(grouped) as UiState<List<AuthorStoriesGroup>>
    }
        .catch { error ->
            val root = unwrapFirestoreOrThrow(error, "observeLiveStories")
            emit(UiState.Error(root.message ?: "Failed to load live stories"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val conversations: StateFlow<UiState<List<ConversationListItemUi>>> = combine(
        repository.observeConversations(currentUserId),
        hydratedDirectoryMap,
        _keyBundle
    ) { convs, dirById, keys ->
        ensurePeersHydrated(convs.map { it.peerIdFor(currentUserId) }, dirById)
        val items = convs.map { conv ->
            buildConversationPreview(conv, dirById, keys)
        }
        UiState.Success(items) as UiState<List<ConversationListItemUi>>
    }
        .flowOn(Dispatchers.Default)
        .catch { error ->
            val root = unwrapFirestoreOrThrow(error, "observeConversations")
            emit(UiState.Error(root.message ?: "Failed to load encrypted chats"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val groups: StateFlow<UiState<List<GroupListItemUi>>> = repository.observeMyGroups(currentUserId)
        .map<List<GroupChat>, UiState<List<GroupListItemUi>>> { list ->
            val uiList = list.map { group ->
                val joinedAt = group.memberJoinedAt[currentUserId] ?: 0L
                val lastMsgTs = group.lastMessageAt?.toDate()?.time ?: 0L
                val preview = when {
                    group.lastMessageCiphertext.isBlank() ->
                        "Encrypted group created • ${group.memberIds.size} members"
                    joinedAt > 0L && lastMsgTs > 0L && lastMsgTs < joinedAt ->
                        "Added to group • ${group.memberIds.size} members"
                    else -> "${group.lastMessageSenderName}: Encrypted message"
                }
                GroupListItemUi(
                    group = group,
                    memberCount = group.memberIds.size,
                    lastMessagePreview = preview
                )
            }
            UiState.Success(uiList)
        }
        .catch { error ->
            val root = unwrapFirestoreOrThrow(error, "observeMyGroups")
            emit(UiState.Error(root.message ?: "Failed to load groups"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val myCalls: StateFlow<UiState<List<CallSession>>> = combine(
        repository.observeMyCalls(currentUserId),
        hydratedDirectoryMap
    ) { calls, dirMap ->
        ensurePeersHydrated(calls.map { it.peerIdFor(currentUserId) }, dirMap)
        UiState.Success(calls) as UiState<List<CallSession>>
    }
        .catch { error ->
            val root = unwrapFirestoreOrThrow(error, "observeMyCalls")
            emit(UiState.Error(root.message ?: "Failed to load call logs"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val incomingRingingCall: StateFlow<CallSession?> = repository.observeMyCalls(currentUserId)
        .map { calls ->
            calls.firstOrNull {
                it.calleeId == currentUserId && it.statusEnum == CallStatus.RINGING
            }
        }
        .catch { error ->
            unwrapFirestoreOrThrow(error, "incomingRingingCall")
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    val scheduledMessages: StateFlow<List<ScheduledMessageItem>> =
        (scheduledMessageManager?.scheduledMessages ?: flowOf(emptyList()))
            .map { items -> items.filter { it.ownerUid == currentUserId } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    init {
        initializeIdentityAndProfile()
        startScheduledMessageTicker()
    }

    private fun startScheduledMessageTicker() {
        val mgr = scheduledMessageManager ?: return
        viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val duePending = scheduledMessages.value.any {
                    it.statusEnum == ScheduledMessageStatus.PENDING && it.scheduledAtMillis <= now
                }
                if (duePending) {
                    val sent = mgr.dispatchDueMessages(
                        repository = repository,
                        cryptoEngine = cryptoEngine,
                        currentUserId = currentUserId,
                        myDisplayName = myProfile.value?.displayName ?: displayName
                    )
                    if (sent > 0) {
                        _actionMessage.value = "Sent $sent scheduled E2EE message(s)"
                    }
                }
                delay(1000L)
            }
        }
    }

    private fun initializeIdentityAndProfile() {
        viewModelScope.launch {
            try {
                val bundle = cryptoEngine.getOrCreateIdentityKeys(currentUserId)
                _keyBundle.value = bundle
                repository.ensureUserProfileInitialized(
                    userId = currentUserId,
                    displayName = displayName,
                    email = email,
                    phoneNumber = phoneNumber,
                    photoUrl = photoUrl,
                    keyBundle = bundle
                ).onFailure { err ->
                    _actionError.value = err.message ?: "Failed to initialize E2EE profile"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.message ?: "Key generation failed"
            }
        }
    }

    fun setPresence(presence: PresenceStatus) {
        viewModelScope.launch {
            repository.updatePresence(presence)
                .onFailure { _actionError.value = it.message ?: "Could not update presence" }
        }
    }

    fun updateStatusBanner(presence: PresenceStatus, mood: String, message: String) {
        viewModelScope.launch {
            repository.updateMyStatusBanner(presence, mood, message)
                .onSuccess { _actionMessage.value = "Live status updated" }
                .onFailure { _actionError.value = it.message ?: "Failed to update status" }
        }
    }

    fun publishStatusFeedUpdate(mood: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) {
            _actionError.value = "Enter a status message before posting."
            return
        }
        viewModelScope.launch {
            val name = myProfile.value?.displayName ?: displayName
            val avatar = myProfile.value?.photoUrl ?: photoUrl
            repository.publishStatusUpdate(name, avatar, mood, clean)
                .onSuccess { _actionMessage.value = "Status broadcast live" }
                .onFailure { _actionError.value = it.message ?: "Failed to post status" }
        }
    }

    fun deleteStatusUpdate(statusId: String) {
        viewModelScope.launch {
            repository.deleteStatusUpdate(statusId)
                .onFailure { _actionError.value = it.message ?: "Failed to delete status" }
        }
    }

    fun updateProfileControls(
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
    ) {
        viewModelScope.launch {
            repository.updateMyProfileControls(
                displayName = displayName,
                handle = handle,
                statusMessage = statusMessage,
                statusMood = statusMood,
                presence = presence,
                discoverable = discoverable,
                avatarBase64 = avatarBase64,
                clearAvatar = clearAvatar,
                readReceiptsEnabled = readReceiptsEnabled,
                lastSeenVisibility = lastSeenVisibility,
                photoVisibility = photoVisibility,
                statusVisibility = statusVisibility
            ).onSuccess {
                _actionMessage.value = "Profile & privacy controls saved"
            }.onFailure {
                _actionError.value = it.message ?: "Failed to update profile"
            }
        }
    }

    fun updatePrivacySettings(
        discoverable: Boolean,
        readReceiptsEnabled: Boolean,
        lastSeenVisibility: PrivacyAudience,
        photoVisibility: PrivacyAudience,
        statusVisibility: PrivacyAudience
    ) {
        viewModelScope.launch {
            repository.updatePrivacySettings(
                discoverable = discoverable,
                readReceiptsEnabled = readReceiptsEnabled,
                lastSeenVisibility = lastSeenVisibility,
                photoVisibility = photoVisibility,
                statusVisibility = statusVisibility
            ).onSuccess {
                _actionMessage.value = "Privacy controls updated"
            }.onFailure {
                _actionError.value = it.message ?: "Failed to save privacy controls"
            }
        }
    }

    fun addContactByQuery(query: String, onAdded: (DirectoryUser) -> Unit = {}) {
        val currentSaved = privateAccount.value?.savedContactIds ?: emptyList()
        viewModelScope.launch {
            repository.findUserAndSaveContact(query, currentSaved)
                .onSuccess { peer ->
                    _actionMessage.value = "Added ${peer.displayName} (${peer.handle}) to contacts"
                    onAdded(peer)
                }
                .onFailure {
                    _actionError.value = it.message ?: "Contact lookup failed"
                }
        }
    }

    fun toggleSavedContact(peerId: String) {
        val currentSaved = privateAccount.value?.savedContactIds ?: emptyList()
        viewModelScope.launch {
            repository.toggleSavedContact(peerId, currentSaved)
                .onSuccess { added ->
                    _actionMessage.value = if (added) "Saved to contacts" else "Removed from saved contacts"
                }
                .onFailure {
                    _actionError.value = it.message ?: "Failed to update contacts"
                }
        }
    }

    fun toggleBlockedUser(peerId: String) {
        val currentBlocked = privateAccount.value?.blockedUserIds ?: emptyList()
        viewModelScope.launch {
            repository.toggleBlockedUser(peerId, currentBlocked)
                .onSuccess { blocked ->
                    _actionMessage.value = if (blocked) "Contact blocked" else "Contact unblocked"
                }
                .onFailure {
                    _actionError.value = it.message ?: "Failed to update block list"
                }
        }
    }

    fun deleteCallHistoryItem(callId: String) {
        viewModelScope.launch {
            repository.deleteCallSession(callId)
                .onSuccess { _actionMessage.value = "Call entry removed" }
                .onFailure { _actionError.value = it.message ?: "Failed to remove call entry" }
        }
    }

    fun clearCallHistory(calls: List<CallSession>) {
        viewModelScope.launch {
            repository.clearMyCallHistory(calls)
                .onSuccess { count ->
                    _actionMessage.value = if (count > 0) "Cleared $count call entries" else "No completed calls to clear"
                }
                .onFailure { _actionError.value = it.message ?: "Failed to clear call history" }
        }
    }

    fun scheduleMessageToTarget(
        targetType: String,
        targetId: String,
        peerId: String,
        targetTitle: String,
        text: String,
        scheduledAtMillis: Long
    ) {
        val mgr = scheduledMessageManager ?: return
        val clean = text.trim()
        if (clean.isEmpty()) {
            _actionError.value = "Enter a message to schedule."
            return
        }
        viewModelScope.launch {
            mgr.scheduleMessage(
                ownerUid = currentUserId,
                targetType = targetType,
                targetId = targetId,
                peerId = peerId,
                targetTitle = targetTitle,
                text = clean,
                scheduledAtMillis = scheduledAtMillis
            )
            _actionMessage.value = "Scheduled E2EE message for $targetTitle"
        }
    }

    fun cancelScheduledMessage(id: String) {
        val mgr = scheduledMessageManager ?: return
        viewModelScope.launch {
            mgr.cancelScheduledMessage(id)
            _actionMessage.value = "Scheduled message cancelled"
        }
    }

    fun rescheduleMessage(id: String, newMillis: Long) {
        val mgr = scheduledMessageManager ?: return
        viewModelScope.launch {
            mgr.rescheduleMessage(id, newMillis)
            _actionMessage.value = "Scheduled time updated"
        }
    }

    fun sendScheduledNow(item: ScheduledMessageItem) {
        val mgr = scheduledMessageManager ?: return
        viewModelScope.launch {
            val sent = mgr.dispatchDueMessages(
                repository = repository,
                cryptoEngine = cryptoEngine,
                currentUserId = currentUserId,
                myDisplayName = myProfile.value?.displayName ?: displayName,
                forceItemId = item.id
            )
            if (sent > 0) {
                _actionMessage.value = "Scheduled message sent now"
            } else {
                _actionError.value = "Could not send scheduled message yet"
            }
        }
    }

    fun forwardToConversation(
        convId: String,
        peerId: String,
        peerPubKey: String,
        sourcePayload: RichMessagePayload,
        targetLabel: String
    ) {
        val keys = _keyBundle.value
        if (keys == null || peerPubKey.isBlank()) {
            _actionError.value = "Target peer E2EE key unavailable"
            return
        }
        val forwardPayload = sourcePayload.copy(
            replyToMessageId = "",
            replyToSenderName = "",
            replyToPreview = "",
            forwarded = true
        )
        val encoded = RichMessageCodec.encode(forwardPayload)
        val preview = RichMessageCodec.previewLabel(forwardPayload)
        viewModelScope.launch {
            runCatching {
                val enc = cryptoEngine.encryptMessage(
                    plaintext = encoded,
                    myPrivateKey = keys.keyPair.private,
                    peerPublicKeyBase64 = peerPubKey,
                    conversationId = convId,
                    senderId = currentUserId
                )
                val encPrev = if (enc.ciphertextBase64.length <= 4096) {
                    enc
                } else {
                    cryptoEngine.encryptMessage(
                        plaintext = preview,
                        myPrivateKey = keys.keyPair.private,
                        peerPublicKeyBase64 = peerPubKey,
                        conversationId = convId,
                        senderId = currentUserId
                    )
                }
                repository.sendEncryptedMessage(
                    conversationId = convId,
                    participantIds = listOf(currentUserId, peerId).sorted(),
                    senderPublicKey = keys.publicKeyBase64,
                    ciphertextBase64 = enc.ciphertextBase64,
                    ivBase64 = enc.ivBase64,
                    messageType = forwardPayload.typeEnum.name,
                    previewCiphertextBase64 = encPrev.ciphertextBase64,
                    previewIvBase64 = encPrev.ivBase64
                ).getOrThrow()
            }.onSuccess {
                _actionMessage.value = "Forwarded to $targetLabel"
            }.onFailure {
                _actionError.value = it.message ?: "Failed to forward message"
            }
        }
    }

    fun forwardToGroup(
        group: GroupChat,
        sourcePayload: RichMessagePayload
    ) {
        val keys = _keyBundle.value ?: return
        val forwardPayload = sourcePayload.copy(
            replyToMessageId = "",
            replyToSenderName = "",
            replyToPreview = "",
            forwarded = true
        )
        val encoded = RichMessageCodec.encode(forwardPayload)
        val preview = RichMessageCodec.previewLabel(forwardPayload)
        viewModelScope.launch {
            runCatching {
                val wrapped = repository.observeMyGroupMemberKey(group.id, currentUserId).first()
                    ?: throw IllegalStateException("Missing group key")
                val secretKey = cryptoEngine.decryptMessage(
                    ciphertextBase64 = wrapped.wrappedKeyCiphertext,
                    ivBase64 = wrapped.wrappedKeyIv,
                    myPrivateKey = keys.keyPair.private,
                    peerPublicKeyBase64 = wrapped.granterPublicKey,
                    conversationId = group.id,
                    senderId = wrapped.granterId
                ).getOrThrow()
                val enc = cryptoEngine.encryptWithGroupKey(
                    plaintext = encoded,
                    groupKeyBase64 = secretKey,
                    groupId = group.id,
                    senderId = currentUserId
                )
                val encPrev = if (enc.ciphertextBase64.length <= 4096) {
                    enc
                } else {
                    cryptoEngine.encryptWithGroupKey(
                        plaintext = preview,
                        groupKeyBase64 = secretKey,
                        groupId = group.id,
                        senderId = currentUserId
                    )
                }
                repository.sendGroupMessage(
                    groupId = group.id,
                    participantIds = group.memberIds,
                    senderName = myProfile.value?.displayName ?: "ToKerUser",
                    ciphertextBase64 = enc.ciphertextBase64,
                    ivBase64 = enc.ivBase64,
                    messageType = forwardPayload.typeEnum.name,
                    previewCiphertextBase64 = encPrev.ciphertextBase64,
                    previewIvBase64 = encPrev.ivBase64
                ).getOrThrow()
            }.onSuccess {
                _actionMessage.value = "Forwarded to ${group.name}"
            }.onFailure {
                _actionError.value = it.message ?: "Failed to forward to group"
            }
        }
    }

    fun buildLocalBackupJson(): String? {
        val me = myProfile.value
        if (me == null) {
            _actionError.value = "Profile not loaded yet."
            return null
        }
        val convList = (conversations.value as? UiState.Success)?.data ?: emptyList()
        val groupList = (groups.value as? UiState.Success)?.data ?: emptyList()
        return BackupManager.buildBackupJson(
            profile = me,
            privateAccount = privateAccount.value,
            conversations = convList,
            groups = groupList,
            scheduledMessages = scheduledMessages.value
        )
    }

    fun importBackupJson(rawJson: String) {
        val parsed = BackupManager.parseBackupJson(rawJson)
            .onFailure { _actionError.value = it.message ?: "Invalid backup JSON" }
            .getOrNull() ?: return

        viewModelScope.launch {
            repository.applyRestoredContactAndPrivacyLists(
                savedContactIds = parsed.savedContactIds,
                blockedUserIds = parsed.blockedUserIds,
                verifiedPeerIds = parsed.verifiedPeerIds,
                readReceiptsEnabled = parsed.privacy.readReceiptsEnabled,
                lastSeenVisibility = PrivacyAudience.fromRaw(parsed.privacy.lastSeenVisibility),
                photoVisibility = PrivacyAudience.fromRaw(parsed.privacy.photoVisibility),
                statusVisibility = PrivacyAudience.fromRaw(parsed.privacy.statusVisibility),
                discoverable = parsed.privacy.discoverable
            ).onSuccess {
                val restoredSchedule = parsed.scheduledMessages.filter {
                    it.ownerUid == currentUserId && it.statusEnum == ScheduledMessageStatus.PENDING
                }
                scheduledMessageManager?.importRestoredSchedule(restoredSchedule)
                _actionMessage.value = "Restored ${parsed.savedContactIds.size} contacts, privacy controls & ${restoredSchedule.size} scheduled items"
            }.onFailure {
                _actionError.value = it.message ?: "Failed to apply restored backup"
            }
        }
    }

    fun backupToCloud() {
        val json = buildLocalBackupJson() ?: return
        viewModelScope.launch {
            repository.saveCloudBackup(json)
                .onSuccess { _actionMessage.value = "ToKer Vault cloud backup saved" }
                .onFailure { _actionError.value = it.message ?: "Cloud backup failed" }
        }
    }

    fun restoreFromCloud() {
        viewModelScope.launch {
            repository.loadCloudBackup()
                .onSuccess { raw ->
                    if (raw.isNullOrBlank()) {
                        _actionError.value = "No cloud backup found for this ToKer account."
                    } else {
                        importBackupJson(raw)
                    }
                }
                .onFailure { _actionError.value = it.message ?: "Cloud restore failed" }
        }
    }

    fun notifyMessage(msg: String) {
        _actionMessage.value = msg
    }

    fun notifyError(err: String) {
        _actionError.value = err
    }

    fun createGroupChat(
        name: String,
        description: String,
        avatarBase64: String?,
        selectedPeers: List<DirectoryUser>,
        onGroupCreated: (String) -> Unit
    ) {
        val keys = _keyBundle.value
        val me = myProfile.value
        if (keys == null || me == null) {
            _actionError.value = "Initializing your E2EE vault, please try again."
            return
        }
        if (selectedPeers.isEmpty()) {
            _actionError.value = "Select at least 1 contact to create a group."
            return
        }
        val groupId = "grp_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val memberIds = (selectedPeers.map { it.uid } + currentUserId).distinct().take(50)
        viewModelScope.launch {
            try {
                val groupAesKey = cryptoEngine.generateGroupKeyBase64()
                val wrappedKeys = mutableMapOf<String, Pair<String, String>>()
                val allUsers = (selectedPeers + me).distinctBy { it.uid }
                allUsers.forEach { member ->
                    if (member.publicKey.isNotBlank()) {
                        val enc = cryptoEngine.encryptMessage(
                            plaintext = groupAesKey,
                            myPrivateKey = keys.keyPair.private,
                            peerPublicKeyBase64 = member.publicKey,
                            conversationId = groupId,
                            senderId = currentUserId
                        )
                        wrappedKeys[member.uid] = enc.ciphertextBase64 to enc.ivBase64
                    }
                }
                repository.createGroupChat(
                    groupId = groupId,
                    name = name,
                    description = description,
                    avatarBase64 = avatarBase64,
                    memberIds = memberIds,
                    creatorName = me.displayName,
                    wrappedMemberKeys = wrappedKeys,
                    granterPublicKey = keys.publicKeyBase64
                ).onSuccess { createdId ->
                    _actionMessage.value = "Encrypted group \"$name\" created"
                    onGroupCreated(createdId)
                }.onFailure { err ->
                    _actionError.value = err.message ?: "Failed to create group"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.message ?: "Group encryption error"
            }
        }
    }

    fun startChatWithPeer(peer: DirectoryUser, onConversationReady: (String, String) -> Unit) {
        val me = myProfile.value
        if (me == null) {
            _actionError.value = "Initializing your E2EE key bundle, try again in a moment."
            return
        }
        viewModelScope.launch {
            repository.getOrCreateConversation(me, peer)
                .onSuccess { convId -> onConversationReady(convId, peer.uid) }
                .onFailure { _actionError.value = it.message ?: "Failed to open encrypted chat" }
        }
    }

    fun updateConversationNickname(conversationId: String, nickname: String) {
        viewModelScope.launch {
            repository.updateConversationNickname(conversationId, nickname)
                .onSuccess {
                    _actionMessage.value = if (nickname.isBlank()) "Chat name reset" else "Chat name updated"
                }
                .onFailure { _actionError.value = it.message ?: "Failed to update chat name" }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            repository.deleteConversation(conversationId)
                .onSuccess { _actionMessage.value = "Encrypted chat deleted" }
                .onFailure { _actionError.value = it.message ?: "Failed to delete chat" }
        }
    }

    fun updateGroupDetails(groupId: String, name: String, description: String) {
        viewModelScope.launch {
            repository.updateGroupProfile(groupId, name, description, null, false)
                .onSuccess { _actionMessage.value = "Group updated" }
                .onFailure { _actionError.value = it.message ?: "Failed to update group" }
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            repository.deleteGroup(groupId)
                .onSuccess { _actionMessage.value = "Encrypted group deleted" }
                .onFailure { _actionError.value = it.message ?: "Failed to delete group" }
        }
    }

    fun leaveGroup(groupId: String, currentMembers: List<String>) {
        viewModelScope.launch {
            repository.leaveGroup(groupId, currentMembers)
                .onSuccess { _actionMessage.value = "Left group" }
                .onFailure { _actionError.value = it.message ?: "Failed to leave group" }
        }
    }

    fun rotateKeys() {
        viewModelScope.launch {
            try {
                val newBundle = cryptoEngine.rotateIdentityKeys(currentUserId)
                _keyBundle.value = newBundle
                repository.ensureUserProfileInitialized(
                    userId = currentUserId,
                    displayName = myProfile.value?.displayName ?: displayName,
                    email = email,
                    phoneNumber = phoneNumber,
                    photoUrl = myProfile.value?.photoUrl ?: photoUrl,
                    keyBundle = newBundle
                ).onSuccess {
                    _actionMessage.value = "Rotated ECDH P-256 identity keypair"
                }.onFailure {
                    _actionError.value = it.message ?: "Failed to publish rotated key"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.message ?: "Key rotation failed"
            }
        }
    }

    fun publishLiveStory(
        storyType: StoryType,
        caption: String,
        gradient: StoryGradient,
        imageBase64: String?,
        videoBase64: String? = null,
        mentionedUserIds: List<String> = emptyList(),
        mentionedHandles: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            val name = myProfile.value?.displayName ?: displayName
            val avatar = myProfile.value?.photoUrl ?: photoUrl
            repository.publishLiveStory(
                authorName = name,
                authorPhotoUrl = avatar,
                storyType = storyType,
                caption = caption,
                gradient = gradient,
                imageBase64 = imageBase64,
                videoBase64 = videoBase64,
                mentionedUserIds = mentionedUserIds,
                mentionedHandles = mentionedHandles
            ).onSuccess {
                _actionMessage.value = "Live story shared (24h)"
            }.onFailure {
                _actionError.value = it.message ?: "Failed to share story"
            }
        }
    }

    fun markStoryViewed(story: LiveStory) {
        viewModelScope.launch {
            repository.markStoryViewed(story)
        }
    }

    fun deleteLiveStory(storyId: String) {
        viewModelScope.launch {
            repository.deleteStory(storyId)
                .onSuccess { _actionMessage.value = "Story deleted" }
                .onFailure { _actionError.value = it.message ?: "Failed to delete story" }
        }
    }

    fun shareStoryMentionToPeer(peer: DirectoryUser, story: LiveStory, note: String = "") {
        val me = myProfile.value
        val keys = _keyBundle.value
        if (me == null || keys == null || peer.publicKey.isBlank()) {
            _actionError.value = "Peer E2EE key unavailable to mention story"
            return
        }
        viewModelScope.launch {
            repository.getOrCreateConversation(me, peer)
                .onSuccess { convId ->
                    val payload = RichMessagePayload(
                        type = RichMessageType.STORY_MENTION.name,
                        text = note.trim().take(240),
                        thumbBase64 = story.imageBase64?.take(52_000),
                        storyMentionId = story.id,
                        storyMentionAuthor = story.authorName,
                        storyMentionCaption = story.caption.take(140),
                        storyMentionType = story.storyType
                    )
                    val encoded = RichMessageCodec.encode(payload)
                    val preview = RichMessageCodec.previewLabel(payload)
                    val enc = cryptoEngine.encryptMessage(
                        plaintext = encoded,
                        myPrivateKey = keys.keyPair.private,
                        peerPublicKeyBase64 = peer.publicKey,
                        conversationId = convId,
                        senderId = currentUserId
                    )
                    val encPreview = if (enc.ciphertextBase64.length <= 4096) {
                        enc
                    } else {
                        cryptoEngine.encryptMessage(
                            plaintext = preview,
                            myPrivateKey = keys.keyPair.private,
                            peerPublicKeyBase64 = peer.publicKey,
                            conversationId = convId,
                            senderId = currentUserId
                        )
                    }
                    repository.sendEncryptedMessage(
                        conversationId = convId,
                        participantIds = listOf(currentUserId, peer.uid).sorted(),
                        senderPublicKey = keys.publicKeyBase64,
                        ciphertextBase64 = enc.ciphertextBase64,
                        ivBase64 = enc.ivBase64,
                        messageType = RichMessageType.STORY_MENTION.name,
                        previewCiphertextBase64 = encPreview.ciphertextBase64,
                        previewIvBase64 = encPreview.ivBase64
                    ).onSuccess {
                        _actionMessage.value = "Mentioned story to ${peer.displayName}"
                    }.onFailure {
                        _actionError.value = it.message ?: "Failed to mention story"
                    }
                }
                .onFailure {
                    _actionError.value = it.message ?: "Failed to open encrypted chat"
                }
        }
    }

    fun replyToStoryPrivately(story: LiveStory, replyText: String) {
        val clean = replyText.trim()
        if (clean.isEmpty() || story.authorId == currentUserId) return
        val me = myProfile.value ?: return
        val keys = _keyBundle.value ?: return
        val dirUsers = (directoryUsers.value as? UiState.Success)?.data ?: emptyList()
        val peer = dirUsers.firstOrNull { it.uid == story.authorId }
        if (peer == null || peer.publicKey.isBlank()) {
            _actionError.value = "Peer E2EE key unavailable for private reply"
            return
        }
        viewModelScope.launch {
            repository.getOrCreateConversation(me, peer)
                .onSuccess { convId ->
                    val payload = RichMessagePayload(
                        type = RichMessageType.STORY_MENTION.name,
                        text = clean,
                        thumbBase64 = story.imageBase64?.take(52_000),
                        storyMentionId = story.id,
                        storyMentionAuthor = story.authorName,
                        storyMentionCaption = story.caption.take(140),
                        storyMentionType = story.storyType
                    )
                    val encoded = RichMessageCodec.encode(payload)
                    val preview = RichMessageCodec.previewLabel(payload)
                    val encrypted = cryptoEngine.encryptMessage(
                        plaintext = encoded,
                        myPrivateKey = keys.keyPair.private,
                        peerPublicKeyBase64 = peer.publicKey,
                        conversationId = convId,
                        senderId = currentUserId
                    )
                    val encPreview = if (encrypted.ciphertextBase64.length <= 4096) {
                        encrypted
                    } else {
                        cryptoEngine.encryptMessage(
                            plaintext = preview,
                            myPrivateKey = keys.keyPair.private,
                            peerPublicKeyBase64 = peer.publicKey,
                            conversationId = convId,
                            senderId = currentUserId
                        )
                    }
                    repository.sendEncryptedMessage(
                        conversationId = convId,
                        participantIds = listOf(currentUserId, peer.uid).sorted(),
                        senderPublicKey = keys.publicKeyBase64,
                        ciphertextBase64 = encrypted.ciphertextBase64,
                        ivBase64 = encrypted.ivBase64,
                        messageType = RichMessageType.STORY_MENTION.name,
                        previewCiphertextBase64 = encPreview.ciphertextBase64,
                        previewIvBase64 = encPreview.ivBase64
                    ).onSuccess {
                        _actionMessage.value = "Encrypted reply sent to ${peer.displayName}"
                    }.onFailure {
                        _actionError.value = it.message ?: "Failed to send encrypted reply"
                    }
                }
                .onFailure {
                    _actionError.value = it.message ?: "Failed to open encrypted chat"
                }
        }
    }

    fun declineIncomingCall(callId: String) {
        viewModelScope.launch {
            repository.updateCallStatus(callId, CallStatus.DECLINED)
        }
    }

    suspend fun markOfflineBeforeSignOut() {
        repository.updatePresence(PresenceStatus.OFFLINE)
    }

    fun clearFeedback() {
        _actionError.value = null
        _actionMessage.value = null
    }

    private fun buildConversationPreview(
        conv: Conversation,
        dirById: Map<String, DirectoryUser>,
        keys: IdentityKeyBundle?
    ): ConversationListItemUi {
        val peerId = conv.peerIdFor(currentUserId)
        val peerDir = dirById[peerId]
        val customNickname = conv.customTitleFor(currentUserId)
        val peerName = customNickname ?: peerDir?.displayName ?: conv.peerNameFor(currentUserId)
        val peerPhoto = peerDir?.photoUrl ?: conv.peerPhotoFor(currentUserId)
        val peerPubKey = peerDir?.publicKey ?: ""
        val preview = if (conv.lastMessageCiphertext.isBlank()) {
            "Encrypted channel ready"
        } else if (keys != null && peerPubKey.isNotBlank()) {
            val rawDecrypted = cryptoEngine.decryptMessage(
                ciphertextBase64 = conv.lastMessageCiphertext,
                ivBase64 = conv.lastMessageIv,
                myPrivateKey = keys.keyPair.private,
                peerPublicKeyBase64 = peerPubKey,
                conversationId = conv.id,
                senderId = conv.lastMessageSenderId
            ).getOrNull()
            if (rawDecrypted == null) {
                "Encrypted message"
            } else {
                RichMessageCodec.previewLabel(RichMessageCodec.decode(rawDecrypted))
            }
        } else {
            "Encrypted message"
        }
        return ConversationListItemUi(
            conversation = conv,
            peerId = peerId,
            peerName = peerName,
            customNickname = customNickname,
            peerPhotoUrl = peerPhoto,
            peerAvatarBase64 = peerDir?.avatarBase64,
            peerPresence = peerDir?.presenceEnum ?: PresenceStatus.OFFLINE,
            peerLastSeenAt = peerDir?.lastSeenAt,
            peerLastSeenVisibility = peerDir?.lastSeenVisibilityEnum ?: PrivacyAudience.EVERYONE,
            peerStatusMessage = peerDir?.statusMessage ?: "",
            peerPublicKey = peerPubKey,
            lastMessagePreview = preview,
            isLastMessageEncrypted = conv.lastMessageCiphertext.isNotBlank(),
            isLastMessageMine = conv.lastMessageSenderId == currentUserId && conv.lastMessageCiphertext.isNotBlank(),
            isLastMessageReadByPeer = false
        )
    }

    private fun unwrapFirestoreOrThrow(error: Throwable, opName: String): Throwable {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, "Stream error in $opName", root)
        return root
    }

    private companion object {
        const val TAG = "MainVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
