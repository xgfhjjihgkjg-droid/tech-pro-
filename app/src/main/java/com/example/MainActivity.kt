package com.example

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.crypto.CryptoEngine
import com.example.data.BiometricAuthHelper
import com.example.data.CallLinkHelper
import com.example.data.CallType
import com.example.data.ChatRepository
import com.example.data.LocalAppStrings
import com.example.data.ParsedCallLink
import com.example.data.PresenceStatus
import com.example.data.ScheduledMessageManager
import com.example.data.SecurityPreferencesRepository
import com.example.data.SecuritySettings
import com.example.data.ThemePreferencesRepository
import com.example.data.ThemeSettings
import com.example.data.UiState
import com.example.data.authStateFlow
import com.example.data.signOutFromToKer
import com.example.data.stringsForLanguage
import com.example.notifications.ToKerNotificationHelper
import com.example.ui.screens.AddContactSheet
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CallScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ChatUnlockPromptDialog
import com.example.ui.screens.DataBackupSheet
import com.example.ui.screens.GroupChatScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.JoinCallLinkDialog
import com.example.ui.screens.LanguagePickerBottomSheet
import com.example.ui.screens.MinimizedCallFloatingOverlay
import com.example.ui.screens.PrivacyAndSecuritySheet
import com.example.ui.screens.ScheduleMessageSheet
import com.example.ui.screens.SetupPinDialog
import com.example.ui.screens.ThemeCustomizationSheet
import com.example.ui.theme.ToKerTheme
import com.example.viewmodel.CallViewModel
import com.example.viewmodel.ChatViewModel
import com.example.viewmodel.GroupChatViewModel
import com.example.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class ChatRoute(val conversationId: String, val peerId: String)

@Serializable
data class GroupRoute(val groupId: String)

@Serializable
data class CallRoute(
    val peerId: String,
    val callTypeName: String,
    val incomingCallId: String? = null
)

sealed interface NotificationNavTarget {
    data class Chat(val conversationId: String, val peerId: String) : NotificationNavTarget
    data class Group(val groupId: String) : NotificationNavTarget
    data class Call(val peerId: String, val callTypeName: String, val callId: String?) : NotificationNavTarget
}

