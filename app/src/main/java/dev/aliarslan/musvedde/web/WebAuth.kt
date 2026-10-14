package dev.aliarslan.musvedde.web

import dev.aliarslan.musvedde.core.Clock
import dev.aliarslan.musvedde.data.db.WebDeviceDao
import dev.aliarslan.musvedde.data.db.WebDeviceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PairResult {
    data class Paired(val token: String) : PairResult
    data object WrongCode : PairResult
    data class Throttled(val retryAfterMillis: Long) : PairResult
}

/**
 * Pairs computers with a one-time code shown on the phone and recognises them afterwards by a random key kept in
 * a cookie. After [MAX_FAILURES] wrong codes the code changes and pairing pauses, doubling the pause each time.
 */
@Singleton
class WebAuth @Inject constructor(
    private val dao: WebDeviceDao,
    private val clock: Clock,
) {
    private val random = SecureRandom()
    private val mutex = Mutex()
    private var failures = 0
    private var blockedUntil = 0L
    private var penalty = FIRST_PENALTY_MS

    private val _pairingCode = MutableStateFlow(newCode())
    val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()

    private val _revoked = MutableSharedFlow<Long?>(extraBufferCapacity = 8)

    /** Ids of removed devices; null when every device was removed. */
    val revoked: SharedFlow<Long?> = _revoked.asSharedFlow()

    val devices: Flow<List<WebDeviceEntity>> = dao.observeAll()

    fun rotateCode() {
        _pairingCode.value = newCode()
    }

    suspend fun pair(code: String, name: String): PairResult = mutex.withLock {
        val now = clock.now()
        if (now < blockedUntil) return@withLock PairResult.Throttled(blockedUntil - now)
        if (!MessageDigest.isEqual(code.trim().toByteArray(), _pairingCode.value.toByteArray())) {
            failures++
            if (failures >= MAX_FAILURES) {
                failures = 0
                blockedUntil = now + penalty
                penalty *= 2
                rotateCode()
            }
            return@withLock PairResult.WrongCode
        }
        failures = 0
        penalty = FIRST_PENALTY_MS
        rotateCode()
        val token = ByteArray(TOKEN_BYTES).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val cleanName = name.trim().take(MAX_NAME_LENGTH).ifEmpty { "Browser" }
        dao.insert(WebDeviceEntity(name = cleanName, tokenHash = hash(token), createdAt = now, lastSeenAt = now))
        PairResult.Paired(token)
    }

    suspend fun authenticate(token: String?): WebDeviceEntity? {
        if (token.isNullOrEmpty()) return null
        val device = dao.byTokenHash(hash(token)) ?: return null
        val now = clock.now()
        if (now - device.lastSeenAt > TOUCH_INTERVAL_MS) dao.touch(device.id, now)
        return device
    }

    suspend fun remove(id: Long) {
        dao.delete(id)
        _revoked.emit(id)
    }

    suspend fun removeAll() {
        dao.deleteAll()
        _revoked.emit(null)
    }

    private fun newCode(): String = random.nextInt(1_000_000).toString().padStart(6, '0')

    private fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val MAX_FAILURES = 5
        const val FIRST_PENALTY_MS = 30_000L
        const val TOKEN_BYTES = 32
        const val MAX_NAME_LENGTH = 60
        const val TOUCH_INTERVAL_MS = 60_000L
    }
}
