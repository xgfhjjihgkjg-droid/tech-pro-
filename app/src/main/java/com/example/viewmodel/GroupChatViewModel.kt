package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.CryptoEngine
import com.example.crypto.IdentityKeyBundle
import com.example.data.ChatRepository
import com.example.data.DecryptedGroupMessageUi
import com.example.data.DirectoryUser
import com.example.data.GroupChat
import com.example.data.GroupMemberKey
import com.example.data.GroupMessage
import com.example.data.PrivateAccount
import com.example.data.RichMessageCodec
import com.example.data.RichMessagePayload
import com.example.data.RichMessageType
import com.example.data.ScheduledMessageItem
import com.example.data.ScheduledMessageManager
import com.example.data.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupChatViewModel(
    private val repository: ChatRepository,
    private val cryptoEngine: CryptoEngine,
    private val currentUserId: String,
    private val groupId: String,
    private val scheduledMessageManager: ScheduledMessageManager? = null
) : ViewModel() {

    private val _keyBundle = MutableStateFlow<IdentityKeyBundle?>(null)
    val keyBundle: StateFlow<IdentityKeyBundle?> = _keyBundle.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    val group: StateFlow<GroupChat?> = repository.observeGroup(groupId)
        .catch { error ->
            unwrapFirestoreOrThrow(error, "observeGroup")
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    val myProfile: StateFlow<DirectoryUser?> = repository.observeDirectoryUser(currentUserId)
        .catch { error ->
            unwrapFirestoreOrThrow(error, "observeMyProfile")
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    val privateAccount: StateFlow<PrivateAccount?> = repository.observePrivateAccount(currentUserId)
        .catch { error ->
            unwrapFirestoreOrThrow(error, "observePrivateAccount")
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    val groupScheduledMessages: StateFlow<List<ScheduledMessageItem>> =
        (scheduledMessageManager?.scheduledMessages ?: flowOf(emptyList()))
            .map { list ->
                list.filter {
                    it.ownerUid == currentUserId &&
                        it.targetType == "GROUP" &&
                        it.targetId == groupId
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    private val _hydratedMembers = MutableStateFlow<Map<String, DirectoryUser>>(emptyMap())
    private val fetchedMemberIds = mutableSetOf<String>()

    val directoryUsers: StateFlow<List<DirectoryUser>> = combine(
        repository.observeDirectoryUsers(),
        _hydratedMembers,
        myProfile,
        group
    ) { discoverable, extra, me, currentGroup ->
        val map = LinkedHashMap<String, DirectoryUser>()
        extra.forEach { (k, v) -> map[k] = v }
        discoverable.forEach { map[it.uid] = it }
        if (me != null) map[me.uid] = me

        val memberIds = currentGroup?.memberIds.orEmpty()
        val missing = memberIds.filter { it.isNotBlank() && it !in map && fetchedMemberIds.add(it) }
        if (missing.isNotEmpty()) {
            viewModelScope.launch {
                val loaded = repository.getDirectoryUsersByIds(missing)
                if (loaded.isNotEmpty()) {
                    _hydratedMembers.value = _hydratedMembers.value + loaded
                }
            }
        }
        map.values.toList()
    }
        .catch { error ->
            unwrapFirestoreOrThrow(error, "observeDirectoryUsers")
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    private val wrappedKeyState: StateFlow<GroupMemberKey?> = repository
        .observeMyGroupMemberKey(groupId, currentUserId)
        .catch { error ->
            unwrapFirestoreOrThrow(error, "observeMyGroupMemberKey")
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    val groupSecretKeyBase64: StateFlow<String?> = combine(_keyBundle, wrappedKeyState) { keys, wrapped ->
        if (keys == null || wrapped == null) {
            null
        } else {
            cryptoEngine.decryptMessage(
                ciphertextBase64 = wrapped.wrappedKeyCiphertext,
                ivBase64 = wrapped.wrappedKeyIv,
                myPrivateKey = keys.keyPair.private,
                peerPublicKeyBase64 = wrapped.granterPublicKey,
                conversationId = groupId,
                senderId = wrapped.granterId
            ).getOrNull()
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    private val nowTickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(5_000L)
        }
    }

    val messagesState: StateFlow<UiState<List<DecryptedGroupMessageUi>>> = combine(
        repository.observeGroupMessages(groupId, currentUserId),
        groupSecretKeyBase64,
        privateAccount,
        group,
        nowTickerFlow
    ) { rawMessages, secretKey, account, currentGroup, nowMillis ->
        val expired = rawMessages.filter { msg ->
            msg.expiresAtMillis != null && msg.expiresAtMillis <= nowMillis
        }
        if (expired.isNotEmpty()) {
            val isAdmin = currentGroup?.isAdmin(currentUserId) == true
            expired.filter { (it.senderId == currentUserId || isAdmin) && it.id.isNotBlank() }.forEach { exp ->
                viewModelScope.launch {
                    repository.deleteExpiredGroupMessageSilently(groupId, exp.id)
                }
            }
        }
        val activeRaw = rawMessages.filter { msg ->
            msg.expiresAtMillis == null || msg.expiresAtMillis > nowMillis
        }
        val joinedAt = currentGroup?.memberJoinedAt?.get(currentUserId) ?: 0L
        val visibleMessages = if (joinedAt > 0L) {
            activeRaw.filter { msg ->
                msg.senderId == currentUserId ||
                    (msg.createdAt?.toDate()?.time ?: Long.MAX_VALUE) >= joinedAt
            }
        } else {
            activeRaw
        }
        if (account?.readReceiptsEnabled != false) {
            val unread = visibleMessages.filter { it.senderId != currentUserId && currentUserId !in it.readBy }
            if (unread.isNotEmpty()) {
                viewModelScope.launch {
                    repository.markGroupMessagesAsRead(groupId, unread)
                }
            }
        }
        val decrypted = visibleMessages.map { msg ->
            decryptGroupMessage(msg, secretKey)
        }
        UiState.Success(decrypted) as UiState<List<DecryptedGroupMessageUi>>
    }
        .flowOn(Dispatchers.Default)
        .catch { error ->
            val root = unwrapFirestoreOrThrow(error, "observeGroupMessages")
            emit(UiState.Error(root.message ?: "Failed to load group messages"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    init {
        viewModelScope.launch {
            _keyBundle.value = cryptoEngine.getOrCreateIdentityKeys(currentUserId)
        }
    }

    fun sendGroupMessage(plaintext: String, replyTo: DecryptedGroupMessageUi? = null) {
        val clean = plaintext.trim()
        if (clean.isEmpty()) return
        val isPureEmoji = isEmojiOnlyMessage(clean)
        sendRichGroupMessage(
            rawPayload = RichMessagePayload(
                type = if (isPureEmoji) RichMessageType.EMOJI.name else RichMessageType.TEXT.name,
                text = clean
            ),
            replyTo = replyTo
        )
    }

    fun sendRichGroupMessage(
        rawPayload: RichMessagePayload,
        replyTo: DecryptedGroupMessageUi? = null
    ) {
        val payload = if (replyTo != null && rawPayload.replyToMessageId.isBlank()) {
            val replyAuthor = if (replyTo.raw.senderId == currentUserId) "You" else replyTo.raw.senderName
            rawPayload.copy(
                replyToMessageId = replyTo.raw.id,
                replyToSenderName = replyAuthor,
                replyToPreview = RichMessageCodec.previewLabel(replyTo.payload).take(80)
            )
        } else {
            rawPayload
        }
        val secretKey = groupSecretKeyBase64.value
        val currentGroup = group.value
        if (secretKey.isNullOrBlank() || currentGroup == null) {
            _actionError.value = "Unwrapping group E2EE key, please try again..."
            return
        }
        if (currentGroup.onlyAdminsCanMessage && !currentGroup.isAdmin(currentUserId)) {
            _actionError.value = "Only group admins can send messages in this group."
            return
        }
        val senderName = myProfile.value?.displayName ?: "ToKerUser"
        val encodedPlaintext = RichMessageCodec.encode(payload)
        val previewPlaintext = RichMessageCodec.previewLabel(payload)
        val disappearSeconds = currentGroup.disappearingSeconds
        val expiresAtMillis = if (disappearSeconds > 0L) {
            System.currentTimeMillis() + disappearSeconds * 1000L
        } else null
        viewModelScope.launch {
            _isSending.value = true
            try {
                val encrypted = cryptoEngine.encryptWithGroupKey(
                    plaintext = encodedPlaintext,
                    groupKeyBase64 = secretKey,
                    groupId = groupId,
                    senderId = currentUserId
                )
                val encryptedPreview = if (encrypted.ciphertextBase64.length <= 4096) {
                    encrypted
                } else {
                    cryptoEngine.encryptWithGroupKey(
                        plaintext = previewPlaintext,
                        groupKeyBase64 = secretKey,
                        groupId = groupId,
                        senderId = currentUserId
                    )
                }
                repository.sendGroupMessage(
                    groupId = groupId,
                    participantIds = currentGroup.memberIds,
                    senderName = senderName,
                    ciphertextBase64 = encrypted.ciphertextBase64,
                    ivBase64 = encrypted.ivBase64,
                    messageType = payload.typeEnum.name,
                    previewCiphertextBase64 = encryptedPreview.ciphertextBase64,
                    previewIvBase64 = encryptedPreview.ivBase64,
                    expiresAtMillis = expiresAtMillis
                ).onFailure { err ->
                    _actionError.value = err.message ?: "Failed to send group message"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.message ?: "Group encryption error"
            } finally {
                _isSending.value = false
            }
        }
    }

    fun votePoll(messageId: String, optionIndex: Int) {
        viewModelScope.launch {
            repository.voteOnGroupPoll(groupId, messageId, optionIndex)
                .onFailure { _actionError.value = it.message ?: "Failed to record group vote" }
        }
    }

    fun toggleReaction(messageId: String, emoji: String, currentReactions: Map<String, String>) {
        viewModelScope.launch {
            repository.reactToGroupMessage(groupId, messageId, emoji, currentReactions)
                .onFailure { _actionError.value = it.message ?: "Failed to update reaction" }
        }
    }

    fun editGroupMessage(target: DecryptedGroupMessageUi, newText: String) {
        if (target.raw.senderId != currentUserId || !target.decryptedSuccessfully) return
        val secretKey = groupSecretKeyBase64.value
        if (secretKey.isNullOrBlank()) {
            _actionError.value = "Group key unavailable to edit message."
            return
        }
        val clean = newText.trim()
        val old = target.payload
        val updatedPayload = when (old.typeEnum) {
            RichMessageType.POLL -> {
                if (clean.isEmpty()) return
                old.copy(text = clean, pollQuestion = clean)
            }
            RichMessageType.LOCATION -> {
                if (clean.isEmpty()) return
                old.copy(text = clean, locationLabel = clean)
            }
            RichMessageType.IMAGE, RichMessageType.VIDEO, RichMessageType.DOCUMENT, RichMessageType.STORY_MENTION -> {
                old.copy(text = clean)
            }
            RichMessageType.TEXT, RichMessageType.EMOJI -> {
                if (clean.isEmpty()) return
                val nextType = if (isEmojiOnlyMessage(clean)) RichMessageType.EMOJI else RichMessageType.TEXT
                old.copy(type = nextType.name, text = clean)
            }
            RichMessageType.CALL_LOG, RichMessageType.VOICE, RichMessageType.STICKER, RichMessageType.GIF -> return
        }

        val currentList = (messagesState.value as? UiState.Success)?.data ?: emptyList()
        val isLatest = currentList.lastOrNull()?.raw?.id == target.raw.id
        val senderName = myProfile.value?.displayName ?: "ToKerUser"
        val encoded = RichMessageCodec.encode(updatedPayload)
        val previewPlaintext = RichMessageCodec.previewLabel(updatedPayload)

        viewModelScope.launch {
            _isSending.value = true
            try {
                val encrypted = cryptoEngine.encryptWithGroupKey(
                    plaintext = encoded,
                    groupKeyBase64 = secretKey,
                    groupId = groupId,
                    senderId = currentUserId
                )
                val encryptedPreview = if (encrypted.ciphertextBase64.length <= 4096) {
                    encrypted
                } else {
                    cryptoEngine.encryptWithGroupKey(
                        plaintext = previewPlaintext,
                        groupKeyBase64 = secretKey,
                        groupId = groupId,
                        senderId = currentUserId
                    )
                }
                repository.editGroupMessage(
                    groupId = groupId,
                    messageId = target.raw.id,
                    senderName = senderName,
                    ciphertextBase64 = encrypted.ciphertextBase64,
                    ivBase64 = encrypted.ivBase64,
                    messageType = updatedPayload.typeEnum.name,
                    updateGroupPreview = isLatest,
                    previewCiphertextBase64 = encryptedPreview.ciphertextBase64,
                    previewIvBase64 = encryptedPreview.ivBase64
                ).onSuccess {
                    _actionMessage.value = "Message updated"
                }.onFailure { err ->
                    _actionError.value = err.message ?: "Failed to edit group message"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.message ?: "Group encryption error"
            } finally {
                _isSending.value = false
            }
        }
    }

    fun isCurrentUserAdmin(): Boolean {
        val currentGroup = group.value ?: return false
        return currentGroup.createdBy == currentUserId || currentUserId in currentGroup.adminIds
    }

    fun deleteGroupMessage(target: DecryptedGroupMessageUi) {
        val canModerate = target.raw.senderId == currentUserId || isCurrentUserAdmin()
        if (!canModerate || target.raw.id.isBlank()) {
            _actionError.value = "Only the sender or a group admin can delete this message."
            return
        }
        val currentList = (messagesState.value as? UiState.Success)?.data ?: emptyList()
        val isLatest = currentList.lastOrNull()?.raw?.id == target.raw.id
        val previousMsg = if (isLatest) currentList.dropLast(1).lastOrNull() else null

        viewModelScope.launch {
            var newCipher = ""
            var newIv = ""
            var newSenderId = ""
            var newSenderName = ""
            if (isLatest && previousMsg != null) {
                val secretKey = groupSecretKeyBase64.value
                if (!secretKey.isNullOrBlank()) {
                    val preview = RichMessageCodec.previewLabel(previousMsg.payload)
                    val enc = cryptoEngine.encryptWithGroupKey(
                        plaintext = preview,
                        groupKeyBase64 = secretKey,
                        groupId = groupId,
                        senderId = currentUserId
                    )
                    newCipher = enc.ciphertextBase64
                    newIv = enc.ivBase64
                    newSenderId = previousMsg.raw.senderId
                    newSenderName = previousMsg.raw.senderName
                }
            }
            repository.deleteGroupMessage(
                groupId = groupId,
                messageId = target.raw.id,
                updateGroupPreview = isLatest,
                newLastCiphertext = newCipher,
                newLastIv = newIv,
                newLastSenderId = newSenderId,
                newLastSenderName = newSenderName
            ).onSuccess {
                _actionMessage.value = "Message deleted"
            }.onFailure { err ->
                _actionError.value = err.message ?: "Failed to delete group message"
            }
        }
    }

    fun addMembers(newPeers: List<DirectoryUser>) {
        val currentGroup = group.value ?: return
        if (!isCurrentUserAdmin()) {
            _actionError.value = "Only group admins can add members."
            return
        }
        val keys = _keyBundle.value ?: return
        val secretKey = groupSecretKeyBase64.value
        if (secretKey.isNullOrBlank()) {
            _actionError.value = "Group key unavailable to invite new members."
            return
        }
        val filtered = newPeers.filter { it.uid !in currentGroup.memberIds && it.publicKey.isNotBlank() }
        if (filtered.isEmpty()) return
        val updatedIds = (currentGroup.memberIds + filtered.map { it.uid }).distinct().take(50)

        viewModelScope.launch {
            val wrapped = mutableMapOf<String, Pair<String, String>>()
            filtered.forEach { peer ->
                val enc = cryptoEngine.encryptMessage(
                    plaintext = secretKey,
                    myPrivateKey = keys.keyPair.private,
                    peerPublicKeyBase64 = peer.publicKey,
                    conversationId = groupId,
                    senderId = currentUserId
                )
                wrapped[peer.uid] = enc.ciphertextBase64 to enc.ivBase64
            }
            repository.addMembersToGroup(
                groupId = groupId,
                updatedMemberIds = updatedIds,
                newWrappedKeys = wrapped,
                granterPublicKey = keys.publicKeyBase64
            ).onSuccess {
                _actionMessage.value = "Added ${filtered.size} contact(s) to group"
            }.onFailure {
                _actionError.value = it.message ?: "Failed to add contacts"
            }
        }
    }

    fun toggleMemberAdmin(targetMemberId: String) {
        val currentGroup = group.value ?: return
        val isCreator = currentGroup.createdBy == currentUserId
        if (!isCurrentUserAdmin()) {
            _actionError.value = "Only group admins can manage admin roles."
            return
        }
        if (targetMemberId == currentGroup.createdBy) {
            _actionError.value = "Group creator cannot lose admin status."
            return
        }
        val currentlyAdmin = targetMemberId in currentGroup.adminIds
        if (currentlyAdmin && !isCreator) {
            _actionError.value = "Only the group creator can demote an admin."
            return
        }
        val baseAdmins = (currentGroup.adminIds + currentGroup.createdBy).distinct()
        val updatedAdmins = if (currentlyAdmin) {
            baseAdmins.filter { it != targetMemberId }
        } else {
            (baseAdmins + targetMemberId).distinct()
        }
        viewModelScope.launch {
            repository.updateGroupAdmins(groupId, updatedAdmins)
                .onSuccess {
                    _actionMessage.value = if (currentlyAdmin) "Removed admin role" else "Promoted to Group Admin"
                }
                .onFailure {
                    _actionError.value = it.message ?: "Failed to update admin role"
                }
        }
    }

    fun removeMember(targetMemberId: String) {
        val currentGroup = group.value ?: return
        val isCreator = currentGroup.createdBy == currentUserId
        if (!isCurrentUserAdmin()) {
            _actionError.value = "Only group admins can remove members."
            return
        }
        if (targetMemberId == currentGroup.createdBy || targetMemberId == currentUserId) {
            _actionError.value = "Group creator cannot be removed."
            return
        }
        if (!isCreator && targetMemberId in currentGroup.adminIds) {
            _actionError.value = "Only the group creator can remove another admin."
            return
        }
        val remainingMembers = currentGroup.memberIds.filter { it != targetMemberId }
        if (remainingMembers.isEmpty()) return
        val remainingAdmins = currentGroup.adminIds
            .filter { it != targetMemberId }
            .ifEmpty { listOf(currentGroup.createdBy) }

        viewModelScope.launch {
            repository.removeMemberFromGroup(
                groupId = groupId,
                targetMemberId = targetMemberId,
                remainingMemberIds = remainingMembers,
                remainingAdminIds = remainingAdmins
            ).onSuccess {
                _actionMessage.value = "Removed member from group"
            }.onFailure {
                _actionError.value = it.message ?: "Failed to remove member"
            }
        }
    }

    fun updateGroupDetails(name: String, description: String, avatarBase64: String?, clearAvatar: Boolean) {
        if (!isCurrentUserAdmin()) {
            _actionError.value = "Only group admins can edit group details."
            return
        }
        viewModelScope.launch {
            repository.updateGroupProfile(groupId, name, description, avatarBase64, clearAvatar)
                .onSuccess { _actionMessage.value = "Group profile updated" }
                .onFailure { _actionError.value = it.message ?: "Failed to update group" }
        }
    }

    fun leaveGroup(onLeft: () -> Unit) {
        val currentMembers = group.value?.memberIds ?: return
        viewModelScope.launch {
            repository.leaveGroup(groupId, currentMembers)
                .onSuccess { onLeft() }
                .onFailure { _actionError.value = it.message ?: "Failed to leave group" }
        }
    }

    fun deleteGroup(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteGroup(groupId)
                .onSuccess { onDeleted() }
                .onFailure { _actionError.value = it.message ?: "Failed to delete group" }
        }
    }

    fun pinGroupMessage(messageId: String?) {
        viewModelScope.launch {
            repository.pinGroupMessage(groupId, messageId)
                .onFailure { _actionError.value = it.message ?: "Failed to pin group message" }
        }
    }

    fun setOnlyAdminsCanMessage(enabled: Boolean) {
        if (!isCurrentUserAdmin()) {
            _actionError.value = "Only group admins can change messaging permissions."
            return
        }
        viewModelScope.launch {
            repository.updateGroupOnlyAdminsCanMessage(groupId, enabled)
                .onSuccess {
                    _actionMessage.value = if (enabled) {
                        "Only group admins can send messages now"
                    } else {
                        "All group members can send messages now"
                    }
                }
                .onFailure { _actionError.value = it.message ?: "Failed to update permission" }
        }
    }

    fun setDisappearingTimer(seconds: Long) {
        if (!isCurrentUserAdmin()) {
            _actionError.value = "Only group admins can configure disappearing messages."
            return
        }
        viewModelScope.launch {
            repository.updateGroupDisappearingTimer(groupId, seconds)
                .onFailure { _actionError.value = it.message ?: "Failed to update disappearing timer" }
        }
    }

    fun scheduleGroupMessage(text: String, scheduledAtMillis: Long) {
        val mgr = scheduledMessageManager ?: return
        val clean = text.trim()
        if (clean.isEmpty()) {
            _actionError.value = "Enter a message before scheduling."
            return
        }
        val title = group.value?.name ?: "ToKer Group"
        viewModelScope.launch {
            mgr.scheduleMessage(
                ownerUid = currentUserId,
                targetType = "GROUP",
                targetId = groupId,
                peerId = "",
                targetTitle = title,
                text = clean,
                scheduledAtMillis = scheduledAtMillis
            )
            _actionMessage.value = "Group message scheduled"
        }
    }

    fun cancelScheduledMessage(id: String) {
        val mgr = scheduledMessageManager ?: return
        viewModelScope.launch { mgr.cancelScheduledMessage(id) }
    }

    fun rescheduleMessage(id: String, newMillis: Long) {
        val mgr = scheduledMessageManager ?: return
        viewModelScope.launch { mgr.rescheduleMessage(id, newMillis) }
    }

    fun sendScheduledNow(item: ScheduledMessageItem) {
        val mgr = scheduledMessageManager ?: return
        viewModelScope.launch {
            mgr.dispatchDueMessages(
                repository = repository,
                cryptoEngine = cryptoEngine,
                currentUserId = currentUserId,
                myDisplayName = myProfile.value?.displayName ?: "ToKerUser",
                forceItemId = item.id
            )
        }
    }

    fun clearFeedback() {
        _actionError.value = null
        _actionMessage.value = null
    }

    private fun decryptGroupMessage(msg: GroupMessage, secretKey: String?): DecryptedGroupMessageUi {
        if (secretKey.isNullOrBlank()) {
            return DecryptedGroupMessageUi(
                raw = msg,
                plaintext = "Decrypting group message...",
                decryptedSuccessfully = false,
                payload = RichMessagePayload(type = RichMessageType.TEXT.name, text = "Decrypting group message...")
            )
        }
        val result = cryptoEngine.decryptWithGroupKey(
            ciphertextBase64 = msg.ciphertext,
            ivBase64 = msg.iv,
            groupKeyBase64 = secretKey,
            groupId = groupId,
            senderId = msg.senderId
        )
        return result.fold(
            onSuccess = { decryptedText ->
                val decoded = RichMessageCodec.decode(decryptedText, msg.messageType)
                DecryptedGroupMessageUi(
                    raw = msg,
                    plaintext = RichMessageCodec.previewLabel(decoded),
                    decryptedSuccessfully = true,
                    payload = decoded
                )
            },
            onFailure = {
                DecryptedGroupMessageUi(
                    raw = msg,
                    plaintext = "[Encrypted group message]",
                    decryptedSuccessfully = false,
                    payload = RichMessagePayload(type = RichMessageType.TEXT.name, text = "[Encrypted group message]")
                )
            }
        )
    }

    private fun isEmojiOnlyMessage(text: String): Boolean {
        val noSpace = text.replace(Regex("\\s+"), "")
        if (noSpace.isEmpty() || noSpace.length > 16) return false
        return noSpace.codePoints().allMatch { cp ->
            cp > 0x2000 && !Character.isLetterOrDigit(cp)
        }
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
        const val TAG = "GroupChatVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