class MainActivity : FragmentActivity() {
    private val incomingCallLinkFlow = MutableStateFlow<ParsedCallLink?>(null)
    private val incomingNotificationTargetFlow = MutableStateFlow<NotificationNavTarget?>(null)
    private var hasOngoingCallForPip: Boolean = false
    private var ongoingCallIsVideo: Boolean = true
    private var isInPipModeState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ToKerNotificationHelper.ensureNotificationChannels(this)
        parseIncomingIntent(intent)
        val pipListener = Consumer<PictureInPictureModeChangedInfo> { info ->
            isInPipModeState = info.isInPictureInPictureMode
        }
        addOnPictureInPictureModeChangedListener(pipListener)
        setContent {
            val context = LocalContext.current
            val themePrefs = remember(context) { ThemePreferencesRepository(context) }
            val securityPrefs = remember(context) { SecurityPreferencesRepository(context) }
            val scheduledMessageManager = remember(context) { ScheduledMessageManager(context) }

            val themeSettings by themePrefs.themeSettings.collectAsStateWithLifecycle(
                initialValue = ThemeSettings()
            )
            val securitySettings by securityPrefs.securitySettings.collectAsStateWithLifecycle(
                initialValue = null
            )
            val pendingCallLink by incomingCallLinkFlow.collectAsStateWithLifecycle()
            val pendingNotificationTarget by incomingNotificationTargetFlow.collectAsStateWithLifecycle()
            val localizedStrings = remember(themeSettings.languageCode) {
                stringsForLanguage(themeSettings.languageCode)
            }

            CompositionLocalProvider(LocalAppStrings provides localizedStrings) {
                ToKerTheme(
                    themeMode = themeSettings.themeMode,
                    accentTheme = themeSettings.accentTheme,
                    fontStyle = themeSettings.fontStyle,
                    textScale = themeSettings.textScale,
                    boldText = themeSettings.boldText
                ) {
                    AppNavigation(
                        activity = this@MainActivity,
                        themeSettings = themeSettings,
                        themePrefs = themePrefs,
                        securitySettings = securitySettings,
                        securityPrefs = securityPrefs,
                        scheduledMessageManager = scheduledMessageManager,
                        pendingCallLink = pendingCallLink,
                        onConsumeCallLink = { incomingCallLinkFlow.value = null },
                        pendingNotificationTarget = pendingNotificationTarget,
                        onConsumeNotificationTarget = { incomingNotificationTargetFlow.value = null },
                        isInPipMode = isInPipModeState,
                        onUpdateOngoingCallPipState = { active, isVideo ->
                            hasOngoingCallForPip = active
                            ongoingCallIsVideo = isVideo
                        }
                    )
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isFinishing &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            hasOngoingCallForPip &&
            !isInPictureInPictureMode
        ) {
            val ratio = if (ongoingCallIsVideo) Rational(9, 16) else Rational(16, 9)
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(ratio)
                        .build()
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseIncomingIntent(intent)
    }

    private fun parseIncomingIntent(intent: Intent?) {
        val data = intent?.data
        if (data != null) {
            val parsed = CallLinkHelper.parseCallLink(data.toString())
            if (parsed != null) {
                incomingCallLinkFlow.value = parsed
            }
        }
        val navType = intent?.getStringExtra(ToKerNotificationHelper.EXTRA_NAV_TYPE) ?: return
        when (navType) {
            ToKerNotificationHelper.NAV_TYPE_CHAT -> {
                val convId = intent.getStringExtra(ToKerNotificationHelper.EXTRA_CONVERSATION_ID)
                val peerId = intent.getStringExtra(ToKerNotificationHelper.EXTRA_PEER_ID)
                if (!convId.isNullOrBlank() && !peerId.isNullOrBlank()) {
                    incomingNotificationTargetFlow.value = NotificationNavTarget.Chat(convId, peerId)
                }
            }
            ToKerNotificationHelper.NAV_TYPE_GROUP -> {
                val groupId = intent.getStringExtra(ToKerNotificationHelper.EXTRA_GROUP_ID)
                if (!groupId.isNullOrBlank()) {
                    incomingNotificationTargetFlow.value = NotificationNavTarget.Group(groupId)
                }
            }
            ToKerNotificationHelper.NAV_TYPE_CALL -> {
                val peerId = intent.getStringExtra(ToKerNotificationHelper.EXTRA_PEER_ID)
                val callId = intent.getStringExtra(ToKerNotificationHelper.EXTRA_CALL_ID)
                val callType = intent.getStringExtra(ToKerNotificationHelper.EXTRA_CALL_TYPE) ?: CallType.AUDIO.name
                if (!peerId.isNullOrBlank()) {
                    incomingNotificationTargetFlow.value = NotificationNavTarget.Call(peerId, callType, callId)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    activity: FragmentActivity,
    themeSettings: ThemeSettings,
    themePrefs: ThemePreferencesRepository,
    securitySettings: SecuritySettings?,
    securityPrefs: SecurityPreferencesRepository,
    scheduledMessageManager: ScheduledMessageManager,
    pendingCallLink: ParsedCallLink?,
    onConsumeCallLink: () -> Unit,
    pendingNotificationTarget: NotificationNavTarget? = null,
    onConsumeNotificationTarget: () -> Unit = {},
    isInPipMode: Boolean = false,
    onUpdateOngoingCallPipState: (active: Boolean, isVideo: Boolean) -> Unit = { _, _ -> },
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        onUpdateOngoingCallPipState(false, false)
        AuthScreen(onAuthSuccess = {})
    } else if (securitySettings == null) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    } else {
        key(user.uid) {
            AuthenticatedAppGraph(
                activity = activity,
                user = user,
                themeSettings = themeSettings,
                themePrefs = themePrefs,
                securitySettings = securitySettings,
                securityPrefs = securityPrefs,
                scheduledMessageManager = scheduledMessageManager,
                pendingCallLink = pendingCallLink,
                onConsumeCallLink = onConsumeCallLink,
                pendingNotificationTarget = pendingNotificationTarget,
                onConsumeNotificationTarget = onConsumeNotificationTarget,
                isInPipMode = isInPipMode,
                onUpdateOngoingCallPipState = onUpdateOngoingCallPipState
            )
        }
    }
}

@Composable
private fun AuthenticatedAppGraph(
    activity: FragmentActivity,
    user: FirebaseUser,
    themeSettings: ThemeSettings,
    themePrefs: ThemePreferencesRepository,
    securitySettings: SecuritySettings,
    securityPrefs: SecurityPreferencesRepository,
    scheduledMessageManager: ScheduledMessageManager,
    pendingCallLink: ParsedCallLink?,
    onConsumeCallLink: () -> Unit,
    pendingNotificationTarget: NotificationNavTarget?,
    onConsumeNotificationTarget: () -> Unit,
    isInPipMode: Boolean = false,
    onUpdateOngoingCallPipState: (active: Boolean, isVideo: Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val navController = rememberNavController()

    var showThemeSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showPrivacySheet by remember { mutableStateOf(false) }
    var showBackupSheet by remember { mutableStateOf(false) }
    var showScheduleSheet by remember { mutableStateOf(false) }
    var showAddContactSheet by remember { mutableStateOf(false) }
    var showJoinCallLinkDialog by remember { mutableStateOf(false) }
    var showSetupPinDialog by remember { mutableStateOf(false) }
    var pendingChatUnlock by remember { mutableStateOf<Triple<String, String, () -> Unit>?>(null) }

    val databaseId = remember(context) { context.applicationContext.getString(R.string.firestore_database_id) }
    var activeCallViewModel by remember(user.uid) { mutableStateOf<CallViewModel?>(null) }
    val activeCallEnded by activeCallViewModel?.callEnded?.collectAsStateWithLifecycle(initialValue = false)
        ?: remember { mutableStateOf(false) }
    SideEffect {
        val callVm = activeCallViewModel
        val active = callVm != null && !activeCallEnded
        onUpdateOngoingCallPipState(active, callVm?.callTypeValue == CallType.VIDEO)
    }

    var isAppUnlocked by remember(user.uid) { mutableStateOf(!securitySettings.appLockEnabled) }
    var isAppInForeground by remember { mutableStateOf(true) }
    var hasOsNotificationPermission by remember {
        mutableStateOf(ToKerNotificationHelper.hasNotificationPermission(context))
    }
    val currentAppLockEnabled by rememberUpdatedState(securitySettings.appLockEnabled)

    val mainViewModel: MainViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val fallbackName = user.displayName?.takeIf { it.isNotBlank() }
                    ?: user.phoneNumber?.takeLast(4)?.let { "ToKerUser • $it" }
                    ?: "ToKerUser"
                MainViewModel(
                    repository = ChatRepository(db),
                    cryptoEngine = CryptoEngine(app.applicationContext),
                    currentUserId = user.uid,
                    displayName = fallbackName,
                    email = user.email ?: "",
                    phoneNumber = user.phoneNumber,
                    photoUrl = user.photoUrl?.toString(),
                    scheduledMessageManager = scheduledMessageManager
                )
            }
        }
    )

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasOsNotificationPermission = ToKerNotificationHelper.hasNotificationPermission(context)
        if (granted) {
            ToKerNotificationHelper.ensureNotificationChannels(context)
            scope.launch { securityPrefs.setNotificationsEnabled(true) }
            mainViewModel.notifyMessage("Notifications enabled")
        } else {
            mainViewModel.notifyMessage("Notifications denied — enable them in App Settings to receive message alerts")
        }
    }

    var hasPromptedNotificationPermission by rememberSaveable(user.uid) { mutableStateOf(false) }
    LaunchedEffect(user.uid) {
        ToKerNotificationHelper.ensureNotificationChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !ToKerNotificationHelper.hasNotificationPermission(context) &&
            !hasPromptedNotificationPermission
        ) {
            hasPromptedNotificationPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val resumeObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOsNotificationPermission = ToKerNotificationHelper.hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(resumeObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(resumeObserver)
        }
    }

    // Real-time Foreground/Background Presence Tracking & App Auto-Lock on backgrounding
    DisposableEffect(user.uid) {
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val databaseId = context.applicationContext.getString(R.string.firestore_database_id)
        val presenceRepo = ChatRepository(FirebaseFirestore.getInstance(databaseId))
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                isAppInForeground = true
                appScope.launch { presenceRepo.updatePresence(PresenceStatus.ONLINE) }
            }

            override fun onStop(owner: LifecycleOwner) {
                isAppInForeground = false
                appScope.launch { presenceRepo.updatePresence(PresenceStatus.OFFLINE) }
                if (currentAppLockEnabled) {
                    isAppUnlocked = false
                }
            }
        }
        val processLifecycle = ProcessLifecycleOwner.get().lifecycle
        processLifecycle.addObserver(observer)
        onDispose {
            processLifecycle.removeObserver(observer)
        }
    }

    val myProfile by mainViewModel.myProfile.collectAsStateWithLifecycle()
    val privateAccount by mainViewModel.privateAccount.collectAsStateWithLifecycle()
    val directoryState by mainViewModel.directoryUsers.collectAsStateWithLifecycle()
    val conversationsState by mainViewModel.conversations.collectAsStateWithLifecycle()
    val groupsState by mainViewModel.groups.collectAsStateWithLifecycle()
    val scheduledMessages by mainViewModel.scheduledMessages.collectAsStateWithLifecycle()
    val incomingRingingCall by mainViewModel.incomingRingingCall.collectAsStateWithLifecycle()

    val savedContactIds = privateAccount?.savedContactIds ?: emptyList()
    val blockedUserIds = privateAccount?.blockedUserIds ?: emptyList()
    val directoryList = (directoryState as? UiState.Success)?.data ?: emptyList()
    val conversationsList = (conversationsState as? UiState.Success)?.data ?: emptyList()
    val groupsList = (groupsState as? UiState.Success)?.data ?: emptyList()

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val activeConversationId = remember(currentBackStackEntry) {
        val entry = currentBackStackEntry
        if (entry?.destination?.route?.contains("ChatRoute") == true) {
            runCatching { entry.toRoute<ChatRoute>().conversationId }.getOrNull()
        } else null
    }
    val activeGroupId = remember(currentBackStackEntry) {
        val entry = currentBackStackEntry
        if (entry?.destination?.route?.contains("GroupRoute") == true) {
            runCatching { entry.toRoute<GroupRoute>().groupId }.getOrNull()
        } else null
    }
    val isInCallScreen = currentBackStackEntry?.destination?.route?.contains("CallRoute") == true

    LaunchedEffect(activeConversationId) {
        activeConversationId?.let { convId ->
            ToKerNotificationHelper.cancelNotificationsForTarget(context, "chat_$convId")
        }
    }
    LaunchedEffect(activeGroupId) {
        activeGroupId?.let { groupId ->
            ToKerNotificationHelper.cancelNotificationsForTarget(context, "grp_$groupId")
        }
    }

    val sessionStartMillis = remember(user.uid) { System.currentTimeMillis() }
    var hasSeededConversations by remember(user.uid) { mutableStateOf(false) }
    val lastNotifiedConvTs = remember(user.uid) { mutableStateMapOf<String, Long>() }
    var hasSeededGroups by remember(user.uid) { mutableStateOf(false) }
    val lastNotifiedGroupTs = remember(user.uid) { mutableStateMapOf<String, Long>() }
    var lastNotifiedCallId by remember(user.uid) { mutableStateOf<String?>(null) }

    LaunchedEffect(
        conversationsState,
        securitySettings.notificationsEnabled,
        securitySettings.appLockEnabled,
        securitySettings.lockedChatIds,
        blockedUserIds,
        activeConversationId,
        isAppInForeground
    ) {
        val convs = (conversationsState as? UiState.Success)?.data ?: return@LaunchedEffect
        if (!hasSeededConversations) {
            convs.forEach { item ->
                lastNotifiedConvTs[item.conversation.id] = item.conversation.lastMessageAt?.toDate()?.time ?: 0L
            }
            hasSeededConversations = true
            return@LaunchedEffect
        }
        convs.forEach { item ->
            val conv = item.conversation
            val ts = conv.lastMessageAt?.toDate()?.time ?: 0L
            val prevTs = lastNotifiedConvTs[conv.id] ?: 0L
            if (ts > prevTs) {
                lastNotifiedConvTs[conv.id] = ts
                val isFromPeer = conv.lastMessageSenderId.isNotBlank() && conv.lastMessageSenderId != user.uid
                val notBlocked = item.peerId !in blockedUserIds
                val viewingThisChat = isAppInForeground && activeConversationId == conv.id
                if (securitySettings.notificationsEnabled &&
                    ts >= sessionStartMillis - 5_000L &&
                    isFromPeer &&
                    notBlocked &&
                    !viewingThisChat
                ) {
                    ToKerNotificationHelper.notifyIncomingDirectMessage(
                        context = context,
                        conversationId = conv.id,
                        peerId = item.peerId,
                        senderName = item.peerName,
                        previewText = item.lastMessagePreview,
                        maskContent = securitySettings.appLockEnabled || conv.id in securitySettings.lockedChatIds,
                        timestampMillis = ts
                    )
                }
            }
        }
    }

    LaunchedEffect(
        groupsState,
        securitySettings.notificationsEnabled,
        securitySettings.appLockEnabled,
        securitySettings.lockedChatIds,
        activeGroupId,
        isAppInForeground
    ) {
        val groups = (groupsState as? UiState.Success)?.data ?: return@LaunchedEffect
        if (!hasSeededGroups) {
            groups.forEach { item ->
                lastNotifiedGroupTs[item.group.id] = item.group.lastMessageAt?.toDate()?.time ?: 0L
            }
            hasSeededGroups = true
            return@LaunchedEffect
        }
        groups.forEach { item ->
            val group = item.group
            val ts = group.lastMessageAt?.toDate()?.time ?: 0L
            val prevTs = lastNotifiedGroupTs[group.id] ?: 0L
            if (ts > prevTs) {
                lastNotifiedGroupTs[group.id] = ts
                val isFromOther = group.lastMessageSenderId.isNotBlank() && group.lastMessageSenderId != user.uid
                val viewingThisGroup = isAppInForeground && activeGroupId == group.id
                if (securitySettings.notificationsEnabled &&
                    ts >= sessionStartMillis - 5_000L &&
                    isFromOther &&
                    !viewingThisGroup
                ) {
                    ToKerNotificationHelper.notifyIncomingGroupMessage(
                        context = context,
                        groupId = group.id,
                        groupName = group.name,
                        senderName = group.lastMessageSenderName,
                        maskContent = securitySettings.appLockEnabled || "grp_${group.id}" in securitySettings.lockedChatIds,
                        timestampMillis = ts
                    )
                }
            }
        }
    }

    LaunchedEffect(incomingRingingCall, securitySettings.notificationsEnabled, blockedUserIds, isInCallScreen, isAppInForeground) {
        val call = incomingRingingCall ?: return@LaunchedEffect
        if (call.id == lastNotifiedCallId) return@LaunchedEffect
        lastNotifiedCallId = call.id
        if (securitySettings.notificationsEnabled &&
            call.callerId !in blockedUserIds &&
            !(isAppInForeground && isInCallScreen)
        ) {
            ToKerNotificationHelper.notifyIncomingCall(
                context = context,
                callId = call.id,
                callerId = call.callerId,
                callerName = call.callerName,
                callType = call.callTypeEnum
            )
        }
    }

    // SAF Backup Export/Import Launchers
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val json = mainViewModel.buildLocalBackupJson() ?: return@rememberLauncherForActivityResult
            scope.launch {
                val writeResult = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(json.toByteArray(Charsets.UTF_8))
                        } ?: throw IllegalStateException("Unable to open destination file")
                    }
                }
                writeResult.onSuccess {
                    mainViewModel.notifyMessage("Local ToKer backup exported")
                }.onFailure {
                    mainViewModel.notifyError(it.message ?: "Failed to export backup")
                }
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val readResult = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
                        } ?: throw IllegalStateException("Unable to read selected backup file")
                    }
                }
                readResult.onSuccess { raw ->
                    mainViewModel.importBackupJson(raw)
                }.onFailure {
                    mainViewModel.notifyError(it.message ?: "Failed to read backup file")
                }
            }
        }
    }

    fun handleToggleChatLock(chatLockKey: String) {
        val currentlyLocked = chatLockKey in securitySettings.lockedChatIds
        if (!currentlyLocked && !securitySettings.hasPinConfigured && !BiometricAuthHelper.canAuthenticate(context)) {
            mainViewModel.notifyMessage("Set an App PIN first to lock individual chats")
            showSetupPinDialog = true
            return
        }
        scope.launch {
            securityPrefs.setChatLocked(chatLockKey, !currentlyLocked)
            mainViewModel.notifyMessage(if (currentlyLocked) "Chat unlocked" else "Chat locked with ToKer Vault")
        }
    }

    fun launchOrResumeCall(peerId: String, callType: CallType, incomingCallId: String?) {
        val existing = activeCallViewModel
        if (existing != null && !existing.callEnded.value) {
            val isSameCall = (incomingCallId != null && existing.callId == incomingCallId) ||
                (incomingCallId == null && existing.peerIdValue == peerId && existing.callTypeValue == callType)
            if (isSameCall) {
                navController.navigate(
                    CallRoute(
                        peerId = existing.peerIdValue,
                        callTypeName = existing.callTypeValue.name,
                        incomingCallId = incomingCallId
                    )
                ) {
                    launchSingleTop = true
                }
                return
            }
            existing.releaseCallResources()
        } else {
            existing?.releaseCallResources()
        }

        val db = FirebaseFirestore.getInstance(databaseId)
        val newCallVm = CallViewModel(
            appContext = context.applicationContext,
            repository = ChatRepository(db),
            cryptoEngine = CryptoEngine(context.applicationContext),
            currentUserId = user.uid,
            peerId = peerId,
            callType = callType,
            incomingCallId = incomingCallId
        )
        activeCallViewModel = newCallVm
        navController.navigate(
            CallRoute(
                peerId = peerId,
                callTypeName = callType.name,
                incomingCallId = incomingCallId
            )
        ) {
            launchSingleTop = true
        }
    }

    LaunchedEffect(activeCallViewModel, activeCallEnded, isInCallScreen) {
        val vm = activeCallViewModel
        if (vm != null && activeCallEnded) {
            vm.releaseCallResources()
            activeCallViewModel = null
            if (isInCallScreen) {
                navController.popBackStack()
            }
        }
    }

    DisposableEffect(user.uid) {
        onDispose {
            activeCallViewModel?.releaseCallResources()
            activeCallViewModel = null
        }
    }

    fun handleOpenCallLink(rawLink: String) {
        val parsed = CallLinkHelper.parseCallLink(rawLink)
        if (parsed == null) {
            mainViewModel.notifyError("Invalid ToKer call link format")
            return
        }
        if (parsed.hostUid == user.uid) {
            mainViewModel.notifyMessage("You hosted this call link")
            return
        }
        launchOrResumeCall(
            peerId = parsed.hostUid,
            callType = parsed.callType,
            incomingCallId = null
        )
    }

    LaunchedEffect(pendingCallLink, myProfile, isAppUnlocked) {
        val link = pendingCallLink ?: return@LaunchedEffect
        if (myProfile == null || (!isAppUnlocked && securitySettings.appLockEnabled)) return@LaunchedEffect
        onConsumeCallLink()
        if (link.hostUid == user.uid) {
            mainViewModel.notifyMessage("You created this ToKer call link")
        } else {
            launchOrResumeCall(
                peerId = link.hostUid,
                callType = link.callType,
                incomingCallId = null
            )
        }
    }

    fun requestOrEnableNotifications() {
        scope.launch { securityPrefs.setNotificationsEnabled(true) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !ToKerNotificationHelper.hasNotificationPermission(context)
        ) {
            if (activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else if (!ToKerNotificationHelper.hasNotificationPermission(context)) {
            ToKerNotificationHelper.openAppNotificationSettings(context)
        } else {
            mainViewModel.notifyMessage("Notifications enabled")
        }
    }

    LaunchedEffect(pendingNotificationTarget, myProfile, isAppUnlocked, conversationsList, groupsList) {
        val target = pendingNotificationTarget ?: return@LaunchedEffect
        if (myProfile == null || (!isAppUnlocked && securitySettings.appLockEnabled)) return@LaunchedEffect
        onConsumeNotificationTarget()
        when (target) {
            is NotificationNavTarget.Chat -> {
                if (target.conversationId in securitySettings.lockedChatIds) {
                    val title = conversationsList.firstOrNull { it.conversation.id == target.conversationId }?.peerName
                        ?: "Locked Chat"
                    pendingChatUnlock = Triple(target.conversationId, title) {
                        navController.navigate(ChatRoute(conversationId = target.conversationId, peerId = target.peerId))
                    }
                } else {
                    navController.navigate(ChatRoute(conversationId = target.conversationId, peerId = target.peerId))
                }
            }
            is NotificationNavTarget.Group -> {
                val lockKey = "grp_${target.groupId}"
                if (lockKey in securitySettings.lockedChatIds) {
                    val title = groupsList.firstOrNull { it.group.id == target.groupId }?.group?.name
                        ?: "Locked Group"
                    pendingChatUnlock = Triple(lockKey, title) {
                        navController.navigate(GroupRoute(groupId = target.groupId))
                    }
                } else {
                    navController.navigate(GroupRoute(groupId = target.groupId))
                }
            }
            is NotificationNavTarget.Call -> {
                launchOrResumeCall(
                    peerId = target.peerId,
                    callType = CallType.fromRaw(target.callTypeName),
                    incomingCallId = target.callId
                )
            }
        }
    }

    val pipOngoingCall = activeCallViewModel
    if (isInPipMode && pipOngoingCall != null && !activeCallEnded) {
        CallScreen(
            viewModel = pipOngoingCall,
            myProfile = myProfile,
            callType = pipOngoingCall.callTypeValue,
            isInPipMode = true,
            onMinimizeCall = {},
            onCallFinished = {
                pipOngoingCall.releaseCallResources()
                if (activeCallViewModel === pipOngoingCall) {
                    activeCallViewModel = null
                }
            }
        )
        return
    }

    if (!isAppUnlocked && securitySettings.appLockEnabled) {
        AppLockScreen(
            settings = securitySettings,
            activity = activity,
            onUnlocked = { isAppUnlocked = true },
            onDisableLockOrSignOut = {
                signOutFromToKer(
                    credentialManager = credentialManager,
                    scope = scope,
                    beforeSignOut = { mainViewModel.markOfflineBeforeSignOut() }
                )
            }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = HomeRoute
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    viewModel = mainViewModel,
                    currentUserId = user.uid,
                    themeSettings = themeSettings,
                    securitySettings = securitySettings,
                    hasOsNotificationPermission = hasOsNotificationPermission,
                    onEnableNotifications = ::requestOrEnableNotifications,
                    onOpenThemeSheet = { showThemeSheet = true },
                    onOpenLanguageSheet = { showLanguageSheet = true },
                    onOpenPrivacySheet = { showPrivacySheet = true },
                    onOpenBackupSheet = { showBackupSheet = true },
                    onOpenScheduleSheet = { showScheduleSheet = true },
                    onOpenAddContactSheet = { showAddContactSheet = true },
                    onOpenJoinCallLinkDialog = { showJoinCallLinkDialog = true },
                    onRequestChatUnlock = { chatKey, title, onSuccess ->
                        pendingChatUnlock = Triple(chatKey, title, onSuccess)
                    },
                    onToggleChatLock = ::handleToggleChatLock,
                    onOpenChat = { convId, peerId ->
                        navController.navigate(ChatRoute(conversationId = convId, peerId = peerId))
                    },
                    onOpenGroup = { groupId ->
                        navController.navigate(GroupRoute(groupId = groupId))
                    },
                    onStartCall = { peerId, callType, incomingCallId ->
                        launchOrResumeCall(peerId, callType, incomingCallId)
                    }
                )
            }

        composable<ChatRoute> { backStackEntry ->
            val route: ChatRoute = backStackEntry.toRoute()
            val chatViewModel: ChatViewModel = viewModel(
                key = "${user.uid}_${route.conversationId}",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        ChatViewModel(
                            repository = ChatRepository(db),
                            cryptoEngine = CryptoEngine(app.applicationContext),
                            currentUserId = user.uid,
                            conversationId = route.conversationId,
                            peerId = route.peerId,
                            scheduledMessageManager = scheduledMessageManager
                        )
                    }
                }
            )
            ChatScreen(
                viewModel = chatViewModel,
                currentUserId = user.uid,
                peerId = route.peerId,
                themeSettings = themeSettings,
                isChatLocked = route.conversationId in securitySettings.lockedChatIds,
                onToggleChatLock = { handleToggleChatLock(route.conversationId) },
                isSavedContact = route.peerId in savedContactIds,
                onToggleSavedContact = { mainViewModel.toggleSavedContact(route.peerId) },
                isPeerBlocked = route.peerId in blockedUserIds,
                onToggleBlockedPeer = { mainViewModel.toggleBlockedUser(route.peerId) },
                conversationsForForward = conversationsList,
                groupsForForward = groupsList,
                onForwardToConversation = { targetConv, payload ->
                    mainViewModel.forwardToConversation(
                        convId = targetConv.conversation.id,
                        peerId = targetConv.peerId,
                        peerPubKey = targetConv.peerPublicKey,
                        sourcePayload = payload,
                        targetLabel = targetConv.peerName
                    )
                },
                onForwardToGroup = { targetGroup, payload ->
                    mainViewModel.forwardToGroup(targetGroup.group, payload)
                },
                onOpenThemeSheet = { showThemeSheet = true },
                onBack = { navController.popBackStack() },
                onStartCall = { peerId, callType ->
                    launchOrResumeCall(peerId, callType, null)
                }
            )
        }

        composable<GroupRoute> { backStackEntry ->
            val route: GroupRoute = backStackEntry.toRoute()
            val groupViewModel: GroupChatViewModel = viewModel(
                key = "${user.uid}_grp_${route.groupId}",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        GroupChatViewModel(
                            repository = ChatRepository(db),
                            cryptoEngine = CryptoEngine(app.applicationContext),
                            currentUserId = user.uid,
                            groupId = route.groupId,
                            scheduledMessageManager = scheduledMessageManager
                        )
                    }
                }
            )
            val lockKey = "grp_${route.groupId}"
            GroupChatScreen(
                viewModel = groupViewModel,
                currentUserId = user.uid,
                themeSettings = themeSettings,
                isChatLocked = lockKey in securitySettings.lockedChatIds,
                onToggleChatLock = { handleToggleChatLock(lockKey) },
                conversationsForForward = conversationsList,
                groupsForForward = groupsList,
                onForwardToConversation = { targetConv, payload ->
                    mainViewModel.forwardToConversation(
                        convId = targetConv.conversation.id,
                        peerId = targetConv.peerId,
                        peerPubKey = targetConv.peerPublicKey,
                        sourcePayload = payload,
                        targetLabel = targetConv.peerName
                    )
                },
                onForwardToGroup = { targetGroup, payload ->
                    mainViewModel.forwardToGroup(targetGroup.group, payload)
                },
                onOpenThemeSheet = { showThemeSheet = true },
                onBack = { navController.popBackStack() }
            )
        }

            composable<CallRoute> {
                val callVm = activeCallViewModel
                if (callVm == null) {
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                    }
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF070A10)))
                } else {
                    CallScreen(
                        viewModel = callVm,
                        myProfile = myProfile,
                        callType = callVm.callTypeValue,
                        onMinimizeCall = {
                            navController.popBackStack()
                        },
                        onCallFinished = {
                            callVm.releaseCallResources()
                            if (activeCallViewModel === callVm) {
                                activeCallViewModel = null
                            }
                            if (isInCallScreen) {
                                navController.popBackStack()
                            }
                        }
                    )
                }
            }
        }

        val ongoingCallVm = activeCallViewModel
        if (ongoingCallVm != null && !activeCallEnded && !isInCallScreen) {
            MinimizedCallFloatingOverlay(
                viewModel = ongoingCallVm,
                onExpandToFullCall = {
                    navController.navigate(
                        CallRoute(
                            peerId = ongoingCallVm.peerIdValue,
                            callTypeName = ongoingCallVm.callTypeValue.name,
                            incomingCallId = ongoingCallVm.callId
                        )
                    ) {
                        launchSingleTop = true
                    }
                }
            )
        }
    }

    if (showThemeSheet) {
        ThemeCustomizationSheet(
            themeSettings = themeSettings,
            themePrefs = themePrefs,
            onDismiss = { showThemeSheet = false },
            onThemeFeedback = { msg -> mainViewModel.notifyMessage(msg) }
        )
    }

    if (showLanguageSheet) {
        LanguagePickerBottomSheet(
            currentLanguageCode = themeSettings.languageCode,
            onSelectLanguage = { lang ->
                scope.launch {
                    themePrefs.setLanguageCode(lang.code)
                    mainViewModel.notifyMessage("Language: ${lang.nativeLabel} (${lang.englishName})")
                }
            },
            onDismiss = { showLanguageSheet = false }
        )
    }

    if (showPrivacySheet) {
        val blockedUsers = directoryList.filter { it.uid in blockedUserIds }
        PrivacyAndSecuritySheet(
            myProfile = myProfile,
            privateAccount = privateAccount,
            securitySettings = securitySettings,
            hasOsNotificationPermission = hasOsNotificationPermission,
            blockedUsers = blockedUsers,
            onSavePrivacy = { discoverable, readReceipts, lastSeenVis, photoVis, statusVis ->
                mainViewModel.updatePrivacySettings(
                    discoverable = discoverable,
                    readReceiptsEnabled = readReceipts,
                    lastSeenVisibility = lastSeenVis,
                    photoVisibility = photoVis,
                    statusVisibility = statusVis
                )
            },
            onRequestSetPin = { showSetupPinDialog = true },
            onToggleAppLock = { enable ->
                scope.launch {
                    securityPrefs.setAppLockEnabled(enable)
                    if (enable) {
                        isAppUnlocked = true
                    }
                    mainViewModel.notifyMessage(if (enable) "ToKer App Lock enabled" else "ToKer App Lock disabled")
                }
            },
            onToggleBiometric = { enable ->
                scope.launch {
                    securityPrefs.setBiometricEnabled(enable)
                    mainViewModel.notifyMessage(if (enable) "Biometric unlock enabled" else "Biometric unlock disabled")
                }
            },
            onToggleNotifications = { enable ->
                if (enable) {
                    requestOrEnableNotifications()
                } else {
                    scope.launch {
                        securityPrefs.setNotificationsEnabled(false)
                        mainViewModel.notifyMessage("Message & call notifications muted")
                    }
                }
            },
            onOpenOsNotificationSettings = {
                ToKerNotificationHelper.openAppNotificationSettings(context)
            },
            onUnblockUser = { uid -> mainViewModel.toggleBlockedUser(uid) },
            onDismiss = { showPrivacySheet = false }
        )
    }

    if (showBackupSheet) {
        DataBackupSheet(
            savedContactsCount = savedContactIds.size,
            scheduledCount = scheduledMessages.size,
            conversationsCount = conversationsList.size,
            onExportLocalJson = {
                exportBackupLauncher.launch("toker_backup_${System.currentTimeMillis()}.json")
            },
            onImportLocalJson = {
                importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            },
            onCloudBackup = { mainViewModel.backupToCloud() },
            onCloudRestore = { mainViewModel.restoreFromCloud() },
            onDismiss = { showBackupSheet = false }
        )
    }

    if (showScheduleSheet) {
        ScheduleMessageSheet(
            scheduledItems = scheduledMessages,
            targetTitle = null,
            onSchedule = null,
            onSendNow = { item -> mainViewModel.sendScheduledNow(item) },
            onReschedule = { id, newMillis -> mainViewModel.rescheduleMessage(id, newMillis) },
            onCancel = { id -> mainViewModel.cancelScheduledMessage(id) },
            onDismiss = { showScheduleSheet = false }
        )
    }

    if (showAddContactSheet) {
        AddContactSheet(
            myUid = user.uid,
            myHandle = myProfile?.handle ?: "@toker_user",
            savedContactIds = savedContactIds,
            directoryUsers = directoryList,
            onSearchAndAdd = { query ->
                mainViewModel.addContactByQuery(query)
            },
            onToggleSaved = { uid -> mainViewModel.toggleSavedContact(uid) },
            onStartChat = { peer ->
                mainViewModel.startChatWithPeer(peer) { convId, peerId ->
                    navController.navigate(ChatRoute(conversationId = convId, peerId = peerId))
                }
            },
            onDismiss = { showAddContactSheet = false }
        )
    }

    if (showJoinCallLinkDialog) {
        JoinCallLinkDialog(
            onJoinLink = ::handleOpenCallLink,
            onDismiss = { showJoinCallLinkDialog = false }
        )
    }

    if (showSetupPinDialog) {
        SetupPinDialog(
            hasExistingPin = securitySettings.hasPinConfigured,
            onSavePin = { pin ->
                scope.launch {
                    val canBio = BiometricAuthHelper.canAuthenticate(context)
                    securityPrefs.setPinAndEnableAppLock(
                        pin = pin,
                        enableBiometric = securitySettings.biometricEnabled || canBio
                    )
                    isAppUnlocked = true
                    mainViewModel.notifyMessage("App PIN saved & ToKer App Lock active")
                }
            },
            onDismiss = { showSetupPinDialog = false }
        )
    }

    pendingChatUnlock?.let { (_, title, onUnlocked) ->
        ChatUnlockPromptDialog(
            chatTitle = title,
            settings = securitySettings,
            activity = activity,
            onUnlocked = {
                pendingChatUnlock = null
                onUnlocked()
            },
            onDismiss = { pendingChatUnlock = null }
        )
    }
}


