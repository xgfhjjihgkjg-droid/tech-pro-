package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.drawable.AnimatedImageDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.LocalAppStrings
import com.example.data.PresenceStatus
import com.example.data.RichMessageCodec
import com.example.data.RichMessagePayload
import com.example.data.RichMessageType
import com.example.media.LocationHelper
import com.example.media.MediaUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale

val QUICK_EMOJI_SET = listOf(
    "👍", "❤️", "🔥", "😂", "😍", "🙌",
    "👏", "🎉", "🚀", "🔒", "🛡️", "✨",
    "🥳", "😮", "😢", "🤔", "💯", "🙏",
    "👌", "😎", "💬", "🎬", "📍", "📊"
)

val CATEGORIZED_EMOJIS: List<Pair<String, List<String>>> = listOf(
    "Smileys" to listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
        "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
        "😋", "😛", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳",
        "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "😣", "😖", "😫",
        "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤯", "😳", "🥵"
    ),
    "Hearts & Gestures" to listOf(
        "👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "🤙", "👋", "🤚",
        "✋", "🖐️", "👏", "🙌", "🙏", "🤝", "🫶", "💪", "❤️", "🧡",
        "💛", "💚", "💙", "💜", "🖤", "🤍", "💖", "💗", "💓", "💞"
    ),
    "India & Festive" to listOf(
        "🇮🇳", "🪔", "🪷", "🕉️", "☪️", "🪯", "✝️", "🎊", "🎉", "🎆",
        "🎇", "🥁", "🎶", "💃", "🕺", "🦚", "🐘", "🍵", "☕", "🍛",
        "🥘", "🫓", "🍨", "🍬", "🌺", "🌸", "🌼", "🌞", "🌙", "⭐"
    ),
    "Daily & Tech" to listOf(
        "🚀", "🔥", "✨", "💯", "⚡", "🛡️", "🔒", "🔑", "📱", "💻",
        "📷", "🎥", "🎙️", "🎧", "📞", "💬", "📍", "📊", "📄", "⏰"
    )
)

data class StickerItem(
    val id: String,
    val title: String,
    val emoji: String,
    val bgHex: Long,
    val accentHex: Long
)

val CURATED_STICKER_PACK = listOf(
    StickerItem("stk_namaste", "Namaste!", "🙏", 0xFFFFF3E0, 0xFFE65100),
    StickerItem("stk_love", "So Much Love", "🫶", 0xFFFCE4EC, 0xFFC2185B),
    StickerItem("stk_fire", "On Fire!", "🔥", 0xFFFFEBEE, 0xFFD84315),
    StickerItem("stk_laugh", "Haha LOL", "🤣", 0xFFFFF8E1, 0xFFF57F17),
    StickerItem("stk_party", "Shabash!", "🥳", 0xFFEDE7F6, 0xFF5E35B1),
    StickerItem("stk_rocket", "Let's Go!", "🚀", 0xFFE3F2FD, 0xFF1565C0),
    StickerItem("stk_chai", "Chai Time", "☕", 0xFFEFEBE9, 0xFF5D4037),
    StickerItem("stk_cool", "Swag Mode", "😎", 0xFFE0F2F1, 0xFF00695C),
    StickerItem("stk_hundred", "100% Real", "💯", 0xFFFFF3E0, 0xFFEF6C00),
    StickerItem("stk_mindblown", "Mind Blown", "🤯", 0xFFF3E5F5, 0xFF7B1FA2),
    StickerItem("stk_cheers", "Celebration", "🎉", 0xFFE8F5E9, 0xFF2E7D32),
    StickerItem("stk_ok", "Done Boss!", "👌", 0xFFE1F5FE, 0xFF0277BD)
)

data class GifPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val emojis: List<String>,
    val startHex: Long,
    val endHex: Long
)

