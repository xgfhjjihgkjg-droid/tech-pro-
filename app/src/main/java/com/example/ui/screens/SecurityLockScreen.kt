package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.data.BiometricAuthHelper
import com.example.data.SecurityPinHasher
import com.example.data.SecuritySettings

@Composable
fun AppLockScreen(
    settings: SecuritySettings,
    activity: FragmentActivity?,
    onUnlocked: () -> Unit,
    onDisableLockOrSignOut: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val biometricReady = remember(settings.biometricEnabled, context) {
        settings.biometricEnabled && BiometricAuthHelper.canAuthenticate(context)
    }
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun triggerBiometric() {
        val act = activity ?: return
        BiometricAuthHelper.showBiometricPrompt(
            activity = act,
            title = "Unlock ToKer",
            subtitle = "Confirm Face ID or Fingerprint to access your encrypted chats",
            negativeButtonText = "Use App PIN",
            onSuccess = onUnlocked,
            onUsePinFallback = { errorMessage = null },
            onError = { msg -> errorMessage = msg }
        )
    }

    LaunchedEffect(biometricReady) {
        if (biometricReady && activity != null) {
            triggerBiometric()
        }
    }

    fun tryPinUnlock(pin: String, explicitSubmit: Boolean) {
        if (!settings.hasPinConfigured) {
            if (biometricReady) {
                triggerBiometric()
            } else {
                onUnlocked()
            }
            return
        }
        if (SecurityPinHasher.verifyPin(pin, settings.pinSalt, settings.pinHash)) {
            errorMessage = null
            onUnlocked()
        } else if (explicitSubmit || pin.length >= 6) {
            errorMessage = "Incorrect App PIN. Please try again."
            enteredPin = ""
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "ToKer Security Lock",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Text(
                    text = "ToKer is Locked",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Enter your App PIN or use Biometric Face ID / Fingerprint to unlock.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // PIN dots indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val slots = 6
                    for (i in 0 until slots) {
                        val filled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (filled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = if (filled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("app_lock_error")
                    )
                }

                // Numeric keypad
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("BIO", "0", "DEL")
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            row.forEach { key ->
                                when (key) {
                                    "BIO" -> {
                                        KeypadActionKey(
                                            enabled = biometricReady && activity != null,
                                            tag = "biometric_unlock_button",
                                            onClick = { triggerBiometric() }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Fingerprint,
                                                contentDescription = "Unlock with biometrics",
                                                tint = if (biometricReady) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                            )
                                        }
                                    }
                                    "DEL" -> {
                                        KeypadActionKey(
                                            enabled = enteredPin.isNotEmpty(),
                                            tag = "pin_backspace_button",
                                            onClick = {
                                                if (enteredPin.isNotEmpty()) {
                                                    enteredPin = enteredPin.dropLast(1)
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                                contentDescription = "Delete digit"
                                            )
                                        }
                                    }
                                    else -> {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .size(68.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    if (enteredPin.length < 6) {
                                                        val next = enteredPin + key
                                                        enteredPin = next
                                                        errorMessage = null
                                                        if (next.length >= 4) {
                                                            tryPinUnlock(next, explicitSubmit = false)
                                                        }
                                                    }
                                                }
                                                .testTag("pin_digit_$key")
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = key,
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    fontWeight = FontWeight.SemiBold
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

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (settings.hasPinConfigured) {
                    Button(
                        onClick = { tryPinUnlock(enteredPin, explicitSubmit = true) },
                        enabled = enteredPin.length >= 4,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("pin_submit_button")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock with PIN")
                    }
                }
                if (biometricReady && activity != null) {
                    OutlinedButton(
                        onClick = { triggerBiometric() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Use Face ID / Fingerprint")
                    }
                }
                if (onDisableLockOrSignOut != null) {
                    TextButton(onClick = onDisableLockOrSignOut) {
                        Text("Sign out instead")
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadActionKey(
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = CircleShape,
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
fun SetupPinDialog(
    hasExistingPin: Boolean,
    onSavePin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var confirmInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Pin, contentDescription = null) },
        title = { Text(if (hasExistingPin) "Change ToKer App PIN" else "Set ToKer App PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Choose a 4 to 6 digit numeric PIN used to lock ToKer and individual chats.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { next ->
                        if (next.length <= 6 && next.all { it.isDigit() }) {
                            pinInput = next
                            errorText = null
                        }
                    },
                    label = { Text("Enter 4–6 Digit PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_pin_input")
                )
                OutlinedTextField(
                    value = confirmInput,
                    onValueChange = { next ->
                        if (next.length <= 6 && next.all { it.isDigit() }) {
                            confirmInput = next
                            errorText = null
                        }
                    },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_setup_pin_input")
                )
                if (!errorText.isNullOrBlank()) {
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        pinInput.length < 4 -> errorText = "PIN must be at least 4 digits."
                        pinInput != confirmInput -> errorText = "PINs do not match."
                        else -> {
                            onSavePin(pinInput)
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.testTag("save_pin_button")
            ) {
                Text("Save PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ChatUnlockPromptDialog(
    chatTitle: String,
    settings: SecuritySettings,
    activity: FragmentActivity?,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val biometricReady = remember(settings.biometricEnabled, context) {
        BiometricAuthHelper.canAuthenticate(context)
    }
    var pinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun launchBiometric() {
        val act = activity ?: return
        BiometricAuthHelper.showBiometricPrompt(
            activity = act,
            title = "Unlock \"$chatTitle\"",
            subtitle = "Authenticate to view this locked ToKer chat",
            negativeButtonText = if (settings.hasPinConfigured) "Use App PIN" else "Cancel",
            onSuccess = onUnlocked,
            onUsePinFallback = {
                if (!settings.hasPinConfigured) {
                    errorMsg = "Set an App PIN in Privacy & Security to unlock without biometrics."
                }
            },
            onError = { msg -> errorMsg = msg }
        )
    }

    LaunchedEffect(Unit) {
        if ((settings.biometricEnabled || !settings.hasPinConfigured) && biometricReady && activity != null) {
            launchBiometric()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text("Locked Chat: $chatTitle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (settings.hasPinConfigured) {
                        "Enter your App PIN or verify with biometrics to open this locked chat."
                    } else {
                        "Verify with Fingerprint / Face ID to open this locked chat."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (settings.hasPinConfigured) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { next ->
                            if (next.length <= 6 && next.all { it.isDigit() }) {
                                pinInput = next
                                errorMsg = null
                            }
                        },
                        label = { Text("App PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("unlock_chat_pin_input")
                    )
                }
                if (biometricReady && activity != null) {
                    OutlinedButton(
                        onClick = { launchBiometric() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Use Fingerprint / Face ID")
                    }
                }
                if (!errorMsg.isNullOrBlank()) {
                    Text(
                        text = errorMsg!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (settings.hasPinConfigured) {
                Button(
                    onClick = {
                        if (SecurityPinHasher.verifyPin(pinInput, settings.pinSalt, settings.pinHash)) {
                            onUnlocked()
                        } else {
                            errorMsg = "Incorrect PIN"
                            pinInput = ""
                        }
                    },
                    enabled = pinInput.length >= 4,
                    modifier = Modifier.testTag("confirm_unlock_chat_pin_button")
                ) {
                    Text("Unlock")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
