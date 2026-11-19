package com.example.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.crypto.CryptoEngine
import com.example.notifications.ToKerNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.scheduledMessagesStore: DataStore<Preferences> by preferencesDataStore(name = "toker_scheduled_messages")

enum class ScheduledMessageStatus {
    PENDING,
    SENT,
    FAILED,
    CANCELLED;

    companion object {
        fun fromRaw(raw: String): ScheduledMessageStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

@Serializable
data class ScheduledMessageItem(
    val id: String,
    val ownerUid: String,
    val targetType: String, // "CONVERSATION" or "GROUP"
    val targetId: String,
    val peerId: String = "",
    val targetTitle: String,
    val text: String,
    val scheduledAtMillis: Long,
    val createdAtMillis: Long,
    val status: String = ScheduledMessageStatus.PENDING.name,
    val errorMessage: String? = null
) {
    val statusEnum: ScheduledMessageStatus
        get() = ScheduledMessageStatus.fromRaw(status)
}

object ScheduledMessageSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeList(items: List<ScheduledMessageItem>): String =
        json.encodeToString(items)

    fun decodeList(raw: String?): List<ScheduledMessageItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<ScheduledMessageItem>>(raw) }
            .getOrDefault(emptyList())
    }
}

class ScheduledMessageManager(context: Context) {
    private val appContext = context.applicationContext

    private object Keys {
        val QUEUE_JSON = stringPreferencesKey("scheduled_queue_json")
    }

    val scheduledMessages: Flow<List<ScheduledMessageItem>> =
        appContext.scheduledMessagesStore.data.map { prefs ->
            ScheduledMessageSerializer.decodeList(prefs[Keys.QUEUE_JSON])
                .sortedBy { it.scheduledAtMillis }
        }

    suspend fun getAll(): List<ScheduledMessageItem> = scheduledMessages.first()

    suspend fun scheduleMessage(
        ownerUid: String,
        targetType: String,
        targetId: String,
        peerId: String,
        targetTitle: String,
        text: String,
        scheduledAtMillis: Long
    ): ScheduledMessageItem {
        val item = ScheduledMessageItem(
            id = "sched_${UUID.randomUUID().toString().replace("-", "").take(14)}",
            ownerUid = ownerUid,
            targetType = targetType,
            targetId = targetId,
            peerId = peerId,
            targetTitle = targetTitle,
            text = text.trim().take(2000),
            scheduledAtMillis = scheduledAtMillis,
            createdAtMillis = System.currentTimeMillis(),
            status = ScheduledMessageStatus.PENDING.name
        )
        upsertItem(item)
        enqueueBackgroundTriggers(item)
        return item
    }

