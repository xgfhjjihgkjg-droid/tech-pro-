package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ConversationListItemUi
import com.example.data.DecryptedGroupMessageUi
import com.example.data.DirectoryUser
import com.example.data.GroupListItemUi
import com.example.data.LocalAppStrings
import com.example.data.PresenceStatus
import com.example.data.RichMessagePayload
import com.example.data.RichMessageType
import com.example.data.ThemeSettings
import com.example.data.UiState
import com.example.media.MediaUtils
import com.example.media.VoiceNoteRecorder
import com.example.ui.components.ChatWallpaperBackdrop
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.DisappearingTimerBottomSheet
import com.example.ui.components.EditingMessageBanner
import com.example.ui.components.EmojiPickerBottomSheet
import com.example.ui.components.EncryptedBadge
import com.example.ui.components.FullscreenImageDialog
import com.example.ui.components.FullscreenVideoDialog
import com.example.ui.components.LocationPickerBottomSheet
import com.example.ui.components.MessageActionBottomSheet
import com.example.ui.components.PinnedMessageBanner
import com.example.ui.components.PollCreatorBottomSheet
import com.example.ui.components.ProfileAvatarViewerDialog
import com.example.ui.components.ReplyingMessageBanner
import com.example.ui.components.RichAttachmentBar
import com.example.ui.components.RichMessageBubbleContent
import com.example.ui.components.ScrollToBottomFab
import com.example.ui.components.SwipeToReplyContainer
import com.example.ui.components.UserAvatarWithPresence
import com.example.ui.components.VoiceRecordingComposerBar
import com.example.ui.components.compressAvatarToBase64
import com.example.ui.components.formatDisappearingLabel
import com.example.ui.components.formatTimeOnly
import com.example.viewmodel.GroupChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    viewModel: GroupChatViewModel,
    currentUserId: String,
    themeSettings: ThemeSettings,
    isChatLocked: Boolean = false,
    onToggleChatLock: () -> Unit = {},
    conversationsForForward: List<ConversationListItemUi> = emptyList(),
    groupsForForward: List<GroupListItemUi> = emptyList(),
    onForwardToConversation: (ConversationListItemUi, RichMessagePayload) -> Unit = { _, _ -> },
    onForwardToGroup: (GroupListItemUi, RichMessagePayload) -> Unit = { _, _ -> },
    onOpenThemeSheet: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val group by viewModel.group.collectAsStateWithLifecycle()
    val directoryUsers by viewModel.directoryUsers.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val scheduledItems by viewModel.groupScheduledMessages.collectAsStateWithLifecycle()

    val strings = LocalAppStrings.current
    var draft by remember { mutableStateOf("") }
    var showGroupInfoSheet by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }
    var showDeleteGroupDialog by remember { mutableStateOf(false) }
    var showLeaveGroupDialog by remember { mutableStateOf(false) }
    var showDisappearingSheet by remember { mutableStateOf(false) }
    var previewAvatarProfile by remember { mutableStateOf<DirectoryUser?>(null) }
    var showGroupAvatarPreview by remember { mutableStateOf(false) }
    var showAttachments by remember { mutableStateOf(false) }
    var showComposerEmojiSheet by remember { mutableStateOf(false) }
    var showLocationSheet by remember { mutableStateOf(false) }
    var showPollSheet by remember { mutableStateOf(false) }
    var showScheduleSheet by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<DecryptedGroupMessageUi?>(null) }
    var replyingMessage by remember { mutableStateOf<DecryptedGroupMessageUi?>(null) }
    var forwardingPayload by remember { mutableStateOf<RichMessagePayload?>(null) }
    var actionTargetMessage by remember { mutableStateOf<DecryptedGroupMessageUi?>(null) }
    var deletingMessage by remember { mutableStateOf<DecryptedGroupMessageUi?>(null) }
    var reactionTargetMessage by remember { mutableStateOf<DecryptedGroupMessageUi?>(null) }
    var inspectedMessage by remember { mutableStateOf<DecryptedGroupMessageUi?>(null) }
    var fullscreenImageBase64 by remember { mutableStateOf<String?>(null) }
    var fullscreenVideo by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var isProcessingMedia by remember { mutableStateOf(false) }
    var memberPendingRemoval by remember { mutableStateOf<Pair<String, String>?>(null) }

    val voiceRecorder = remember { VoiceNoteRecorder() }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }

    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val dirMap = remember(directoryUsers) { directoryUsers.associateBy { it.uid } }
    val isGroupCreator = group?.createdBy == currentUserId
    val isGroupAdmin = isGroupCreator || (group?.adminIds?.contains(currentUserId) == true)
    val isComposerLockedForMember = (group?.onlyAdminsCanMessage == true) && !isGroupAdmin

    fun beginVoiceRecording() {
        showAttachments = false
        voiceRecorder.start(context)
            .onSuccess {
                recordingSeconds = 0
                isRecordingVoice = true
            }
            .onFailure { err ->
                scope.launch {
                    snackbarHostState.showSnackbar(err.message ?: "Could not start voice recording")
                }
            }
    }

    fun finishVoiceRecording() {
        isRecordingVoice = false
        isProcessingMedia = true
        scope.launch {
            voiceRecorder.stopAndEncode()
                .onSuccess { note ->
                    val currentReply = replyingMessage
                    replyingMessage = null
                    viewModel.sendRichGroupMessage(
                        RichMessagePayload(
                            type = RichMessageType.VOICE.name,
                            mediaBase64 = note.audioBase64,
                            durationMs = note.durationMs
                        ),
                        replyTo = currentReply
                    )
                }
                .onFailure { err ->
                    snackbarHostState.showSnackbar(err.message ?: "Failed to save voice message")
                }
            isProcessingMedia = false
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beginVoiceRecording()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Microphone permission is required for voice messages.")
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { voiceRecorder.cancel() }
    }

    LaunchedEffect(isRecordingVoice) {
        while (isActive && isRecordingVoice) {
            delay(1000L)
            recordingSeconds += 1
            if (recordingSeconds >= 118) {
                finishVoiceRecording()
                break
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isProcessingMedia = true
            scope.launch {
                val b64 = MediaUtils.compressImageToBase64(context, uri)
                isProcessingMedia = false
                if (b64 == null) {
                    snackbarHostState.showSnackbar("Could not process selected photo.")
                } else {
                    val caption = draft.trim()
                    val currentReply = replyingMessage
                    draft = ""
                    replyingMessage = null
                    viewModel.sendRichGroupMessage(
                        RichMessagePayload(
                            type = RichMessageType.IMAGE.name,
                            text = caption,
                            mediaBase64 = b64
                        ),
                        replyTo = currentReply
                    )
                }
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isProcessingMedia = true
            scope.launch {
                MediaUtils.processPickedVideoToBase64(context, uri, maxRawBytes = 320_000)
                    .onSuccess { encoded ->
                        val caption = draft.trim()
                        val currentReply = replyingMessage
                        draft = ""
                        replyingMessage = null
                        viewModel.sendRichGroupMessage(
                            RichMessagePayload(
                                type = RichMessageType.VIDEO.name,
                                text = caption,
                                mediaBase64 = encoded.videoBase64,
                                thumbBase64 = encoded.thumbBase64.takeIf { it.isNotBlank() },
                                durationMs = encoded.durationMs
                            ),
                            replyTo = currentReply
                        )
                    }
                    .onFailure { err ->
                        snackbarHostState.showSnackbar(err.message ?: "Failed to process video")
                    }
                isProcessingMedia = false
            }
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isProcessingMedia = true
            scope.launch {
                MediaUtils.readDocumentToBase64(context, uri)
                    .onSuccess { encoded ->
                        val caption = draft.trim()
                        val currentReply = replyingMessage
                        draft = ""
                        replyingMessage = null
                        viewModel.sendRichGroupMessage(
                            RichMessagePayload(
                                type = RichMessageType.DOCUMENT.name,
                                text = caption,
                                mediaBase64 = encoded.base64,
                                docName = encoded.fileName,
                                docMimeType = encoded.mimeType,
                                docSizeBytes = encoded.sizeBytes
                            ),
                            replyTo = currentReply
                        )
                    }
                    .onFailure { err ->
                        snackbarHostState.showSnackbar(err.message ?: "Failed to attach document")
                    }
                isProcessingMedia = false
            }
        }
    }

    LaunchedEffect(actionError, actionMessage) {
        val msg = actionError ?: actionMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    var previousMsgCount by remember { mutableIntStateOf(0) }
    val decryptedMessages = (messagesState as? UiState.Success<List<DecryptedGroupMessageUi>>)?.data ?: emptyList()
    val messageCount = decryptedMessages.size

    val isNearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total <= 1) return@derivedStateOf true
            val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            lastVisible.index >= total - 2 && (lastVisible.offset + lastVisible.size) <= info.viewportEndOffset + 96
        }
    }
    val showScrollToBottom by remember {
        derivedStateOf {
            messageCount > 0 && listState.canScrollForward && !isNearBottom
        }
    }

    LaunchedEffect(messageCount) {
        if (messageCount > 0) {
            val latestSender = decryptedMessages.lastOrNull()?.raw?.senderId
            if (previousMsgCount == 0) {
                listState.scrollToItem(messageCount)
            } else if (messageCount > previousMsgCount && (latestSender == currentUserId || isNearBottom)) {
                listState.animateScrollToItem(messageCount)
            }
            previousMsgCount = messageCount
        } else {
            previousMsgCount = 0
        }
    }

    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(imeVisible) {
        if (imeVisible && messageCount > 0 && isNearBottom) {
            listState.animateScrollToItem(messageCount)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("group_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_cd))
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showGroupInfoSheet = true }
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { showGroupAvatarPreview = true }
                                .testTag("group_header_avatar_button")
                        ) {
                            UserAvatarWithPresence(
                                name = group?.name ?: "Group",
                                photoUrl = null,
                                presence = PresenceStatus.ONLINE,
                                size = 40.dp,
                                showPresenceDot = false,
                                avatarBase64 = group?.avatarBase64
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = group?.name ?: "Group",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val groupSub = buildString {
                                append("${group?.memberIds?.size ?: 0} members")
                                if (group?.onlyAdminsCanMessage == true) append(" • Admins only")
                                val dSecs = group?.disappearingSeconds ?: 0L
                                if (dSecs > 0L) append(" • ⏱ ${formatDisappearingLabel(dSecs)}")
                            }
                            Text(
                                text = groupSub,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenThemeSheet,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("group_theme_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Customize chat theme",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { showGroupInfoSheet = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("group_info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = "Group info & members"
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { showGroupMenu = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("group_more_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Group options"
                            )
                        }
                        DropdownMenu(
                            expanded = showGroupMenu,
                            onDismissRequest = { showGroupMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Schedule Message") },
                                leadingIcon = { Icon(Icons.Default.ScheduleSend, contentDescription = null) },
                                onClick = {
                                    showGroupMenu = false
                                    showScheduleSheet = true
                                },
                                modifier = Modifier.testTag("group_menu_schedule")
                            )
                            if (isGroupAdmin) {
                                DropdownMenuItem(
                                    text = {
                                        val timerLabel = formatDisappearingLabel(group?.disappearingSeconds ?: 0L)
                                        Text("${strings.disappearingMessagesTitle} ($timerLabel)")
                                    },
                                    leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                                    onClick = {
                                        showGroupMenu = false
                                        showDisappearingSheet = true
                                    },
                                    modifier = Modifier.testTag("group_menu_disappearing")
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (isChatLocked) "Unlock Group Chat" else "Lock Group Chat") },
                                leadingIcon = {
                                    Icon(
                                        if (isChatLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showGroupMenu = false
                                    onToggleChatLock()
                                },
                                modifier = Modifier.testTag("group_menu_toggle_lock")
                            )
                            DropdownMenuItem(
                                text = { Text("Edit Group Info") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showGroupMenu = false
                                    showGroupInfoSheet = true
                                },
                                modifier = Modifier.testTag("group_menu_edit")
                            )
                            if (isGroupCreator) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.delete_group_label),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showGroupMenu = false
                                        showDeleteGroupDialog = true
                                    },
                                    modifier = Modifier.testTag("group_menu_delete")
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Leave Group") },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                                    },
                                    onClick = {
                                        showGroupMenu = false
                                        showLeaveGroupDialog = true
                                    },
                                    modifier = Modifier.testTag("group_menu_leave")
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    editingMessage?.let { currentEditing ->
                        EditingMessageBanner(
                            originalPreview = currentEditing.plaintext,
                            onCancelEdit = {
                                editingMessage = null
                                draft = ""
                            }
                        )
                    }

                    if (editingMessage == null) {
                        replyingMessage?.let { targetReply ->
                            val replyAuthor = if (targetReply.raw.senderId == currentUserId) "You" else targetReply.raw.senderName
                            ReplyingMessageBanner(
                                senderName = replyAuthor,
                                previewText = targetReply.plaintext,
                                onCancelReply = { replyingMessage = null }
                            )
                        }
                    }

                    AnimatedVisibility(visible = showAttachments && editingMessage == null) {
                        RichAttachmentBar(
                            onPickImage = {
                                showAttachments = false
                                imagePickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onPickVideo = {
                                showAttachments = false
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            onPickDocument = {
                                showAttachments = false
                                documentPickerLauncher.launch(arrayOf("*/*"))
                            },
                            onOpenLocation = {
                                showAttachments = false
                                showLocationSheet = true
                            },
                            onOpenPoll = {
                                showAttachments = false
                                showPollSheet = true
                            }
                        )
                    }

                    val allowsEmptyCaptionEdit = editingMessage?.payload?.typeEnum in listOf(
                        RichMessageType.IMAGE,
                        RichMessageType.VIDEO,
                        RichMessageType.DOCUMENT,
                        RichMessageType.STORY_MENTION
                    )
                    val canSubmit = (draft.isNotBlank() || allowsEmptyCaptionEdit) && !isSending && !isProcessingMedia
                    val showMicButton = draft.isBlank() && editingMessage == null && !allowsEmptyCaptionEdit && !isSending && !isProcessingMedia

                    if (isComposerLockedForMember) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                                .testTag("group_admin_only_banner"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.onlyAdminsCanMessageBanner,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else if (isRecordingVoice) {
                        VoiceRecordingComposerBar(
                            elapsedSeconds = recordingSeconds,
                            onCancel = {
                                voiceRecorder.cancel()
                                isRecordingVoice = false
                            },
                            onStopAndSend = { finishVoiceRecording() }
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (editingMessage != null) {
                                        editingMessage = null
                                        draft = ""
                                    } else {
                                        showAttachments = !showAttachments
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("group_attach_button")
                            ) {
                                Icon(
                                    imageVector = if (showAttachments || editingMessage != null) Icons.Default.Close else Icons.Default.AddCircleOutline,
                                    contentDescription = stringResource(R.string.attach_media_cd),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { showComposerEmojiSheet = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("group_emoji_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEmotions,
                                    contentDescription = stringResource(R.string.emoji_picker_cd),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                placeholder = {
                                    Text(
                                        if (editingMessage != null) "Edit message…"
                                        else strings.messageInputHint
                                    )
                                },
                                maxLines = 4,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("group_message_input")
                            )
                            IconButton(
                                onClick = { showScheduleSheet = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("group_schedule_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ScheduleSend,
                                    contentDescription = "Schedule auto-send group message",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (showMicButton) {
                                IconButton(
                                    onClick = {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            beginVoiceRecording()
                                        } else {
                                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .testTag("group_voice_record_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = stringResource(R.string.voice_message_cd),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        val currentEdit = editingMessage
                                        val currentReply = replyingMessage
                                        val textToSend = draft
                                        draft = ""
                                        if (currentEdit != null) {
                                            editingMessage = null
                                            viewModel.editGroupMessage(currentEdit, textToSend)
                                        } else {
                                            replyingMessage = null
                                            viewModel.sendGroupMessage(textToSend, replyTo = currentReply)
                                        }
                                    },
                                    enabled = canSubmit,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (canSubmit) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .testTag("group_send_button")
                                ) {
                                    if (isSending || isProcessingMedia) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (editingMessage != null) Icons.Default.Check else Icons.AutoMirrored.Filled.Send,
                                            contentDescription = if (editingMessage != null) "Save edited message" else stringResource(R.string.send_message_cd),
                                            tint = if (canSubmit) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            ChatWallpaperBackdrop(themeSettings = themeSettings)

            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
            ) {
                val successList = (messagesState as? UiState.Success<List<DecryptedGroupMessageUi>>)?.data ?: emptyList()
                val pinnedId = group?.pinnedMessageId
                val pinnedIndex = if (!pinnedId.isNullOrBlank()) {
                    successList.indexOfFirst { it.raw.id == pinnedId }
                } else -1
                if (pinnedIndex >= 0) {
                    val pinnedMsg = successList[pinnedIndex]
                    PinnedMessageBanner(
                        previewText = "${pinnedMsg.raw.senderName}: ${pinnedMsg.plaintext}",
                        onJumpToPinned = {
                            scope.launch { listState.animateScrollToItem(pinnedIndex + 1) }
                        },
                        onUnpin = { viewModel.pinGroupMessage(null) }
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (val state = messagesState) {
                        UiState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        is UiState.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(state.message, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        is UiState.Success -> {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        EncryptedBadge(label = stringResource(R.string.e2ee_badge_label))
                                    }
                                }

                                items(state.data, key = { it.raw.id }) { msgUi ->
                                    val isMine = msgUi.raw.senderId == currentUserId
                                    val senderDir = dirMap[msgUi.raw.senderId]
                                    SwipeToReplyContainer(
                                        messageKey = msgUi.raw.id,
                                        onSwipeReply = {
                                            editingMessage = null
                                            replyingMessage = msgUi
                                        }
                                    ) {
                                        GroupMessageBubble(
                                            messageUi = msgUi,
                                            currentUserId = currentUserId,
                                            isMine = isMine,
                                            senderPhotoUrl = senderDir?.photoUrl,
                                            senderAvatarBase64 = senderDir?.avatarBase64,
                                            onOpenActions = { actionTargetMessage = msgUi },
                                            onVotePoll = { optionIdx ->
                                                viewModel.votePoll(msgUi.raw.id, optionIdx)
                                            },
                                            onOpenReactionPicker = { reactionTargetMessage = msgUi },
                                            onToggleReaction = { emoji ->
                                                viewModel.toggleReaction(msgUi.raw.id, emoji, msgUi.raw.reactions)
                                            },
                                            onOpenImageFullscreen = { b64 -> fullscreenImageBase64 = b64 },
                                            onOpenVideoFullscreen = { b64, caption ->
                                                fullscreenVideo = Triple(msgUi.raw.id, b64, caption)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    ScrollToBottomFab(
                        visible = showScrollToBottom,
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(messageCount)
                            }
                        },
                        testTag = "group_scroll_to_bottom_button",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 14.dp)
                    )
                }
            }
        }
    }

    if (showComposerEmojiSheet) {
        EmojiPickerBottomSheet(
            title = "Emojis, Stickers & GIFs",
            onAppendEmoji = { emoji -> draft += emoji },
            onSendDirectEmoji = { emoji ->
                val currentReply = replyingMessage
                replyingMessage = null
                viewModel.sendRichGroupMessage(
                    RichMessagePayload(
                        type = RichMessageType.EMOJI.name,
                        text = emoji
                    ),
                    replyTo = currentReply
                )
            },
            onSendSticker = { sticker ->
                val currentReply = replyingMessage
                replyingMessage = null
                viewModel.sendRichGroupMessage(
                    RichMessagePayload(
                        type = RichMessageType.STICKER.name,
                        text = sticker.title,
                        stickerId = sticker.id
                    ),
                    replyTo = currentReply
                )
            },
            onSendGifPreset = { gif ->
                val currentReply = replyingMessage
                replyingMessage = null
                viewModel.sendRichGroupMessage(
                    RichMessagePayload(
                        type = RichMessageType.GIF.name,
                        text = gif.title,
                        gifId = gif.id
                    ),
                    replyTo = currentReply
                )
            },
            onSendGalleryGif = { mediaBase64, label ->
                val currentReply = replyingMessage
                replyingMessage = null
                viewModel.sendRichGroupMessage(
                    RichMessagePayload(
                        type = RichMessageType.GIF.name,
                        text = label,
                        mediaBase64 = mediaBase64
                    ),
                    replyTo = currentReply
                )
            },
            onDismiss = { showComposerEmojiSheet = false }
        )
    }

    reactionTargetMessage?.let { target ->
        EmojiPickerBottomSheet(
            title = "React to Message",
            onAppendEmoji = null,
            onSendDirectEmoji = { emoji ->
                viewModel.toggleReaction(target.raw.id, emoji, target.raw.reactions)
            },
            onDismiss = { reactionTargetMessage = null }
        )
    }

    if (showLocationSheet) {
        LocationPickerBottomSheet(
            onSendLocation = { lat, lng, label ->
                val currentReply = replyingMessage
                replyingMessage = null
                viewModel.sendRichGroupMessage(
                    RichMessagePayload(
                        type = RichMessageType.LOCATION.name,
                        text = label,
                        latitude = lat,
                        longitude = lng,
                        locationLabel = label
                    ),
                    replyTo = currentReply
                )
            },
            onDismiss = { showLocationSheet = false }
        )
    }

    if (showPollSheet) {
        PollCreatorBottomSheet(
            onCreatePoll = { question, options ->
                val currentReply = replyingMessage
                replyingMessage = null
                viewModel.sendRichGroupMessage(
                    RichMessagePayload(
                        type = RichMessageType.POLL.name,
                        text = question,
                        pollQuestion = question,
                        pollOptions = options
                    ),
                    replyTo = currentReply
                )
            },
            onDismiss = { showPollSheet = false }
        )
    }

    if (showScheduleSheet) {
        ScheduleMessageSheet(
            scheduledItems = scheduledItems,
            targetTitle = group?.name ?: "Group",
            initialMessage = draft,
            onSchedule = { text, ts ->
                viewModel.scheduleGroupMessage(text, ts)
                draft = ""
            },
            onSendNow = { item -> viewModel.sendScheduledNow(item) },
            onReschedule = { id, newTs -> viewModel.rescheduleMessage(id, newTs) },
            onCancel = { id -> viewModel.cancelScheduledMessage(id) },
            onDismiss = { showScheduleSheet = false }
        )
    }

    forwardingPayload?.let { payload ->
        ForwardMessageSheet(
            conversations = conversationsForForward,
            groups = groupsForForward,
            previewLabel = payload.text.ifBlank { payload.docName.ifBlank { payload.typeEnum.name } },
            onForwardToConv = { conv -> onForwardToConversation(conv, payload) },
            onForwardToGroup = { grp -> onForwardToGroup(grp, payload) },
            onDismiss = { forwardingPayload = null }
        )
    }

    fullscreenImageBase64?.let { b64 ->
        FullscreenImageDialog(
            imageBase64 = b64,
            onDismiss = { fullscreenImageBase64 = null }
        )
    }

    fullscreenVideo?.let { (id, b64, caption) ->
        FullscreenVideoDialog(
            videoBase64 = b64,
            caption = caption,
            cacheKey = id,
            onDismiss = { fullscreenVideo = null }
        )
    }

    inspectedMessage?.let { msgUi ->
        ModalBottomSheet(
            onDismissRequest = { inspectedMessage = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Group Cipher Inspector", style = MaterialTheme.typography.titleLarge)
                Text("Suite: ${msgUi.raw.encryptionAlg} • ${msgUi.payload.typeEnum.name}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("Decrypted: ${msgUi.plaintext}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Ciphertext: ${msgUi.raw.ciphertext.take(280)}${if (msgUi.raw.ciphertext.length > 280) "… (${msgUi.raw.ciphertext.length} chars)" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showGroupInfoSheet && group != null) {
        val currentGroup = group!!
        var editName by remember(currentGroup.name) { mutableStateOf(currentGroup.name) }
        var editDesc by remember(currentGroup.description) { mutableStateOf(currentGroup.description) }
        val nonMembers = directoryUsers.filter { it.uid !in currentGroup.memberIds }

        val groupDpPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val b64 = compressAvatarToBase64(context, uri)
                    if (b64 != null) {
                        viewModel.updateGroupDetails(editName, editDesc, b64, false)
                    }
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showGroupInfoSheet = false },
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Group Settings & Members", style = MaterialTheme.typography.titleLarge)
                    Surface(
                        color = if (isGroupAdmin) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = when {
                                isGroupCreator -> "Creator"
                                isGroupAdmin -> "Admin"
                                else -> "Member"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isGroupAdmin) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    UserAvatarWithPresence(
                        name = currentGroup.name,
                        photoUrl = null,
                        presence = PresenceStatus.ONLINE,
                        size = 56.dp,
                        showPresenceDot = false,
                        avatarBase64 = currentGroup.avatarBase64
                    )
                    if (isGroupAdmin) {
                        OutlinedButton(
                            onClick = {
                                groupDpPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Change Group DP")
                        }
                    } else {
                        Column {
                            Text(currentGroup.name, style = MaterialTheme.typography.titleMedium)
                            if (currentGroup.description.isNotBlank()) {
                                Text(
                                    text = currentGroup.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (isGroupAdmin) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { if (it.length <= 60) editName = it },
                        label = { Text("Group Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { if (it.length <= 140) editDesc = it },
                        label = { Text("Description") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            viewModel.updateGroupDetails(editName, editDesc, null, false)
                        },
                        enabled = editName.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Save Group Info")
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                viewModel.setOnlyAdminsCanMessage(!currentGroup.onlyAdminsCanMessage)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.onlyAdminsCanMessageToggle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = strings.onlyAdminsCanMessageSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = currentGroup.onlyAdminsCanMessage,
                                onCheckedChange = { checked -> viewModel.setOnlyAdminsCanMessage(checked) },
                                modifier = Modifier.testTag("only_admins_can_message_switch")
                            )
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                showGroupInfoSheet = false
                                showDisappearingSheet = true
                            }
                            .testTag("group_disappearing_messages_row")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(strings.disappearingMessagesTitle, style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                text = formatDisappearingLabel(currentGroup.disappearingSeconds),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Text(
                        text = if (currentGroup.onlyAdminsCanMessage) {
                            "${strings.onlyAdminsCanMessageBanner} Disappearing timer: ${formatDisappearingLabel(currentGroup.disappearingSeconds)}."
                        } else {
                            "Only group admins can edit group details or manage members. Disappearing timer: ${formatDisappearingLabel(currentGroup.disappearingSeconds)}."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Text("Members (${currentGroup.memberIds.size})", style = MaterialTheme.typography.titleSmall)
                currentGroup.memberIds.forEach { uid ->
                    val member = dirMap[uid]
                    val memberName = member?.displayName ?: uid.take(10)
                    val memberIsCreator = uid == currentGroup.createdBy
                    val memberIsAdmin = memberIsCreator || (uid in currentGroup.adminIds)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    previewAvatarProfile = member ?: DirectoryUser(uid = uid, displayName = memberName)
                                }
                        ) {
                            UserAvatarWithPresence(
                                name = member?.displayName ?: uid.take(8),
                                photoUrl = member?.photoUrl,
                                presence = member?.presenceEnum ?: PresenceStatus.OFFLINE,
                                size = 38.dp,
                                avatarBase64 = member?.avatarBase64
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (uid == currentUserId) "${member?.displayName ?: "You"} (You)" else memberName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                if (memberIsCreator) {
                                    Text(
                                        text = "Creator",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else if (memberIsAdmin) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = "Admin",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = member?.handle ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isGroupAdmin && uid != currentUserId && !memberIsCreator) {
                            if (isGroupCreator || !memberIsAdmin) {
                                IconButton(
                                    onClick = { viewModel.toggleMemberAdmin(uid) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("toggle_admin_button_$uid")
                                ) {
                                    Icon(
                                        imageVector = if (memberIsAdmin) Icons.Default.AdminPanelSettings else Icons.Default.Shield,
                                        contentDescription = if (memberIsAdmin) "Remove Admin" else "Make Admin",
                                        tint = if (memberIsAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { memberPendingRemoval = uid to memberName },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("remove_member_button_$uid")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PersonRemove,
                                        contentDescription = stringResource(R.string.remove_member_label),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                if (isGroupAdmin) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Text("Add Contacts to Group", style = MaterialTheme.typography.titleSmall)
                    if (nonMembers.isEmpty()) {
                        Text(
                            text = "All available contacts are already in this group.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        nonMembers.forEach { peer ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { viewModel.addMembers(listOf(peer)) }
                                    .testTag("add_member_row_${peer.uid}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserAvatarWithPresence(
                                        name = peer.displayName,
                                        photoUrl = peer.photoUrl,
                                        presence = peer.presenceEnum,
                                        size = 36.dp,
                                        avatarBase64 = peer.avatarBase64
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(peer.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.PersonAdd, contentDescription = "Add to group", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        showGroupInfoSheet = false
                        showLeaveGroupDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("leave_group_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Leave Group")
                }
                if (currentGroup.createdBy == currentUserId) {
                    Button(
                        onClick = {
                            showGroupInfoSheet = false
                            showDeleteGroupDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("delete_group_button")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.delete_group_label))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showDisappearingSheet) {
        DisappearingTimerBottomSheet(
            currentSeconds = group?.disappearingSeconds ?: 0L,
            onSelectSeconds = { secs -> viewModel.setDisappearingTimer(secs) },
            onDismiss = { showDisappearingSheet = false }
        )
    }

    previewAvatarProfile?.let { profile ->
        ProfileAvatarViewerDialog(
            name = profile.displayName,
            handle = profile.handle,
            photoUrl = profile.photoUrl,
            avatarBase64 = profile.avatarBase64,
            statusMessage = profile.statusMessage,
            presence = profile.presenceEnum,
            onDismiss = { previewAvatarProfile = null }
        )
    }

    if (showGroupAvatarPreview && group != null) {
        ProfileAvatarViewerDialog(
            name = group!!.name,
            handle = "${group!!.memberIds.size} members",
            photoUrl = null,
            avatarBase64 = group!!.avatarBase64,
            statusMessage = group!!.description,
            presence = PresenceStatus.ONLINE,
            onDismiss = { showGroupAvatarPreview = false }
        )
    }

    actionTargetMessage?.let { target ->
        val editableText = groupEditableMessageText(target)
        val canEdit = target.raw.senderId == currentUserId &&
            target.decryptedSuccessfully &&
            target.payload.typeEnum != RichMessageType.CALL_LOG &&
            target.payload.typeEnum != RichMessageType.VOICE
        val isPinned = group?.pinnedMessageId == target.raw.id
        MessageActionBottomSheet(
            previewText = target.plaintext,
            isMine = target.raw.senderId == currentUserId,
            canEdit = canEdit,
            canDelete = target.raw.senderId == currentUserId || isGroupAdmin,
            canCopy = target.decryptedSuccessfully && target.plaintext.isNotBlank(),
            isPinned = isPinned,
            currentReaction = target.raw.reactions[currentUserId],
            onQuickReact = { emoji ->
                viewModel.toggleReaction(target.raw.id, emoji, target.raw.reactions)
            },
            onOpenFullReactionPicker = { reactionTargetMessage = target },
            onReplyClick = {
                editingMessage = null
                replyingMessage = target
            },
            onForwardClick = {
                forwardingPayload = target.payload
            },
            onTogglePinClick = {
                viewModel.pinGroupMessage(if (isPinned) null else target.raw.id)
            },
            onEditClick = {
                showAttachments = false
                replyingMessage = null
                editingMessage = target
                draft = editableText
            },
            onDeleteClick = { deletingMessage = target },
            onCopyClick = {
                clipboardManager.setText(AnnotatedString(editableText.ifBlank { target.plaintext }))
                scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
            },
            onInspectCipherClick = { inspectedMessage = target },
            onDismiss = { actionTargetMessage = null }
        )
    }

    deletingMessage?.let { target ->
        ConfirmDeleteDialog(
            title = "Delete Group Message?",
            message = "This will permanently remove this message for everyone in ${group?.name ?: "the group"}.",
            onConfirm = {
                if (editingMessage?.raw?.id == target.raw.id) {
                    editingMessage = null
                    draft = ""
                }
                viewModel.deleteGroupMessage(target)
            },
            onDismiss = { deletingMessage = null }
        )
    }

    if (showDeleteGroupDialog) {
        ConfirmDeleteDialog(
            title = "Delete Encrypted Group?",
            message = "This permanently deletes \"${group?.name ?: "Group"}\" and all its encrypted messages for all members.",
            onConfirm = { viewModel.deleteGroup(onBack) },
            onDismiss = { showDeleteGroupDialog = false }
        )
    }

    if (showLeaveGroupDialog) {
        ConfirmDeleteDialog(
            title = "Leave Group?",
            message = "You will no longer receive encrypted messages from \"${group?.name ?: "this group"}\".",
            confirmLabel = "Leave",
            onConfirm = { viewModel.leaveGroup(onBack) },
            onDismiss = { showLeaveGroupDialog = false }
        )
    }

    memberPendingRemoval?.let { (memberUid, memberName) ->
        ConfirmDeleteDialog(
            title = "Remove $memberName?",
            message = "$memberName will be removed from \"${group?.name ?: "this group"}\" and lose access to group messages.",
            confirmLabel = "Remove",
            onConfirm = {
                memberPendingRemoval = null
                viewModel.removeMember(memberUid)
            },
            onDismiss = { memberPendingRemoval = null }
        )
    }
}

private fun groupEditableMessageText(messageUi: DecryptedGroupMessageUi): String =
    when (messageUi.payload.typeEnum) {
        RichMessageType.POLL -> messageUi.payload.pollQuestion
        RichMessageType.LOCATION -> messageUi.payload.locationLabel
        else -> messageUi.payload.text
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupMessageBubble(
    messageUi: DecryptedGroupMessageUi,
    currentUserId: String,
    isMine: Boolean,
    senderPhotoUrl: String?,
    senderAvatarBase64: String?,
    onOpenActions: () -> Unit,
    onVotePoll: (Int) -> Unit,
    onOpenReactionPicker: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onOpenImageFullscreen: (String) -> Unit,
    onOpenVideoFullscreen: (String, String) -> Unit
) {
    val containerColor = if (isMine) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isMine) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isMine) 18.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 18.dp
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMine) {
            UserAvatarWithPresence(
                name = messageUi.raw.senderName,
                photoUrl = senderPhotoUrl,
                presence = PresenceStatus.ONLINE,
                size = 30.dp,
                showPresenceDot = false,
                avatarBase64 = senderAvatarBase64
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Surface(
            color = containerColor,
            shape = shape,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .combinedClickable(
                    onClick = onOpenActions,
                    onDoubleClick = { onToggleReaction("❤️") },
                    onLongClick = onOpenActions
                )
                .testTag("group_message_bubble_${messageUi.raw.id}")
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!isMine) {
                    Text(
                        text = messageUi.raw.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                RichMessageBubbleContent(
                    payload = messageUi.payload,
                    pollVotes = messageUi.raw.pollVotes,
                    reactions = messageUi.raw.reactions,
                    currentUserId = currentUserId,
                    contentColor = contentColor,
                    isMine = isMine,
                    onVotePoll = onVotePoll,
                    onOpenReactionPicker = onOpenReactionPicker,
                    onToggleReaction = onToggleReaction,
                    onOpenImageFullscreen = onOpenImageFullscreen,
                    onOpenVideoFullscreen = onOpenVideoFullscreen
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = formatTimeOnly(messageUi.raw.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                    if (messageUi.raw.edited) {
                        Text(
                            text = "• ${stringResource(R.string.edited_badge_label)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                    }
                    if (isMine) {
                        val isReadByAnyMember = messageUi.raw.readBy.any { it != currentUserId }
                        val isDelivered = messageUi.raw.createdAt != null
                        Icon(
                            imageVector = if (isDelivered) Icons.Default.DoneAll else Icons.Default.Check,
                            contentDescription = if (isReadByAnyMember) "Read" else "Delivered",
                            tint = if (isReadByAnyMember) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
