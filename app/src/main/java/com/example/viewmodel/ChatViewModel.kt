package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.CryptoEngine
import com.example.crypto.IdentityKeyBundle
import com.example.data.ChatRepository
import com.example.data.Conversation
import com.example.data.DecryptedMessageUi
import com.example.data.DirectoryUser
import com.example.data.EncryptedMessage
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
import kotlinx.coroutines.Job
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

class ChatViewModel(
    private val repository: ChatRepository,
    private val cryptoEngine: CryptoEngine,
    private val currentUserId: String,
    private val conversationId: String,
    private val peerId: String,
    private val scheduledMessageManager: ScheduledMessageManager? = null
) : ViewModel() {

    private val _keyBundle = MutableStateFlow<IdentityKeyBundle?>(null)
    val keyBundle: StateFlow<IdentityKeyBundle?> = _keyBundle.asStateFlow()

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private var typingResetJob: Job? = null
    private var lastTypingSentMillis: Long = 0L

    val conversation: StateFlow<Conversation?> = repository.observeConversation(conversationId)
        .catch { error ->
            unwrapFirestoreOrThrow(error, "observeConversation")
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    val peerProfile: StateFlow<DirectoryUser?> = repository.observeDirectoryUser(peerId)
        .catch { error ->
            unwrapFirestoreOrThrow(error, "observePeerProfile")
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

    val isPeerTyping: StateFlow<Boolean> = repository.observeTypingStates(conversationId, currentUserId)
        .map { states ->
            states.any { it.userId == peerId && it.typing }
        }
        .catch { error ->
            unwrapFirestoreOrThrow(error, "observeTypingStates")
            emit(false)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = false
        )

    val peerScheduledMessages: StateFlow<List<ScheduledMessageItem>> =
        (scheduledMessageManager?.scheduledMessages ?: flowOf(emptyList()))
            .map { list ->
                list.filter {
                    it.ownerUid == currentUserId &&
                        it.targetType == "CONVERSATION" &&
                        it.targetId == conversationId
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    private val nowTickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(5_000L)
        }
    }

    val messagesState: StateFlow<UiState<List<DecryptedMessageUi>>> = combine(
        repository.observeMessages(conversationId, currentUserId),
        peerProfile,
        _keyBundle,
        privateAccount,
        nowTickerFlow
    ) { rawMessages, peer, keys, account, nowMillis ->
        val expired = rawMessages.filter { msg ->
            msg.expiresAtMillis != null && msg.expiresAtMillis <= nowMillis
        }
        if (expired.isNotEmpty()) {
            expired.filter { it.senderId == currentUserId && it.id.isNotBlank() }.forEach { exp ->
                viewModelScope.launch {
                    repository.deleteExpiredConversationMessageSilently(conversationId, exp.id)
                }
            }
        }
        val activeMessages = rawMessages.filter { msg ->
            msg.expiresAtMillis == null || msg.expiresAtMillis > nowMillis
        }
        val sendReadReceipts = account?.readReceiptsEnabled != false
        if (sendReadReceipts) {
            val unreadFromPeer = activeMessages.filter {
                it.senderId == peerId && currentUserId !in it.readBy
            }
            if (unreadFromPeer.isNotEmpty()) {
                viewModelScope.launch {
                    repository.markMessagesAsRead(conversationId, unreadFromPeer)
                }
            }
        }
        val decrypted = activeMessages.map { msg ->
            decryptSingleMessage(msg, peer?.publicKey.orEmpty(), keys)
        }
        UiState.Success(decrypted) as UiState<List<DecryptedMessageUi>>
    }
        .flowOn(Dispatchers.Default)
        .catch { error ->
            val root = unwrapFirestoreOrThrow(error, "observeMessages")
            emit(UiState.Error(root.message ?: "Failed to load encrypted messages"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val safetyNumber: StateFlow<String> = combine(_keyBundle, peerProfile) { myKeys, peer ->
        val myPub = myKeys?.publicKeyBase64.orEmpty()
        val peerPub = peer?.publicKey.orEmpty()
        if (myPub.isNotBlank() && peerPub.isNotBlank()) {
            cryptoEngine.computeSafetyNumber(myPub, peerPub)
        } else {
            ""
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ""
        )

    init {
        viewModelScope.launch {
            _keyBundle.value = cryptoEngine.getOrCreateIdentityKeys(currentUserId)
        }
    }

    fun onDraftChanged(draft: String) {
        val participants = listOf(currentUserId, peerId).sorted()
        typingResetJob?.cancel()
        if (draft.isBlank()) {
            lastTypingSentMillis = 0L
            viewModelScope.launch {
                repository.setTypingState(conversationId, participants, false)
            }
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastTypingSentMillis >= 2_000L) {
            lastTypingSentMillis = now
            viewModelScope.launch {
                repository.setTypingState(conversationId, participants, true)
            }
        }
        typingResetJob = viewModelScope.launch {
            delay(3500L)
            lastTypingSentMillis = 0L
            repository.setTypingState(conversationId, participants, false)
        }
    }

    fun sendMessage(plaintext: String, replyTo: DecryptedMessageUi? = null) {
        val clean = plaintext.trim()
        if (clean.isEmpty()) return
        val isPureEmoji = isEmojiOnlyMessage(clean)
        sendRichMessage(
            rawPayload = RichMessagePayload(
                type = if (isPureEmoji) RichMessageType.EMOJI.name else RichMessageType.TEXT.name,
                text = clean
            ),
            replyTo = replyTo
        )
    }

    fun sendRichMessage(rawPayload: RichMessagePayload, replyTo: DecryptedMessageUi? = null) {
        val payload = if (replyTo != null && rawPayload.replyToMessageId.isBlank()) {
            val replyAuthor = if (replyTo.raw.senderId == currentUserId) {
                "You"
            } else {
                peerProfile.value?.displayName ?: "Contact"
            }
            rawPayload.copy(
                replyToMessageId = replyTo.raw.id,
                replyToSenderName = replyAuthor,
                replyToPreview = RichMessageCodec.previewLabel(replyTo.payload).take(80)
            )
        } else {
            rawPayload
        }
        val keys = _keyBundle.value
        val peerPub = peerProfile.value?.publicKey
        if (keys == null || peerPub.isNullOrBlank()) {
            _sendError.value = "Waiting for peer E2EE public key exchange..."
            return
        }
        val encodedPlaintext = RichMessageCodec.encode(payload)
        val previewPlaintext = RichMessageCodec.previewLabel(payload)
        val participants = listOf(currentUserId, peerId).sorted()
        viewModelScope.launch {
            _isSending.value = true
            try {
                val encrypted = cryptoEngine.encryptMessage(
                    plaintext = encodedPlaintext,
                    myPrivateKey = keys.keyPair.private,
                    peerPublicKeyBase64 = peerPub,
                    conversationId = conversationId,
                    senderId = currentUserId
                )
                val encryptedPreview = if (encrypted.ciphertextBase64.length <= 4096) {
                    encrypted
                } else {
                    cryptoEngine.encryptMessage(
                        plaintext = previewPlaintext,
                        myPrivateKey = keys.keyPair.private,
                        peerPublicKeyBase64 = peerPub,
                        conversationId = conversationId,
                        senderId = currentUserId
                    )
                }
                val disappearSeconds = conversation.value?.disappearingSeconds ?: 0L
                val expiresAtMillis = if (disappearSeconds > 0L) {
                    System.currentTimeMillis() + disappearSeconds * 1000L
                } else null
                repository.sendEncryptedMessage(
                    conversationId = conversationId,
                    participantIds = participants,
                    senderPublicKey = keys.publicKeyBase64,
                    ciphertextBase64 = encrypted.ciphertextBase64,
                    ivBase64 = encrypted.ivBase64,
                    messageType = payload.typeEnum.name,
                    previewCiphertextBase64 = encryptedPreview.ciphertextBase64,
                    previewIvBase64 = encryptedPreview.ivBase64,
                    expiresAtMillis = expiresAtMillis
                ).onFailure { err ->
                    _sendError.value = err.message ?: "Failed to send encrypted message"
                }
                repository.setTypingState(conversationId, participants, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _sendError.value = e.message ?: "Encryption error"
            } finally {
                _isSending.value = false
            }
        }
    }

    fun votePoll(messageId: String, optionIndex: Int) {
        viewModelScope.launch {
            repository.voteOnConversationPoll(conversationId, messageId, optionIndex)
                .onFailure { _sendError.value = it.message ?: "Failed to record vote" }
        }
    }

    fun toggleReaction(messageId: String, emoji: String, currentReactions: Map<String, String>) {
        viewModelScope.launch {
            repository.reactToConversationMessage(conversationId, messageId, emoji, currentReactions)
                .onFailure { _sendError.value = it.message ?: "Failed to update reaction" }
        }
    }

    fun editMessage(target: DecryptedMessageUi, newText: String) {
        if (target.raw.senderId != currentUserId || !target.decryptedSuccessfully) return
        val keys = _keyBundle.value
        val peerPub = peerProfile.value?.publicKey
        if (keys == null || peerPub.isNullOrBlank()) {
            _sendError.value = "Peer E2EE key unavailable to edit message"
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
        val encoded = RichMessageCodec.encode(updatedPayload)
        val previewPlaintext = RichMessageCodec.previewLabel(updatedPayload)

        viewModelScope.launch {
            _isSending.value = true
            try {
                val encrypted = cryptoEngine.encryptMessage(
                    plaintext = encoded,
                    myPrivateKey = keys.keyPair.private,
                    peerPublicKeyBase64 = peerPub,
                    conversationId = conversationId,
                    senderId = currentUserId
                )
                val encryptedPreview = if (encrypted.ciphertextBase64.length <= 4096) {
                    encrypted
                } else {
                    cryptoEngine.encryptMessage(
                        plaintext = previewPlaintext,
                        myPrivateKey = keys.keyPair.private,
                        peerPublicKeyBase64 = peerPub,
                        conversationId = conversationId,
                        senderId = currentUserId
                    )
                }
                repository.editEncryptedMessage(
                    conversationId = conversationId,
                    messageId = target.raw.id,
                    senderPublicKey = keys.publicKeyBase64,
                    ciphertextBase64 = encrypted.ciphertextBase64,
                    ivBase64 = encrypted.ivBase64,
                    messageType = updatedPayload.typeEnum.name,
                    updateConversationPreview = isLatest,
                    previewCiphertextBase64 = encryptedPreview.ciphertextBase64,
                    previewIvBase64 = encryptedPreview.ivBase64
                ).onFailure { err ->
                    _sendError.value = err.message ?: "Failed to edit message"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _sendError.value = e.message ?: "Failed to encrypt edited message"
            } finally {
                _isSending.value = false
            }
        }
    }

    fun deleteMessage(target: DecryptedMessageUi) {
        if (target.raw.senderId != currentUserId || target.raw.id.isBlank()) return
        val currentList = (messagesState.value as? UiState.Success)?.data ?: emptyList()
        val isLatest = currentList.lastOrNull()?.raw?.id == target.raw.id
        val previousMsg = if (isLatest) currentList.dropLast(1).lastOrNull() else null

        viewModelScope.launch {
            var newCipher = ""
            var newIv = ""
            var newSenderId = ""
            var newSenderPub = ""
            if (isLatest && previousMsg != null) {
                val keys = _keyBundle.value
                val peerPub = peerProfile.value?.publicKey
                if (keys != null && !peerPub.isNullOrBlank()) {
                    val previewLabel = RichMessageCodec.previewLabel(previousMsg.payload)
                    val enc = cryptoEngine.encryptMessage(
                        plaintext = previewLabel,
                        myPrivateKey = keys.keyPair.private,
                        peerPublicKeyBase64 = peerPub,
                        conversationId = conversationId,
                        senderId = currentUserId
                    )
                    newCipher = enc.ciphertextBase64
                    newIv = enc.ivBase64
                    newSenderId = currentUserId
                    newSenderPub = keys.publicKeyBase64
                }
            }
            repository.deleteEncryptedMessage(
                conversationId = conversationId,
                messageId = target.raw.id,
                updateConversationPreview = isLatest,
                newLastCiphertext = newCipher,
                newLastIv = newIv,
                newLastSenderId = newSenderId,
                newLastSenderPublicKey = newSenderPub
            ).onFailure { err ->
                _sendError.value = err.message ?: "Failed to delete message"
            }
        }
    }

    fun updateChatNickname(nickname: String) {
        viewModelScope.launch {
            repository.updateConversationNickname(conversationId, nickname)
                .onFailure { _sendError.value = it.message ?: "Failed to update chat name" }
        }
    }

    fun deleteChat(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteConversation(conversationId)
                .onSuccess { onDeleted() }
                .onFailure { _sendError.value = it.message ?: "Failed to delete chat" }
        }
    }

    fun togglePeerVerified() {
        val currentVerified = privateAccount.value?.verifiedPeerIds ?: emptyList()
        viewModelScope.launch {
            repository.toggleVerifiedPeer(peerId, currentVerified)
                .onFailure { _sendError.value = it.message ?: "Failed to update verification" }
        }
    }

    fun pinMessage(messageId: String?) {
        viewModelScope.launch {
            repository.pinConversationMessage(conversationId, messageId)
                .onFailure { _sendError.value = it.message ?: "Failed to update pinned message" }
        }
    }

    fun setDisappearingTimer(seconds: Long) {
        viewModelScope.launch {
            repository.updateConversationDisappearingTimer(conversationId, seconds)
                .onFailure { _sendError.value = it.message ?: "Failed to update disappearing timer" }
        }
    }

    fun scheduleMessage(text: String, scheduledAtMillis: Long) {
        val mgr = scheduledMessageManager ?: return
        val clean = text.trim()
        if (clean.isEmpty()) {
            _sendError.value = "Enter a message before scheduling."
            return
        }
        val title = conversation.value?.customTitleFor(currentUserId)
            ?: peerProfile.value?.displayName
            ?: "Contact"
        viewModelScope.launch {
            mgr.scheduleMessage(
                ownerUid = currentUserId,
                targetType = "CONVERSATION",
                targetId = conversationId,
                peerId = peerId,
                targetTitle = title,
                text = clean,
                scheduledAtMillis = scheduledAtMillis
            )
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
                myDisplayName = "ToKerUser",
                forceItemId = item.id
            )
        }
    }

    fun clearSendError() {
        _sendError.value = null
    }

    override fun onCleared() {
        super.onCleared()
        typingResetJob?.cancel()
    }

    private fun decryptSingleMessage(
        msg: EncryptedMessage,
        currentPeerPubKey: String,
        keys: IdentityKeyBundle?
    ): DecryptedMessageUi {
        if (keys == null) {
            return DecryptedMessageUi(
                raw = msg,
                plaintext = "Decrypting...",
                decryptedSuccessfully = false,
                payload = RichMessagePayload(type = RichMessageType.TEXT.name, text = "Decrypting...")
            )
        }
        val peerKeyToUse = if (msg.senderId == peerId && msg.senderPublicKey.isNotBlank()) {
            msg.senderPublicKey
        } else {
            currentPeerPubKey
        }
        if (peerKeyToUse.isBlank()) {
            return DecryptedMessageUi(
                raw = msg,
                plaintext = "[Peer public key unavailable]",
                decryptedSuccessfully = false,
                payload = RichMessagePayload(type = RichMessageType.TEXT.name, text = "[Peer public key unavailable]")
            )
        }
        val result = cryptoEngine.decryptMessage(
            ciphertextBase64 = msg.ciphertext,
            ivBase64 = msg.iv,
            myPrivateKey = keys.keyPair.private,
            peerPublicKeyBase64 = peerKeyToUse,
            conversationId = conversationId,
            senderId = msg.senderId
        )
        return result.fold(
            onSuccess = { decryptedText ->
                val decodedPayload = RichMessageCodec.decode(decryptedText, msg.messageType)
                DecryptedMessageUi(
                    raw = msg,
                    plaintext = RichMessageCodec.previewLabel(decodedPayload),
                    decryptedSuccessfully = true,
                    payload = decodedPayload
                )
            },
            onFailure = {
                DecryptedMessageUi(
                    raw = msg,
                    plaintext = "[Encrypted with rotated key]",
                    decryptedSuccessfully = false,
                    payload = RichMessagePayload(type = RichMessageType.TEXT.name, text = "[Encrypted with rotated key]")
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
        const val TAG = "ChatVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