val CURATED_GIF_PACK = listOf(
    GifPreset("gif_party", "PARTY VIBES", "Wohoo!", listOf("🥳", "🎉", "✨"), 0xFF6A11CB, 0xFF2575FC),
    GifPreset("gif_applause", "CLAP CLAP", "Superb!", listOf("👏", "🌟", "🙌"), 0xFF11998E, 0xFF38EF7D),
    GifPreset("gif_haha", "ROFL LOL", "Too Funny!", listOf("😂", "🤣", "💀"), 0xFFF7971E, 0xFFFFD200),
    GifPreset("gif_heart", "SENDING LOVE", "Dil Se", listOf("❤️", "🫶", "💖"), 0xFFFF416C, 0xFFFF4B2B),
    GifPreset("gif_swag", "BOSS LEVEL", "Cool", listOf("😎", "🔥", "💯"), 0xFF0F2027, 0xFF2C5364),
    GifPreset("gif_namaste", "NAMASTE", "Swagatam", listOf("🙏", "🪷", "✨"), 0xFFFF8008, 0xFFFFC837),
    GifPreset("gif_thinking", "HMMM...", "Thinking", listOf("🤔", "🧐", "💭"), 0xFF4776E6, 0xFF8E54E9),
    GifPreset("gif_rocket", "TO THE MOON", "Fast!", listOf("🚀", "⚡", "💫"), 0xFF1D2B64, 0xFFF8CDDA)
)

