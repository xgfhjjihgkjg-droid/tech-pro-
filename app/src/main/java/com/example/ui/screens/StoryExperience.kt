package com.example.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.AuthorStoriesGroup
import com.example.data.DirectoryUser
import com.example.data.LiveStory
import com.example.data.PresenceStatus
import com.example.data.StoryGradient
import com.example.data.StoryType
import com.example.data.UiState
import com.example.media.MediaUtils
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.LoopingVideoPlayer
import com.example.ui.components.UserAvatarWithPresence
import com.example.ui.components.formatRelativeTimestamp
import com.example.ui.components.rememberDecodedBase64Bitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun storyGradientColors(gradient: StoryGradient): List<Color> = when (gradient) {
    StoryGradient.EMERALD -> listOf(Color(0xFF004D36), Color(0xFF00B37E), Color(0xFF0A3A4A))
    StoryGradient.OCEAN -> listOf(Color(0xFF0B2545), Color(0xFF0284C7), Color(0xFF06B6D4))
    StoryGradient.SUNSET -> listOf(Color(0xFF7C2D12), Color(0xFFEA580C), Color(0xFFF59E0B))
    StoryGradient.VIOLET -> listOf(Color(0xFF2E1065), Color(0xFF7C3AED), Color(0xFFEC4899))
    StoryGradient.OBSIDIAN -> listOf(Color(0xFF0B0E14), Color(0xFF1E293B), Color(0xFF0F172A))
}

