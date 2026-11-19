package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AppAccentTheme
import com.example.data.AppFontStyle
import com.example.data.AppLanguage
import com.example.data.AppTextScale
import com.example.data.AppThemeMode
import com.example.data.DirectoryUser
import com.example.data.LocalAppStrings
import com.example.data.PresenceStatus
import com.example.data.PrivacyAudience
import com.example.data.PrivateAccount
import com.example.data.ThemePreferencesRepository
import com.example.data.ThemeSettings
import com.example.ui.components.UserAvatarWithPresence
import com.example.ui.components.compressAvatarToBase64
import com.example.ui.components.rememberDecodedFileBitmap
import com.example.ui.theme.accentSwatchColor
import com.example.ui.theme.fontFamilyForStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileControlSheet(
    myProfile: DirectoryUser?,
    privateAccount: PrivateAccount?,
    onDismiss: () -> Unit,
    onSaveProfile: (
        displayName: String,
        handle: String,
        statusMessage: String,
        statusMood: String,
        presence: PresenceStatus,
        discoverable: Boolean,
        avatarBase64: String?,
        clearAvatar: Boolean,
        readReceiptsEnabled: Boolean,
        lastSeenVisibility: PrivacyAudience,
        photoVisibility: PrivacyAudience,
        statusVisibility: PrivacyAudience
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strings = LocalAppStrings.current

    var displayName by remember(myProfile?.displayName) { mutableStateOf(myProfile?.displayName ?: "") }
    var handle by remember(myProfile?.handle) { mutableStateOf(myProfile?.handle ?: "@toker") }
    var statusMessage by remember(myProfile?.statusMessage) { mutableStateOf(myProfile?.statusMessage ?: "") }
    var statusMood by remember(myProfile?.statusMood) { mutableStateOf(myProfile?.statusMood ?: "Available") }
    var presence by remember(myProfile?.presence) { mutableStateOf(myProfile?.presenceEnum ?: PresenceStatus.ONLINE) }
    var pendingAvatarBase64 by remember(myProfile?.avatarBase64) { mutableStateOf(myProfile?.avatarBase64) }
    var clearAvatar by remember { mutableStateOf(false) }
    var isProcessingDp by remember { mutableStateOf(false) }

    val moods = listOf("Available", "In Focus", "In a Call", "On the Move", "Encrypted")

    val dpPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isProcessingDp = true
            scope.launch {
                val b64 = compressAvatarToBase64(context, uri)
                isProcessingDp = false
                if (b64 != null) {
                    pendingAvatarBase64 = b64
                    clearAvatar = false
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = strings.profileLabel,
                style = MaterialTheme.typography.titleLarge
            )

            // DP Control Row
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    var showFullMyDp by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { showFullMyDp = true },
                        contentAlignment = Alignment.Center
                    ) {
                        UserAvatarWithPresence(
                            name = displayName.ifBlank { "ToKer" },
                            photoUrl = if (clearAvatar) null else myProfile?.photoUrl,
                            presence = presence,
                            size = 64.dp,
                            avatarBase64 = if (clearAvatar) null else pendingAvatarBase64
                        )
                        if (isProcessingDp) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                        }
                    }
                    if (showFullMyDp) {
                        com.example.ui.components.ProfileAvatarViewerDialog(
                            name = displayName.ifBlank { "ToKer" },
                            handle = handle,
                            photoUrl = if (clearAvatar) null else myProfile?.photoUrl,
                            avatarBase64 = if (clearAvatar) null else pendingAvatarBase64,
                            statusMessage = statusMessage,
                            presence = presence,
                            onDismiss = { showFullMyDp = false }
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                dpPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("pick_profile_dp_button")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.galleryLabel)
                        }
                        if (!pendingAvatarBase64.isNullOrBlank() || (!myProfile?.photoUrl.isNullOrBlank() && !clearAvatar)) {
                            TextButton(
                                onClick = {
                                    pendingAvatarBase64 = null
                                    clearAvatar = true
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("remove_profile_dp_button")
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.removeLabel)
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = { if (it.length <= 60) displayName = it },
                label = { Text(strings.nameLabel) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_display_name_input")
            )

            OutlinedTextField(
                value = handle,
                onValueChange = { if (it.length <= 40) handle = it },
                label = { Text(strings.handleLabel) },
                leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_handle_input")
            )

            OutlinedTextField(
                value = statusMessage,
                onValueChange = { if (it.length <= 140) statusMessage = it },
                label = { Text(strings.bioLabel) },
                maxLines = 2,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_bio_input")
            )

            Text(strings.statusLabel, style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(PresenceStatus.ONLINE, PresenceStatus.AWAY, PresenceStatus.BUSY).forEach { p ->
                    val pLabel = when (p) {
                        PresenceStatus.ONLINE -> strings.onlineLabel
                        PresenceStatus.AWAY -> strings.awayLabel
                        PresenceStatus.BUSY -> strings.busyLabel
                        PresenceStatus.OFFLINE -> strings.offlineLabel
                    }
                    FilterChip(
                        selected = presence == p,
                        onClick = { presence = p },
                        label = { Text(pLabel) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
            Text(strings.moodLabel, style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                moods.forEach { mood ->
                    FilterChip(
                        selected = statusMood == mood,
                        onClick = { statusMood = mood },
                        label = { Text(mood) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }

            Button(
                onClick = {
                    onSaveProfile(
                        displayName,
                        handle,
                        statusMessage,
                        statusMood,
                        presence,
                        myProfile?.discoverable ?: true,
                        pendingAvatarBase64,
                        clearAvatar,
                        privateAccount?.readReceiptsEnabled ?: true,
                        myProfile?.lastSeenVisibilityEnum ?: PrivacyAudience.EVERYONE,
                        myProfile?.photoVisibilityEnum ?: PrivacyAudience.EVERYONE,
                        myProfile?.statusVisibilityEnum ?: PrivacyAudience.EVERYONE
                    )
                    onDismiss()
                },
                enabled = displayName.isNotBlank() && !isProcessingDp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_profile_controls_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.saveLabel, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeCustomizationSheet(
    themeSettings: ThemeSettings,
    themePrefs: ThemePreferencesRepository,
    onDismiss: () -> Unit,
    onThemeFeedback: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val strings = LocalAppStrings.current
    val wallpaperPreview = rememberDecodedFileBitmap(themeSettings.chatWallpaperPath)
    val isDark = when (themeSettings.themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }

    val wallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                themePrefs.saveGalleryWallpaper(uri)
                    .onSuccess { onThemeFeedback(strings.savedLabel) }
                    .onFailure { onThemeFeedback(strings.cancelLabel) }
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = strings.themeAppearanceLabel,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            // Live Chat Theme & Text Preview Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            ) {
                if (wallpaperPreview != null) {
                    Image(
                        bitmap = wallpaperPreview,
                        contentDescription = strings.previewLabel,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = themeSettings.wallpaperDim))
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "${strings.messageInputHint} • ${strings.previewLabel}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = "${strings.themeAppearanceLabel} • ${themeSettings.accentTheme.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Text(strings.modeLabel, style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppThemeMode.entries.forEach { mode ->
                    val modeLabel = when (mode) {
                        AppThemeMode.SYSTEM -> strings.systemLabel
                        AppThemeMode.LIGHT -> strings.lightLabel
                        AppThemeMode.DARK -> strings.darkLabel
                    }
                    FilterChip(
                        selected = themeSettings.themeMode == mode,
                        onClick = { scope.launch { themePrefs.setThemeMode(mode) } },
                        leadingIcon = {
                            Icon(
                                imageVector = when (mode) {
                                    AppThemeMode.DARK -> Icons.Default.DarkMode
                                    AppThemeMode.LIGHT -> Icons.Default.LightMode
                                    AppThemeMode.SYSTEM -> Icons.Default.Visibility
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(modeLabel) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("theme_mode_${mode.name.lowercase()}")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.colorLabel, style = MaterialTheme.typography.labelLarge)
                Text(
                    text = "${AppAccentTheme.entries.size} • ${themeSettings.accentTheme.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            LazyHorizontalGrid(
                rows = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(AppAccentTheme.entries, key = { it.name }) { accent ->
                    val swatchColor = accentSwatchColor(accent, isDark = isDark)
                    val selected = themeSettings.accentTheme == accent
                    Surface(
                        color = if (selected) swatchColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .width(120.dp)
                            .height(48.dp)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) swatchColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { scope.launch { themePrefs.setAccentTheme(accent) } }
                            .testTag("accent_theme_${accent.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Text(
                                text = accent.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.fontLabel, style = MaterialTheme.typography.labelLarge)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${AppFontStyle.entries.size} • ${themeSettings.fontStyle.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (
                        themeSettings.fontStyle != AppFontStyle.CLASSIC ||
                        themeSettings.textScale != AppTextScale.MEDIUM ||
                        themeSettings.boldText
                    ) {
                        TextButton(
                            onClick = { scope.launch { themePrefs.resetTextStyle() } },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("reset_text_style_button")
                        ) {
                            Text(strings.resetLabel)
                        }
                    }
                }
            }
            LazyHorizontalGrid(
                rows = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(142.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(AppFontStyle.entries, key = { it.name }) { style ->
                    val selected = themeSettings.fontStyle == style
                    val previewFamily = fontFamilyForStyle(style)
                    Surface(
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .width(106.dp)
                            .height(64.dp)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { scope.launch { themePrefs.setFontStyle(style) } }
                            .testTag("font_style_${style.name.lowercase()}")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Aa",
                                fontFamily = previewFamily,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                            Text(
                                text = style.label,
                                fontFamily = previewFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Text(strings.sizeLabel, style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppTextScale.entries.forEach { scale ->
                    val scaleLabel = when (scale) {
                        AppTextScale.COMPACT -> strings.smallLabel
                        AppTextScale.MEDIUM -> strings.defaultLabel
                        AppTextScale.LARGE -> strings.largeLabel
                        AppTextScale.XLARGE -> strings.xlLabel
                    }
                    FilterChip(
                        selected = themeSettings.textScale == scale,
                        onClick = { scope.launch { themePrefs.setTextScale(scale) } },
                        label = { Text(scaleLabel) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("text_scale_${scale.name.lowercase()}")
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { scope.launch { themePrefs.setBoldText(!themeSettings.boldText) } }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(strings.boldLabel, style = MaterialTheme.typography.titleSmall)
                Switch(
                    checked = themeSettings.boldText,
                    onCheckedChange = { checked ->
                        scope.launch { themePrefs.setBoldText(checked) }
                    },
                    modifier = Modifier.testTag("bold_text_switch")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            Text(strings.wallpaperLabel, style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        wallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("pick_wallpaper_gallery_button")
                ) {
                    Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.galleryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (themeSettings.chatWallpaperPath != null) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                themePrefs.clearGalleryWallpaper()
                                onThemeFeedback(strings.resetLabel)
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("clear_wallpaper_button")
                    ) {
                        Text(strings.resetLabel)
                    }
                }
            }

            if (themeSettings.chatWallpaperPath != null) {
                Column {
                    Text(
                        text = "${strings.dimLabel} (${(themeSettings.wallpaperDim * 100).toInt()}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = themeSettings.wallpaperDim,
                        onValueChange = { newVal ->
                            scope.launch { themePrefs.setWallpaperDim(newVal) }
                        },
                        valueRange = 0.15f..0.85f
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerBottomSheet(
    currentLanguageCode: String,
    onSelectLanguage: (AppLanguage) -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(strings.languageSectionTitle, style = MaterialTheme.typography.titleLarge)
            AppLanguage.entries.forEach { lang ->
                val selected = currentLanguageCode == lang.code
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            onSelectLanguage(lang)
                            onDismiss()
                        }
                        .testTag("language_row_${lang.code}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(lang.nativeLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(lang.englishName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (selected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = strings.languageLabel, tint = MaterialTheme.colorScheme.primary)
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
fun InviteFriendsSheet(
    myProfile: DirectoryUser?,
    directoryUsers: List<DirectoryUser>,
    currentUserId: String,
    onStartChatWithPeer: (DirectoryUser) -> Unit,
    onCopiedMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val strings = LocalAppStrings.current

    val myHandle = myProfile?.handle ?: "@toker_user"
    val myName = myProfile?.displayName ?: "ToKer"
    val fingerprint = myProfile?.keyFingerprint ?: ""
    val inviteText = buildString {
        append("ToKer ($myName • $myHandle")
        if (fingerprint.isNotBlank()) append(" • $fingerprint")
        append(") https://ais-pre-s4iiq3b4eixirwhcf2vh3h-216920005579.asia-east1.run.app")
    }

    val peers = directoryUsers.filter { it.uid != currentUserId }

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = strings.shareAppLabel,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${myProfile?.displayName ?: "ToKer"} ($myHandle)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = inviteText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "ToKer")
                            putExtra(Intent.EXTRA_TEXT, inviteText)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, strings.shareAppLabel))
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("share_invite_intent_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.shareLabel)
                }
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(inviteText))
                        onCopiedMessage(strings.copiedLabel)
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("copy_invite_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = strings.copyLabel, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.copyLabel)
                }
            }

            if (peers.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Text(
                    text = "${strings.tabContacts} (${peers.size})",
                    style = MaterialTheme.typography.titleSmall
                )
                peers.forEach { peer ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onDismiss()
                                onStartChatWithPeer(peer)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatarWithPresence(
                                name = peer.displayName,
                                photoUrl = peer.photoUrl,
                                presence = peer.presenceEnum,
                                size = 40.dp,
                                avatarBase64 = peer.avatarBase64
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(peer.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(peer.handle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                contentDescription = strings.newChatLabel,
                                tint = MaterialTheme.colorScheme.primary
                            )
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
fun CreateGroupSheet(
    peers: List<DirectoryUser>,
    onCreateGroup: (name: String, description: String, avatarBase64: String?, selectedPeers: List<DirectoryUser>) -> Unit,
    onInviteFriendsClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strings = LocalAppStrings.current

    var groupName by remember { mutableStateOf("") }
    var groupDesc by remember { mutableStateOf("") }
    var groupAvatarBase64 by remember { mutableStateOf<String?>(null) }
    var isProcessingDp by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }

    val groupDpPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isProcessingDp = true
            scope.launch {
                val b64 = compressAvatarToBase64(context, uri)
                isProcessingDp = false
                if (b64 != null) groupAvatarBase64 = b64
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.GroupAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(strings.newGroupLabel, style = MaterialTheme.typography.titleLarge)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            groupDpPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        .testTag("pick_group_dp_button")
                ) {
                    UserAvatarWithPresence(
                        name = groupName.ifBlank { strings.newGroupLabel },
                        photoUrl = null,
                        presence = PresenceStatus.ONLINE,
                        size = 58.dp,
                        showPresenceDot = false,
                        avatarBase64 = groupAvatarBase64
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { if (it.length <= 60) groupName = it },
                        label = { Text(strings.nameLabel) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("group_name_input")
                    )
                }
            }

            OutlinedTextField(
                value = groupDesc,
                onValueChange = { if (it.length <= 140) groupDesc = it },
                label = { Text(strings.descriptionLabel) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("group_desc_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${strings.activeMembersLabel} (${selectedIds.size})",
                    style = MaterialTheme.typography.titleSmall
                )
                TextButton(
                    onClick = {
                        onDismiss()
                        onInviteFriendsClick()
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(strings.shareAppLabel)
                }
            }

            if (peers.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onInviteFriendsClick()
                            },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.shareAppLabel)
                        }
                    }
                }
            } else {
                peers.forEach { peer ->
                    val isSelected = peer.uid in selectedIds
                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                if (isSelected) selectedIds.remove(peer.uid) else selectedIds.add(peer.uid)
                            }
                            .testTag("group_select_peer_${peer.uid}")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatarWithPresence(
                                name = peer.displayName,
                                photoUrl = peer.photoUrl,
                                presence = peer.presenceEnum,
                                size = 42.dp,
                                avatarBase64 = peer.avatarBase64
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(peer.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(peer.handle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val chosen = peers.filter { it.uid in selectedIds }
                    onCreateGroup(groupName, groupDesc, groupAvatarBase64, chosen)
                    onDismiss()
                },
                enabled = groupName.trim().isNotEmpty() && selectedIds.isNotEmpty() && !isProcessingDp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("create_group_submit_button")
            ) {
                Icon(Icons.Default.GroupAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.newGroupLabel, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
