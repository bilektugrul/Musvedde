package dev.aliarslan.musvedde.data.lock

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.aliarslan.musvedde.core.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

data class LockConfig(
    val verifyIterations: Int = 120_000,
    val backupIterations: Int = 600_000,
)

/** The key locked notes are encrypted with in backups, and what is needed to derive it again from the PIN. */
class BackupKey(val salt: ByteArray, val iterations: Int, val key: ByteArray)

sealed interface UnlockResult {
    data object Unlocked : UnlockResult
    data class WrongPin(val attemptsBeforeDelay: Int) : UnlockResult
    data class TooManyAttempts(val retryAt: Long) : UnlockResult
}

sealed interface SetPinResult {
    data object Saved : SetPinResult
    data object Invalid : SetPinResult
    data object WrongCurrentPin : SetPinResult
}

/**
 * Owns the app PIN and whether locked notes are currently open. Only a slow hash of the PIN is stored; the
 * backup key is derived from it and kept wrapped by the Keystore so scheduled backups can still encrypt
 * locked notes.
 */
@Singleton
class LockManager @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val keyWrapper: KeyWrapper,
    private val clock: Clock,
    private val config: LockConfig,
) {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    val hasPin: Flow<Boolean> = dataStore.data.map { it[Keys.pinHash] != null }.distinctUntilChanged()

    val biometricEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.pinHash] != null && it[Keys.biometric] == true }.distinctUntilChanged()

    private var failures = 0
    private var retryAt = 0L

    fun lock() {
        _unlocked.value = false
    }

    /** Called after the system biometric prompt succeeded; only offered once a PIN exists. */
    fun unlockWithBiometric() {
        _unlocked.value = true
    }

    suspend fun unlock(pin: String): UnlockResult {
        val now = clock.now()
        if (now < retryAt) return UnlockResult.TooManyAttempts(retryAt)
        val prefs = dataStore.data.first()
        val hash = prefs[Keys.pinHash]?.decoded() ?: return UnlockResult.WrongPin(MAX_FREE_ATTEMPTS)
        val salt = prefs[Keys.pinSalt]!!.decoded()
        val candidate = withContext(Dispatchers.Default) { PinKdf.derive(pin, salt, config.verifyIterations) }
        if (!MessageDigest.isEqual(candidate, hash)) {
            failures++
            if (failures >= MAX_FREE_ATTEMPTS) {
                retryAt = now + BASE_DELAY_MILLIS * (1L shl (failures - MAX_FREE_ATTEMPTS).coerceAtMost(MAX_DELAY_DOUBLINGS))
                return UnlockResult.TooManyAttempts(retryAt)
            }
            return UnlockResult.WrongPin(MAX_FREE_ATTEMPTS - failures)
        }
        failures = 0
        retryAt = 0
        if (backupKey() == null) storeBackupKey(pin, prefs[Keys.backupSalt]?.decoded() ?: PinKdf.salt())
        _unlocked.value = true
        return UnlockResult.Unlocked
    }

    /** Sets the first PIN, or changes it when [currentPin] is right. Leaves locked content open afterwards. */
    suspend fun setPin(newPin: String, currentPin: String? = null): SetPinResult {
        if (!isValidPin(newPin)) return SetPinResult.Invalid
        if (hasPin.first()) {
            if (currentPin == null || unlock(currentPin) != UnlockResult.Unlocked) return SetPinResult.WrongCurrentPin
        }
        val salt = PinKdf.salt()
        val hash = withContext(Dispatchers.Default) { PinKdf.derive(newPin, salt, config.verifyIterations) }
        dataStore.edit {
            it[Keys.pinSalt] = salt.encoded()
            it[Keys.pinHash] = hash.encoded()
        }
        storeBackupKey(newPin, PinKdf.salt())
        _unlocked.value = true
        return SetPinResult.Saved
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.biometric] = enabled }
    }

    /** Derives the key a backup's locked notes were encrypted with; used when restoring with the PIN typed in. */
    suspend fun deriveBackupKey(pin: String, salt: ByteArray, iterations: Int): ByteArray =
        withContext(Dispatchers.Default) { PinKdf.derive(pin, salt, iterations) }

    suspend fun backupKey(): BackupKey? {
        val prefs = dataStore.data.first()
        val wrapped = prefs[Keys.backupKey]?.decoded() ?: return null
        val salt = prefs[Keys.backupSalt]?.decoded() ?: return null
        val iterations = prefs[Keys.backupIterations]?.toIntOrNull() ?: return null
        val key = runCatching { keyWrapper.unwrap(wrapped) }.getOrNull() ?: return null
        return BackupKey(salt, iterations, key)
    }

    private suspend fun storeBackupKey(pin: String, salt: ByteArray) {
        val key = withContext(Dispatchers.Default) { PinKdf.derive(pin, salt, config.backupIterations) }
        val wrapped = runCatching { keyWrapper.wrap(key) }.getOrNull() ?: return
        dataStore.edit { prefs: MutablePreferences ->
            prefs[Keys.backupSalt] = salt.encoded()
            prefs[Keys.backupIterations] = config.backupIterations.toString()
            prefs[Keys.backupKey] = wrapped.encoded()
        }
    }

    private fun ByteArray.encoded(): String = Base64.getEncoder().encodeToString(this)

    private fun String.decoded(): ByteArray = Base64.getDecoder().decode(this)

    private object Keys {
        val pinHash = stringPreferencesKey("lockPinHash")
        val pinSalt = stringPreferencesKey("lockPinSalt")
        val biometric = booleanPreferencesKey("lockBiometric")
        val backupSalt = stringPreferencesKey("lockBackupSalt")
        val backupIterations = stringPreferencesKey("lockBackupIterations")
        val backupKey = stringPreferencesKey("lockBackupKey")
    }

    companion object {
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 16
        private const val MAX_FREE_ATTEMPTS = 5
        private const val BASE_DELAY_MILLIS = 30_000L
        private const val MAX_DELAY_DOUBLINGS = 5

        fun isValidPin(pin: String) = pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it in '0'..'9' }
    }
}
