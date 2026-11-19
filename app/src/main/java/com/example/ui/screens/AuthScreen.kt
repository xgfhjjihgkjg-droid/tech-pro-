package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.LocalAppStrings
import com.example.data.attemptAutoSignIn
import com.example.data.onGoogleSignInClicked
import com.example.data.signInWithPhoneCredential
import com.example.data.startPhoneNumberVerification
import com.google.firebase.auth.PhoneAuthProvider

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val strings = LocalAppStrings.current

    var selectedMethod by rememberSaveable { mutableIntStateOf(0) } // 0 = Google, 1 = Phone
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Phone Auth state
    var phoneInput by rememberSaveable { mutableStateOf("") }
    var displayNameInput by rememberSaveable { mutableStateOf("") }
    var otpInput by rememberSaveable { mutableStateOf("") }
    var verificationId by rememberSaveable { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(195.dp)
                        .clip(RoundedCornerShape(28.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_auth_hero),
                        contentDescription = "ToKer encrypted messaging illustration",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.88f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(18.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = strings.authTagline,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = strings.encryptedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier.padding(top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Segmented Auth Method Selector: Google vs Mobile Number
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AuthMethodTab(
                            selected = selectedMethod == 0,
                            label = "Google",
                            icon = Icons.Default.Shield,
                            onClick = {
                                selectedMethod = 0
                                errorMessage = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("auth_tab_google")
                        )
                        AuthMethodTab(
                            selected = selectedMethod == 1,
                            label = stringResource(R.string.sign_in_tab_phone),
                            icon = Icons.Default.PhoneAndroid,
                            onClick = {
                                selectedMethod = 1
                                errorMessage = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("auth_tab_phone")
                        )
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (selectedMethod == 0) {
                    Button(
                        onClick = {
                            errorMessage = null
                            isLoading = true
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    isLoading = false
                                    onAuthSuccess()
                                },
                                onAuthError = { msg ->
                                    isLoading = false
                                    errorMessage = msg
                                },
                                scope = scope,
                                onAuthCancelled = { isLoading = false }
                            )
                        },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("google_sign_in_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = strings.loginWithGoogle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (verificationId == null) {
                                OutlinedTextField(
                                    value = displayNameInput,
                                    onValueChange = { if (it.length <= 40) displayNameInput = it },
                                    label = { Text(strings.nameLabel) },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("phone_display_name_input")
                                )

                                OutlinedTextField(
                                    value = phoneInput,
                                    onValueChange = { raw ->
                                        val filtered = raw.filter { it.isDigit() || it == '+' || it == ' ' || it == '-' }
                                        if (filtered.length <= 20) phoneInput = filtered
                                    },
                                    label = { Text("+91 98765 43210") },
                                    placeholder = { Text("+1 555 010 9999") },
                                    leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("phone_number_input")
                                )

                                Button(
                                    onClick = {
                                        val normalized = normalizeE164Phone(phoneInput)
                                        if (normalized == null) {
                                            errorMessage = "Enter a valid mobile number with country code (e.g. +15550109999)."
                                            return@Button
                                        }
                                        val hostActivity = activity
                                        if (hostActivity == null) {
                                            errorMessage = "Unable to access Activity for SMS verification."
                                            return@Button
                                        }
                                        errorMessage = null
                                        isLoading = true
                                        startPhoneNumberVerification(
                                            activity = hostActivity,
                                            phoneNumberE164 = normalized,
                                            resendToken = resendToken,
                                            onCodeSent = { vId, token ->
                                                isLoading = false
                                                verificationId = vId
                                                resendToken = token
                                            },
                                            onAutoVerified = { credential ->
                                                signInWithPhoneCredential(
                                                    credential = credential,
                                                    preferredDisplayName = displayNameInput,
                                                    onAuthSuccess = {
                                                        isLoading = false
                                                        onAuthSuccess()
                                                    },
                                                    onAuthError = { msg ->
                                                        isLoading = false
                                                        errorMessage = msg
                                                    },
                                                    scope = scope
                                                )
                                            },
                                            onError = { msg ->
                                                isLoading = false
                                                errorMessage = msg
                                            }
                                        )
                                    },
                                    enabled = !isLoading && phoneInput.isNotBlank(),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .testTag("send_otp_button")
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.5.dp
                                        )
                                    } else {
                                        Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(19.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = strings.sendButtonLabel,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = normalizeE164Phone(phoneInput) ?: phoneInput,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }

                                OutlinedTextField(
                                    value = otpInput,
                                    onValueChange = { raw ->
                                        val digits = raw.filter { it.isDigit() }
                                        if (digits.length <= 6) otpInput = digits
                                    },
                                    label = { Text("OTP") },
                                    leadingIcon = { Icon(Icons.Default.Pin, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("otp_code_input")
                                )

                                Button(
                                    onClick = {
                                        val vId = verificationId ?: return@Button
                                        val code = otpInput.trim()
                                        if (code.length < 6) {
                                            errorMessage = strings.wrongPinError
                                            return@Button
                                        }
                                        errorMessage = null
                                        isLoading = true
                                        val cred = PhoneAuthProvider.getCredential(vId, code)
                                        signInWithPhoneCredential(
                                            credential = cred,
                                            preferredDisplayName = displayNameInput,
                                            onAuthSuccess = {
                                                isLoading = false
                                                onAuthSuccess()
                                            },
                                            onAuthError = { msg ->
                                                isLoading = false
                                                errorMessage = msg
                                            },
                                            scope = scope
                                        )
                                    },
                                    enabled = !isLoading && otpInput.length == 6,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .testTag("verify_otp_button")
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.5.dp
                                        )
                                    } else {
                                        Icon(Icons.Default.RadioButtonChecked, contentDescription = null, modifier = Modifier.size(19.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = strings.verifyLabel,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TextButton(
                                        onClick = {
                                            verificationId = null
                                            otpInput = ""
                                            errorMessage = null
                                        },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("change_phone_button")
                                    ) {
                                        Text("Change Number")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val normalized = normalizeE164Phone(phoneInput) ?: return@OutlinedButton
                                            val hostActivity = activity ?: return@OutlinedButton
                                            errorMessage = null
                                            isLoading = true
                                            startPhoneNumberVerification(
                                                activity = hostActivity,
                                                phoneNumberE164 = normalized,
                                                resendToken = resendToken,
                                                onCodeSent = { vId, token ->
                                                    isLoading = false
                                                    verificationId = vId
                                                    resendToken = token
                                                },
                                                onAutoVerified = { credential ->
                                                    signInWithPhoneCredential(
                                                        credential = credential,
                                                        preferredDisplayName = displayNameInput,
                                                        onAuthSuccess = {
                                                            isLoading = false
                                                            onAuthSuccess()
                                                        },
                                                        onAuthError = { msg ->
                                                            isLoading = false
                                                            errorMessage = msg
                                                        },
                                                        scope = scope
                                                    )
                                                },
                                                onError = { msg ->
                                                    isLoading = false
                                                    errorMessage = msg
                                                }
                                            )
                                        },
                                        enabled = !isLoading,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("resend_otp_button")
                                    ) {
                                        Text("Resend Code")
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
private fun AuthMethodTab(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (selected) 3.dp else 0.dp,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun normalizeE164Phone(raw: String): String? {
    val compact = raw.trim().replace(Regex("[\\s\\-()]"), "")
    return if (Regex("^\\+[1-9]\\d{6,14}$").matches(compact)) compact else null
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
