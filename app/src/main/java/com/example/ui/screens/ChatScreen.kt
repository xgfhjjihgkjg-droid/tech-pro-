package com.example.ui.screens

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.example.data.CallLinkHelper
import com.example.data.CallType
import com.example.data.ConversationListItemUi
import com.example.data.DecryptedMessageUi
import com.example.data.GroupListItemUi
import com.example.data.LocalAppStrings
import com.example.data.PresenceStatus
import com.example.data.PrivacyAudience
import com.example.data.RichMessagePayload
import com.example.data.RichMessageType
import com.example.data.ThemeSettings
import com.example.data.UiState
import com.example.media.MediaUtils
import com.example.media.VoiceNoteRecorder
import com.example.ui.components.ChatWallpaperBackdrop
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.DisappearingTimerBottomSheet
import com.example.ui.components.EditChatNameDialog
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
import com.example.ui.components.formatDisappearingLabel
import com.example.ui.components.formatLastSeen
import com.example.ui.components.formatTimeOnly
import com.example.ui.components.isVisibleForAudience
import com.example.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    currentUserId: String,
    peerId: String,
    themeSettings: ThemeSettings,
    isChatLocked: Boolean = false,
    onToggleChatLock: () -> Unit = {},
    isSavedContact: Boolean = false,
    onToggleSavedContact: () -> Unit = {},
    isPeerBlocked: Boolean = false,
    onToggleBlockedPeer: () -> Unit = {},
    conversationsForForward: List<ConversationListItemUi> = emptyList(),
    groupsForForward: List<GroupListItemUi> = emptyList(),
    onForwardToConversation: (ConversationListItemUi, RichMessagePayload) -> Unit = { _, _ -> },
    onForwardToGroup: (GroupListItemUi, RichMessagePayload) -> Unit = { _, _ -> },
    onOpenThemeSheet: () -> Unit,
    onBack: () -> Unit,
    onStartCall: (peerId: String, callType: CallType) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val peerProfile by viewModel.peerProfile.collectAsStateWithLifecycle()
    val privateAccount by viewModel.privateAccount.collectAsStateWithLifecycle()
    val isPeerTyping by viewModel.isPeerTyping.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val safetyNumber by viewModel.safetyNumber.collectAsStateWithLifecycle()
    val keyBundle by viewModel.keyBundle.collectAsStateWithLifecycle()
    val sendError by viewModel.sendError.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val scheduledItems by viewModel.peerScheduledMessages.collectAsStateWithLifecycle()

    val peerDefaultName = peerProfile?.displayName ?: "Encrypted Peer"
    val customNickname = conversation?.customTitleFor(currentUserId)
    val displayPeerTitle = customNickname ?: peerDefaultName

    val strings = LocalAppStrings.current
    var draft by remember { mutableStateOf("") }
    var showSafetySheet by remember { mutableStateOf(false) }
    var showChatMenu by remember { mutableStateOf(false) }
    var showEditChatNameDialog by remember { mutableStateOf(false) }
    var showDeleteChatDialog by remember { mutableStateOf(false) }
    var showDisappearingSheet by remember { mutableStateOf(false) }
    var showPeerAvatarDialog by remember { mutableStateOf(false) }
    var showAttachments by remember { mutableStateOf(false) }
    var showComposerEmojiSheet by remember { mutableStateOf(false) }
    var showLocationSheet by remember { mutableStateOf(false) }
    var showPollSheet by remember { mutableStateOf(false) }
    var showScheduleSheet by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<DecryptedMessageUi?>(null) }
    var replyingMessage by remember { mutableStateOf<DecryptedMessageUi?>(null) }
    var forwardingPayload by remember { mutableStateOf<RichMessagePayload?>(null) }
    var actionTargetMessage by remember { mutableStateOf<DecryptedMessageUi?>(null) }
    var deletingMessage by remember { mutableStateOf<DecryptedMessageUi?>(null) }
    var reactionTargetMessage by remember { mutableStateOf<DecryptedMessageUi?>(null) }
    var inspectedMessage by remember { mutableStateOf<DecryptedMessageUi?>(null) }
    var fullscreenImageBase64 by remember { mutableStateOf<String?>(null) }
    var fullscreenVideo by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var isProcessingMedia by remember { mutableStateOf(false) }

    val voiceRecorder = remember { VoiceNoteRecorder() }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }

    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isPeerVerified = peerId in (privateAccount?.verifiedPeerIds ?: emptyList())

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
                    viewModel.sendRichMessage(
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
                    viewModel.sendRichMessage(
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
                        viewModel.sendRichMessage(
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
                        viewModel.sendRichMessage(
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

    LaunchedEffect(sendError) {
        if (!sendError.isNullOrBlank()) {
            snackbarHostState.showSnackbar(sendError!!)
            viewModel.clearSendError()
        }
    }

    var previousMsgCount by remember { mutableIntStateOf(0) }
    val decryptedMessages = (messagesState as? UiState.Success<List<DecryptedMessageUi>>)?.data ?: emptyList()
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
    LaunchedEffect(imeVisible, isPeerTyping) {
        if (messageCount > 0 && isNearBottom && (imeVisible || isPeerTyping)) {
            listState.animateScrollToItem(messageCount + if (isPeerTyping) 1 else 0)
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
                            .testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_cd)
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showSafetySheet = true }
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                    ) {
                        val hidePeerAvatar = peerProfile != null &&
                            !isVisibleForAudience(peerProfile!!.photoVisibilityEnum, isSavedContact)
                        Box(
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .clickable { showPeerAvatarDialog = true }
                                .testTag("chat_peer_avatar_button")
                        ) {
                            UserAvatarWithPresence(
                                name = displayPeerTitle,
                                photoUrl = peerProfile?.photoUrl,
                                presence = peerProfile?.presenceEnum ?: PresenceStatus.OFFLINE,
                                size = 40.dp,
                                avatarBase64 = peerProfile?.avatarBase64,
                                hideAvatar = hidePeerAvatar
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = displayPeerTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isPeerVerified) {
                                    Icon(
                                        imageVector = Icons.Default.VerifiedUser,
                                        contentDescription = "Safety number verified",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            val baseSubtitle = when {
                                isPeerTyping -> "typing..."
                                peerProfile != null -> {
                                    val p = peerProfile!!
                                    formatLastSeen(
                                        lastSeenAt = p.lastSeenAt,
                                        presence = p.presenceEnum,
                                        visibility = p.lastSeenVisibilityEnum,
                                        isSavedContact = isSavedContact
                                    )
                                }
                                else -> "Connecting..."
                            }
                            val disappearSecs = conversation?.disappearingSeconds ?: 0L
                            val subtitle = if (disappearSecs > 0L) {
                                "$baseSubtitle • ⏱ ${formatDisappearingLabel(disappearSecs)}"
                            } else {
                                baseSubtitle
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isPeerTyping) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onStartCall(peerId, CallType.AUDIO) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chat_audio_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = stringResource(R.string.audio_call_cd),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { onStartCall(peerId, CallType.VIDEO) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chat_video_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = stringResource(R.string.video_call_cd),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(
                        onClick = { showSafetySheet = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chat_safety_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = stringResource(R.string.safety_number_cd),
                            tint = if (isPeerVerified) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { showChatMenu = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chat_more_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Chat options"
                            )
                        }
                        DropdownMenu(
                            expanded = showChatMenu,
                            onDismissRequest = { showChatMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Schedule Message") },
                                leadingIcon = { Icon(Icons.Default.ScheduleSend, contentDescription = null) },
                                onClick = {
                                    showChatMenu = false
                                    showScheduleSheet = true
                                },
                                modifier = Modifier.testTag("menu_schedule_message")
                            )
                            DropdownMenuItem(
                                text = {
                                    val timerLabel = formatDisappearingLabel(conversation?.disappearingSeconds ?: 0L)
                                    Text("${strings.disappearingMessagesTitle} ($timerLabel)")
                                },
                                leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                                onClick = {
                                    showChatMenu = false
                                    showDisappearingSheet = true
                                },
                                modifier = Modifier.testTag("menu_disappearing_messages")
                            )
                            DropdownMenuItem(
                                text = { Text(if (isChatLocked) "Unlock Chat" else "Lock Chat") },
                                leadingIcon = {
                                    Icon(
                                        if (isChatLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showChatMenu = false
                                    onToggleChatLock()
                                },
                                modifier = Modifier.testTag("menu_toggle_chat_lock")
                            )
                            DropdownMenuItem(
                                text = { Text("Share Call Link") },
                                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                                onClick = {
                                    showChatMenu = false
                                    val linkMsg = CallLinkHelper.buildShareableMessage(
                                        hostUid = currentUserId,
                                        hostName = "ToKer Call",
                                        callType = CallType.VIDEO
                                    )
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, linkMsg)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share ToKer Call Link"))
                                },
                                modifier = Modifier.testTag("menu_share_call_link")
                            )
                            DropdownMenuItem(
                                text = { Text(if (isSavedContact) "Remove Contact" else "Add to Contacts") },
                                leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                                onClick = {
                                    showChatMenu = false
                                    onToggleSavedContact()
                                },
                                modifier = Modifier.testTag("menu_toggle_save_contact")
                            )
                            DropdownMenuItem(
                                text = { Text(if (isPeerBlocked) "Unblock Contact" else "Block Contact") },
                                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                                onClick = {
                                    showChatMenu = false
                                    onToggleBlockedPeer()
                                },
                                modifier = Modifier.testTag("menu_toggle_block_contact")
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_chat_name_label)) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showChatMenu = false
                                    showEditChatNameDialog = true
                                },
                                modifier = Modifier.testTag("menu_edit_chat_name")
                            )
                            DropdownMenuItem(
                                text = { Text("Theme, Text Style & Wallpaper") },
                                leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                                onClick = {
                                    showChatMenu = false
                                    onOpenThemeSheet()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.delete_chat_label),
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
                                    showChatMenu = false
                                    showDeleteChatDialog = true
                                },
                                modifier = Modifier.testTag("menu_delete_chat")
                            )
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
                            val replyAuthor = if (targetReply.raw.senderId == currentUserId) "You" else displayPeerTitle
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

                    if (isRecordingVoice) {
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
                                    .testTag("chat_attach_button")
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
                                    .testTag("chat_emoji_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEmotions,
                                    contentDescription = stringResource(R.string.emoji_picker_cd),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                            OutlinedTextField(
                                value = draft,
                                onValueChange = {
                                    draft = it
                                    if (editingMessage == null) {
                                        viewModel.onDraftChanged(it)
                                    }
                                },
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
                                    .testTag("chat_message_input")
                            )
                            IconButton(
                                onClick = { showScheduleSheet = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("chat_schedule_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ScheduleSend,
                                    contentDescription = "Schedule auto-send message",
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
                                        .testTag("chat_voice_record_button")
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
                                            viewModel.editMessage(currentEdit, textToSend)
                                        } else {
                                            replyingMessage = null
                                            viewModel.sendMessage(textToSend, replyTo = currentReply)
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
                                        .testTag("chat_send_button")
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
                val successList = (messagesState as? UiState.Success<List<DecryptedMessageUi>>)?.data ?: emptyList()
                val pinnedId = conversation?.pinnedMessageId
                val pinnedIndex = if (!pinnedId.isNullOrBlank()) {
                    successList.indexOfFirst { it.raw.id == pinnedId }
                } else -1
                if (pinnedIndex >= 0) {
                    val pinnedMsg = successList[pinnedIndex]
                    PinnedMessageBanner(
                        previewText = pinnedMsg.plaintext,
                        onJumpToPinned = {
                            scope.launch { listState.animateScrollToItem(pinnedIndex + 1) }
                        },
                        onUnpin = { viewModel.pinMessage(null) }
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
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
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
                                        EncryptedBadge(
                                            label = stringResource(R.string.e2ee_badge_label)
                                        )
                                    }
                                }

                                items(state.data, key = { it.raw.id }) { msgUi ->
                                    val isMine = msgUi.raw.senderId == currentUserId
                                    val isReadByPeer = peerId in msgUi.raw.readBy
                                    SwipeToReplyContainer(
                                        messageKey = msgUi.raw.id,
                                        onSwipeReply = {
                                            editingMessage = null
                                            replyingMessage = msgUi
                                        }
                                    ) {
                                        MessageBubble(
                                            messageUi = msgUi,
                                            currentUserId = currentUserId,
                                            isMine = isMine,
                                            isReadByPeer = isReadByPeer,
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

                                item {
                                    androidx.compose.animation.AnimatedVisibility(visible = isPeerTyping) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Text(
                                                text = "${peerProfile?.displayName ?: "Peer"} is typing…",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
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
                        testTag = "scroll_to_bottom_button",
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
            onAppendEmoji = { emoji ->
                draft += emoji
                viewModel.onDraftChanged(draft)
            },
            onSendDirectEmoji = { emoji ->
                val currentReply = replyingMessage
                replyingMessage = null
                viewModel.sendRichMessage(
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
                viewModel.sendRichMessage(
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
                viewModel.sendRichMessage(
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
                viewModel.sendRichMessage(
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
                viewModel.sendRichMessage(
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
                viewModel.sendRichMessage(
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
            targetTitle = displayPeerTitle,
            initialMessage = draft,
            onSchedule = { text, ts ->
                viewModel.scheduleMessage(text, ts)
                draft = ""
                scope.launch { snackbarHostState.showSnackbar("Scheduled auto-send for $displayPeerTitle") }
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("End-to-End Cipher Inspector", style = MaterialTheme.typography.titleLarge)
                }
                CipherFieldCard(label = "Cryptographic Suite", value = msgUi.raw.encryptionAlg)
                CipherFieldCard(label = "Message Type", value = msgUi.payload.typeEnum.name)
                CipherFieldCard(label = "Decrypted Summary (On-Device)", value = msgUi.plaintext)
                CipherFieldCard(
                    label = "AES-256-GCM Ciphertext (Stored in Firestore)",
                    value = msgUi.raw.ciphertext.take(360) + if (msgUi.raw.ciphertext.length > 360) "… (${msgUi.raw.ciphertext.length} chars)" else ""
                )
                CipherFieldCard(label = "12-Byte GCM IV (Base64)", value = msgUi.raw.iv)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showSafetySheet) {
        ModalBottomSheet(
            onDismissRequest = { showSafetySheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isPeerVerified) Icons.Default.VerifiedUser else Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Verify Safety Number", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    text = "Compare this 60-digit safety number with ${peerProfile?.displayName ?: "your contact"} to confirm zero-MITM key agreement.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = safetyNumber.ifBlank { "Computing safety number..." },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                CipherFieldCard(
                    label = "Your Key Fingerprint",
                    value = keyBundle?.keyFingerprint ?: "..."
                )
                CipherFieldCard(
                    label = "${peerProfile?.displayName ?: "Peer"} Key Fingerprint",
                    value = peerProfile?.keyFingerprint ?: "..."
                )
                if (isPeerVerified) {
                    OutlinedButton(
                        onClick = { viewModel.togglePeerVerified() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("toggle_verify_peer_button")
                    ) {
                        Text("Remove Verified Badge")
                    }
                } else {
                    Button(
                        onClick = { viewModel.togglePeerVerified() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("toggle_verify_peer_button")
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mark Contact as Verified")
                    }
                }
                OutlinedButton(
                    onClick = {
                        showSafetySheet = false
                        showDisappearingSheet = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("chat_disappearing_button")
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${strings.disappearingMessagesTitle}: ${formatDisappearingLabel(conversation?.disappearingSeconds ?: 0L)}")
                }
                OutlinedButton(
                    onClick = {
                        showSafetySheet = false
                        onOpenThemeSheet()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("chat_customize_theme_button")
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Change Chat Theme / Gallery Wallpaper")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showDisappearingSheet) {
        DisappearingTimerBottomSheet(
            currentSeconds = conversation?.disappearingSeconds ?: 0L,
            onSelectSeconds = { secs -> viewModel.setDisappearingTimer(secs) },
            onDismiss = { showDisappearingSheet = false }
        )
    }

    if (showPeerAvatarDialog) {
        val hidePeerAvatar = peerProfile != null &&
            !isVisibleForAudience(peerProfile!!.photoVisibilityEnum, isSavedContact)
        ProfileAvatarViewerDialog(
            name = displayPeerTitle,
            handle = peerProfile?.handle ?: "",
            photoUrl = peerProfile?.photoUrl,
            avatarBase64 = peerProfile?.avatarBase64,
            statusMessage = peerProfile?.statusMessage ?: "",
            presence = peerProfile?.presenceEnum ?: PresenceStatus.OFFLINE,
            hideAvatar = hidePeerAvatar,
            onDismiss = { showPeerAvatarDialog = false }
        )
    }

    actionTargetMessage?.let { target ->
        val editableText = chatEditableMessageText(target)
        val canEdit = target.raw.senderId == currentUserId &&
            target.decryptedSuccessfully &&
            target.payload.typeEnum != RichMessageType.CALL_LOG &&
            target.payload.typeEnum != RichMessageType.VOICE
        val isPinned = conversation?.pinnedMessageId == target.raw.id
        MessageActionBottomSheet(
            previewText = target.plaintext,
            isMine = target.raw.senderId == currentUserId,
            canEdit = canEdit,
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
                viewModel.pinMessage(if (isPinned) null else target.raw.id)
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
            title = "Delete Encrypted Message?",
            message = "This will remove this encrypted message for both participants.",
            onConfirm = {
                if (editingMessage?.raw?.id == target.raw.id) {
                    editingMessage = null
                    draft = ""
                }
                viewModel.deleteMessage(target)
            },
            onDismiss = { deletingMessage = null }
        )
    }

    if (showEditChatNameDialog) {
        EditChatNameDialog(
            initialName = customNickname ?: "",
            peerDefaultName = peerDefaultName,
            onSave = { viewModel.updateChatNickname(it) },
            onDismiss = { showEditChatNameDialog = false }
        )
    }

    if (showDeleteChatDialog) {
        ConfirmDeleteDialog(
            title = "Delete Encrypted Chat?",
            message = "This permanently deletes this conversation and all encrypted messages with $displayPeerTitle.",
            onConfirm = { viewModel.deleteChat(onBack) },
            onDismiss = { showDeleteChatDialog = false }
        )
    }
}

private fun chatEditableMessageText(messageUi: DecryptedMessageUi): String =
    when (messageUi.payload.typeEnum) {
        RichMessageType.POLL -> messageUi.payload.pollQuestion
        RichMessageType.LOCATION -> messageUi.payload.locationLabel
        else -> messageUi.payload.text
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    messageUi: DecryptedMessageUi,
    currentUserId: String,
    isMine: Boolean,
    isReadByPeer: Boolean,
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
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = containerColor,
            shape = shape,
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clip(shape)
                .combinedClickable(
                    onClick = onOpenActions,
                    onDoubleClick = { onToggleReaction("❤️") },
                    onLongClick = onOpenActions
                )
                .testTag("message_bubble_${messageUi.raw.id}")
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
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
                        val isDelivered = messageUi.raw.createdAt != null
                        Icon(
                            imageVector = if (isDelivered) Icons.Default.DoneAll else Icons.Default.Check,
                            contentDescription = when {
                                isReadByPeer -> "Read receipt"
                                isDelivered -> "Delivered"
                                else -> "Sending"
                            },
                            tint = if (isReadByPeer) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CipherFieldCard(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
