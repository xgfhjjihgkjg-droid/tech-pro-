package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.CallLinkHelper
import com.example.data.CallSession
import com.example.data.CallType
import com.example.data.LocalAppStrings
import com.example.data.PresenceStatus
import com.example.data.PrivacyAudience
import com.example.data.ThemeSettings
import com.example.ui.theme.CallAcceptGreen
import com.example.ui.theme.CallEndRed
import com.example.ui.theme.StatusAway
import com.example.ui.theme.StatusBusy
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusOnline
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

fun presenceColor(status: PresenceStatus): Color = when (status) {
    PresenceStatus.ONLINE -> StatusOnline
    PresenceStatus.AWAY -> StatusAway
    PresenceStatus.BUSY -> StatusBusy
    PresenceStatus.OFFLINE -> StatusOffline
}

@Composable
fun UserAvatarWithPresence(
    name: String,
    photoUrl: String?,
    presence: PresenceStatus,
    size: Dp = 48.dp,
    showPresenceDot: Boolean = true,
    avatarBase64: String? = null,
    hideAvatar: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dotColor by animateColorAsState(targetValue = presenceColor(presence), label = "presenceColor")
    val decodedBase64 = rememberDecodedBase64Bitmap(if (hideAvatar) null else avatarBase64)

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (!hideAvatar && decodedBase64 != null) {
                Image(
                    bitmap = decodedBase64,
                    contentDescription = "$name avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (!hideAvatar && !photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "$name avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                val initials = name.trim()
                    .split(Regex("\\s+"))
                    .filter { it.isNotEmpty() }
                    .take(2)
                    .joinToString("") { it.first().uppercase() }
                    .ifEmpty { "?" }
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (showPresenceDot) {
            val dotSize = (size * 0.28f).coerceIn(10.dp, 16.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
fun ChatWallpaperBackdrop(
    themeSettings: ThemeSettings,
    modifier: Modifier = Modifier
) {
    val wallpaperBitmap = rememberDecodedFileBitmap(themeSettings.chatWallpaperPath)
    if (wallpaperBitmap != null) {
        Box(modifier = modifier.fillMaxSize()) {
            Image(
                bitmap = wallpaperBitmap,
                contentDescription = "Custom chat wallpaper",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = themeSettings.wallpaperDim))
            )
        }
    }
}

@Composable
fun PresenceChip(
    presence: PresenceStatus,
    modifier: Modifier = Modifier
) {
    val color = presenceColor(presence)
    val strings = LocalAppStrings.current
    val label = when (presence) {
        PresenceStatus.ONLINE -> strings.onlineLabel
        PresenceStatus.AWAY -> strings.awayLabel
        PresenceStatus.BUSY -> strings.busyLabel
        PresenceStatus.OFFLINE -> strings.offlineLabel
    }
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun EncryptedBadge(
    label: String = LocalAppStrings.current.encryptedLabel,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun IncomingCallBanner(
    call: CallSession,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatarWithPresence(
                name = call.callerName,
                photoUrl = call.callerPhotoUrl,
                presence = PresenceStatus.ONLINE,
                size = 46.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = call.callerName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (call.callTypeEnum == CallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp)
                    )
                    val strings = LocalAppStrings.current
                    Text(
                        text = "${strings.incomingLabel} • ${if (call.callTypeEnum == CallType.VIDEO) strings.videoLabel else strings.audioLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            val strings = LocalAppStrings.current
            IconButton(
                onClick = onDecline,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = CallEndRed,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("decline_incoming_call_button")
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = strings.declineLabel)
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onAccept,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = CallAcceptGreen,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("accept_incoming_call_button")
            ) {
                Icon(
                    imageVector = if (call.callTypeEnum == CallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call,
                    contentDescription = strings.acceptLabel
                )
            }
        }
    }
}

fun shareCallLink(
    context: Context,
    hostUid: String,
    hostName: String,
    callType: CallType,
    chooserTitle: String
) {
    val msg = CallLinkHelper.buildShareableMessage(
        hostUid = hostUid,
        hostName = hostName,
        callType = callType
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, msg)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

suspend fun compressAvatarToBase64(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sampleSize = 1
            val maxDim = max(bounds.outWidth, bounds.outHeight)
            while (maxDim / sampleSize > 512) {
                sampleSize *= 2
            }

            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            } ?: return@withContext null

            val scale = 280f / max(decoded.width, decoded.height).toFloat()
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).roundToInt().coerceAtLeast(1),
                    (decoded.height * scale).roundToInt().coerceAtLeast(1),
                    true
                )
            } else {
                decoded
            }

            val baos = ByteArrayOutputStream()
            var quality = 82
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            while (baos.size() > 105_000 && quality > 35) {
                baos.reset()
                quality -= 10
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            }
            if (scaled !== decoded) decoded.recycle()
            scaled.recycle()

            val encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            if (encoded.length <= 155_000) encoded else null
        } catch (_: Exception) {
            null
        }
    }

