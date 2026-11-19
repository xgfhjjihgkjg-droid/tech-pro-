package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class PresenceStatus(val label: String) {
    ONLINE("Online"),
    AWAY("Away"),
    BUSY("Busy"),
    OFFLINE("Offline");

    companion object {
        fun fromRaw(raw: String): PresenceStatus =
            entries.firstOrNull { it.name == raw } ?: OFFLINE
    }
}

enum class PrivacyAudience(val label: String) {
    EVERYONE("Everyone"),
    CONTACTS("My Contacts"),
    NOBODY("Nobody");

    companion object {
        fun fromRaw(raw: String?): PrivacyAudience =
            entries.firstOrNull { it.name == raw } ?: EVERYONE
    }
}

enum class CallHistoryFilter(val label: String) {
    ALL("All"),
    MISSED("Missed"),
    AUDIO("Audio"),
    VIDEO("Video")
}

enum class CallType {
    AUDIO,
    VIDEO;

    companion object {
        fun fromRaw(raw: String): CallType =
            entries.firstOrNull { it.name == raw } ?: AUDIO
    }
}

enum class CallStatus {
    RINGING,
    ACCEPTED,
    CONNECTED,
    DECLINED,
    ENDED,
    MISSED;

    val isTerminal: Boolean
        get() = this == DECLINED || this == ENDED || this == MISSED

    companion object {
        fun fromRaw(raw: String): CallStatus =
            entries.firstOrNull { it.name == raw } ?: ENDED
    }
}

@Keep
@IgnoreExtraProperties
data class DirectoryUser(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val handle: String = "",
    val photoUrl: String? = null,
    val avatarBase64: String? = null,
    val publicKey: String = "",
    val keyFingerprint: String = "",
    val presence: String = PresenceStatus.OFFLINE.name,
    val statusMessage: String = "",
    val statusMood: String = "Available",
    val discoverable: Boolean = true,
    val lastSeenVisibility: String = PrivacyAudience.EVERYONE.name,
    val photoVisibility: String = PrivacyAudience.EVERYONE.name,
    val statusVisibility: String = PrivacyAudience.EVERYONE.name,
    @ServerTimestamp val statusUpdatedAt: Timestamp? = null,
    @ServerTimestamp val lastSeenAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val presenceEnum: PresenceStatus
        get() = PresenceStatus.fromRaw(presence)

    val lastSeenVisibilityEnum: PrivacyAudience
        get() = PrivacyAudience.fromRaw(lastSeenVisibility)

    val photoVisibilityEnum: PrivacyAudience
        get() = PrivacyAudience.fromRaw(photoVisibility)

    val statusVisibilityEnum: PrivacyAudience
        get() = PrivacyAudience.fromRaw(statusVisibility)
}

@Keep
@IgnoreExtraProperties
data class PrivateAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val phoneNumber: String? = null,
    val verifiedPeerIds: List<String> = emptyList(),
    val savedContactIds: List<String> = emptyList(),
    val blockedUserIds: List<String> = emptyList(),
    val readReceiptsEnabled: Boolean = true,
    val lastSeenVisibility: String = PrivacyAudience.EVERYONE.name,
    val photoVisibility: String = PrivacyAudience.EVERYONE.name,
    val statusVisibility: String = PrivacyAudience.EVERYONE.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val lastSeenVisibilityEnum: PrivacyAudience
        get() = PrivacyAudience.fromRaw(lastSeenVisibility)

    val photoVisibilityEnum: PrivacyAudience
        get() = PrivacyAudience.fromRaw(photoVisibility)

    val statusVisibilityEnum: PrivacyAudience
        get() = PrivacyAudience.fromRaw(statusVisibility)
}

@Keep
@IgnoreExtraProperties
data class StatusUpdate(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String? = null,
    val mood: String = "Available",
    val text: String = "",
    val visibility: String = "PUBLIC",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val participantIds: List<String> = emptyList(),
    val peerAId: String = "",
    val peerAName: String = "",
    val peerAPhotoUrl: String? = null,
    val peerBId: String = "",
    val peerBName: String = "",
    val peerBPhotoUrl: String? = null,
    val nicknames: Map<String, String> = emptyMap(),
    val pinnedMessageId: String? = null,
    val pinnedBy: String? = null,
    val disappearingSeconds: Long = 0L,
    val lastMessageCiphertext: String = "",
    val lastMessageIv: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageSenderPublicKey: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun peerIdFor(myUid: String): String =
        if (peerAId == myUid) peerBId else peerAId

    fun peerNameFor(myUid: String): String =
        if (peerAId == myUid) peerBName else peerBName

    fun peerPhotoFor(myUid: String): String? =
        if (peerAId == myUid) peerAPhotoUrl else peerBPhotoUrl

    fun customTitleFor(myUid: String): String? =
        nicknames[myUid]?.trim()?.takeIf { it.isNotBlank() }
}