@Composable
fun LiveStoriesCarousel(
    storiesState: UiState<List<AuthorStoriesGroup>>,
    currentUserId: String,
    onAddStoryClick: () -> Unit,
    onOpenGroup: (AuthorStoriesGroup) -> Unit,
    onTextStoryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.live_stories_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onTextStoryClick != null) {
                    TextButton(
                        onClick = onTextStoryClick,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("add_text_story_header_button")
                    ) {
                        Text("Aa Text")
                    }
                }
                TextButton(
                    onClick = onAddStoryClick,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("add_story_header_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.add_story_label))
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "add_story_tile") {
                Column(
                    modifier = Modifier
                        .width(72.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onAddStoryClick)
                        .padding(vertical = 4.dp)
                        .testTag("add_story_ring_button"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.add_story_label),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.add_story_label),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (storiesState is UiState.Success) {
                itemsIndexed(
                    items = storiesState.data,
                    key = { _, group -> group.authorId }
                ) { _, group ->
                    val isMe = group.authorId == currentUserId
                    val mentionedMe = !isMe && group.stories.any { currentUserId in it.mentionedUserIds }
                    val ringBrush = if (group.hasUnviewed || mentionedMe) {
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.outline,
                                MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                    Column(
                        modifier = Modifier
                            .width(76.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenGroup(group) }
                            .padding(vertical = 4.dp)
                            .testTag("story_ring_${group.authorId}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .border(
                                    width = if (group.hasUnviewed || mentionedMe) 3.dp else 2.dp,
                                    brush = ringBrush,
                                    shape = CircleShape
                                )
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            UserAvatarWithPresence(
                                name = group.authorName,
                                photoUrl = group.authorPhotoUrl,
                                presence = PresenceStatus.ONLINE,
                                size = 54.dp,
                                showPresenceDot = false,
                                avatarBase64 = group.authorAvatarBase64
                            )
                            if (mentionedMe) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondary,
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Text(
                                        text = "@",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondary,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            if (group.stories.size > 1) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = CircleShape,
                                    tonalElevation = 4.dp,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(20.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${group.stories.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = if (isMe) "My Story" else group.authorName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (group.hasUnviewed) FontWeight.Bold else FontWeight.Normal,
                            color = if (group.hasUnviewed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateStoryBottomSheet(
    directoryUsers: List<DirectoryUser>,
    currentUserId: String,
    initialStoryType: StoryType = StoryType.TEXT,
    initialPhotoBase64: String? = null,
    initialVideoBase64: String? = null,
    onDismiss: () -> Unit,
    onPublishStory: (
        storyType: StoryType,
        caption: String,
        gradient: StoryGradient,
        imageBase64: String?,
        videoBase64: String?,
        mentionedUserIds: List<String>,
        mentionedHandles: List<String>
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var storyType by remember(initialStoryType, initialPhotoBase64, initialVideoBase64) {
        mutableStateOf(initialStoryType)
    }
    var caption by remember { mutableStateOf("") }
    var selectedGradient by remember { mutableStateOf(StoryGradient.EMERALD) }
    var pickedPhotoBase64 by remember(initialPhotoBase64) { mutableStateOf(initialPhotoBase64) }
    var pickedVideoBase64 by remember(initialVideoBase64) { mutableStateOf(initialVideoBase64) }
    var pickedVideoThumbBase64 by remember { mutableStateOf<String?>(null) }
    var isProcessingMedia by remember { mutableStateOf(false) }
    var pickerError by remember { mutableStateOf<String?>(null) }
    val mentionedPeers = remember { mutableStateListOf<DirectoryUser>() }

    val availablePeers = remember(directoryUsers, currentUserId) {
        directoryUsers.filter { it.uid != currentUserId }
    }
    val decodedPreview = rememberDecodedBase64Bitmap(pickedPhotoBase64)

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isProcessingMedia = true
            pickerError = null
            scope.launch {
                val encoded = MediaUtils.compressImageToBase64(context, uri)
                isProcessingMedia = false
                if (encoded == null) {
                    pickerError = "Could not process selected photo. Try another image."
                } else {
                    pickedPhotoBase64 = encoded
                    storyType = StoryType.PHOTO
                }
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isProcessingMedia = true
            pickerError = null
            scope.launch {
                MediaUtils.processPickedVideoToBase64(context, uri, maxRawBytes = 480_000)
                    .onSuccess { encodedVideo ->
                        pickedVideoBase64 = encodedVideo.videoBase64
                        pickedVideoThumbBase64 = encodedVideo.thumbBase64.takeIf { it.isNotBlank() }
                        storyType = StoryType.VIDEO
                    }
                    .onFailure { err ->
                        pickerError = err.message ?: "Could not process selected video."
                    }
                isProcessingMedia = false
            }
        }
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.share_live_story),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Share a 24h text, photo, or video (up to 1 min) story and @mention friends.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = storyType == StoryType.TEXT,
                    onClick = { storyType = StoryType.TEXT },
                    leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text("Text") },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("story_type_text")
                )
                FilterChip(
                    selected = storyType == StoryType.PHOTO,
                    onClick = {
                        storyType = StoryType.PHOTO
                        if (pickedPhotoBase64 == null) {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text("Photo") },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("story_type_photo")
                )
                FilterChip(
                    selected = storyType == StoryType.VIDEO,
                    onClick = {
                        storyType = StoryType.VIDEO
                        if (pickedVideoBase64 == null) {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text("Video") },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("story_type_video")
                )
            }

            // Live Story Preview Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(storyGradientColors(selectedGradient))),
                contentAlignment = Alignment.Center
            ) {
                when (storyType) {
                    StoryType.VIDEO -> {
                        if (isProcessingMedia) {
                            CircularProgressIndicator(color = Color.White)
                        } else if (!pickedVideoBase64.isNullOrBlank()) {
                            LoopingVideoPlayer(
                                videoBase64 = pickedVideoBase64!!,
                                cacheKey = "story_preview_${pickedVideoBase64.hashCode()}",
                                isPlaying = true,
                                isMuted = true,
                                isLooping = true,
                                cropCenter = true,
                                modifier = Modifier.fillMaxSize()
                            )
                            TextButton(
                                onClick = {
                                    videoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .heightIn(min = 48.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                    .testTag("change_story_video_button")
                            ) {
                                Text("Change Video", color = Color.White)
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .clickable {
                                        videoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    }
                                    .padding(24.dp)
                                    .testTag("pick_story_video_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = stringResource(R.string.pick_story_video),
                                    tint = Color.White,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = stringResource(R.string.pick_story_video),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    StoryType.PHOTO -> {
                        if (isProcessingMedia) {
                            CircularProgressIndicator(color = Color.White)
                        } else if (decodedPreview != null) {
                            Image(
                                bitmap = decodedPreview,
                                contentDescription = "Selected live story photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            TextButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .heightIn(min = 48.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                    .testTag("change_story_photo_button")
                            ) {
                                Text("Change", color = Color.White)
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .clickable {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                    .padding(24.dp)
                                    .testTag("pick_story_photo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = stringResource(R.string.pick_story_photo),
                                    tint = Color.White,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = stringResource(R.string.pick_story_photo),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    StoryType.TEXT -> {
                        Text(
                            text = caption.ifBlank { "Type your live story update below…" },
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White.copy(alpha = if (caption.isBlank()) 0.6f else 1f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }

                if (mentionedPeers.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        mentionedPeers.forEach { peer ->
                            Surface(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = peer.handle.ifBlank { "@${peer.displayName}" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (pickerError != null) {
                Text(
                    text = pickerError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (storyType == StoryType.TEXT) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StoryGradient.entries.forEach { grad ->
                        FilterChip(
                            selected = selectedGradient == grad,
                            onClick = { selectedGradient = grad },
                            label = { Text(grad.label) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("story_gradient_${grad.name.lowercase()}")
                        )
                    }
                }
            }

            OutlinedTextField(
                value = caption,
                onValueChange = { if (it.length <= 280) caption = it },
                placeholder = { Text(stringResource(R.string.story_caption_hint)) },
                maxLines = 3,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("story_caption_input")
            )

            // Mention Contacts Selector
            if (availablePeers.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AlternateEmail,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.mention_contacts_label),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availablePeers.forEach { peer ->
                            val isMentioned = mentionedPeers.any { it.uid == peer.uid }
                            FilterChip(
                                selected = isMentioned,
                                onClick = {
                                    if (isMentioned) {
                                        mentionedPeers.removeAll { it.uid == peer.uid }
                                    } else if (mentionedPeers.size < 25) {
                                        mentionedPeers.add(peer)
                                    }
                                },
                                label = {
                                    Text(peer.handle.ifBlank { "@${peer.displayName}" })
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("mention_peer_${peer.uid}")
                            )
                        }
                    }
                }
            }

            val canSubmit = !isProcessingMedia && (
                (storyType == StoryType.TEXT && caption.trim().isNotEmpty()) ||
                    (storyType == StoryType.PHOTO && !pickedPhotoBase64.isNullOrBlank()) ||
                    (storyType == StoryType.VIDEO && !pickedVideoBase64.isNullOrBlank())
                )

            Button(
                onClick = {
                    val imgOut = when (storyType) {
                        StoryType.PHOTO -> pickedPhotoBase64
                        StoryType.VIDEO -> pickedVideoThumbBase64
                        StoryType.TEXT -> null
                    }
                    val vidOut = if (storyType == StoryType.VIDEO) pickedVideoBase64 else null
                    val mentionIds = mentionedPeers.map { it.uid }
                    val mentionHandles = mentionedPeers.map {
                        it.handle.ifBlank { "@${it.displayName.replace(" ", "_")}" }
                    }
                    onPublishStory(
                        storyType,
                        caption,
                        selectedGradient,
                        imgOut,
                        vidOut,
                        mentionIds,
                        mentionHandles
                    )
                    onDismiss()
                },
                enabled = canSubmit,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("publish_live_story_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.share_live_story),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryViewerOverlay(
    group: AuthorStoriesGroup,
    currentUserId: String,
    directoryUsers: List<DirectoryUser>,
    onMarkViewed: (LiveStory) -> Unit,
    onDeleteStory: (String) -> Unit,
    onReplyPrivately: (LiveStory, String) -> Unit,
    onMentionStoryToPeer: (DirectoryUser, LiveStory) -> Unit,
    onAdvanceToNextGroup: () -> Unit,
    onBackToPrevGroup: () -> Unit,
    onDismiss: () -> Unit
) {
    val stories = group.stories
    if (stories.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    var currentIndex by remember(group.authorId) {
        val firstUnviewed = stories.indexOfFirst { currentUserId !in it.viewedBy }
        mutableIntStateOf(if (firstUnviewed >= 0) firstUnviewed else 0)
    }
    val safeIndex = currentIndex.coerceIn(0, stories.lastIndex)
    val currentStory = stories[safeIndex]

    var progress by remember(currentStory.id) { mutableFloatStateOf(0f) }
    var currentVideoDurationMs by remember(currentStory.id) { mutableLongStateOf(15_000L) }
    var isHoldingPause by remember { mutableStateOf(false) }
    var showViewersSheet by remember { mutableStateOf(false) }
    var showMentionInChatSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isVideoMuted by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }

    val decodedPhoto = rememberDecodedBase64Bitmap(currentStory.imageBase64)

    val isMine = currentStory.authorId == currentUserId

    LaunchedEffect(currentStory.id) {
        onMarkViewed(currentStory)
    }

    LaunchedEffect(
        currentStory.id,
        currentVideoDurationMs,
        isHoldingPause,
        showViewersSheet,
        showMentionInChatSheet,
        showDeleteConfirmDialog,
        replyText.isNotEmpty()
    ) {
        if (isHoldingPause || showViewersSheet || showMentionInChatSheet || showDeleteConfirmDialog || replyText.isNotEmpty()) {
            return@LaunchedEffect
        }
        val totalDurationMs = if (currentStory.storyTypeEnum == StoryType.VIDEO) {
            currentVideoDurationMs.coerceIn(3_000L, 60_000L)
        } else {
            5_500L
        }
        val tickMs = 50L
        while (progress < 1f) {
            delay(tickMs)
            progress = (progress + tickMs.toFloat() / totalDurationMs).coerceAtMost(1f)
        }
        if (safeIndex < stories.lastIndex) {
            currentIndex = safeIndex + 1
        } else {
            onAdvanceToNextGroup()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070A0F))
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
            ) {
                // Background Story Content
                when {
                    currentStory.storyTypeEnum == StoryType.VIDEO && !currentStory.videoBase64.isNullOrBlank() -> {
                        LoopingVideoPlayer(
                            videoBase64 = currentStory.videoBase64,
                            cacheKey = currentStory.id,
                            isPlaying = !isHoldingPause && !showViewersSheet && !showMentionInChatSheet && !showDeleteConfirmDialog && replyText.isEmpty(),
                            isMuted = isVideoMuted,
                            isLooping = true,
                            cropCenter = false,
                            onDurationKnown = { duration ->
                                currentVideoDurationMs = duration.coerceIn(3_000L, 60_000L)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    currentStory.storyTypeEnum == StoryType.PHOTO && decodedPhoto != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = decodedPhoto,
                                contentDescription = currentStory.caption.ifBlank { "Live story photo" },
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(storyGradientColors(currentStory.gradientEnum)))
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentStory.caption,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Left / Right tap & hold-to-pause navigation layer
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 88.dp, bottom = 112.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.35f)
                            .fillMaxHeight()
                            .pointerInput(currentStory.id) {
                                detectTapGestures(
                                    onPress = {
                                        isHoldingPause = true
                                        tryAwaitRelease()
                                        isHoldingPause = false
                                    },
                                    onTap = {
                                        if (safeIndex > 0) {
                                            currentIndex = safeIndex - 1
                                        } else {
                                            onBackToPrevGroup()
                                        }
                                    }
                                )
                            }
                            .testTag("story_prev_tap_zone")
                    )
                    Box(
                        modifier = Modifier
                            .weight(0.65f)
                            .fillMaxHeight()
                            .pointerInput(currentStory.id) {
                                detectTapGestures(
                                    onPress = {
                                        isHoldingPause = true
                                        tryAwaitRelease()
                                        isHoldingPause = false
                                    },
                                    onTap = {
                                        if (safeIndex < stories.lastIndex) {
                                            currentIndex = safeIndex + 1
                                        } else {
                                            onAdvanceToNextGroup()
                                        }
                                    }
                                )
                            }
                            .testTag("story_next_tap_zone")
                    )
                }

                // Top Scrim + Progress Bars + Author Header
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        stories.forEachIndexed { index, _ ->
                            val targetProgress = when {
                                index < safeIndex -> 1f
                                index == safeIndex -> progress
                                else -> 0f
                            }
                            val animated by animateFloatAsState(
                                targetValue = targetProgress,
                                animationSpec = tween(durationMillis = 50, easing = LinearEasing),
                                label = "storyProgress"
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.3f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(animated)
                                        .background(Color.White)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatarWithPresence(
                            name = group.authorName,
                            photoUrl = group.authorPhotoUrl,
                            presence = PresenceStatus.ONLINE,
                            size = 40.dp,
                            showPresenceDot = false,
                            avatarBase64 = group.authorAvatarBase64
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isMine) "My Story" else group.authorName,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatRelativeTimestamp(currentStory.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                        }
                        if (currentStory.storyTypeEnum == StoryType.VIDEO) {
                            IconButton(
                                onClick = { isVideoMuted = !isVideoMuted },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("story_video_mute_button")
                            ) {
                                Icon(
                                    imageVector = if (isVideoMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = if (isVideoMuted) "Unmute story video" else "Mute story video",
                                    tint = Color.White
                                )
                            }
                        }
                        IconButton(
                            onClick = { showMentionInChatSheet = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("mention_story_in_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.mention_story_in_chat),
                                tint = Color.White
                            )
                        }
                        if (isMine) {
                            IconButton(
                                onClick = { showDeleteConfirmDialog = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("delete_story_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete story",
                                    tint = Color.White
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("close_story_viewer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close story viewer",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Bottom Overlay (Mentions + Caption + Viewers OR E2EE Reply)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (currentStory.mentionedHandles.isNotEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            currentStory.mentionedHandles.forEach { handle ->
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = handle,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (currentStory.storyTypeEnum != StoryType.TEXT && currentStory.caption.isNotBlank()) {
                        Text(
                            text = currentStory.caption,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (isMine) {
                        val peerViewers = currentStory.viewedBy.filter { it != currentUserId }
                        val dirMap = directoryUsers.associateBy { it.uid }

                        if (showViewersSheet) {
                            Surface(
                                color = Color(0xFF141B26),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Viewed by ${peerViewers.size}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = Color.White
                                        )
                                        TextButton(
                                            onClick = { showViewersSheet = false },
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("Hide", color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    if (peerViewers.isEmpty()) {
                                        Text(
                                            text = "No contacts have viewed this story yet.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                    } else {
                                        peerViewers.forEach { viewerUid ->
                                            val peer = dirMap[viewerUid]
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                UserAvatarWithPresence(
                                                    name = peer?.displayName ?: "ToKer Contact",
                                                    photoUrl = peer?.photoUrl,
                                                    presence = peer?.presenceEnum ?: PresenceStatus.OFFLINE,
                                                    size = 32.dp
                                                )
                                                Text(
                                                    text = peer?.displayName ?: viewerUid.take(10),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                color = Color.White.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable { showViewersSheet = !showViewersSheet }
                                    .testTag("story_viewers_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = "Story viewers",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Viewed by ${peerViewers.size}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("🔥", "❤️", "👏", "🙌").forEach { emoji ->
                                Surface(
                                    color = Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(50))
                                        .clickable {
                                            onReplyPrivately(currentStory, emoji)
                                        }
                                        .testTag("story_quick_react_$emoji")
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = emoji, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = replyText,
                                onValueChange = { if (it.length <= 300) replyText = it },
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.story_reply_hint),
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
                                    focusedContainerColor = Color.Black.copy(alpha = 0.45f),
                                    unfocusedContainerColor = Color.Black.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("story_reply_input")
                            )
                            Surface(
                                color = if (replyText.isNotBlank()) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                                shape = CircleShape
                            ) {
                                IconButton(
                                    onClick = {
                                        val trimmed = replyText.trim()
                                        if (trimmed.isNotEmpty()) {
                                            onReplyPrivately(currentStory, trimmed)
                                            replyText = ""
                                        }
                                    },
                                    enabled = replyText.isNotBlank(),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("send_story_reply_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send encrypted reply",
                                        tint = if (replyText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMentionInChatSheet) {
        val peers = directoryUsers.filter { it.uid != currentUserId }
        ModalBottomSheet(
            onDismissRequest = { showMentionInChatSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.mention_story_in_chat), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Pick a contact to share & mention this story in an E2EE chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (peers.isEmpty()) {
                    Text(
                        text = "No other contacts available yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    peers.forEach { peer ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onMentionStoryToPeer(peer, currentStory)
                                    showMentionInChatSheet = false
                                }
                                .testTag("mention_story_peer_${peer.uid}")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                UserAvatarWithPresence(
                                    name = peer.displayName,
                                    photoUrl = peer.photoUrl,
                                    presence = peer.presenceEnum,
                                    size = 38.dp,
                                    avatarBase64 = peer.avatarBase64
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(peer.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = peer.handle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send mention", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showDeleteConfirmDialog) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.story_delete_confirm_title),
            message = stringResource(R.string.story_delete_confirm_body),
            onConfirm = {
                val idToDelete = currentStory.id
                showDeleteConfirmDialog = false
                if (stories.size <= 1) {
                    onDeleteStory(idToDelete)
                    onDismiss()
                } else {
                    onDeleteStory(idToDelete)
                    if (safeIndex >= stories.lastIndex) {
                        currentIndex = (safeIndex - 1).coerceAtLeast(0)
                    }
                }
            },
            onDismiss = { showDeleteConfirmDialog = false }
        )
    }
}