@Composable
fun LoopingVideoPlayer(
    videoBase64: String,
    cacheKey: String,
    isPlaying: Boolean = true,
    isMuted: Boolean = false,
    isLooping: Boolean = true,
    cropCenter: Boolean = false,
    onDurationKnown: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var videoFile by remember(cacheKey, videoBase64) { mutableStateOf<File?>(null) }
    var activeSurface by remember { mutableStateOf<Surface?>(null) }
    var textureWidth by remember { mutableIntStateOf(0) }
    var textureHeight by remember { mutableIntStateOf(0) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    LaunchedEffect(cacheKey, videoBase64) {
        videoFile = MediaUtils.writeVideoBase64ToCacheFile(context, cacheKey, videoBase64)
    }

    fun applyAspectTransform() {
        val tv = textureViewRef ?: return
        if (textureWidth <= 0 || textureHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return
        val viewAspect = textureWidth.toFloat() / textureHeight.toFloat()
        val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
        val scaleX: Float
        val scaleY: Float
        if (cropCenter) {
            if (videoAspect > viewAspect) {
                scaleX = videoAspect / viewAspect
                scaleY = 1f
            } else {
                scaleX = 1f
                scaleY = viewAspect / videoAspect
            }
        } else {
            if (videoAspect > viewAspect) {
                scaleX = 1f
                scaleY = viewAspect / videoAspect
            } else {
                scaleX = videoAspect / viewAspect
                scaleY = 1f
            }
        }
        val matrix = Matrix().apply {
            setScale(scaleX, scaleY, textureWidth / 2f, textureHeight / 2f)
        }
        tv.setTransform(matrix)
    }

    DisposableEffect(videoFile, activeSurface) {
        val file = videoFile
        val surface = activeSurface
        var mp: MediaPlayer? = null
        if (file != null && file.exists() && surface != null) {
            mp = MediaPlayer().apply {
                setSurface(surface)
                setDataSource(file.absolutePath)
                this.isLooping = isLooping
                setOnVideoSizeChangedListener { _, w, h ->
                    videoWidth = w
                    videoHeight = h
                    applyAspectTransform()
                }
                setOnPreparedListener { player ->
                    val vol = if (isMuted) 0f else 1f
                    player.setVolume(vol, vol)
                    if (player.duration > 0) {
                        onDurationKnown(player.duration.toLong())
                    }
                    applyAspectTransform()
                    if (isPlaying) {
                        player.start()
                    }
                }
                prepareAsync()
            }
            mediaPlayerRef = mp
        }
        onDispose {
            mediaPlayerRef = null
            runCatching { mp?.stop() }
            runCatching { mp?.release() }
        }
    }

    LaunchedEffect(isMuted) {
        val vol = if (isMuted) 0f else 1f
        runCatching { mediaPlayerRef?.setVolume(vol, vol) }
    }

    LaunchedEffect(isPlaying) {
        val mp = mediaPlayerRef ?: return@LaunchedEffect
        runCatching {
            if (isPlaying && !mp.isPlaying) {
                mp.start()
            } else if (!isPlaying && mp.isPlaying) {
                mp.pause()
            }
        }
    }

    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    textureViewRef = this
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            textureWidth = w
                            textureHeight = h
                            activeSurface = Surface(st)
                            applyAspectTransform()
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                            textureWidth = w
                            textureHeight = h
                            applyAspectTransform()
                        }

                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            activeSurface?.release()
                            activeSurface = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (videoFile == null) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun RichMessageBubbleContent(
    payload: RichMessagePayload,
    pollVotes: Map<String, Long>,
    reactions: Map<String, String>,
    currentUserId: String,
    contentColor: Color,
    isMine: Boolean,
    onVotePoll: (Int) -> Unit,
    onOpenReactionPicker: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onOpenImageFullscreen: (String) -> Unit,
    onOpenVideoFullscreen: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (payload.forwarded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Forward,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.72f),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "Forwarded",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.72f)
                )
            }
        }
        if (payload.replyToMessageId.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = payload.replyToSenderName.ifBlank { "Reply" },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = payload.replyToPreview.ifBlank { "Encrypted message" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        when (payload.typeEnum) {
            RichMessageType.EMOJI -> {
                Text(
                    text = payload.text.ifBlank { "😊" },
                    fontSize = 38.sp,
                    lineHeight = 44.sp
                )
            }

            RichMessageType.STICKER -> {
                val stk = CURATED_STICKER_PACK.firstOrNull { it.id == payload.stickerId }
                    ?: StickerItem(
                        id = payload.stickerId,
                        title = payload.text.ifBlank { "Sticker" },
                        emoji = payload.text.ifBlank { "✨" }.take(2),
                        bgHex = 0xFFFFF3E0,
                        accentHex = 0xFFE65100
                    )
                StickerBubbleView(sticker = stk)
            }

            RichMessageType.GIF -> {
                val b64 = payload.mediaBase64
                if (!b64.isNullOrBlank()) {
                    AnimatedGifBase64Box(
                        base64 = b64,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenImageFullscreen(b64) }
                    )
                } else {
                    val preset = CURATED_GIF_PACK.firstOrNull { it.id == payload.gifId }
                        ?: CURATED_GIF_PACK.first()
                    AnimatedPresetGifCard(
                        preset = preset,
                        modifier = Modifier
                            .width(210.dp)
                            .height(116.dp)
                    )
                }
            }

            RichMessageType.IMAGE -> {
                val decoded = rememberDecodedBase64Bitmap(payload.mediaBase64)
                if (decoded != null) {
                    Image(
                        bitmap = decoded,
                        contentDescription = payload.text.ifBlank { "Encrypted image attachment" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { payload.mediaBase64?.let(onOpenImageFullscreen) },
                        contentScale = ContentScale.Crop
                    )
                }
                if (payload.text.isNotBlank()) {
                    Text(
                        text = payload.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                }
            }

            RichMessageType.VIDEO -> {
                val thumb = rememberDecodedBase64Bitmap(payload.thumbBase64)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0E141F))
                        .clickable {
                            payload.mediaBase64?.let { b64 ->
                                onOpenVideoFullscreen(b64, payload.text)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (thumb != null) {
                        Image(
                            bitmap = thumb,
                            contentDescription = "Encrypted video preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.28f))
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = CircleShape,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play encrypted video",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "🎬 ${RichMessageCodec.formatDurationShort(payload.durationMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                if (payload.text.isNotBlank()) {
                    Text(
                        text = payload.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                }
            }

            RichMessageType.DOCUMENT -> {
                val docName = payload.docName.ifBlank { "ToKer_Document" }
                val sizeLabel = RichMessageCodec.formatFileSize(payload.docSizeBytes)
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val b64 = payload.mediaBase64 ?: return@clickable
                            scope.launch {
                                val uri = MediaUtils.writeDocumentAndBuildUri(
                                    context = context,
                                    cacheKey = "${docName}_${payload.docSizeBytes}",
                                    fileName = docName,
                                    base64 = b64
                                )
                                if (uri != null) {
                                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, payload.docMimeType.ifBlank { "*/*" })
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    runCatching {
                                        context.startActivity(Intent.createChooser(viewIntent, "Open $docName"))
                                    }
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            shape = CircleShape,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "Document",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = docName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$sizeLabel • Tap to open",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open document",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (payload.text.isNotBlank()) {
                    Text(
                        text = payload.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                }
            }

            RichMessageType.LOCATION -> {
                val lat = payload.latitude ?: 0.0
                val lng = payload.longitude ?: 0.0
                val label = payload.locationLabel.ifBlank { "Shared Location" }
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val uri = Uri.parse(
                                String.format(
                                    Locale.US,
                                    "geo:%.6f,%.6f?q=%.6f,%.6f(%s)",
                                    lat,
                                    lng,
                                    lat,
                                    lng,
                                    Uri.encode(label)
                                )
                            )
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            shape = CircleShape,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "Location pin",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = String.format(Locale.US, "%.4f, %.4f • Open in Maps", lat, lng),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            RichMessageType.POLL -> {
                val options = payload.pollOptions
                val totalVotes = pollVotes.size.coerceAtLeast(0)
                val myVote = pollVotes[currentUserId]?.toInt()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Poll,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = payload.pollQuestion.ifBlank { "Encrypted Poll" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                    }
                    options.forEachIndexed { idx, optLabel ->
                        val count = pollVotes.values.count { it.toInt() == idx }
                        val ratio = if (totalVotes > 0) count.toFloat() / totalVotes.toFloat() else 0f
                        val isSelected = myVote == idx
                        Surface(
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else contentColor.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onVotePoll(idx) }
                                .testTag("poll_option_$idx")
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (ratio > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .fillMaxWidth(ratio)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Your vote",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Text(
                                            text = optLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = if (totalVotes > 0) "${(ratio * 100).toInt()}% ($count)" else "0",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = "$totalVotes total vote(s) • Tap an option to vote",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.72f)
                    )
                }
            }

            RichMessageType.STORY_MENTION -> {
                val thumb = rememberDecodedBase64Bitmap(payload.thumbBase64)
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (thumb != null) {
                            Image(
                                bitmap = thumb,
                                contentDescription = "Mentioned story thumbnail",
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mentioned ${payload.storyMentionAuthor}'s ${payload.storyMentionType.lowercase()} story",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (payload.storyMentionCaption.isNotBlank()) {
                                Text(
                                    text = payload.storyMentionCaption,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                if (payload.text.isNotBlank()) {
                    Text(
                        text = payload.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor
                    )
                }
            }

            RichMessageType.VOICE -> {
                VoiceMessagePlayer(
                    audioBase64 = payload.mediaBase64.orEmpty(),
                    durationMs = payload.durationMs,
                    contentColor = contentColor
                )
            }

            RichMessageType.TEXT, RichMessageType.CALL_LOG -> {
                Text(
                    text = payload.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor
                )
            }
        }

        // Reaction pills row (only shown when reactions exist; add via double-tap or long-press)
        val groupedReactions = remember(reactions) {
            reactions.values.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
        }
        if (groupedReactions.isNotEmpty()) {
            val myReaction = reactions[currentUserId]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    groupedReactions.forEach { (emoji, count) ->
                        val iReacted = myReaction == emoji
                        Surface(
                            color = if (iReacted) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onToggleReaction(emoji) }
                        ) {
                            Text(
                                text = if (count > 1) "$emoji $count" else emoji,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RichAttachmentBar(
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickDocument: () -> Unit = {},
    onOpenLocation: () -> Unit,
    onOpenPoll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AttachmentActionPill(
                icon = Icons.Default.Image,
                label = stringResource(R.string.send_image_label),
                tag = "attach_image_button",
                onClick = onPickImage
            )
            AttachmentActionPill(
                icon = Icons.Default.VideoLibrary,
                label = stringResource(R.string.send_video_label),
                tag = "attach_video_button",
                onClick = onPickVideo
            )
            AttachmentActionPill(
                icon = Icons.Default.Description,
                label = stringResource(R.string.send_document_label),
                tag = "attach_document_button",
                onClick = onPickDocument
            )
            AttachmentActionPill(
                icon = Icons.Default.LocationOn,
                label = stringResource(R.string.send_location_label),
                tag = "attach_location_button",
                onClick = onOpenLocation
            )
            AttachmentActionPill(
                icon = Icons.Default.Poll,
                label = stringResource(R.string.send_poll_label),
                tag = "attach_poll_button",
                onClick = onOpenPoll
            )
        }
    }
}

@Composable
private fun AttachmentActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(tag)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            shape = CircleShape,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EmojiPickerBottomSheet(
    title: String = "Emojis, Stickers & GIFs",
    onAppendEmoji: ((String) -> Unit)? = null,
    onSendDirectEmoji: (String) -> Unit,
    onSendSticker: ((StickerItem) -> Unit)? = null,
    onSendGifPreset: ((GifPreset) -> Unit)? = null,
    onSendGalleryGif: ((mediaBase64: String, label: String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strings = LocalAppStrings.current
    val supportsRichTabs = onSendSticker != null || onSendGifPreset != null
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedEmojiCategory by remember { mutableIntStateOf(0) }
    var isProcessingGif by remember { mutableStateOf(false) }

    val gifGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && onSendGalleryGif != null) {
            isProcessingGif = true
            scope.launch {
                val encoded = encodePickedGifOrImageToBase64(context, uri)
                isProcessingGif = false
                if (encoded != null) {
                    onSendGalleryGif(encoded, "GIF")
                    onDismiss()
                }
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
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)

            if (supportsRichTabs) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(strings.emojiTabLabel) },
                        modifier = Modifier.testTag("picker_tab_emojis")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(strings.stickersTabLabel) },
                        modifier = Modifier.testTag("picker_tab_stickers")
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text(strings.gifsTabLabel) },
                        modifier = Modifier.testTag("picker_tab_gifs")
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    if (supportsRichTabs) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CATEGORIZED_EMOJIS.forEachIndexed { idx, (catName, _) ->
                                FilterChip(
                                    selected = selectedEmojiCategory == idx,
                                    onClick = { selectedEmojiCategory = idx },
                                    label = { Text(catName) },
                                    modifier = Modifier.heightIn(min = 40.dp)
                                )
                            }
                        }
                        val emojiList = CATEGORIZED_EMOJIS.getOrNull(selectedEmojiCategory)?.second ?: QUICK_EMOJI_SET
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                emojiList.forEach { emoji ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                if (onAppendEmoji != null) {
                                                    onAppendEmoji(emoji)
                                                } else {
                                                    onSendDirectEmoji(emoji)
                                                    onDismiss()
                                                }
                                            }
                                            .testTag("emoji_cell_$emoji")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(emoji, fontSize = 24.sp)
                                        }
                                    }
                                }
                            }
                        }
                        if (onAppendEmoji != null) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("🔥", "😂", "❤️", "🙏", "🥳", "👋").forEach { emoji ->
                                    OutlinedButton(
                                        onClick = {
                                            onSendDirectEmoji(emoji)
                                            onDismiss()
                                        },
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Text("Send $emoji")
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Tap an emoji to react.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            QUICK_EMOJI_SET.forEach { emoji ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            onSendDirectEmoji(emoji)
                                            onDismiss()
                                        }
                                        .testTag("emoji_cell_$emoji")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(emoji, fontSize = 26.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 310.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CURATED_STICKER_PACK.forEach { stk ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable {
                                            onSendSticker?.invoke(stk)
                                            onDismiss()
                                        }
                                        .testTag("sticker_cell_${stk.id}")
                                ) {
                                    StickerBubbleView(sticker = stk, compact = true)
                                }
                            }
                        }
                    }
                }

                2 -> {
                    if (onSendGalleryGif != null) {
                        OutlinedButton(
                            onClick = {
                                gifGalleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !isProcessingGif,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("pick_gallery_gif_button")
                        ) {
                            if (isProcessingGif) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Gif, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.pickGifFromGallery)
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CURATED_GIF_PACK.forEach { preset ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            onSendGifPreset?.invoke(preset)
                                            onDismiss()
                                        }
                                        .testTag("gif_preset_${preset.id}")
                                ) {
                                    AnimatedPresetGifCard(
                                        preset = preset,
                                        modifier = Modifier
                                            .width(156.dp)
                                            .height(92.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerBottomSheet(
    onSendLocation: (lat: Double, lng: Double, label: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isFetching by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var detectedLat by remember { mutableStateOf<Double?>(null) }
    var detectedLng by remember { mutableStateOf<Double?>(null) }
    var labelInput by remember { mutableStateOf("") }
    var customCoordsInput by remember { mutableStateOf("") }

    fun fetchGps() {
        isFetching = true
        statusMessage = null
        scope.launch {
            LocationHelper.fetchCurrentLocation(context)
                .onSuccess { resolved ->
                    detectedLat = resolved.latitude
                    detectedLng = resolved.longitude
                    if (labelInput.isBlank()) labelInput = resolved.label
                    customCoordsInput = String.format(Locale.US, "%.5f, %.5f", resolved.latitude, resolved.longitude)
                }
                .onFailure { err ->
                    statusMessage = err.message ?: "Unable to fetch GPS location."
                }
            isFetching = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasPermission = granted
        if (granted) {
            fetchGps()
        } else {
            statusMessage = "Location permission denied. You can still type a place or coordinates manually."
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && detectedLat == null) {
            fetchGps()
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
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.send_location_label), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.location_permission_rationale),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = {
                    if (hasPermission) {
                        fetchGps()
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                enabled = !isFetching,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("fetch_gps_location_button")
            ) {
                if (isFetching) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (hasPermission) "Refresh Live GPS Location" else "Grant Location & Detect GPS")
            }

            if (!statusMessage.isNullOrBlank()) {
                Text(
                    text = statusMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            OutlinedTextField(
                value = labelInput,
                onValueChange = { if (it.length <= 100) labelInput = it },
                label = { Text("Place / Label (e.g. Meet at Central Park)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("location_label_input")
            )

            OutlinedTextField(
                value = customCoordsInput,
                onValueChange = { customCoordsInput = it },
                label = { Text("Coordinates (lat, lng)") },
                placeholder = { Text("37.42200, -122.08400") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("location_coords_input")
            )

            val parsedManual = remember(customCoordsInput) {
                val parts = customCoordsInput.split(",").map { it.trim() }
                if (parts.size == 2) {
                    val lat = parts[0].toDoubleOrNull()
                    val lng = parts[1].toDoubleOrNull()
                    if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                        lat to lng
                    } else null
                } else null
            }

            val finalCoords = parsedManual ?: if (detectedLat != null && detectedLng != null) {
                detectedLat!! to detectedLng!!
            } else null

            Button(
                onClick = {
                    val coords = finalCoords ?: (0.0 to 0.0)
                    val cleanLabel = labelInput.trim().ifBlank {
                        String.format(Locale.US, "%.4f, %.4f", coords.first, coords.second)
                    }
                    onSendLocation(coords.first, coords.second, cleanLabel)
                    onDismiss()
                },
                enabled = finalCoords != null || labelInput.trim().isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("confirm_send_location_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send Encrypted Location")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollCreatorBottomSheet(
    onCreatePoll: (question: String, options: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Create Encrypted Poll", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = question,
                onValueChange = { if (it.length <= 140) question = it },
                label = { Text("Poll Question") },
                placeholder = { Text("Where should we meet tonight?") },
                maxLines = 2,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("poll_question_input")
            )

            options.forEachIndexed { idx, value ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { next ->
                            if (next.length <= 60) options[idx] = next
                        },
                        label = { Text("Option ${idx + 1}") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("poll_option_input_$idx")
                    )
                    if (options.size > 2) {
                        IconButton(
                            onClick = { options.removeAt(idx) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove option")
                        }
                    }
                }
            }

            if (options.size < 6) {
                TextButton(
                    onClick = { options.add("") },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Option")
                }
            }

            val cleanOptions = options.map { it.trim() }.filter { it.isNotEmpty() }
            Button(
                onClick = {
                    onCreatePoll(question.trim(), cleanOptions)
                    onDismiss()
                },
                enabled = question.trim().isNotEmpty() && cleanOptions.size >= 2,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("confirm_create_poll_button")
            ) {
                Icon(Icons.Default.Poll, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send Encrypted Poll")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun FullscreenImageDialog(
    imageBase64: String,
    onDismiss: () -> Unit
) {
    val decoded = rememberDecodedBase64Bitmap(imageBase64)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .safeDrawingPadding()
        ) {
            if (decoded != null) {
                Image(
                    bitmap = decoded,
                    contentDescription = "Fullscreen encrypted image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close image", tint = Color.White)
            }
        }
    }
}

@Composable
fun FullscreenVideoDialog(
    videoBase64: String,
    caption: String,
    cacheKey: String,
    onDismiss: () -> Unit
) {
    var isMuted by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .safeDrawingPadding()
        ) {
            LoopingVideoPlayer(
                videoBase64 = videoBase64,
                cacheKey = cacheKey,
                isPlaying = isPlaying,
                isMuted = isMuted,
                isLooping = true,
                cropCenter = false,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { isPlaying = !isPlaying }
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { isMuted = !isMuted },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (isMuted) "Unmute video" else "Mute video",
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close video", tint = Color.White)
                }
            }
            if (caption.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(16.dp)
                ) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceMessagePlayer(
    audioBase64: String,
    durationMs: Long,
    contentColor: Color
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cacheKey = remember(audioBase64, durationMs) {
        "${durationMs}_${audioBase64.hashCode()}"
    }

    var isPreparing by remember(cacheKey) { mutableStateOf(false) }
    var isPlaying by remember(cacheKey) { mutableStateOf(false) }
    var totalMs by remember(cacheKey) { mutableLongStateOf(durationMs.coerceAtLeast(1000L)) }
    var positionMs by remember(cacheKey) { mutableLongStateOf(0L) }
    var mediaPlayer by remember(cacheKey) { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(cacheKey) {
        onDispose {
            runCatching { mediaPlayer?.stop() }
            runCatching { mediaPlayer?.release() }
            mediaPlayer = null
        }
    }

    LaunchedEffect(isPlaying, mediaPlayer) {
        val mp = mediaPlayer
        while (isActive && isPlaying && mp != null) {
            runCatching {
                positionMs = mp.currentPosition.toLong().coerceAtLeast(0L)
                if (mp.duration > 0) {
                    totalMs = mp.duration.toLong()
                }
            }
            delay(100L)
        }
    }

    val progress = remember(positionMs, totalMs) {
        if (totalMs > 0L) (positionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) else 0f
    }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !isPreparing && audioBase64.isNotBlank()) {
                        val existing = mediaPlayer
                        if (existing != null) {
                            runCatching {
                                if (isPlaying) {
                                    existing.pause()
                                    isPlaying = false
                                } else {
                                    existing.start()
                                    isPlaying = true
                                }
                            }
                        } else {
                            isPreparing = true
                            scope.launch {
                                val file = MediaUtils.writeAudioBase64ToCacheFile(
                                    context = context,
                                    cacheKey = cacheKey,
                                    audioBase64 = audioBase64
                                )
                                if (file != null && file.exists()) {
                                    runCatching {
                                        val player = MediaPlayer().apply {
                                            setDataSource(file.absolutePath)
                                            setOnPreparedListener { mp ->
                                                if (mp.duration > 0) {
                                                    totalMs = mp.duration.toLong()
                                                }
                                                mp.start()
                                                isPlaying = true
                                                isPreparing = false
                                            }
                                            setOnCompletionListener {
                                                isPlaying = false
                                                positionMs = 0L
                                                runCatching { it.seekTo(0) }
                                            }
                                            prepareAsync()
                                        }
                                        mediaPlayer = player
                                    }.onFailure {
                                        isPreparing = false
                                    }
                                } else {
                                    isPreparing = false
                                }
                            }
                        }
                    }
                    .testTag("voice_message_play_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when {
                        isPreparing -> CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        isPlaying -> Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = stringResource(R.string.voice_pause_cd),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        else -> Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.voice_play_cd),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = contentColor.copy(alpha = 0.22f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = contentColor.copy(alpha = 0.72f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Voice note",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.78f)
                        )
                    }
                    Text(
                        text = if (isPlaying || positionMs > 0L) {
                            "${RichMessageCodec.formatDurationShort(positionMs)} / ${RichMessageCodec.formatDurationShort(totalMs)}"
                        } else {
                            RichMessageCodec.formatDurationShort(totalMs)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
fun StickerBubbleView(
    sticker: StickerItem,
    compact: Boolean = false
) {
    Surface(
        color = Color(sticker.bgHex),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .width(if (compact) 104.dp else 132.dp)
            .border(1.5.dp, Color(sticker.accentHex).copy(alpha = 0.45f), RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = if (compact) 10.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = sticker.emoji,
                fontSize = if (compact) 34.sp else 46.sp
            )
            Surface(
                color = Color(sticker.accentHex).copy(alpha = 0.14f),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = sticker.title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(sticker.accentHex),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun AnimatedPresetGifCard(
    preset: GifPreset,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "gif_${preset.id}")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(680, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val tilt by transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tilt"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(preset.startHex), Color(preset.endHex))
                )
            )
            .padding(10.dp)
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.45f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Text(
                text = "GIF",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                preset.emojis.forEachIndexed { idx, emoji ->
                    Text(
                        text = emoji,
                        fontSize = if (idx == 1) 26.sp else 20.sp,
                        modifier = Modifier
                            .scale(if (idx == 1) pulse else 1f)
                            .graphicsLayer { rotationZ = if (idx % 2 == 0) tilt else -tilt }
                    )
                }
            }
            Text(
                text = preset.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 1
            )
            Text(
                text = preset.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.88f),
                maxLines = 1
            )
        }
    }
}

@Composable
fun AnimatedGifBase64Box(
    base64: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val animatedDrawable = remember(base64) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                val drawable = ImageDecoder.decodeDrawable(source)
                if (drawable is AnimatedImageDrawable) {
                    drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    drawable.start()
                    drawable
                } else {
                    null
                }
            }.getOrNull()
        } else {
            null
        }
    }

    Box(modifier = modifier.background(Color(0xFF0E141F))) {
        if (animatedDrawable != null) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageDrawable(animatedDrawable)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val bmp = rememberDecodedBase64Bitmap(base64)
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = "Animated GIF attachment",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Surface(
            color = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Text(
                text = "GIF",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

private suspend fun encodePickedGifOrImageToBase64(context: android.content.Context, uri: Uri): String? {
    return withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri).orEmpty().lowercase(Locale.US)
        if (mime.contains("gif")) {
            val rawBytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (rawBytes != null && rawBytes.size <= 350_000) {
                return@withContext Base64.encodeToString(rawBytes, Base64.NO_WRAP)
            }
        }
        MediaUtils.compressImageToBase64(context, uri)
    }
}

fun formatDisappearingLabel(seconds: Long): String {
    return when {
        seconds <= 0L -> "Off"
        seconds < 3600L -> "${seconds / 60L}m"
        seconds < 86400L -> "${seconds / 3600L}h"
        else -> "${seconds / 86400L}d"
    }
}

private val DISAPPEARING_PRESETS = listOf(
    0L to "Off (Keep history)",
    300L to "5 Minutes",
    3600L to "1 Hour",
    86400L to "24 Hours",
    604800L to "7 Days",
    2592000L to "30 Days"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisappearingTimerBottomSheet(
    currentSeconds: Long,
    onSelectSeconds: (Long) -> Unit,
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
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(strings.disappearingMessagesTitle, style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = "New encrypted messages sent in this chat will automatically disappear after the chosen duration.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DISAPPEARING_PRESETS.forEach { (secs, label) ->
                val selected = currentSeconds == secs
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            onSelectSeconds(secs)
                            onDismiss()
                        }
                        .testTag("disappearing_option_${secs}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (secs == 0L) "${strings.disappearingMessagesOff} (Keep history)" else label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
fun ProfileAvatarViewerDialog(
    name: String,
    handle: String = "",
    photoUrl: String? = null,
    avatarBase64: String? = null,
    statusMessage: String = "",
    presence: PresenceStatus = PresenceStatus.OFFLINE,
    hideAvatar: Boolean = false,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .clickable(onClick = onDismiss)
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .clickable(enabled = false) {}
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name.ifBlank { "ToKer User" },
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (handle.isNotBlank()) {
                                Text(
                                    text = handle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close DP preview")
                        }
                    }

                    val decoded = rememberDecodedBase64Bitmap(if (hideAvatar) null else avatarBase64)
                    Box(
                        modifier = Modifier
                            .size(230.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            decoded != null -> Image(
                                bitmap = decoded,
                                contentDescription = "$name profile photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            !hideAvatar && !photoUrl.isNullOrBlank() -> coil.compose.AsyncImage(
                                model = photoUrl,
                                contentDescription = "$name profile photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            else -> UserAvatarWithPresence(
                                name = name,
                                photoUrl = photoUrl,
                                presence = presence,
                                size = 180.dp,
                                avatarBase64 = avatarBase64,
                                hideAvatar = hideAvatar
                            )
                        }
                    }

                    if (statusMessage.isNotBlank()) {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}


