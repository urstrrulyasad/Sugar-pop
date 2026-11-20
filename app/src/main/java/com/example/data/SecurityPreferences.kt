package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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

enum class ArcadeGameMode(val label: String) {
    PLATFORMER("2.5D Run"),
    MATCH3("Match-3")
}

data class VaultSecuritySettings(
    val hasPinConfigured: Boolean = false,
    val autoLockOnBackground: Boolean = true,
    val screenShieldEnabled: Boolean = true,
    val promptDeleteOriginal: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val defaultPlaybackSpeed: Float = 1.0f,
    val candyHighScore: Int = 0,
    val candyLevel: Int = 1,
    val platformerHighScore: Int = 0,
    val platformerLevel: Int = 1,
    val selectedGameMode: ArcadeGameMode = ArcadeGameMode.PLATFORMER
)

data class SecurityBackupSnapshot(
    val pinHash: String?,
    val pinSalt: String?,
    val autoLockOnBackground: Boolean,
    val screenShieldEnabled: Boolean,
    val promptDeleteOriginal: Boolean,
    val themeMode: String,
    val defaultPlaybackSpeed: Float,
    val candyHighScore: Int,
    val candyLevel: Int,
    val platformerHighScore: Int,
    val platformerLevel: Int,
    val selectedGameMode: String
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
        val CANDY_HIGH_SCORE = intPreferencesKey("candy_high_score")
        val CANDY_LEVEL = intPreferencesKey("candy_level")
        val PLATFORMER_HIGH_SCORE = intPreferencesKey("platformer_high_score")
        val PLATFORMER_LEVEL = intPreferencesKey("platformer_level")
        val SELECTED_GAME_MODE = stringPreferencesKey("selected_game_mode")
    }

    val settingsFlow: Flow<VaultSecuritySettings> = context.securityDataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name
        val modeStr = prefs[Keys.SELECTED_GAME_MODE] ?: ArcadeGameMode.PLATFORMER.name
        VaultSecuritySettings(
            hasPinConfigured = !prefs[Keys.PIN_HASH].isNullOrBlank(),
            autoLockOnBackground = prefs[Keys.AUTO_LOCK] ?: true,
            screenShieldEnabled = prefs[Keys.SCREEN_SHIELD] ?: true,
            promptDeleteOriginal = prefs[Keys.PROMPT_DELETE_ORIGINAL] ?: true,
            themeMode = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.DARK),
            defaultPlaybackSpeed = prefs[Keys.DEFAULT_SPEED] ?: 1.0f,
            candyHighScore = prefs[Keys.CANDY_HIGH_SCORE] ?: 0,
            candyLevel = prefs[Keys.CANDY_LEVEL] ?: 1,
            platformerHighScore = prefs[Keys.PLATFORMER_HIGH_SCORE] ?: 0,
            platformerLevel = prefs[Keys.PLATFORMER_LEVEL] ?: 1,
            selectedGameMode = runCatching { ArcadeGameMode.valueOf(modeStr) }
                .getOrDefault(ArcadeGameMode.PLATFORMER)
        )
    }

    suspend fun saveCandyProgress(score: Int, level: Int) {
        context.securityDataStore.edit { prefs ->
            val prevHigh = prefs[Keys.CANDY_HIGH_SCORE] ?: 0
            if (score > prevHigh) {
                prefs[Keys.CANDY_HIGH_SCORE] = score
            }
            val prevLevel = prefs[Keys.CANDY_LEVEL] ?: 1
            prefs[Keys.CANDY_LEVEL] = maxOf(prevLevel, level).coerceAtLeast(1)
        }
    }

    suspend fun savePlatformerProgress(score: Int, level: Int) {
        context.securityDataStore.edit { prefs ->
            val prevHigh = prefs[Keys.PLATFORMER_HIGH_SCORE] ?: 0
            if (score > prevHigh) {
                prefs[Keys.PLATFORMER_HIGH_SCORE] = score
            }
            val prevLevel = prefs[Keys.PLATFORMER_LEVEL] ?: 1
            prefs[Keys.PLATFORMER_LEVEL] = maxOf(prevLevel, level).coerceAtLeast(1)
        }
    }

    suspend fun setSelectedGameMode(mode: ArcadeGameMode) {
        context.securityDataStore.edit { it[Keys.SELECTED_GAME_MODE] = mode.name }
    }

    suspend fun exportBackupSnapshot(): SecurityBackupSnapshot {
        val prefs = context.securityDataStore.data.first()
        return SecurityBackupSnapshot(
            pinHash = prefs[Keys.PIN_HASH],
            pinSalt = prefs[Keys.PIN_SALT],
            autoLockOnBackground = prefs[Keys.AUTO_LOCK] ?: true,
            screenShieldEnabled = prefs[Keys.SCREEN_SHIELD] ?: true,
            promptDeleteOriginal = prefs[Keys.PROMPT_DELETE_ORIGINAL] ?: true,
            themeMode = prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name,
            defaultPlaybackSpeed = prefs[Keys.DEFAULT_SPEED] ?: 1.0f,
            candyHighScore = prefs[Keys.CANDY_HIGH_SCORE] ?: 0,
            candyLevel = prefs[Keys.CANDY_LEVEL] ?: 1,
            platformerHighScore = prefs[Keys.PLATFORMER_HIGH_SCORE] ?: 0,
            platformerLevel = prefs[Keys.PLATFORMER_LEVEL] ?: 1,
            selectedGameMode = prefs[Keys.SELECTED_GAME_MODE] ?: ArcadeGameMode.PLATFORMER.name
        )
    }

    suspend fun restoreBackupSnapshot(snapshot: SecurityBackupSnapshot) {
        context.securityDataStore.edit { prefs ->
            if (!snapshot.pinHash.isNullOrBlank() && !snapshot.pinSalt.isNullOrBlank()) {
                prefs[Keys.PIN_HASH] = snapshot.pinHash
                prefs[Keys.PIN_SALT] = snapshot.pinSalt
            }
            prefs[Keys.AUTO_LOCK] = snapshot.autoLockOnBackground
            prefs[Keys.SCREEN_SHIELD] = snapshot.screenShieldEnabled
            prefs[Keys.PROMPT_DELETE_ORIGINAL] = snapshot.promptDeleteOriginal
            prefs[Keys.THEME_MODE] = snapshot.themeMode
            prefs[Keys.DEFAULT_SPEED] = snapshot.defaultPlaybackSpeed
            prefs[Keys.CANDY_HIGH_SCORE] = maxOf(prefs[Keys.CANDY_HIGH_SCORE] ?: 0, snapshot.candyHighScore)
            prefs[Keys.CANDY_LEVEL] = maxOf(prefs[Keys.CANDY_LEVEL] ?: 1, snapshot.candyLevel)
            prefs[Keys.PLATFORMER_HIGH_SCORE] = maxOf(prefs[Keys.PLATFORMER_HIGH_SCORE] ?: 0, snapshot.platformerHighScore)
            prefs[Keys.PLATFORMER_LEVEL] = maxOf(prefs[Keys.PLATFORMER_LEVEL] ?: 1, snapshot.platformerLevel)
            prefs[Keys.SELECTED_GAME_MODE] = snapshot.selectedGameMode
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
