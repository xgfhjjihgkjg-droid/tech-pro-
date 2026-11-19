package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BiometricAuthHelper
import com.example.data.ConversationListItemUi
import com.example.data.DirectoryUser
import com.example.data.GroupListItemUi
import com.example.data.LocalAppStrings
import com.example.data.PrivacyAudience
import com.example.data.PrivateAccount
import com.example.data.ScheduledMessageItem
import com.example.data.ScheduledMessageStatus
import com.example.data.SecuritySettings
import com.example.ui.components.UserAvatarWithPresence
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactSheet(
    myUid: String,
    myHandle: String,
    savedContactIds: List<String>,
    directoryUsers: List<DirectoryUser>,
    onSearchAndAdd: (String) -> Unit,
    onToggleSaved: (String) -> Unit,
    onStartChat: (DirectoryUser) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    var query by remember { mutableStateOf("") }
    val savedUsers = remember(directoryUsers, savedContactIds) {
        directoryUsers.filter { it.uid != myUid && it.uid in savedContactIds }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(strings.tabContacts, style = MaterialTheme.typography.titleLarge)
            }

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.handleLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(myHandle.ifBlank { "@toker_user" }, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "UID: $myUid",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("ToKer", "$myHandle ($myUid)"))
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = strings.copyLabel)
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { if (it.length <= 64) query = it },
                label = { Text(strings.handleLabel) },
                placeholder = { Text("@alex") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_contact_query_input")
            )

            Button(
                onClick = {
                    if (query.isNotBlank()) {
                        onSearchAndAdd(query)
                        query = ""
                    }
                },
                enabled = query.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("add_contact_submit_button")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.addLabel)
            }

            if (savedUsers.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Text(
                    text = "${strings.tabContacts} (${savedUsers.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                savedUsers.forEach { user ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onDismiss()
                                onStartChat(user)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatarWithPresence(
                                name = user.displayName,
                                photoUrl = user.photoUrl,
                                avatarBase64 = user.avatarBase64,
                                presence = user.presenceEnum,
                                size = 40.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(user.handle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onToggleSaved(user.uid) }) {
                                Icon(Icons.Default.Star, contentDescription = strings.removeLabel, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleMessageSheet(
    scheduledItems: List<ScheduledMessageItem>,
    targetTitle: String?,
    initialMessage: String = "",
    onSchedule: ((text: String, scheduledAtMillis: Long) -> Unit)?,
    onSendNow: (ScheduledMessageItem) -> Unit,
    onReschedule: (id: String, newMillis: Long) -> Unit,
    onCancel: (id: String) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    var messageDraft by remember(initialMessage) { mutableStateOf(initialMessage) }
    var selectedTimestamp by remember {
        mutableLongStateOf(System.currentTimeMillis() + 60_000L * 5L)
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("EEE, MMM d • HH:mm:ss", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.ScheduleSend, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(strings.scheduleLabel, style = MaterialTheme.typography.titleLarge)
                    if (targetTitle != null) {
                        Text(
                            text = targetTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (onSchedule != null) {
                OutlinedTextField(
                    value = messageDraft,
                    onValueChange = { if (it.length <= 2000) messageDraft = it },
                    label = { Text(strings.messageInputHint) },
                    placeholder = { Text(strings.messageInputHint) },
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("schedule_message_text_input")
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "+1m" to 60_000L,
                        "+5m" to 300_000L,
                        "+30m" to 1_800_000L,
                        "+1h" to 3_600_000L
                    ).forEach { (label, offset) ->
                        FilterChip(
                            selected = false,
                            onClick = { selectedTimestamp = System.currentTimeMillis() + offset },
                            label = { Text(label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("schedule_pick_date_button")
                    ) {
                        Text(dateFmt.format(Date(selectedTimestamp)).substringBefore("•").trim())
                    }
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("schedule_pick_time_button")
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(dateFmt.format(Date(selectedTimestamp)).substringAfter("•").trim())
                    }
                }

                Button(
                    onClick = {
                        val targetTs = selectedTimestamp.coerceAtLeast(System.currentTimeMillis() + 5_000L)
                        onSchedule(messageDraft.trim(), targetTs)
                        messageDraft = ""
                    },
                    enabled = messageDraft.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("confirm_schedule_message_button")
                ) {
                    Icon(Icons.Default.ScheduleSend, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.scheduleLabel)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            Text(
                text = "${strings.scheduleLabel} (${scheduledItems.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (scheduledItems.isEmpty()) {
                Text(
                    text = strings.disappearingMessagesOff,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                scheduledItems.forEach { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${item.targetTitle} • ${item.statusEnum.name}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = when (item.statusEnum) {
                                        ScheduledMessageStatus.PENDING -> MaterialTheme.colorScheme.primary
                                        ScheduledMessageStatus.SENT -> MaterialTheme.colorScheme.secondary
                                        ScheduledMessageStatus.FAILED -> MaterialTheme.colorScheme.error
                                        ScheduledMessageStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Text(
                                    text = dateFmt.format(Date(item.scheduledAtMillis)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = item.text,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!item.errorMessage.isNullOrBlank()) {
                                Text(
                                    text = item.errorMessage,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (item.statusEnum == ScheduledMessageStatus.PENDING ||
                                    item.statusEnum == ScheduledMessageStatus.FAILED
                                ) {
                                    TextButton(onClick = { onSendNow(item) }) {
                                        Text(strings.sendButtonLabel)
                                    }
                                    TextButton(
                                        onClick = { onReschedule(item.id, System.currentTimeMillis() + 60_000L) }
                                    ) {
                                        Text("+1m")
                                    }
                                }
                                TextButton(onClick = { onCancel(item.id) }) {
                                    Text(strings.removeLabel, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedTimestamp)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pickedUtcMillis = datePickerState.selectedDateMillis
                        if (pickedUtcMillis != null) {
                            val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = pickedUtcMillis
                            }
                            val localCal = Calendar.getInstance().apply {
                                timeInMillis = selectedTimestamp
                                set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                                set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                                set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                            }
                            selectedTimestamp = localCal.timeInMillis
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(strings.confirmLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(strings.cancelLabel) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val initialCal = remember(selectedTimestamp) {
            Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
        }
        val timePickerState = rememberTimePickerState(
            initialHour = initialCal.get(Calendar.HOUR_OF_DAY),
            initialMinute = initialCal.get(Calendar.MINUTE),
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(strings.scheduleLabel) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updated = Calendar.getInstance().apply {
                            timeInMillis = selectedTimestamp
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                            set(Calendar.SECOND, 0)
                        }
                        selectedTimestamp = updated.timeInMillis
                        showTimePicker = false
                    }
                ) {
                    Text(strings.confirmLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(strings.cancelLabel) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardMessageSheet(
    conversations: List<ConversationListItemUi>,
    groups: List<GroupListItemUi>,
    previewLabel: String,
    onForwardToConv: (ConversationListItemUi) -> Unit,
    onForwardToGroup: (GroupListItemUi) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(strings.forwardLabel, style = MaterialTheme.typography.titleLarge)
            if (previewLabel.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = previewLabel,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            if (conversations.isEmpty() && groups.isEmpty()) {
                Text(
                    text = strings.noChatsYet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                if (conversations.isNotEmpty()) {
                    Text(strings.tabChats, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    conversations.forEach { conv ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onForwardToConv(conv)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatarWithPresence(
                                    name = conv.peerName,
                                    photoUrl = conv.peerPhotoUrl,
                                    avatarBase64 = conv.peerAvatarBase64,
                                    presence = conv.peerPresence,
                                    size = 38.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(conv.peerName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.forwardLabel, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                if (groups.isNotEmpty()) {
                    Text(strings.tabGroups, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    groups.forEach { group ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onForwardToGroup(group)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(group.group.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.forwardLabel, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyAndSecuritySheet(
    myProfile: DirectoryUser?,
    privateAccount: PrivateAccount?,
    securitySettings: SecuritySettings,
    hasOsNotificationPermission: Boolean,
    blockedUsers: List<DirectoryUser>,
    onSavePrivacy: (
        discoverable: Boolean,
        readReceipts: Boolean,
        lastSeenVisibility: PrivacyAudience,
        photoVisibility: PrivacyAudience,
        statusVisibility: PrivacyAudience
    ) -> Unit,
    onRequestSetPin: () -> Unit,
    onToggleAppLock: (Boolean) -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onOpenOsNotificationSettings: () -> Unit,
    onUnblockUser: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val biometricSupported = remember(context) { BiometricAuthHelper.canAuthenticate(context) }

    var discoverable by remember(myProfile) { mutableStateOf(myProfile?.discoverable ?: true) }
    var readReceipts by remember(privateAccount) { mutableStateOf(privateAccount?.readReceiptsEnabled ?: true) }
    var lastSeenVis by remember(myProfile) {
        mutableStateOf(myProfile?.lastSeenVisibilityEnum ?: PrivacyAudience.EVERYONE)
    }
    var photoVis by remember(myProfile) {
        mutableStateOf(myProfile?.photoVisibilityEnum ?: PrivacyAudience.EVERYONE)
    }
    var statusVis by remember(myProfile) {
        mutableStateOf(myProfile?.statusVisibilityEnum ?: PrivacyAudience.EVERYONE)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(strings.privacyBackupLabel, style = MaterialTheme.typography.titleLarge)
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.lockAppLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = strings.lockAppLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = securitySettings.appLockEnabled,
                            onCheckedChange = { wantEnable ->
                                if (wantEnable && !securitySettings.hasPinConfigured) {
                                    onRequestSetPin()
                                } else {
                                    onToggleAppLock(wantEnable)
                                }
                            },
                            modifier = Modifier.testTag("toggle_app_lock_switch")
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = strings.biometricLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = securitySettings.biometricEnabled,
                            enabled = biometricSupported,
                            onCheckedChange = onToggleBiometric,
                            modifier = Modifier.testTag("toggle_biometric_lock_switch")
                        )
                    }
                    OutlinedButton(
                        onClick = onRequestSetPin,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("configure_app_pin_button")
                    ) {
                        Icon(Icons.Default.Pin, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.pinCodeLabel)
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.notificationsLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = strings.notificationsLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = securitySettings.notificationsEnabled && hasOsNotificationPermission,
                            onCheckedChange = onToggleNotifications,
                            modifier = Modifier.testTag("toggle_notifications_switch")
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenOsNotificationSettings,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("request_notification_permission_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.settingsTitle)
                    }
                }
            }

            AudienceSelectorRow(
                label = strings.lastSeenLabel,
                selected = lastSeenVis,
                onSelect = { lastSeenVis = it }
            )
            AudienceSelectorRow(
                label = strings.viewProfilePhoto,
                selected = photoVis,
                onSelect = { photoVis = it }
            )
            AudienceSelectorRow(
                label = strings.tabStories,
                selected = statusVis,
                onSelect = { statusVis = it }
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = strings.receiptsLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = readReceipts,
                    onCheckedChange = { readReceipts = it },
                    modifier = Modifier.testTag("privacy_read_receipts_switch")
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = strings.discoverableLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = discoverable,
                    onCheckedChange = { discoverable = it },
                    modifier = Modifier.testTag("privacy_discoverable_switch")
                )
            }

            Button(
                onClick = {
                    onSavePrivacy(discoverable, readReceipts, lastSeenVis, photoVis, statusVis)
                    onDismiss()
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_privacy_controls_button")
            ) {
                Text(strings.saveLabel)
            }

            if (blockedUsers.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Text("${strings.blockedLabel} (${blockedUsers.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                blockedUsers.forEach { user ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Text(user.displayName, style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = { onUnblockUser(user.uid) }) {
                            Text(strings.unblockLabel)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AudienceSelectorRow(
    label: String,
    selected: PrivacyAudience,
    onSelect: (PrivacyAudience) -> Unit
) {
    val strings = LocalAppStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrivacyAudience.entries.forEach { audience ->
                val localized = when (audience) {
                    PrivacyAudience.EVERYONE -> strings.everyoneLabel
                    PrivacyAudience.CONTACTS -> strings.tabContacts
                    PrivacyAudience.NOBODY -> strings.nobodyLabel
                }
                FilterChip(
                    selected = selected == audience,
                    onClick = { onSelect(audience) },
                    label = { Text(localized) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataBackupSheet(
    savedContactsCount: Int,
    scheduledCount: Int,
    conversationsCount: Int,
    onExportLocalJson: () -> Unit,
    onImportLocalJson: () -> Unit,
    onCloudBackup: () -> Unit,
    onCloudRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(strings.backupLabel, style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = "${strings.tabChats}: $conversationsCount • ${strings.tabContacts}: $savedContactsCount • ${strings.scheduleLabel}: $scheduledCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.cloudLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = onCloudBackup,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("cloud_backup_now_button")
                    ) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.backupLabel)
                    }
                    OutlinedButton(
                        onClick = onCloudRestore,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("cloud_restore_button")
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.restoreLabel)
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.localLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = onExportLocalJson,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("export_local_backup_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.exportLabel)
                    }
                    OutlinedButton(
                        onClick = onImportLocalJson,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("import_local_backup_button")
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.importLabel)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun JoinCallLinkDialog(
    onJoinLink: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    var linkInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Link, contentDescription = null) },
        title = { Text(strings.joinLabel) },
        text = {
            OutlinedTextField(
                value = linkInput,
                onValueChange = { linkInput = it },
                label = { Text(strings.linkLabel) },
                placeholder = { Text("toker://call?host=...") },
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("join_call_link_input")
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onJoinLink(linkInput)
                    onDismiss()
                },
                enabled = linkInput.isNotBlank(),
                modifier = Modifier.testTag("confirm_join_call_link_button")
            ) {
                Text(strings.joinLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelLabel)
            }
        }
    )
}
