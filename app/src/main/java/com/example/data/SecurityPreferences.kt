package com.example.data

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.securityPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "toker_security_prefs")

data class SecuritySettings(
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val pinHash: String = "",
    val pinSalt: String = "",
    val lockedChatIds: Set<String> = emptySet()
) {
    val hasPinConfigured: Boolean
        get() = pinHash.isNotBlank() && pinSalt.isNotBlank()
}

object SecurityPinHasher {
    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$salt:${pin.trim()}".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(pin: String, salt: String, expectedHash: String): Boolean {
        if (pin.isBlank() || salt.isBlank() || expectedHash.isBlank()) return false
        return MessageDigest.isEqual(
            hashPin(pin, salt).toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8)
        )
    }
}

class SecurityPreferencesRepository(context: Context) {
    private val appContext = context.applicationContext

    private object Keys {
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val LOCKED_CHAT_IDS = stringSetPreferencesKey("locked_chat_ids")
    }

    val securitySettings: Flow<SecuritySettings> = appContext.securityPrefsDataStore.data.map { prefs ->
        SecuritySettings(
            appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            pinHash = prefs[Keys.PIN_HASH] ?: "",
            pinSalt = prefs[Keys.PIN_SALT] ?: "",
            lockedChatIds = prefs[Keys.LOCKED_CHAT_IDS] ?: emptySet()
        )
    }

    suspend fun setPinAndEnableAppLock(pin: String, enableBiometric: Boolean) {
        val salt = SecurityPinHasher.generateSalt()
        val hash = SecurityPinHasher.hashPin(pin, salt)
        appContext.securityPrefsDataStore.edit { prefs ->
            prefs[Keys.PIN_SALT] = salt
            prefs[Keys.PIN_HASH] = hash
            prefs[Keys.APP_LOCK_ENABLED] = true
            prefs[Keys.BIOMETRIC_ENABLED] = enableBiometric
        }
    }

    suspend fun updatePinOnly(pin: String) {
        val salt = SecurityPinHasher.generateSalt()
        val hash = SecurityPinHasher.hashPin(pin, salt)
        appContext.securityPrefsDataStore.edit { prefs ->
            prefs[Keys.PIN_SALT] = salt
            prefs[Keys.PIN_HASH] = hash
        }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        appContext.securityPrefsDataStore.edit { prefs ->
            prefs[Keys.APP_LOCK_ENABLED] = enabled
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        appContext.securityPrefsDataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_ENABLED] = enabled
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        appContext.securityPrefsDataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setChatLocked(chatLockKey: String, locked: Boolean) {
        appContext.securityPrefsDataStore.edit { prefs ->
            val current = prefs[Keys.LOCKED_CHAT_IDS]?.toMutableSet() ?: mutableSetOf()
            if (locked) current.add(chatLockKey) else current.remove(chatLockKey)
            prefs[Keys.LOCKED_CHAT_IDS] = current
        }
    }

    suspend fun clearAllLocks() {
        appContext.securityPrefsDataStore.edit { prefs ->
            prefs[Keys.APP_LOCK_ENABLED] = false
            prefs[Keys.BIOMETRIC_ENABLED] = false
            prefs[Keys.LOCKED_CHAT_IDS] = emptySet()
        }
    }
}

object BiometricAuthHelper {
    private const val ALLOWED_AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun canAuthenticate(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(ALLOWED_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String = "Use App PIN",
        onSuccess: () -> Unit,
        onUsePinFallback: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!canAuthenticate(activity)) {
            onUsePinFallback()
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                        errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE ||
                        errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT
                    ) {
                        onUsePinFallback()
                    } else if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        onError(errString.toString())
                    }
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .build()

        prompt.authenticate(info)
    }
}
