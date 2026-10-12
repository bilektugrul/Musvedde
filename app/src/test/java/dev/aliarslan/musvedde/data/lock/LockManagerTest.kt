package dev.aliarslan.musvedde.data.lock

import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.data.settings.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException

class FakeKeyWrapper : KeyWrapper {
    var broken = false
    override fun wrap(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    override fun unwrap(wrapped: ByteArray): ByteArray {
        check(!broken) { "key gone" }
        return wrap(wrapped)
    }
}

class LockManagerTest {
    private var now = 1_000L
    private val store = InMemoryPreferencesDataStore()
    private val wrapper = FakeKeyWrapper()
    private val lock = LockManager(store, wrapper, clock = { now }, LockConfig(verifyIterations = 1_000, backupIterations = 2_000))

    @Test
    fun settingAPinUnlocksAndLockClosesAgain() = runTest {
        assertThat(lock.hasPin.first()).isFalse()

        assertThat(lock.setPin("1234")).isEqualTo(SetPinResult.Saved)

        assertThat(lock.hasPin.first()).isTrue()
        assertThat(lock.unlocked.value).isTrue()
        lock.lock()
        assertThat(lock.unlocked.value).isFalse()
        assertThat(lock.unlock("1234")).isEqualTo(UnlockResult.Unlocked)
        assertThat(lock.unlocked.value).isTrue()
    }

    @Test
    fun rejectsInvalidPins() = runTest {
        assertThat(lock.setPin("123")).isEqualTo(SetPinResult.Invalid)
        assertThat(lock.setPin("12ab")).isEqualTo(SetPinResult.Invalid)
        assertThat(lock.setPin("1".repeat(17))).isEqualTo(SetPinResult.Invalid)
        assertThat(lock.hasPin.first()).isFalse()
    }

    @Test
    fun theStoredPinIsNotThePin() = runTest {
        lock.setPin("482915")

        val stored = store.data.first().asMap().values.joinToString()
        assertThat(stored).doesNotContain("482915")
    }

    @Test
    fun changingThePinNeedsTheCurrentOne() = runTest {
        lock.setPin("1234")

        assertThat(lock.setPin("5678")).isEqualTo(SetPinResult.WrongCurrentPin)
        assertThat(lock.setPin("5678", currentPin = "0000")).isEqualTo(SetPinResult.WrongCurrentPin)
        assertThat(lock.setPin("5678", currentPin = "1234")).isEqualTo(SetPinResult.Saved)
        lock.lock()
        assertThat(lock.unlock("1234")).isInstanceOf(UnlockResult.WrongPin::class.java)
        assertThat(lock.unlock("5678")).isEqualTo(UnlockResult.Unlocked)
    }

    @Test
    fun repeatedWrongPinsAreSlowedDown() = runTest {
        lock.setPin("1234")
        lock.lock()

        repeat(4) { assertThat(lock.unlock("0000")).isInstanceOf(UnlockResult.WrongPin::class.java) }
        val blocked = lock.unlock("0000") as UnlockResult.TooManyAttempts
        assertThat(lock.unlock("1234")).isEqualTo(blocked)

        now = blocked.retryAt
        assertThat(lock.unlock("1234")).isEqualTo(UnlockResult.Unlocked)
    }

    @Test
    fun backupKeyComesFromThePin() = runTest {
        lock.setPin("1234")

        val key = lock.backupKey()!!
        assertThat(key.iterations).isEqualTo(2_000)
        assertThat(lock.deriveBackupKey("1234", key.salt, key.iterations)).isEqualTo(key.key)
        assertThat(lock.deriveBackupKey("9999", key.salt, key.iterations)).isNotEqualTo(key.key)
        val sealed = Aead.encrypt(key.key, "gizli".toByteArray())
        assertThat(String(Aead.decrypt(key.key, sealed))).isEqualTo("gizli")
        val wrongKey = lock.deriveBackupKey("9999", key.salt, key.iterations)
        assertThrows(AEADBadTagException::class.java) { Aead.decrypt(wrongKey, sealed) }
    }

    @Test
    fun aLostKeystoreKeyIsRebuiltOnTheNextUnlock() = runTest {
        lock.setPin("1234")
        val before = lock.backupKey()!!
        wrapper.broken = true
        assertThat(lock.backupKey()).isNull()

        wrapper.broken = false
        store.updateData { prefs -> prefs.toMutablePreferences().apply { asMap().keys.filter { it.name == "lockBackupKey" }.forEach { remove(it) } } }
        lock.lock()
        lock.unlock("1234")

        val after = lock.backupKey()!!
        assertThat(after.key).isEqualTo(before.key)
    }
}