private object ToKerBitmapCache {
    private val cache = object : LruCache<String, ImageBitmap>(90) {}

    fun get(key: String): ImageBitmap? = synchronized(cache) { cache.get(key) }

    fun put(key: String, bitmap: ImageBitmap) {
        synchronized(cache) { cache.put(key, bitmap) }
    }
}

private fun base64CacheKey(base64: String): String =
    "b64_${base64.length}_${base64.hashCode()}"

@Composable
fun rememberDecodedBase64Bitmap(base64: String?): ImageBitmap? {
    val clean = base64?.takeIf { it.isNotBlank() } ?: return null
    val key = base64CacheKey(clean)
    val initial = ToKerBitmapCache.get(key)
    val state by produceState(initialValue = initial, key1 = key) {
        if (value == null) {
            value = withContext(Dispatchers.Default) {
                decodeBase64Avatar(clean)
            }
        }
    }
    return state
}

@Composable
fun rememberDecodedFileBitmap(path: String?): ImageBitmap? {
    val clean = path?.takeIf { it.isNotBlank() } ?: return null
    val file = File(clean)
    val cacheKey = "file_${clean}_${file.lastModified()}"
    val initial = ToKerBitmapCache.get(cacheKey)
    val state by produceState(initialValue = initial, key1 = cacheKey) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                decodeFileImageBitmap(clean)
            }
        }
    }
    return state
}

fun decodeBase64Avatar(base64: String?): ImageBitmap? {
    if (base64.isNullOrBlank()) return null
    val key = base64CacheKey(base64)
    ToKerBitmapCache.get(key)?.let { return it }
    return try {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val img = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        if (img != null) ToKerBitmapCache.put(key, img)
        img
    } catch (_: Exception) {
        null
    }
}

fun decodeFileImageBitmap(path: String?): ImageBitmap? {
    if (path.isNullOrBlank()) return null
    return try {
        val file = File(path)
        if (!file.exists()) return null
        val key = "file_${path}_${file.lastModified()}"
        ToKerBitmapCache.get(key)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sampleSize = 1
        val maxDim = max(bounds.outWidth, bounds.outHeight)
        while (maxDim / sampleSize > 1440) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val img = BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
        if (img != null) ToKerBitmapCache.put(key, img)
        img
    } catch (_: Exception) {
        null
    }
}

fun formatRelativeTimestamp(timestamp: Timestamp?): String {
    if (timestamp == null) return "Now"
    val millis = timestamp.toDate().time
    val diff = System.currentTimeMillis() - millis
    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000L}m ago"
        diff < 86_400_000L -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(millis))
    }
}

fun formatTimeOnly(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: Date()
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
}

fun isVisibleForAudience(audience: PrivacyAudience, isSavedContact: Boolean): Boolean =
    when (audience) {
        PrivacyAudience.EVERYONE -> true
        PrivacyAudience.CONTACTS -> isSavedContact
        PrivacyAudience.NOBODY -> false
    }

fun formatLastSeen(
    lastSeenAt: Timestamp?,
    presence: PresenceStatus,
    visibility: PrivacyAudience,
    isSavedContact: Boolean
): String {
    if (!isVisibleForAudience(visibility, isSavedContact)) {
        return "E2EE Private"
    }
    if (presence == PresenceStatus.ONLINE) {
        return "Online"
    }
    if (lastSeenAt == null) {
        return presence.label
    }
    val millis = lastSeenAt.toDate().time
    val diff = System.currentTimeMillis() - millis
    return when {
        diff < 60_000L -> "Last seen just now"
        diff < 3_600_000L -> "Last seen ${diff / 60_000L}m ago"
        diff < 86_400_000L -> "Last seen today at ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))}"
        else -> "Last seen ${SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(millis))}"
    }
}
