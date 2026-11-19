package com.example.ui.screens

import android.media.MediaMetadataRetriever
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.AppLanguage
import com.example.data.AppStrings
import com.example.data.AuthorStoriesGroup
import com.example.data.CallHistoryFilter
import com.example.data.CallSession
import com.example.data.CallStatus
import com.example.data.CallType
import com.example.data.ConversationListItemUi
import com.example.data.DirectoryUser
import com.example.data.GroupListItemUi
import com.example.data.LocalAppStrings
import com.example.data.PresenceStatus
import com.example.data.PrivacyAudience
import com.example.data.ScheduledMessageStatus
import com.example.data.SecuritySettings
import com.example.data.StatusUpdate
import com.example.data.StoryType
import com.example.data.ThemeSettings
import com.example.data.UiState
import com.example.data.signOutFromToKer
import com.example.media.MediaUtils
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EditChatNameDialog
import com.example.ui.components.EditGroupSummaryDialog
import com.example.ui.components.EncryptedBadge
import com.example.ui.components.IncomingCallBanner
import com.example.ui.components.ProfileAvatarViewerDialog
import com.example.ui.components.UserAvatarWithPresence
import com.example.ui.components.formatDisappearingLabel
import com.example.ui.components.formatLastSeen
import com.example.ui.components.formatRelativeTimestamp
import com.example.ui.components.isVisibleForAudience
import com.example.ui.components.shareCallLink
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    currentUserId: String,
    themeSettings: ThemeSettings,
    securitySettings: SecuritySettings,
    hasOsNotificationPermission: Boolean = true,
    onEnableNotifications: () -> Unit = {},
    onOpenThemeSheet: () -> Unit,
    onOpenLanguageSheet: () -> Unit = {},
    onOpenPrivacySheet: () -> Unit,
    onOpenBackupSheet: () -> Unit,
    onOpenScheduleSheet: () -> Unit,
    onOpenAddContactSheet: () -> Unit,
    onOpenJoinCallLinkDialog: () -> Unit,
    onRequestChatUnlock: (chatKey: String, title: String, onSuccess: () -> Unit) -> Unit,
    onToggleChatLock: (chatKey: String) -> Unit,
    onOpenChat: (conversationId: String, peerId: String) -> Unit,
    onOpenGroup: (groupId: String) -> Unit,
    onStartCall: (peerId: String, callType: CallType, incomingCallId: String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val myProfile by viewModel.myProfile.collectAsStateWithLifecycle()
    val privateAccount by viewModel.privateAccount.collectAsStateWithLifecycle()
    val keyBundle by viewModel.keyBundle.collectAsStateWithLifecycle()
    val directoryState by viewModel.directoryUsers.collectAsStateWithLifecycle()
    val conversationsState by viewModel.conversations.collectAsStateWithLifecycle()
    val groupsState by viewModel.groups.collectAsStateWithLifecycle()
    val statusesState by viewModel.publicStatuses.collectAsStateWithLifecycle()
    val liveStoriesState by viewModel.liveStoryGroups.collectAsStateWithLifecycle()
    val callsState by viewModel.myCalls.collectAsStateWithLifecycle()
    val scheduledMessages by viewModel.scheduledMessages.collectAsStateWithLifecycle()
    val incomingCall by viewModel.incomingRingingCall.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    val hydratedDirMap by viewModel.hydratedDirectoryMap.collectAsStateWithLifecycle()
    val savedContactIds = privateAccount?.savedContactIds ?: emptyList()
    val blockedUserIds = privateAccount?.blockedUserIds ?: emptyList()
    val dirById = remember(directoryState, hydratedDirMap) {
        val base = (directoryState as? UiState.Success)?.data?.associateBy { it.uid } ?: emptyMap()
        if (hydratedDirMap.isEmpty()) base else base + hydratedDirMap
    }
    val pendingScheduledCount = remember(scheduledMessages) {
        scheduledMessages.count { it.statusEnum == ScheduledMessageStatus.PENDING }
    }
    val visibleLiveStoriesState = remember(liveStoriesState, dirById, savedContactIds, blockedUserIds, currentUserId) {
        when (val s = liveStoriesState) {
            is UiState.Success -> {
                UiState.Success(
                    s.data.filter { grp ->
                        if (grp.authorId == currentUserId) true
                        else if (grp.authorId in blockedUserIds) false
                        else {
                            val dir = dirById[grp.authorId]
                            isVisibleForAudience(
                                dir?.statusVisibilityEnum ?: PrivacyAudience.EVERYONE,
                                grp.authorId in savedContactIds
                            )
                        }
                    }
                )
            }
            else -> s
        }
    }
    val visibleStatusesState = remember(statusesState, dirById, savedContactIds, blockedUserIds, currentUserId) {
        when (val s = statusesState) {
            is UiState.Success -> {
                UiState.Success(
                    s.data.filter { status ->
                        if (status.authorId == currentUserId) true
                        else if (status.authorId in blockedUserIds) false
                        else {
                            val dir = dirById[status.authorId]
                            isVisibleForAudience(
                                dir?.statusVisibilityEnum ?: PrivacyAudience.EVERYONE,
                                status.authorId in savedContactIds
                            )
                        }
                    }
                )
            }
            else -> s
        }
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = selectedTab.coerceIn(0, 2),
        pageCount = { 3 }
    )
    LaunchedEffect(pagerState.currentPage) {
        selectedTab = pagerState.currentPage
    }
    var showDirectorySheet by remember { mutableStateOf(false) }
    var showCreateStorySheet by remember { mutableStateOf(false) }
    var prefilledStoryType by remember { mutableStateOf(StoryType.TEXT) }
    var prefilledStoryPhoto by remember { mutableStateOf<String?>(null) }
    var prefilledStoryVideo by remember { mutableStateOf<String?>(null) }
    var isPreparingStoryMedia by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var showCreateGroupSheet by remember { mutableStateOf(false) }
    var showInviteSheet by remember { mutableStateOf(false) }
    var showSettingsHubSheet by remember { mutableStateOf(false) }
    var viewingStoryAuthorId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var editingConvItem by remember { mutableStateOf<ConversationListItemUi?>(null) }
    var deletingConvItem by remember { mutableStateOf<ConversationListItemUi?>(null) }
    var editingGroupItem by remember { mutableStateOf<GroupListItemUi?>(null) }
    var deletingGroupItem by remember { mutableStateOf<GroupListItemUi?>(null) }
    var leavingGroupItem by remember { mutableStateOf<GroupListItemUi?>(null) }
    var showClearCallHistoryDialog by remember { mutableStateOf(false) }
    var previewDpTarget by remember { mutableStateOf<DirectoryUser?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val strings = LocalAppStrings.current

    val storyGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isPreparingStoryMedia = true
        scope.launch {
            val isVideo = withContext(Dispatchers.IO) {
                val mime = context.contentResolver.getType(uri).orEmpty()
                if (mime.startsWith("video/", ignoreCase = true)) {
                    true
                } else if (mime.startsWith("image/", ignoreCase = true)) {
                    false
                } else {
                    runCatching {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(context, uri)
                        val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                        retriever.release()
                        hasVideo == "yes"
                    }.getOrDefault(false)
                }
            }
            if (isVideo) {
                MediaUtils.processPickedVideoToBase64(context, uri, maxRawBytes = 480_000)
                    .onSuccess { encoded ->
                        prefilledStoryType = StoryType.VIDEO
                        prefilledStoryPhoto = null
                        prefilledStoryVideo = encoded.videoBase64
                        showCreateStorySheet = true
                    }
                    .onFailure { err ->
                        snackbarHostState.showSnackbar(err.message ?: "Could not process selected video")
                    }
            } else {
                val encodedPhoto = MediaUtils.compressImageToBase64(context, uri)
                if (encodedPhoto != null) {
                    prefilledStoryType = StoryType.PHOTO
                    prefilledStoryPhoto = encodedPhoto
                    prefilledStoryVideo = null
                    showCreateStorySheet = true
                } else {
                    snackbarHostState.showSnackbar("Could not process selected photo")
                }
            }
            isPreparingStoryMedia = false
        }
    }

    fun openStoryFromGallery() {
        storyGalleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        )
    }

    fun openTextStoryComposer() {
        prefilledStoryType = StoryType.TEXT
        prefilledStoryPhoto = null
        prefilledStoryVideo = null
        showCreateStorySheet = true
    }

    LaunchedEffect(actionError, actionMessage) {
        val msg = actionError ?: actionMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    val navItems = listOf(
        Triple(0, strings.tabChats, Icons.AutoMirrored.Filled.Chat),
        Triple(1, strings.tabStories, Icons.Default.RadioButtonChecked),
        Triple(2, strings.tabCalls, Icons.Default.Call)
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    navItems.forEach { (index, label, icon) ->
                        NavigationRailItem(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("nav_rail_tab_$index")
                        )
                    }
                }
            }

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .clickable { showProfileSheet = true }
                                        .testTag("open_profile_controls_avatar"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    UserAvatarWithPresence(
                                        name = myProfile?.displayName ?: strings.profileLabel,
                                        photoUrl = myProfile?.photoUrl,
                                        presence = myProfile?.presenceEnum ?: PresenceStatus.ONLINE,
                                        size = 38.dp,
                                        avatarBase64 = myProfile?.avatarBase64
                                    )
                                }
                                Column {
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sub = myProfile?.statusMessage?.ifBlank {
                                        localizedPresence(myProfile?.presenceEnum, strings)
                                    } ?: localizedPresence(myProfile?.presenceEnum, strings)
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { showSettingsHubSheet = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("home_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = strings.settingsTitle,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            navItems.forEach { (index, label, icon) ->
                                NavigationBarItem(
                                    selected = pagerState.currentPage == index,
                                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("nav_bar_tab_$index")
                                )
                            }
                        }
                    }
                },
                floatingActionButton = {
                    if (pagerState.currentPage == 0) {
                        FloatingActionButton(
                            onClick = { showDirectorySheet = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("new_chat_fab")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddComment,
                                contentDescription = strings.newChatLabel
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isPreparingStoryMedia) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "Preparing selected gallery photo or video for story…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    incomingCall?.let { call ->
                        IncomingCallBanner(
                            call = call,
                            onAccept = {
                                onStartCall(call.callerId, call.callTypeEnum, call.id)
                            },
                            onDecline = {
                                viewModel.declineIncomingCall(call.id)
                            }
                        )
                    }

                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 0,
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> ChatsTab(
                                conversationsState = conversationsState,
                                groupsState = groupsState,
                                dirById = dirById,
                                savedContactIds = savedContactIds,
                                blockedUserIds = blockedUserIds,
                                lockedChatIds = securitySettings.lockedChatIds,
                                hasOsNotificationPermission = hasOsNotificationPermission,
                                notificationsEnabled = securitySettings.notificationsEnabled,
                                onEnableNotificationsClick = onEnableNotifications,
                                currentUserId = currentUserId,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                onOpenChat = { convId, peerId, title ->
                                    if (convId in securitySettings.lockedChatIds) {
                                        onRequestChatUnlock(convId, title) {
                                            onOpenChat(convId, peerId)
                                        }
                                    } else {
                                        onOpenChat(convId, peerId)
                                    }
                                },
                                onOpenGroup = { groupId, title ->
                                    val lockKey = "grp_$groupId"
                                    if (lockKey in securitySettings.lockedChatIds) {
                                        onRequestChatUnlock(lockKey, title) {
                                            onOpenGroup(groupId)
                                        }
                                    } else {
                                        onOpenGroup(groupId)
                                    }
                                },
                                onToggleChatLock = onToggleChatLock,
                                onEditChatName = { editingConvItem = it },
                                onDeleteChat = { deletingConvItem = it },
                                onEditGroup = { editingGroupItem = it },
                                onDeleteGroup = { deletingGroupItem = it },
                                onLeaveGroup = { leavingGroupItem = it },
                                onOpenNewChat = { showDirectorySheet = true },
                                onOpenCreateGroup = { showCreateGroupSheet = true }
                            )

                            1 -> StatusUpdatesTab(
                                myProfile = myProfile,
                                currentUserId = currentUserId,
                                directoryState = directoryState,
                                savedContactIds = savedContactIds,
                                blockedUserIds = blockedUserIds,
                                statusesState = visibleStatusesState,
                                liveStoriesState = visibleLiveStoriesState,
                                onAddStoryClick = ::openStoryFromGallery,
                                onTextStoryClick = ::openTextStoryComposer,
                                onOpenStoryGroup = { group -> viewingStoryAuthorId = group.authorId },
                                onUpdateMyStatus = { presence, mood, msg ->
                                    viewModel.updateStatusBanner(presence, mood, msg)
                                },
                                onPublishStatus = { mood, text ->
                                    viewModel.publishStatusFeedUpdate(mood, text)
                                },
                                onDeleteStatus = { statusId ->
                                    viewModel.deleteStatusUpdate(statusId)
                                },
                                onPeerClick = { peer ->
                                    viewModel.startChatWithPeer(peer) { convId, peerId ->
                                        onOpenChat(convId, peerId)
                                    }
                                }
                            )

                            2 -> CallsTab(
                                myProfile = myProfile,
                                dirById = dirById,
                                currentUserId = currentUserId,
                                callsState = callsState,
                                onStartCall = { peerId, type ->
                                    onStartCall(peerId, type, null)
                                },
                                onDeleteCall = { callId -> viewModel.deleteCallHistoryItem(callId) },
                                onRequestClearCallHistory = { showClearCallHistoryDialog = true },
                                onPreviewDp = { previewDpTarget = it },
                                onOpenJoinCallLink = onOpenJoinCallLinkDialog
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDirectorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showDirectorySheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.newChatLabel,
                        style = MaterialTheme.typography.titleLarge
                    )
                    TextButton(
                        onClick = {
                            showDirectorySheet = false
                            onOpenAddContactSheet()
                        },
                        modifier = Modifier.testTag("directory_add_contact_button")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.tabContacts)
                    }
                }
                Text(
                    text = strings.encryptedLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (val state = directoryState) {
                    UiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    is UiState.Success -> {
                        val peers = state.data.filter { it.uid != currentUserId }
                        if (peers.isEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No other users registered yet. Sign in on another device or emulator with a Google account to exchange E2EE messages and calls in real time.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(bottom = 28.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(peers, key = { it.uid }) { peer ->
                                    DirectoryPeerRow(
                                        peer = peer,
                                        isSavedContact = peer.uid in savedContactIds,
                                        isBlocked = peer.uid in blockedUserIds,
                                        onChatClick = {
                                            showDirectorySheet = false
                                            viewModel.startChatWithPeer(peer) { convId, peerId ->
                                                onOpenChat(convId, peerId)
                                            }
                                        },
                                        onAudioCallClick = {
                                            showDirectorySheet = false
                                            onStartCall(peer.uid, CallType.AUDIO, null)
                                        },
                                        onVideoCallClick = {
                                            showDirectorySheet = false
                                            onStartCall(peer.uid, CallType.VIDEO, null)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showCreateStorySheet) {
        val dirUsers = (directoryState as? UiState.Success)?.data ?: emptyList()
        CreateStoryBottomSheet(
            directoryUsers = dirUsers,
            currentUserId = currentUserId,
            initialStoryType = prefilledStoryType,
            initialPhotoBase64 = prefilledStoryPhoto,
            initialVideoBase64 = prefilledStoryVideo,
            onDismiss = { showCreateStorySheet = false },
            onPublishStory = { storyType, caption, gradient, imageBase64, videoBase64, mentionedIds, mentionedHandles ->
                viewModel.publishLiveStory(
                    storyType = storyType,
                    caption = caption,
                    gradient = gradient,
                    imageBase64 = imageBase64,
                    videoBase64 = videoBase64,
                    mentionedUserIds = mentionedIds,
                    mentionedHandles = mentionedHandles
                )
            }
        )
    }

    val activeGroups = (liveStoriesState as? UiState.Success)?.data ?: emptyList()
    val viewingAuthorId = viewingStoryAuthorId
    if (viewingAuthorId != null) {
        val groupIndex = activeGroups.indexOfFirst { it.authorId == viewingAuthorId }
        val activeGroup = activeGroups.getOrNull(groupIndex)
        if (activeGroup != null) {
            val dirUsers = (directoryState as? UiState.Success)?.data ?: emptyList()
            StoryViewerOverlay(
                group = activeGroup,
                currentUserId = currentUserId,
                directoryUsers = dirUsers,
                onMarkViewed = { story -> viewModel.markStoryViewed(story) },
                onDeleteStory = { storyId -> viewModel.deleteLiveStory(storyId) },
                onReplyPrivately = { story, text -> viewModel.replyToStoryPrivately(story, text) },
                onMentionStoryToPeer = { peer, story -> viewModel.shareStoryMentionToPeer(peer, story) },
                onAdvanceToNextGroup = {
                    val nextGroup = activeGroups.getOrNull(groupIndex + 1)
                    viewingStoryAuthorId = nextGroup?.authorId
                },
                onBackToPrevGroup = {
                    val prevGroup = activeGroups.getOrNull(groupIndex - 1)
                    viewingStoryAuthorId = prevGroup?.authorId ?: viewingAuthorId
                },
                onDismiss = { viewingStoryAuthorId = null }
            )
        } else {
            LaunchedEffect(viewingAuthorId, activeGroups) {
                viewingStoryAuthorId = null
            }
        }
    }

    if (showProfileSheet) {
        ProfileControlSheet(
            myProfile = myProfile,
            privateAccount = privateAccount,
            onDismiss = { showProfileSheet = false },
            onSaveProfile = { name, handle, statusMsg, mood, presence, discoverable, avatarBase64, clearAvatar, readReceipts, lastSeenVis, photoVis, statusVis ->
                viewModel.updateProfileControls(
                    displayName = name,
                    handle = handle,
                    statusMessage = statusMsg,
                    statusMood = mood,
                    presence = presence,
                    discoverable = discoverable,
                    avatarBase64 = avatarBase64,
                    clearAvatar = clearAvatar,
                    readReceiptsEnabled = readReceipts,
                    lastSeenVisibility = lastSeenVis,
                    photoVisibility = photoVis,
                    statusVisibility = statusVis
                )
            }
        )
    }

    if (showCreateGroupSheet) {
        val dirPeers = (directoryState as? UiState.Success)?.data?.filter { it.uid != currentUserId } ?: emptyList()
        CreateGroupSheet(
            peers = dirPeers,
            onCreateGroup = { name, desc, avatarBase64, chosenPeers ->
                viewModel.createGroupChat(
                    name = name,
                    description = desc,
                    avatarBase64 = avatarBase64,
                    selectedPeers = chosenPeers,
                    onGroupCreated = onOpenGroup
                )
            },
            onInviteFriendsClick = { showInviteSheet = true },
            onDismiss = { showCreateGroupSheet = false }
        )
    }

    if (showInviteSheet) {
        val dirUsers = (directoryState as? UiState.Success)?.data ?: emptyList()
        InviteFriendsSheet(
            myProfile = myProfile,
            directoryUsers = dirUsers,
            currentUserId = currentUserId,
            onStartChatWithPeer = { peer ->
                viewModel.startChatWithPeer(peer) { convId, peerId ->
                    onOpenChat(convId, peerId)
                }
            },
            onCopiedMessage = { msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
            },
            onDismiss = { showInviteSheet = false }
        )
    }

    editingConvItem?.let { item ->
        val peerOriginalName = item.conversation.peerNameFor(currentUserId).ifBlank { item.peerName }
        EditChatNameDialog(
            initialName = item.customNickname ?: "",
            peerDefaultName = peerOriginalName,
            onSave = { newName ->
                viewModel.updateConversationNickname(item.conversation.id, newName)
            },
            onDismiss = { editingConvItem = null }
        )
    }

    deletingConvItem?.let { item ->
        ConfirmDeleteDialog(
            title = strings.deleteLabel,
            message = item.peerName,
            onConfirm = { viewModel.deleteConversation(item.conversation.id) },
            onDismiss = { deletingConvItem = null }
        )
    }

    editingGroupItem?.let { item ->
        EditGroupSummaryDialog(
            initialName = item.group.name,
            initialDescription = item.group.description,
            onSave = { name, desc ->
                viewModel.updateGroupDetails(item.group.id, name, desc)
            },
            onDismiss = { editingGroupItem = null }
        )
    }

    deletingGroupItem?.let { item ->
        ConfirmDeleteDialog(
            title = strings.deleteLabel,
            message = item.group.name,
            onConfirm = { viewModel.deleteGroup(item.group.id) },
            onDismiss = { deletingGroupItem = null }
        )
    }

    leavingGroupItem?.let { item ->
        ConfirmDeleteDialog(
            title = strings.leaveLabel,
            message = item.group.name,
            confirmLabel = strings.leaveLabel,
            onConfirm = { viewModel.leaveGroup(item.group.id, item.group.memberIds) },
            onDismiss = { leavingGroupItem = null }
        )
    }

    if (showClearCallHistoryDialog) {
        val currentCalls = (callsState as? UiState.Success)?.data ?: emptyList()
        ConfirmDeleteDialog(
            title = strings.clearLabel,
            message = strings.tabCalls,
            confirmLabel = strings.clearLabel,
            onConfirm = { viewModel.clearCallHistory(currentCalls) },
            onDismiss = { showClearCallHistoryDialog = false }
        )
    }

    previewDpTarget?.let { target ->
        val isSaved = target.uid in savedContactIds
        val hideAvatar = target.uid != currentUserId &&
            !isVisibleForAudience(target.photoVisibilityEnum, isSaved)
        ProfileAvatarViewerDialog(
            name = target.displayName,
            handle = target.handle,
            photoUrl = target.photoUrl,
            avatarBase64 = target.avatarBase64,
            statusMessage = target.statusMessage,
            presence = target.presenceEnum,
            hideAvatar = hideAvatar,
            onDismiss = { previewDpTarget = null }
        )
    }

    if (showSettingsHubSheet) {
        SettingsHubBottomSheet(
            myProfile = myProfile,
            themeSettings = themeSettings,
            securitySettings = securitySettings,
            hasOsNotificationPermission = hasOsNotificationPermission,
            pendingScheduledCount = pendingScheduledCount,
            savedContactsCount = savedContactIds.size,
            blockedUsersCount = blockedUserIds.size,
            fingerprint = keyBundle?.keyFingerprint ?: "...",
            onDismiss = { showSettingsHubSheet = false },
            onOpenProfile = { showSettingsHubSheet = false; showProfileSheet = true },
            onOpenThemeAndTextStyles = { showSettingsHubSheet = false; onOpenThemeSheet() },
            onOpenLanguagePicker = { showSettingsHubSheet = false; onOpenLanguageSheet() },
            onOpenPrivacyAndAppLock = { showSettingsHubSheet = false; onOpenPrivacySheet() },
            onEnableNotifications = { showSettingsHubSheet = false; onEnableNotifications() },
            onOpenScheduledMessages = { showSettingsHubSheet = false; onOpenScheduleSheet() },
            onOpenAddContact = { showSettingsHubSheet = false; onOpenAddContactSheet() },
            onOpenCreateGroup = { showSettingsHubSheet = false; showCreateGroupSheet = true },
            onOpenDataBackup = { showSettingsHubSheet = false; onOpenBackupSheet() },
            onOpenInviteFriends = { showSettingsHubSheet = false; showInviteSheet = true },
            onRotateKeys = { showSettingsHubSheet = false; viewModel.rotateKeys() },
            onSignOut = {
                showSettingsHubSheet = false
                signOutFromToKer(
                    credentialManager = credentialManager,
                    scope = scope,
                    beforeSignOut = { viewModel.markOfflineBeforeSignOut() }
                )
            }
        )
    }
}

@Composable
private fun ChatsTab(
    conversationsState: UiState<List<ConversationListItemUi>>,
    groupsState: UiState<List<GroupListItemUi>>,
    dirById: Map<String, DirectoryUser>,
    savedContactIds: List<String>,
    blockedUserIds: List<String>,
    lockedChatIds: Set<String>,
    hasOsNotificationPermission: Boolean,
    notificationsEnabled: Boolean,
    onEnableNotificationsClick: () -> Unit,
    currentUserId: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenChat: (String, String, String) -> Unit,
    onOpenGroup: (String, String) -> Unit,
    onToggleChatLock: (String) -> Unit,
    onEditChatName: (ConversationListItemUi) -> Unit,
    onDeleteChat: (ConversationListItemUi) -> Unit,
    onEditGroup: (GroupListItemUi) -> Unit,
    onDeleteGroup: (GroupListItemUi) -> Unit,
    onLeaveGroup: (GroupListItemUi) -> Unit,
    onOpenNewChat: () -> Unit,
    onOpenCreateGroup: () -> Unit
) {
    val strings = LocalAppStrings.current
    Column(modifier = Modifier.fillMaxSize()) {
        if (!hasOsNotificationPermission || !notificationsEnabled) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = strings.notificationsLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onEnableNotificationsClick,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("enable_notifications_banner_button")
                    ) {
                        Text(strings.confirmLabel)
                    }
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(strings.searchChatsHint) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = strings.clearLabel)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("search_chats_input")
        )

        val convData = (conversationsState as? UiState.Success)?.data
        val groupData = (groupsState as? UiState.Success)?.data

        if (conversationsState is UiState.Loading && groupsState is UiState.Loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (conversationsState is UiState.Error && groupData.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = conversationsState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            val filteredConvs = (convData ?: emptyList()).filter {
                searchQuery.isBlank() ||
                    it.peerName.contains(searchQuery, ignoreCase = true) ||
                    it.lastMessagePreview.contains(searchQuery, ignoreCase = true)
            }
            val filteredGroups = (groupData ?: emptyList()).filter {
                searchQuery.isBlank() ||
                    it.group.name.contains(searchQuery, ignoreCase = true) ||
                    it.lastMessagePreview.contains(searchQuery, ignoreCase = true)
            }

            if (filteredConvs.isEmpty() && filteredGroups.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_empty_chats),
                        contentDescription = strings.noChatsYet,
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(170.dp)
                            .clip(RoundedCornerShape(24.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = strings.noChatsYet,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.encryptedLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onOpenNewChat,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("empty_start_chat_button")
                        ) {
                            Icon(Icons.Default.AddComment, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.newChatLabel)
                        }
                        OutlinedButton(
                            onClick = onOpenCreateGroup,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("empty_create_group_button")
                        ) {
                            Icon(Icons.Default.GroupAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.newGroupLabel)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    if (filteredGroups.isNotEmpty()) {
                        item(key = "section_groups") {
                            Text(
                                text = strings.tabGroups,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                        items(filteredGroups, key = { "grp_${it.group.id}" }) { groupItem ->
                            val grpLockKey = "grp_${groupItem.group.id}"
                            val isLocked = grpLockKey in lockedChatIds
                            GroupRowCard(
                                item = groupItem,
                                isCreator = groupItem.group.createdBy == currentUserId,
                                isLocked = isLocked,
                                onClick = { onOpenGroup(groupItem.group.id, groupItem.group.name) },
                                onToggleLock = { onToggleChatLock(grpLockKey) },
                                onEditGroup = { onEditGroup(groupItem) },
                                onDeleteGroup = { onDeleteGroup(groupItem) },
                                onLeaveGroup = { onLeaveGroup(groupItem) }
                            )
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                modifier = Modifier.padding(start = 80.dp, end = 16.dp)
                            )
                        }
                    }

                    if (filteredConvs.isNotEmpty()) {
                        if (filteredGroups.isNotEmpty()) {
                            item(key = "section_direct") {
                                Text(
                                    text = strings.tabChats,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                                )
                            }
                        }
                        items(filteredConvs, key = { it.conversation.id }) { item ->
                            val isLocked = item.conversation.id in lockedChatIds
                            val isSaved = item.peerId in savedContactIds
                            val isBlocked = item.peerId in blockedUserIds
                            val photoVis = dirById[item.peerId]?.photoVisibilityEnum ?: PrivacyAudience.EVERYONE
                            val hideAvatar = !isVisibleForAudience(photoVis, isSaved)
                            ConversationRowCard(
                                item = item,
                                isLocked = isLocked,
                                isBlocked = isBlocked,
                                hideAvatar = hideAvatar,
                                onClick = { onOpenChat(item.conversation.id, item.peerId, item.peerName) },
                                onToggleLock = { onToggleChatLock(item.conversation.id) },
                                onEditChatName = { onEditChatName(item) },
                                onDeleteChat = { onDeleteChat(item) }
                            )
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                modifier = Modifier.padding(start = 80.dp, end = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupRowCard(
    item: GroupListItemUi,
    isCreator: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit,
    onToggleLock: () -> Unit,
    onEditGroup: () -> Unit,
    onDeleteGroup: () -> Unit,
    onLeaveGroup: () -> Unit
) {
    val strings = LocalAppStrings.current
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
            .testTag("group_card_${item.group.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatarWithPresence(
                name = item.group.name,
                photoUrl = null,
                presence = PresenceStatus.ONLINE,
                size = 52.dp,
                showPresenceDot = false,
                avatarBase64 = item.group.avatarBase64
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = strings.newGroupLabel,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = item.group.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = formatRelativeTimestamp(item.group.lastMessageAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (item.group.disappearingSeconds > 0L) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = strings.disappearingMessagesTitle,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    if (isLocked) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = strings.lockAppLabel,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = if (isLocked) strings.lockAppLabel
                        else "${item.memberCount} • ${item.lastMessagePreview}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("group_card_more_${item.group.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = strings.settingsTitle,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isLocked) strings.unlockLabel else strings.lockAppLabel) },
                        leadingIcon = {
                            Icon(
                                if (isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            onToggleLock()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.editLabel) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onEditGroup()
                        }
                    )
                    if (isCreator) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = strings.deleteLabel,
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
                                showMenu = false
                                onDeleteGroup()
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(strings.leaveLabel) },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                            },
                            onClick = {
                                showMenu = false
                                onLeaveGroup()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRowCard(
    item: ConversationListItemUi,
    isLocked: Boolean,
    isBlocked: Boolean,
    hideAvatar: Boolean,
    onClick: () -> Unit,
    onToggleLock: () -> Unit,
    onEditChatName: () -> Unit,
    onDeleteChat: () -> Unit
) {
    val strings = LocalAppStrings.current
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
            .testTag("conversation_card_${item.conversation.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatarWithPresence(
                name = item.peerName,
                photoUrl = item.peerPhotoUrl,
                presence = item.peerPresence,
                size = 52.dp,
                avatarBase64 = item.peerAvatarBase64,
                hideAvatar = hideAvatar
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.peerName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isBlocked) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = strings.blockedLabel,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = formatRelativeTimestamp(item.conversation.lastMessageAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (item.conversation.disappearingSeconds > 0L) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = strings.disappearingMessagesTitle,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    if (isLocked) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = strings.lockAppLabel,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    if (!isLocked && item.isLastMessageMine) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = if (isLocked) strings.lockAppLabel else item.lastMessagePreview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("conversation_card_more_${item.conversation.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = strings.settingsTitle,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isLocked) strings.unlockLabel else strings.lockAppLabel) },
                        leadingIcon = {
                            Icon(
                                if (isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            onToggleLock()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.renameLabel) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onEditChatName()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = strings.deleteLabel,
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
                            showMenu = false
                            onDeleteChat()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectoryPeerRow(
    peer: DirectoryUser,
    isSavedContact: Boolean,
    isBlocked: Boolean,
    onChatClick: () -> Unit,
    onAudioCallClick: () -> Unit,
    onVideoCallClick: () -> Unit
) {
    val hideAvatar = !isVisibleForAudience(peer.photoVisibilityEnum, isSavedContact)
    val lastSeenLabel = formatLastSeen(
        lastSeenAt = peer.lastSeenAt,
        presence = peer.presenceEnum,
        visibility = peer.lastSeenVisibilityEnum,
        isSavedContact = isSavedContact
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onChatClick)
            .testTag("directory_peer_${peer.uid}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatarWithPresence(
                name = peer.displayName,
                photoUrl = peer.photoUrl,
                presence = peer.presenceEnum,
                size = 46.dp,
                avatarBase64 = peer.avatarBase64,
                hideAvatar = hideAvatar
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = peer.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isSavedContact) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Saved contact",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (isBlocked) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = "Blocked",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = "${peer.handle} • $lastSeenLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onAudioCallClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("directory_audio_call_${peer.uid}")
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = stringResource(R.string.audio_call_cd),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onVideoCallClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("directory_video_call_${peer.uid}")
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = stringResource(R.string.video_call_cd),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun StatusUpdatesTab(
    myProfile: DirectoryUser?,
    currentUserId: String,
    directoryState: UiState<List<DirectoryUser>>,
    savedContactIds: List<String>,
    blockedUserIds: List<String>,
    statusesState: UiState<List<StatusUpdate>>,
    liveStoriesState: UiState<List<AuthorStoriesGroup>>,
    onAddStoryClick: () -> Unit,
    onTextStoryClick: () -> Unit,
    onOpenStoryGroup: (AuthorStoriesGroup) -> Unit,
    onUpdateMyStatus: (PresenceStatus, String, String) -> Unit,
    onPublishStatus: (String, String) -> Unit,
    onDeleteStatus: (String) -> Unit,
    onPeerClick: (DirectoryUser) -> Unit
) {
    val strings = LocalAppStrings.current
    var draftStatus by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf(myProfile?.statusMood ?: "Available") }
    var selectedPresence by remember(myProfile?.presence) {
        mutableStateOf(myProfile?.presenceEnum ?: PresenceStatus.ONLINE)
    }
    val moods = listOf("✨", "💼", "📞", "🚀", "🔒")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(22.dp),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                LiveStoriesCarousel(
                    storiesState = liveStoriesState,
                    currentUserId = currentUserId,
                    onAddStoryClick = onAddStoryClick,
                    onTextStoryClick = onTextStoryClick,
                    onOpenGroup = onOpenStoryGroup,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
        }

        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(22.dp),
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        UserAvatarWithPresence(
                            name = myProfile?.displayName ?: strings.profileLabel,
                            photoUrl = myProfile?.photoUrl,
                            presence = selectedPresence,
                            size = 48.dp,
                            avatarBase64 = myProfile?.avatarBase64
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.statusLabel,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = myProfile?.statusMessage?.ifBlank {
                                    localizedPresence(selectedPresence, strings)
                                } ?: localizedPresence(selectedPresence, strings),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(PresenceStatus.ONLINE, PresenceStatus.AWAY, PresenceStatus.BUSY).forEach { p ->
                            FilterChip(
                                selected = selectedPresence == p,
                                onClick = {
                                    selectedPresence = p
                                    onUpdateMyStatus(p, selectedMood, myProfile?.statusMessage ?: "")
                                },
                                label = { Text(localizedPresence(p, strings)) },
                                modifier = Modifier.testTag("status_presence_${p.name.lowercase()}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        moods.forEach { mood ->
                            FilterChip(
                                selected = selectedMood == mood,
                                onClick = { selectedMood = mood },
                                label = { Text(mood) },
                                modifier = Modifier.testTag("status_mood_$mood")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = draftStatus,
                            onValueChange = { if (it.length <= 180) draftStatus = it },
                            placeholder = { Text(strings.statusLabel) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("status_composer_input")
                        )
                        Button(
                            onClick = {
                                if (draftStatus.isNotBlank()) {
                                    onPublishStatus(selectedMood, draftStatus)
                                    draftStatus = ""
                                }
                            },
                            enabled = draftStatus.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("publish_status_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.sendButtonLabel)
                        }
                    }
                }
            }
        }

        if (directoryState is UiState.Success) {
            val activePeers = directoryState.data.filter {
                it.uid != currentUserId &&
                    it.uid !in blockedUserIds &&
                    isVisibleForAudience(it.statusVisibilityEnum, it.uid in savedContactIds)
            }
            if (activePeers.isNotEmpty()) {
                item {
                    Text(
                        text = strings.tabContacts,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(activePeers, key = { it.uid }) { peer ->
                            val isSaved = peer.uid in savedContactIds
                            val hideAvatar = !isVisibleForAudience(peer.photoVisibilityEnum, isSaved)
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .width(148.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { onPeerClick(peer) }
                                    .testTag("live_peer_status_${peer.uid}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    UserAvatarWithPresence(
                                        name = peer.displayName,
                                        photoUrl = peer.photoUrl,
                                        presence = peer.presenceEnum,
                                        size = 48.dp,
                                        avatarBase64 = peer.avatarBase64,
                                        hideAvatar = hideAvatar
                                    )
                                    Text(
                                        text = peer.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = localizedPresence(peer.presenceEnum, strings),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = strings.statusLabel,
                style = MaterialTheme.typography.titleMedium
            )
        }

        val dirAvatarMap = (directoryState as? UiState.Success)?.data
            ?.associate { it.uid to it.avatarBase64 }
            ?: emptyMap()

        when (statusesState) {
            UiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Text(statusesState.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is UiState.Success -> {
                if (statusesState.data.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = strings.noStoriesYet,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(statusesState.data, key = { it.id }) { status ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(18.dp),
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                UserAvatarWithPresence(
                                    name = status.authorName,
                                    photoUrl = status.authorPhotoUrl,
                                    presence = PresenceStatus.ONLINE,
                                    size = 42.dp,
                                    showPresenceDot = false,
                                    avatarBase64 = dirAvatarMap[status.authorId]
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = status.authorName,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = formatRelativeTimestamp(status.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = status.text,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = status.mood,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                if (status.authorId == currentUserId) {
                                    IconButton(
                                        onClick = { onDeleteStatus(status.id) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = strings.deleteLabel,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallsTab(
    myProfile: DirectoryUser?,
    dirById: Map<String, DirectoryUser>,
    currentUserId: String,
    callsState: UiState<List<CallSession>>,
    onStartCall: (peerId: String, callType: CallType) -> Unit,
    onDeleteCall: (String) -> Unit,
    onRequestClearCallHistory: () -> Unit,
    onPreviewDp: (DirectoryUser) -> Unit,
    onOpenJoinCallLink: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    var selectedFilter by rememberSaveable { mutableStateOf(CallHistoryFilter.ALL) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(22.dp),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(strings.linkLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                shareCallLink(
                                    context = context,
                                    hostUid = currentUserId,
                                    hostName = myProfile?.displayName ?: "ToKer",
                                    callType = CallType.AUDIO,
                                    chooserTitle = strings.shareLabel
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_audio_call_link_button")
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.audioLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = {
                                shareCallLink(
                                    context = context,
                                    hostUid = currentUserId,
                                    hostName = myProfile?.displayName ?: "ToKer",
                                    callType = CallType.VIDEO,
                                    chooserTitle = strings.shareLabel
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_video_call_link_button")
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.videoLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(
                            onClick = onOpenJoinCallLink,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("join_call_link_vault_button")
                        ) {
                            Icon(Icons.Default.Link, contentDescription = strings.joinLabel, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.joinLabel, maxLines = 1)
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.tabCalls,
                        style = MaterialTheme.typography.titleMedium
                    )
                    val totalCalls = (callsState as? UiState.Success)?.data?.size ?: 0
                    if (totalCalls > 0) {
                        TextButton(
                            onClick = onRequestClearCallHistory,
                            modifier = Modifier.testTag("clear_call_history_button")
                        ) {
                            Text(strings.clearLabel, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CallHistoryFilter.entries.forEach { filter ->
                        val filterLabel = when (filter) {
                            CallHistoryFilter.ALL -> strings.allLabel
                            CallHistoryFilter.MISSED -> strings.missedLabel
                            CallHistoryFilter.AUDIO -> strings.audioLabel
                            CallHistoryFilter.VIDEO -> strings.videoLabel
                        }
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filterLabel) },
                            modifier = Modifier.testTag("call_filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }
        }

        when (callsState) {
            UiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Text(callsState.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is UiState.Success -> {
                val filteredCalls = callsState.data.filter { call ->
                    when (selectedFilter) {
                        CallHistoryFilter.ALL -> true
                        CallHistoryFilter.MISSED -> call.statusEnum == CallStatus.MISSED || call.statusEnum == CallStatus.DECLINED
                        CallHistoryFilter.AUDIO -> call.callTypeEnum == CallType.AUDIO
                        CallHistoryFilter.VIDEO -> call.callTypeEnum == CallType.VIDEO
                    }
                }
                if (filteredCalls.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = strings.noCallsYet,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(filteredCalls, key = { it.id }) { call ->
                        val peerId = call.peerIdFor(currentUserId)
                        val peerName = call.peerNameFor(currentUserId)
                        val isOutgoing = call.callerId == currentUserId
                        val dirLabel = if (isOutgoing) strings.outgoingLabel else strings.incomingLabel
                        val typeLabel = if (call.callTypeEnum == CallType.VIDEO) strings.videoLabel else strings.audioLabel
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val peerDir = dirById[peerId]
                            Row(
                                modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            onPreviewDp(
                                                peerDir ?: DirectoryUser(
                                                    uid = peerId,
                                                    displayName = peerName,
                                                    photoUrl = call.peerPhotoFor(currentUserId)
                                                )
                                            )
                                        }
                                ) {
                                    UserAvatarWithPresence(
                                        name = peerName,
                                        photoUrl = peerDir?.photoUrl ?: call.peerPhotoFor(currentUserId),
                                        presence = peerDir?.presenceEnum ?: PresenceStatus.OFFLINE,
                                        size = 44.dp,
                                        showPresenceDot = false,
                                        avatarBase64 = peerDir?.avatarBase64
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = peerName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "$dirLabel • $typeLabel • ${formatRelativeTimestamp(call.createdAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onStartCall(peerId, CallType.AUDIO) },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Call,
                                        contentDescription = strings.audioLabel,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { onStartCall(peerId, CallType.VIDEO) },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Videocam,
                                        contentDescription = strings.videoLabel,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                if (call.statusEnum.isTerminal) {
                                    IconButton(
                                        onClick = { onDeleteCall(call.id) },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .testTag("delete_call_${call.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = strings.deleteLabel,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsHubBottomSheet(
    myProfile: DirectoryUser?,
    themeSettings: ThemeSettings,
    securitySettings: SecuritySettings,
    hasOsNotificationPermission: Boolean,
    pendingScheduledCount: Int,
    savedContactsCount: Int,
    blockedUsersCount: Int,
    fingerprint: String,
    onDismiss: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenThemeAndTextStyles: () -> Unit,
    onOpenLanguagePicker: () -> Unit,
    onOpenPrivacyAndAppLock: () -> Unit,
    onEnableNotifications: () -> Unit,
    onOpenScheduledMessages: () -> Unit,
    onOpenAddContact: () -> Unit,
    onOpenCreateGroup: () -> Unit,
    onOpenDataBackup: () -> Unit,
    onOpenInviteFriends: () -> Unit,
    onRotateKeys: () -> Unit,
    onSignOut: () -> Unit
) {
    val strings = LocalAppStrings.current
    val currentLanguage = remember(themeSettings.languageCode) {
        AppLanguage.fromCode(themeSettings.languageCode)
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(strings.settingsTitle, style = MaterialTheme.typography.titleLarge)
            }

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenProfile)
                    .testTag("settings_hub_profile_card")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    UserAvatarWithPresence(
                        name = myProfile?.displayName ?: strings.profileLabel,
                        photoUrl = myProfile?.photoUrl,
                        presence = myProfile?.presenceEnum ?: PresenceStatus.ONLINE,
                        size = 48.dp,
                        avatarBase64 = myProfile?.avatarBase64
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = myProfile?.displayName ?: strings.profileLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${myProfile?.handle ?: "@toker"} • ${localizedPresence(myProfile?.presenceEnum, strings)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = strings.profileLabel
                    )
                }
            }

            SettingsHubRow(
                icon = Icons.Default.Palette,
                title = strings.themeAppearanceLabel,
                subtitle = "${themeSettings.accentTheme.label} • ${themeSettings.fontStyle.label}",
                tag = "settings_hub_theme_text",
                onClick = onOpenThemeAndTextStyles
            )
            SettingsHubRow(
                icon = Icons.Default.Language,
                title = strings.languageLabel,
                subtitle = "${currentLanguage.nativeLabel} (${currentLanguage.englishName})",
                tag = "settings_hub_language",
                onClick = onOpenLanguagePicker
            )
            SettingsHubRow(
                icon = Icons.Default.Shield,
                title = strings.privacyBackupLabel,
                subtitle = "${if (securitySettings.appLockEnabled) strings.lockAppLabel else strings.unlockLabel} • ${securitySettings.lockedChatIds.size} • $blockedUsersCount ${strings.blockedLabel}",
                tag = "settings_hub_privacy_lock",
                onClick = onOpenPrivacyAndAppLock
            )
            SettingsHubRow(
                icon = Icons.Default.Notifications,
                title = strings.notificationsLabel,
                subtitle = if (hasOsNotificationPermission && securitySettings.notificationsEnabled) {
                    strings.onlineLabel
                } else {
                    strings.disappearingMessagesOff
                },
                tag = "settings_hub_notifications",
                onClick = onEnableNotifications
            )
            SettingsHubRow(
                icon = Icons.Default.ScheduleSend,
                title = strings.scheduleLabel,
                subtitle = if (pendingScheduledCount > 0) "$pendingScheduledCount" else strings.disappearingMessagesOff,
                tag = "settings_hub_scheduled",
                onClick = onOpenScheduledMessages
            )
            SettingsHubRow(
                icon = Icons.Default.PersonAdd,
                title = strings.tabContacts,
                subtitle = "$savedContactsCount",
                tag = "settings_hub_add_contact",
                onClick = onOpenAddContact
            )
            SettingsHubRow(
                icon = Icons.Default.GroupAdd,
                title = strings.newGroupLabel,
                subtitle = strings.onlyAdminsCanMessageToggle,
                tag = "settings_hub_create_group",
                onClick = onOpenCreateGroup
            )
            SettingsHubRow(
                icon = Icons.Default.Backup,
                title = strings.backupLabel,
                subtitle = "${strings.cloudLabel} • ${strings.localLabel}",
                tag = "settings_hub_backup",
                onClick = onOpenDataBackup
            )
            SettingsHubRow(
                icon = Icons.Default.Key,
                title = strings.keysLabel,
                subtitle = fingerprint,
                tag = "settings_hub_rotate_keys",
                onClick = onRotateKeys
            )
            SettingsHubRow(
                icon = Icons.Default.Share,
                title = strings.shareAppLabel,
                subtitle = myProfile?.handle ?: "@toker",
                tag = "settings_hub_invite",
                onClick = onOpenInviteFriends
            )
            OutlinedButton(
                onClick = onSignOut,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("settings_hub_sign_out")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.signOutLabel)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun localizedPresence(presence: PresenceStatus?, strings: AppStrings): String = when (presence) {
    PresenceStatus.ONLINE -> strings.onlineLabel
    PresenceStatus.AWAY -> strings.awayLabel
    PresenceStatus.BUSY -> strings.busyLabel
    PresenceStatus.OFFLINE, null -> strings.offlineLabel
}

@Composable
private fun SettingsHubRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
