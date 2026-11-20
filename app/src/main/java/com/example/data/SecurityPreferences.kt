package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom

private val Context.securityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vault_security_prefs"
)

data class VaultSecuritySettings(
    val hasPinConfigured: Boolean = false,
    val autoLockOnBackground: Boolean = true,
    val screenShieldEnabled: Boolean = true,
    val promptDeleteOriginal: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val defaultPlaybackSpeed: Float = 1.0f,
    val candyHighScore: Int = 0,
    val candyLevel: Int = 1
)

class SecurityPreferences(private val context: Context) {

    private object Keys {
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val AUTO_LOCK = booleanPreferencesKey("auto_lock_on_background")
        val SCREEN_SHIELD = booleanPreferencesKey("screen_shield_enabled")
        val PROMPT_DELETE_ORIGINAL = booleanPreferencesKey("prompt_delete_original")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_SPEED = floatPreferencesKey("default_playback_speed")
        val CANDY_HIGH_SCORE = androidx.datastore.preferences.core.intPreferencesKey("candy_high_score")
        val CANDY_LEVEL = androidx.datastore.preferences.core.intPreferencesKey("candy_level")
    }

    val settingsFlow: Flow<VaultSecuritySettings> = context.securityDataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name
        VaultSecuritySettings(
            hasPinConfigured = !prefs[Keys.PIN_HASH].isNullOrBlank(),
            autoLockOnBackground = prefs[Keys.AUTO_LOCK] ?: true,
            screenShieldEnabled = prefs[Keys.SCREEN_SHIELD] ?: true,
            promptDeleteOriginal = prefs[Keys.PROMPT_DELETE_ORIGINAL] ?: true,
            themeMode = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.DARK),
            defaultPlaybackSpeed = prefs[Keys.DEFAULT_SPEED] ?: 1.0f,
            candyHighScore = prefs[Keys.CANDY_HIGH_SCORE] ?: 0,
            candyLevel = prefs[Keys.CANDY_LEVEL] ?: 1
        )
    }

    suspend fun saveCandyProgress(score: Int, level: Int) {
        context.securityDataStore.edit { prefs ->
            val prevHigh = prefs[Keys.CANDY_HIGH_SCORE] ?: 0
            if (score > prevHigh) {
                prefs[Keys.CANDY_HIGH_SCORE] = score
            }
            prefs[Keys.CANDY_LEVEL] = level.coerceAtLeast(1)
        }
    }

    suspend fun savePin(pin: String) {
        val saltBytes = ByteArray(16)
        SecureRandom().nextBytes(saltBytes)
        val salt = saltBytes.joinToString("") { "%02x".format(it) }
        val hash = sha256(pin, salt)
        context.securityDataStore.edit { prefs ->
            prefs[Keys.PIN_SALT] = salt
            prefs[Keys.PIN_HASH] = hash
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val prefs = context.securityDataStore.data.first()
        val storedHash = prefs[Keys.PIN_HASH] ?: return false
        val storedSalt = prefs[Keys.PIN_SALT] ?: return false
        return sha256(pin, storedSalt) == storedHash
    }

    suspend fun setAutoLockOnBackground(enabled: Boolean) {
        context.securityDataStore.edit { it[Keys.AUTO_LOCK] = enabled }
    }

    suspend fun setScreenShieldEnabled(enabled: Boolean) {
        context.securityDataStore.edit { it[Keys.SCREEN_SHIELD] = enabled }
    }

    suspend fun setPromptDeleteOriginal(enabled: Boolean) {
        context.securityDataStore.edit { it[Keys.PROMPT_DELETE_ORIGINAL] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.securityDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDefaultPlaybackSpeed(speed: Float) {
        context.securityDataStore.edit { it[Keys.DEFAULT_SPEED] = speed }
    }

    private fun sha256(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
