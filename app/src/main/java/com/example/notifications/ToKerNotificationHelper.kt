package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.CallType
import com.example.data.ScheduledMessageItem

object ToKerNotificationHelper {
    const val CHANNEL_MESSAGES = "toker_messages"
    const val CHANNEL_CALLS = "toker_calls"
    const val CHANNEL_SCHEDULED = "toker_scheduled"

    const val EXTRA_NAV_TYPE = "toker_nav_type"
    const val NAV_TYPE_CHAT = "CHAT"
    const val NAV_TYPE_GROUP = "GROUP"
    const val NAV_TYPE_CALL = "CALL"

    const val EXTRA_CONVERSATION_ID = "toker_conversation_id"
    const val EXTRA_PEER_ID = "toker_peer_id"
    const val EXTRA_GROUP_ID = "toker_group_id"
    const val EXTRA_CALL_ID = "toker_call_id"
    const val EXTRA_CALL_TYPE = "toker_call_type"

    fun ensureNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val messagesChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            appContext.getString(R.string.notification_channel_messages),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = appContext.getString(R.string.notification_channel_messages_desc)
            enableVibration(true)
            setShowBadge(true)
        }

        val callsChannel = NotificationChannel(
            CHANNEL_CALLS,
            appContext.getString(R.string.notification_channel_calls),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = appContext.getString(R.string.notification_channel_calls_desc)
            enableVibration(true)
            setShowBadge(true)
        }

        val scheduledChannel = NotificationChannel(
            CHANNEL_SCHEDULED,
            appContext.getString(R.string.notification_channel_scheduled),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = appContext.getString(R.string.notification_channel_scheduled_desc)
        }

        manager.createNotificationChannels(
            listOf(messagesChannel, callsChannel, scheduledChannel)
        )
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun openAppNotificationSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun buildDirectMessageContentText(
        previewText: String,
        maskContent: Boolean
    ): String {
        if (maskContent) return "New end-to-end encrypted message"
        return previewText.trim().ifBlank { "New encrypted message" }
    }

    fun notifyIncomingDirectMessage(
        context: Context,
        conversationId: String,
        peerId: String,
        senderName: String,
        previewText: String,
        maskContent: Boolean,
        timestampMillis: Long = System.currentTimeMillis()
    ) {
        if (!hasNotificationPermission(context)) return
        ensureNotificationChannels(context)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAV_TYPE, NAV_TYPE_CHAT)
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
            putExtra(EXTRA_PEER_ID, peerId)
        }
        val pi = PendingIntent.getActivity(
            context,
            ("chat_$conversationId").hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = buildDirectMessageContentText(previewText, maskContent)
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(senderName.ifBlank { "ToKer Contact" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setWhen(timestampMillis)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(
                notificationIdForTarget("chat_$conversationId"),
                notification
            )
        }
    }

    fun notifyIncomingGroupMessage(
        context: Context,
        groupId: String,
        groupName: String,
        senderName: String,
        maskContent: Boolean,
        timestampMillis: Long = System.currentTimeMillis()
    ) {
        if (!hasNotificationPermission(context)) return
        ensureNotificationChannels(context)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAV_TYPE, NAV_TYPE_GROUP)
            putExtra(EXTRA_GROUP_ID, groupId)
        }
        val pi = PendingIntent.getActivity(
            context,
            ("grp_$groupId").hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = if (maskContent) {
            "New encrypted group message"
        } else {
            "${senderName.ifBlank { "Member" }}: Encrypted message"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(groupName.ifBlank { "ToKer Group" })
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setWhen(timestampMillis)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(
                notificationIdForTarget("grp_$groupId"),
                notification
            )
        }
    }

    fun notifyIncomingCall(
        context: Context,
        callId: String,
        callerId: String,
        callerName: String,
        callType: CallType
    ) {
        if (!hasNotificationPermission(context)) return
        ensureNotificationChannels(context)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAV_TYPE, NAV_TYPE_CALL)
            putExtra(EXTRA_PEER_ID, callerId)
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CALL_TYPE, callType.name)
        }
        val pi = PendingIntent.getActivity(
            context,
            ("call_$callId").hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val label = if (callType == CallType.VIDEO) "video call" else "audio call"
        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Incoming ToKer $label")
            .setContentText("${callerName.ifBlank { "Contact" }} • Tap to answer (E2EE)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(
                notificationIdForTarget("call_$callId"),
                notification
            )
        }
    }

    fun notifyScheduledMessageSent(
        context: Context,
        item: ScheduledMessageItem
    ) {
        if (!hasNotificationPermission(context)) return
        ensureNotificationChannels(context)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (item.targetType == "GROUP") {
                putExtra(EXTRA_NAV_TYPE, NAV_TYPE_GROUP)
                putExtra(EXTRA_GROUP_ID, item.targetId)
            } else {
                putExtra(EXTRA_NAV_TYPE, NAV_TYPE_CHAT)
                putExtra(EXTRA_CONVERSATION_ID, item.targetId)
                putExtra(EXTRA_PEER_ID, item.peerId)
            }
        }
        val pi = PendingIntent.getActivity(
            context,
            item.id.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SCHEDULED)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Scheduled message sent")
            .setContentText("Delivered to ${item.targetTitle}: ${item.text.take(80)}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(
                notificationIdForTarget("sched_${item.id}"),
                notification
            )
        }
    }

    fun cancelNotificationsForTarget(context: Context, key: String) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(notificationIdForTarget(key))
        }
    }

    private fun notificationIdForTarget(key: String): Int =
        (key.hashCode() and 0x7FFFFFFF).coerceAtLeast(100)
}
