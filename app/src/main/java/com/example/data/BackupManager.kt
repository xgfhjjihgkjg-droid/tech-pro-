package com.example.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupPrivacySnapshot(
    val discoverable: Boolean = true,
    val readReceiptsEnabled: Boolean = true,
    val lastSeenVisibility: String = PrivacyAudience.EVERYONE.name,
    val photoVisibility: String = PrivacyAudience.EVERYONE.name,
    val statusVisibility: String = PrivacyAudience.EVERYONE.name
)

@Serializable
data class BackupConversationSummary(
    val conversationId: String,
    val peerId: String,
    val peerName: String,
    val lastPreview: String
)

@Serializable
data class BackupGroupSummary(
    val groupId: String,
    val name: String,
    val memberCount: Int
)

@Serializable
data class ToKerBackupBundle(
    val app: String = "ToKer",
    val version: Int = 1,
    val exportedAtMillis: Long,
    val uid: String,
    val displayName: String,
    val handle: String,
    val statusMessage: String,
    val statusMood: String,
    val privacy: BackupPrivacySnapshot,
    val savedContactIds: List<String> = emptyList(),
    val blockedUserIds: List<String> = emptyList(),
    val verifiedPeerIds: List<String> = emptyList(),
    val conversations: List<BackupConversationSummary> = emptyList(),
    val groups: List<BackupGroupSummary> = emptyList(),
    val scheduledMessages: List<ScheduledMessageItem> = emptyList()
)

object BackupManager {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun buildBackupJson(
        profile: DirectoryUser,
        privateAccount: PrivateAccount?,
        conversations: List<ConversationListItemUi>,
        groups: List<GroupListItemUi>,
        scheduledMessages: List<ScheduledMessageItem>
    ): String {
        val bundle = ToKerBackupBundle(
            exportedAtMillis = System.currentTimeMillis(),
            uid = profile.uid,
            displayName = profile.displayName,
            handle = profile.handle,
            statusMessage = profile.statusMessage,
            statusMood = profile.statusMood,
            privacy = BackupPrivacySnapshot(
                discoverable = profile.discoverable,
                readReceiptsEnabled = privateAccount?.readReceiptsEnabled ?: true,
                lastSeenVisibility = profile.lastSeenVisibility,
                photoVisibility = profile.photoVisibility,
                statusVisibility = profile.statusVisibility
            ),
            savedContactIds = privateAccount?.savedContactIds ?: emptyList(),
            blockedUserIds = privateAccount?.blockedUserIds ?: emptyList(),
            verifiedPeerIds = privateAccount?.verifiedPeerIds ?: emptyList(),
            conversations = conversations.take(100).map {
                BackupConversationSummary(
                    conversationId = it.conversation.id,
                    peerId = it.peerId,
                    peerName = it.peerName,
                    lastPreview = it.lastMessagePreview.take(100)
                )
            },
            groups = groups.take(50).map {
                BackupGroupSummary(
                    groupId = it.group.id,
                    name = it.group.name,
                    memberCount = it.memberCount
                )
            },
            scheduledMessages = scheduledMessages.take(100)
        )
        return json.encodeToString(bundle)
    }

    fun parseBackupJson(rawJson: String): Result<ToKerBackupBundle> =
        runCatching {
            val parsed = json.decodeFromString<ToKerBackupBundle>(rawJson)
            require(parsed.uid.isNotBlank()) { "Invalid ToKer backup file" }
            parsed
        }
}