@Keep
@IgnoreExtraProperties
data class EncryptedMessage(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val participantIds: List<String> = emptyList(),
    val senderId: String = "",
    val senderPublicKey: String = "",
    val ciphertext: String = "",
    val iv: String = "",
    val encryptionAlg: String = "ECDH-P256-HKDF-AES256GCM",
    val messageType: String = "TEXT",
    val readBy: List<String> = emptyList(),
    val pollVotes: Map<String, Long> = emptyMap(),
    val reactions: Map<String, String> = emptyMap(),
    val edited: Boolean = false,
    val expiresAtMillis: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class DecryptedMessageUi(
    val raw: EncryptedMessage,
    val plaintext: String,
    val decryptedSuccessfully: Boolean,
    val payload: RichMessagePayload = RichMessageCodec.decode(plaintext, raw.messageType)
)

data class ConversationListItemUi(
    val conversation: Conversation,
    val peerId: String,
    val peerName: String,
    val customNickname: String? = null,
    val peerPhotoUrl: String?,
    val peerAvatarBase64: String? = null,
    val peerPresence: PresenceStatus,
    val peerLastSeenAt: Timestamp? = null,
    val peerLastSeenVisibility: PrivacyAudience = PrivacyAudience.EVERYONE,
    val peerStatusMessage: String,
    val peerPublicKey: String,
    val lastMessagePreview: String,
    val isLastMessageEncrypted: Boolean,
    val isLastMessageMine: Boolean = false,
    val isLastMessageReadByPeer: Boolean = false
)

@Keep
@IgnoreExtraProperties
data class TypingState(
    @DocumentId val id: String = "",
    val userId: String = "",
    val participantIds: List<String> = emptyList(),
    val typing: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val callerPhotoUrl: String? = null,
    val calleeId: String = "",
    val calleeName: String = "",
    val calleePhotoUrl: String? = null,
    val participantIds: List<String> = emptyList(),
    val callType: String = CallType.AUDIO.name,
    val status: String = CallStatus.RINGING.name,
    val offerSdp: String = "",
    val answerSdp: String? = null,
    val e2eeFingerprint: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val callTypeEnum: CallType
        get() = CallType.fromRaw(callType)

    val statusEnum: CallStatus
        get() = CallStatus.fromRaw(status)

    fun peerNameFor(myUid: String): String =
        if (callerId == myUid) calleeName else callerName

    fun peerIdFor(myUid: String): String =
        if (callerId == myUid) calleeId else callerId

    fun peerPhotoFor(myUid: String): String? =
        if (callerId == myUid) calleePhotoUrl else callerPhotoUrl
}

@Keep
@IgnoreExtraProperties
data class IceCandidateModel(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val participantIds: List<String> = emptyList(),
    val sdpMid: String = "",
    val sdpMLineIndex: Long = 0L,
    val candidate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class StoryType {
    TEXT,
    PHOTO,
    VIDEO;

    companion object {
        fun fromRaw(raw: String): StoryType =
            entries.firstOrNull { it.name == raw } ?: TEXT
    }
}

enum class StoryGradient(val label: String) {
    EMERALD("Emerald"),
    OCEAN("Ocean"),
    SUNSET("Sunset"),
    VIOLET("Violet"),
    OBSIDIAN("Obsidian");

    companion object {
        fun fromRaw(raw: String): StoryGradient =
            entries.firstOrNull { it.name == raw } ?: EMERALD
    }
}

@Keep
@IgnoreExtraProperties
data class LiveStory(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String? = null,
    val storyType: String = StoryType.TEXT.name,
    val caption: String = "",
    val gradientId: String = StoryGradient.EMERALD.name,
    val imageBase64: String? = null,
    val videoBase64: String? = null,
    val mentionedUserIds: List<String> = emptyList(),
    val mentionedHandles: List<String> = emptyList(),
    val visibility: String = "PUBLIC",
    val viewedBy: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val storyTypeEnum: StoryType
        get() = StoryType.fromRaw(storyType)

    val gradientEnum: StoryGradient
        get() = StoryGradient.fromRaw(gradientId)
}

data class AuthorStoriesGroup(
    val authorId: String,
    val authorName: String,
    val authorPhotoUrl: String?,
    val authorAvatarBase64: String? = null,
    val stories: List<LiveStory>,
    val hasUnviewed: Boolean
)

@Keep
@IgnoreExtraProperties
data class GroupChat(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val avatarBase64: String? = null,
    val createdBy: String = "",
    val adminIds: List<String> = emptyList(),
    val memberIds: List<String> = emptyList(),
    val memberJoinedAt: Map<String, Long> = emptyMap(),
    val onlyAdminsCanMessage: Boolean = false,
    val disappearingSeconds: Long = 0L,
    val pinnedMessageId: String? = null,
    val pinnedBy: String? = null,
    val lastMessageCiphertext: String = "",
    val lastMessageIv: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageSenderName: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isAdmin(uid: String): Boolean = uid == createdBy || uid in adminIds
}

@Keep
@IgnoreExtraProperties
data class GroupMemberKey(
    @DocumentId val id: String = "",
    val memberId: String = "",
    val participantIds: List<String> = emptyList(),
    val granterId: String = "",
    val granterPublicKey: String = "",
    val wrappedKeyCiphertext: String = "",
    val wrappedKeyIv: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GroupMessage(
    @DocumentId val id: String = "",
    val groupId: String = "",
    val participantIds: List<String> = emptyList(),
    val senderId: String = "",
    val senderName: String = "",
    val ciphertext: String = "",
    val iv: String = "",
    val encryptionAlg: String = "GROUP-AES256GCM",
    val messageType: String = "TEXT",
    val readBy: List<String> = emptyList(),
    val pollVotes: Map<String, Long> = emptyMap(),
    val reactions: Map<String, String> = emptyMap(),
    val edited: Boolean = false,
    val expiresAtMillis: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class DecryptedGroupMessageUi(
    val raw: GroupMessage,
    val plaintext: String,
    val decryptedSuccessfully: Boolean,
    val payload: RichMessagePayload = RichMessageCodec.decode(plaintext, raw.messageType)
)

data class GroupListItemUi(
    val group: GroupChat,
    val memberCount: Int,
    val lastMessagePreview: String
)