    suspend fun rescheduleMessage(id: String, newScheduledAtMillis: Long) {
        var updatedItem: ScheduledMessageItem? = null
        appContext.scheduledMessagesStore.edit { prefs ->
            val list = ScheduledMessageSerializer.decodeList(prefs[Keys.QUEUE_JSON]).toMutableList()
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) {
                val updated = list[idx].copy(
                    scheduledAtMillis = newScheduledAtMillis,
                    status = ScheduledMessageStatus.PENDING.name,
                    errorMessage = null
                )
                list[idx] = updated
                updatedItem = updated
                prefs[Keys.QUEUE_JSON] = ScheduledMessageSerializer.encodeList(list)
            }
        }
        updatedItem?.let { enqueueBackgroundTriggers(it) }
    }

    suspend fun cancelScheduledMessage(id: String) {
        appContext.scheduledMessagesStore.edit { prefs ->
            val list = ScheduledMessageSerializer.decodeList(prefs[Keys.QUEUE_JSON])
                .filterNot { it.id == id }
            prefs[Keys.QUEUE_JSON] = ScheduledMessageSerializer.encodeList(list)
        }
        cancelBackgroundTriggers(id)
    }

    suspend fun markStatus(id: String, status: ScheduledMessageStatus, error: String? = null) {
        appContext.scheduledMessagesStore.edit { prefs ->
            val list = ScheduledMessageSerializer.decodeList(prefs[Keys.QUEUE_JSON]).toMutableList()
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) {
                list[idx] = list[idx].copy(status = status.name, errorMessage = error)
                prefs[Keys.QUEUE_JSON] = ScheduledMessageSerializer.encodeList(list)
            }
        }
    }

    suspend fun importRestoredSchedule(items: List<ScheduledMessageItem>) {
        if (items.isEmpty()) return
        appContext.scheduledMessagesStore.edit { prefs ->
            val existing = ScheduledMessageSerializer.decodeList(prefs[Keys.QUEUE_JSON])
            val merged = (existing + items).distinctBy { it.id }
            prefs[Keys.QUEUE_JSON] = ScheduledMessageSerializer.encodeList(merged)
        }
        items.filter { it.statusEnum == ScheduledMessageStatus.PENDING }.forEach {
            enqueueBackgroundTriggers(it)
        }
    }

    private fun enqueueBackgroundTriggers(item: ScheduledMessageItem) {
        val delayMs = (item.scheduledAtMillis - System.currentTimeMillis()).coerceAtLeast(1000L)
        val request = OneTimeWorkRequestBuilder<ScheduledMessageWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("toker_sched_${item.id}")
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "toker_sched_${item.id}",
            ExistingWorkPolicy.REPLACE,
            request
        )

        runCatching {
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val intent = Intent(appContext, ScheduledMessageAlarmReceiver::class.java).apply {
                putExtra("scheduled_id", item.id)
            }
            val pi = PendingIntent.getBroadcast(
                appContext,
                item.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager?.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                item.scheduledAtMillis,
                pi
            )
        }
    }

    private fun cancelBackgroundTriggers(id: String) {
        WorkManager.getInstance(appContext).cancelUniqueWork("toker_sched_$id")
        runCatching {
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val intent = Intent(appContext, ScheduledMessageAlarmReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                appContext,
                id.hashCode(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                alarmManager?.cancel(pi)
            }
        }
    }

    private suspend fun upsertItem(item: ScheduledMessageItem) {
        appContext.scheduledMessagesStore.edit { prefs ->
            val list = ScheduledMessageSerializer.decodeList(prefs[Keys.QUEUE_JSON])
                .filterNot { it.id == item.id } + item
            prefs[Keys.QUEUE_JSON] = ScheduledMessageSerializer.encodeList(list.takeLast(100))
        }
    }

    suspend fun dispatchDueMessages(
        repository: ChatRepository,
        cryptoEngine: CryptoEngine,
        currentUserId: String,
        myDisplayName: String,
        forceItemId: String? = null
    ): Int = dispatchMutex.withLock {
        val now = System.currentTimeMillis()
        val all = getAll()
        val due = all.filter { item ->
            item.ownerUid == currentUserId &&
                item.statusEnum == ScheduledMessageStatus.PENDING &&
                (forceItemId == item.id || item.scheduledAtMillis <= now)
        }
        if (due.isEmpty()) return 0

        val keys = cryptoEngine.getOrCreateIdentityKeys(currentUserId)
        var sentCount = 0

        for (item in due) {
            val payload = RichMessagePayload(
                type = RichMessageType.TEXT.name,
                text = item.text
            )
            val encoded = RichMessageCodec.encode(payload)
            val preview = RichMessageCodec.previewLabel(payload)

            val result = if (item.targetType == "CONVERSATION") {
                val peerUser = repository.observeDirectoryUser(item.peerId).first()
                val peerPub = peerUser?.publicKey.orEmpty()
                if (peerPub.isBlank()) {
                    Result.failure(IllegalStateException("Peer public key unavailable"))
                } else {
                    val enc = cryptoEngine.encryptMessage(
                        plaintext = encoded,
                        myPrivateKey = keys.keyPair.private,
                        peerPublicKeyBase64 = peerPub,
                        conversationId = item.targetId,
                        senderId = currentUserId
                    )
                    val encPreview = if (enc.ciphertextBase64.length <= 4096) {
                        enc
                    } else {
                        cryptoEngine.encryptMessage(
                            plaintext = preview,
                            myPrivateKey = keys.keyPair.private,
                            peerPublicKeyBase64 = peerPub,
                            conversationId = item.targetId,
                            senderId = currentUserId
                        )
                    }
                    repository.sendEncryptedMessage(
                        conversationId = item.targetId,
                        participantIds = listOf(currentUserId, item.peerId).sorted(),
                        senderPublicKey = keys.publicKeyBase64,
                        ciphertextBase64 = enc.ciphertextBase64,
                        ivBase64 = enc.ivBase64,
                        messageType = RichMessageType.TEXT.name,
                        previewCiphertextBase64 = encPreview.ciphertextBase64,
                        previewIvBase64 = encPreview.ivBase64
                    )
                }
            } else {
                val group = repository.observeGroup(item.targetId).first()
                val wrapped = repository.observeMyGroupMemberKey(item.targetId, currentUserId).first()
                val secretKey = if (wrapped != null) {
                    cryptoEngine.decryptMessage(
                        ciphertextBase64 = wrapped.wrappedKeyCiphertext,
                        ivBase64 = wrapped.wrappedKeyIv,
                        myPrivateKey = keys.keyPair.private,
                        peerPublicKeyBase64 = wrapped.granterPublicKey,
                        conversationId = item.targetId,
                        senderId = wrapped.granterId
                    ).getOrNull()
                } else {
                    null
                }
                if (group == null || secretKey.isNullOrBlank()) {
                    Result.failure(IllegalStateException("Group E2EE key unavailable"))
                } else {
                    val enc = cryptoEngine.encryptWithGroupKey(
                        plaintext = encoded,
                        groupKeyBase64 = secretKey,
                        groupId = item.targetId,
                        senderId = currentUserId
                    )
                    val encPreview = if (enc.ciphertextBase64.length <= 4096) {
                        enc
                    } else {
                        cryptoEngine.encryptWithGroupKey(
                            plaintext = preview,
                            groupKeyBase64 = secretKey,
                            groupId = item.targetId,
                            senderId = currentUserId
                        )
                    }
                    repository.sendGroupMessage(
                        groupId = item.targetId,
                        participantIds = group.memberIds,
                        senderName = myDisplayName.ifBlank { "ToKerUser" },
                        ciphertextBase64 = enc.ciphertextBase64,
                        ivBase64 = enc.ivBase64,
                        messageType = RichMessageType.TEXT.name,
                        previewCiphertextBase64 = encPreview.ciphertextBase64,
                        previewIvBase64 = encPreview.ivBase64
                    )
                }
            }

            result.onSuccess {
                markStatus(item.id, ScheduledMessageStatus.SENT, null)
                cancelBackgroundTriggers(item.id)
                val notifyEnabled = runCatching {
                    SecurityPreferencesRepository(appContext).securitySettings.first().notificationsEnabled
                }.getOrDefault(true)
                if (notifyEnabled) {
                    ToKerNotificationHelper.notifyScheduledMessageSent(appContext, item)
                }
                sentCount++
            }.onFailure { err ->
                markStatus(item.id, ScheduledMessageStatus.FAILED, err.message ?: "Delivery failed")
            }
        }
        sentCount
    }

    private companion object {
        val dispatchMutex = Mutex()
    }
}

class ScheduledMessageWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val user = Firebase.auth.currentUser ?: return Result.success()
        val databaseId = applicationContext.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        val repo = ChatRepository(db)
        val crypto = CryptoEngine(applicationContext)
        val manager = ScheduledMessageManager(applicationContext)
        val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: "ToKerUser"

        return runCatching {
            manager.dispatchDueMessages(
                repository = repo,
                cryptoEngine = crypto,
                currentUserId = user.uid,
                myDisplayName = displayName
            )
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}

class ScheduledMessageAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val request = OneTimeWorkRequestBuilder<ScheduledMessageWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "toker_sched_alarm_dispatch",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
