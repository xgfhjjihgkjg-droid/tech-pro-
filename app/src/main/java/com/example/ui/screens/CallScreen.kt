package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.CallLinkHelper
import com.example.data.CallStatus
import com.example.data.CallType
import com.example.data.DirectoryUser
import com.example.data.PresenceStatus
import com.example.ui.components.UserAvatarWithPresence
import com.example.ui.theme.CallEndRed
import com.example.viewmodel.CallViewModel
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import kotlin.math.roundToInt

@Composable
fun CallScreen(
    viewModel: CallViewModel,
    myProfile: DirectoryUser?,
    callType: CallType,
    onMinimizeCall: () -> Unit,
    onCallFinished: () -> Unit,
    isInPipMode: Boolean = false
) {
    val context = LocalContext.current
    val callSession by viewModel.callSession.collectAsStateWithLifecycle()
    val peerUser by viewModel.peerUser.collectAsStateWithLifecycle()
    val localVideoTrack by viewModel.localVideoTrack.collectAsStateWithLifecycle()
    val remoteVideoTrack by viewModel.remoteVideoTrack.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val isCameraEnabled by viewModel.isCameraEnabled.collectAsStateWithLifecycle()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsStateWithLifecycle()
    val isNoiseCancellationEnabled by viewModel.isNoiseCancellationEnabled.collectAsStateWithLifecycle()
    val cameraUnavailable by viewModel.cameraUnavailable.collectAsStateWithLifecycle()
    val isScreenSharing by viewModel.isScreenSharing.collectAsStateWithLifecycle()
    val durationSeconds by viewModel.callDurationSeconds.collectAsStateWithLifecycle()
    val ringingSecondsLeft by viewModel.ringingSecondsLeft.collectAsStateWithLifecycle()
    val callError by viewModel.callError.collectAsStateWithLifecycle()
    val callEnded by viewModel.callEnded.collectAsStateWithLifecycle()

    BackHandler(enabled = !callEnded && !isInPipMode) {
        onMinimizeCall()
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.startScreenSharing(result.data!!)
        }
    }

    val requiredPermissions = remember(callType) {
        if (callType == CallType.VIDEO) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
    }

    var permissionsGranted by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsGranted = requiredPermissions.all { result[it] == true }
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    LaunchedEffect(permissionsGranted, myProfile, peerUser) {
        val me = myProfile
        val peer = peerUser
        if (permissionsGranted && me != null && peer != null) {
            viewModel.startCallMediaAndSignaling(me, peer)
        }
    }

    LaunchedEffect(callEnded) {
        if (callEnded) {
            onCallFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF070A10),
                        Color(0xFF0E1726),
                        Color(0xFF070A10)
                    )
                )
            )
            .safeDrawingPadding()
    ) {
        if (!permissionsGranted) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = "Permissions Needed for Live Call",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.permission_call_rationale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f)
                )
                Button(
                    onClick = { permissionLauncher.launch(requiredPermissions) },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("grant_call_permissions_button")
                ) {
                    Text(stringResource(R.string.grant_permissions))
                }
                OutlinedButton(
                    onClick = { viewModel.endCall() },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("cancel_call_permission_button")
                ) {
                    Text(stringResource(R.string.end_call), color = Color.White)
                }
            }
            return@Box
        }

        val eglContext = viewModel.eglBaseContext
        if (isInPipMode) {
            val pipTrack = remoteVideoTrack ?: localVideoTrack
            if (pipTrack != null && eglContext != null) {
                WebRtcVideoSurface(
                    videoTrack = pipTrack,
                    eglContext = eglContext,
                    mirror = remoteVideoTrack == null && !isScreenSharing,
                    isMediaOverlay = false,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UserAvatarWithPresence(
                        name = peerUser?.displayName ?: callSession?.calleeName ?: "Peer",
                        photoUrl = peerUser?.photoUrl,
                        presence = peerUser?.presenceEnum ?: PresenceStatus.ONLINE,
                        size = 56.dp,
                        avatarBase64 = peerUser?.avatarBase64
                    )
                    Text(
                        text = if (durationSeconds > 0) formatDuration(durationSeconds) else "HD Call",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }
            return@Box
        }

        // Remote Video / Screen Share Surface (full screen when available)
        if (remoteVideoTrack != null && eglContext != null) {
            WebRtcVideoSurface(
                videoTrack = remoteVideoTrack,
                eglContext = eglContext,
                mirror = false,
                isMediaOverlay = false,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Audio Call / Waiting for Remote Video Stage
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(136.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    UserAvatarWithPresence(
                        name = peerUser?.displayName ?: callSession?.calleeName ?: "Peer",
                        photoUrl = peerUser?.photoUrl,
                        presence = peerUser?.presenceEnum ?: PresenceStatus.ONLINE,
                        size = 116.dp,
                        avatarBase64 = peerUser?.avatarBase64
                    )
                }
                Text(
                    text = peerUser?.displayName ?: callSession?.calleeName ?: "Connecting...",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                val statusLabel = when {
                    !callError.isNullOrBlank() -> callError!!
                    durationSeconds > 0 -> formatDuration(durationSeconds)
                    callSession?.statusEnum == CallStatus.ACCEPTED -> "Connecting WebRTC media..."
                    callSession?.statusEnum == CallStatus.CONNECTED -> formatDuration(durationSeconds)
                    ringingSecondsLeft != null -> "Ringing… 0:%02d (Auto-cut at 30s)".format(ringingSecondsLeft)
                    else -> "Ringing (DTLS-SRTP Encrypted)..."
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (callType == CallType.VIDEO && cameraUnavailable) {
                    Text(
                        text = "Camera hardware unavailable on this device — audio active",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }
            }
        }

        // Top Minimize + E2EE Call Verification Badge + Share Link button
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 14.dp, start = 14.dp, end = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onMinimizeCall,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.55f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("minimize_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimize call"
                    )
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        val code = callSession?.e2eeFingerprint ?: "SRTP"
                        val qualityPrefix = when {
                            isScreenSharing -> "HD Screen"
                            callType == CallType.VIDEO -> "HD Video"
                            else -> "HD Audio"
                        }
                        val ancSuffix = if (isNoiseCancellationEnabled) " • ANC" else ""
                        val timerSuffix = when {
                            durationSeconds > 0 -> " • ${formatDuration(durationSeconds)}"
                            ringingSecondsLeft != null -> " • 0:%02d".format(ringingSecondsLeft)
                            else -> " • $code"
                        }
                        Text(
                            text = "$qualityPrefix$ancSuffix$timerSuffix",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (myProfile != null) {
                IconButton(
                    onClick = {
                        val msg = CallLinkHelper.buildShareableMessage(
                            hostUid = myProfile.uid,
                            hostName = myProfile.displayName,
                            callType = callType
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, msg)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share ToKer Call Link"))
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.55f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("share_call_link_in_call_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share Call Link")
                }
            }
        }

        // Floating Local Video / Screen Share PiP
        if ((isScreenSharing || (callType == CallType.VIDEO && isCameraEnabled)) &&
            localVideoTrack != null &&
            eglContext != null
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 74.dp, end = 16.dp)
                    .size(width = 112.dp, height = 156.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
            ) {
                WebRtcVideoSurface(
                    videoTrack = localVideoTrack,
                    eglContext = eglContext,
                    mirror = !isScreenSharing,
                    isMediaOverlay = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Bottom Call Control Dock
        Surface(
            color = Color(0xCC121826),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 22.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControlButton(
                    icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (isMuted) "Unmute microphone" else "Mute microphone",
                    active = isMuted,
                    testTag = "call_mute_button",
                    onClick = { viewModel.toggleMute() }
                )

                if (callType == CallType.VIDEO) {
                    CallControlButton(
                        icon = if (isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        contentDescription = if (isCameraEnabled) "Turn off camera" else "Turn on camera",
                        active = !isCameraEnabled,
                        testTag = "call_camera_toggle_button",
                        onClick = { viewModel.toggleCamera() }
                    )
                    CallControlButton(
                        icon = Icons.Default.Cameraswitch,
                        contentDescription = "Switch front and back camera",
                        active = false,
                        testTag = "call_switch_camera_button",
                        onClick = { viewModel.switchCamera() }
                    )
                }

                CallControlButton(
                    icon = if (isScreenSharing) Icons.Default.StopScreenShare else Icons.Default.ScreenShare,
                    contentDescription = if (isScreenSharing) "Stop screen sharing" else "Share screen",
                    active = isScreenSharing,
                    testTag = "call_screen_share_button",
                    onClick = {
                        if (isScreenSharing) {
                            viewModel.stopScreenSharing()
                        } else {
                            val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                            if (mgr != null) {
                                screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
                            }
                        }
                    }
                )

                CallControlButton(
                    icon = Icons.Default.GraphicEq,
                    contentDescription = if (isNoiseCancellationEnabled) "Noise cancellation enabled" else "Noise cancellation off",
                    active = isNoiseCancellationEnabled,
                    testTag = "call_anc_button",
                    onClick = { viewModel.toggleNoiseCancellation() }
                )

                CallControlButton(
                    icon = Icons.Default.VolumeUp,
                    contentDescription = if (isSpeakerOn) "Disable speakerphone" else "Enable speakerphone",
                    active = isSpeakerOn,
                    testTag = "call_speaker_button",
                    onClick = { viewModel.toggleSpeaker() }
                )

                IconButton(
                    onClick = { viewModel.endCall() },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = CallEndRed,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("end_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = stringResource(R.string.end_call),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MinimizedCallFloatingOverlay(
    viewModel: CallViewModel,
    onExpandToFullCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val peerUser by viewModel.peerUser.collectAsStateWithLifecycle()
    val callSession by viewModel.callSession.collectAsStateWithLifecycle()
    val localVideoTrack by viewModel.localVideoTrack.collectAsStateWithLifecycle()
    val remoteVideoTrack by viewModel.remoteVideoTrack.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val durationSeconds by viewModel.callDurationSeconds.collectAsStateWithLifecycle()
    val ringingSecondsLeft by viewModel.ringingSecondsLeft.collectAsStateWithLifecycle()

    val callType = viewModel.callTypeValue
    val eglContext = viewModel.eglBaseContext
    val peerName = peerUser?.displayName ?: callSession?.calleeName ?: "Active Call"
    val timeLabel = when {
        durationSeconds > 0 -> formatDuration(durationSeconds)
        ringingSecondsLeft != null -> "0:%02d".format(ringingSecondsLeft)
        else -> "Live"
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        val density = LocalDensity.current
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }

        if (callType == CallType.VIDEO) {
            val pipWidthDp = 148.dp
            val pipHeightDp = 212.dp
            val pipWidthPx = with(density) { pipWidthDp.toPx() }
            val pipHeightPx = with(density) { pipHeightDp.toPx() }
            val marginPx = with(density) { 14.dp.toPx() }

            var offsetX by remember(maxW) {
                mutableFloatStateOf((maxW - pipWidthPx - marginPx).coerceAtLeast(marginPx))
            }
            var offsetY by remember(maxH) {
                mutableFloatStateOf(with(density) { 84.dp.toPx() })
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF0E1726),
                shadowElevation = 14.dp,
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .size(width = pipWidthDp, height = pipHeightDp)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .pointerInput(maxW, maxH) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x).coerceIn(
                                marginPx,
                                (maxW - pipWidthPx - marginPx).coerceAtLeast(marginPx)
                            )
                            offsetY = (offsetY + dragAmount.y).coerceIn(
                                marginPx,
                                (maxH - pipHeightPx - marginPx).coerceAtLeast(marginPx)
                            )
                        }
                    }
                    .clickable(onClick = onExpandToFullCall)
                    .testTag("mini_video_call_pip")
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val activeTrack = remoteVideoTrack ?: localVideoTrack
                    if (activeTrack != null && eglContext != null) {
                        WebRtcVideoSurface(
                            videoTrack = activeTrack,
                            eglContext = eglContext,
                            mirror = remoteVideoTrack == null,
                            isMediaOverlay = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            UserAvatarWithPresence(
                                name = peerName,
                                photoUrl = peerUser?.photoUrl,
                                presence = peerUser?.presenceEnum ?: PresenceStatus.ONLINE,
                                size = 56.dp,
                                avatarBase64 = peerUser?.avatarBase64
                            )
                            Text(
                                text = peerName,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Top live timer pill
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "$timeLabel • $peerName",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    // Bottom quick controls
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleMute() },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("mini_call_mute_button")
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Toggle mute",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onExpandToFullCall,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("mini_call_expand_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInFull,
                                contentDescription = "Return to full call",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.endCall() },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = CallEndRed,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("mini_call_end_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "End call",
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // Audio Call compact floating pill
            Surface(
                color = Color(0xFF0E1726),
                shape = RoundedCornerShape(50),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp, start = 16.dp, end = 16.dp)
                    .widthIn(min = 240.dp, max = 360.dp)
                    .border(1.2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onExpandToFullCall)
                    .testTag("mini_audio_call_pill")
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = peerName,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Ongoing Audio • $timeLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { viewModel.toggleMute() },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("mini_audio_mute_button")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Mute",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.endCall() },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = CallEndRed,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("mini_audio_end_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End call",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (active) Color.White else Color.White.copy(alpha = 0.14f),
            contentColor = if (active) Color.Black else Color.White
        ),
        modifier = Modifier
            .size(52.dp)
            .testTag(testTag)
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

@Composable
private fun WebRtcVideoSurface(
    videoTrack: VideoTrack?,
    eglContext: EglBase.Context,
    mirror: Boolean,
    isMediaOverlay: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val renderer = remember(eglContext, isMediaOverlay) {
        SurfaceViewRenderer(context).apply {
            if (isMediaOverlay) {
                setZOrderMediaOverlay(true)
            }
            init(eglContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setEnableHardwareScaler(true)
            setMirror(mirror)
        }
    }

    DisposableEffect(videoTrack, renderer) {
        videoTrack?.addSink(renderer)
        onDispose {
            videoTrack?.removeSink(renderer)
        }
    }

    DisposableEffect(renderer) {
        onDispose {
            runCatching { renderer.release() }
        }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier
    )
}

private fun formatDuration(totalSeconds: Long): String {
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return "%02d:%02d".format(mins, secs)
}
