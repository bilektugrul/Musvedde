package dev.aliarslan.musvedde.ui.lock

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aliarslan.musvedde.R
import dev.aliarslan.musvedde.core.Clock
import dev.aliarslan.musvedde.data.lock.LockManager
import dev.aliarslan.musvedde.data.lock.SetPinResult
import dev.aliarslan.musvedde.data.lock.UnlockResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(private val lock: LockManager, private val clock: Clock) : ViewModel() {
    val unlocked: StateFlow<Boolean> = lock.unlocked

    val hasPin: StateFlow<Boolean?> = lock.hasPin.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val biometricEnabled: StateFlow<Boolean> = lock.biometricEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun now(): Long = clock.now()

    suspend fun unlock(pin: String): UnlockResult = lock.unlock(pin)

    suspend fun setPin(newPin: String, currentPin: String?): SetPinResult = lock.setPin(newPin, currentPin)

    fun unlockWithBiometric() = lock.unlockWithBiometric()

    fun setBiometricEnabled(enabled: Boolean) = viewModelScope.launch { lock.setBiometricEnabled(enabled) }
}

fun biometricAvailable(context: Context): Boolean {
    val manager = context.getSystemService(BiometricManager::class.java) ?: return false
    val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
    } else {
        @Suppress("DEPRECATION")
        manager.canAuthenticate()
    }
    return result == BiometricManager.BIOMETRIC_SUCCESS
}

/** Shows the system fingerprint/face prompt; the app only learns whether it succeeded. */
fun promptBiometric(activity: Activity, title: String, cancel: String, onError: (String) -> Unit = {}, onSuccess: () -> Unit) {
    val builder = BiometricPrompt.Builder(activity).setTitle(title)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
    }
    builder.setNegativeButton(cancel, activity.mainExecutor) { _, _ -> }
    builder.build().authenticate(
        CancellationSignal(),
        activity.mainExecutor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode !in DISMISSED_ERRORS) onError(errString.toString())
            }
        },
    )
}

/**
 * Returns a function that runs an action once locked content is open: right away when it already is, otherwise
 * after the PIN (or fingerprint) is entered. Without a PIN yet, the user is asked to create one first.
 */
@Composable
fun rememberRequireUnlock(viewModel: LockViewModel = hiltViewModel()): (() -> Unit) -> Unit {
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    pending?.let { action ->
        AccessDialog(
            onUnlocked = {
                pending = null
                action()
            },
            onDismiss = { pending = null },
            viewModel = viewModel,
        )
    }
    return { action -> if (viewModel.unlocked.value) action() else pending = action }
}

/** Returns a function that runs an action once a PIN exists, asking the user to create one first if needed. */
@Composable
fun rememberRequirePin(viewModel: LockViewModel = hiltViewModel()): (() -> Unit) -> Unit {
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    pending?.let { action ->
        PinSetupDialog(
            changing = false,
            onDone = {
                pending = null
                action()
            },
            onDismiss = { pending = null },
            viewModel = viewModel,
        )
    }
    return { action -> if (viewModel.hasPin.value == true) action() else pending = action }
}

/** Shows [content] only while locked content is open; otherwise a lock screen that asks for the PIN. */
@Composable
fun LockGate(locked: Boolean, modifier: Modifier = Modifier, viewModel: LockViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val unlocked by viewModel.unlocked.collectAsStateWithLifecycle()
    if (!locked || unlocked) {
        content()
        return
    }
    var asking by rememberSaveable { mutableStateOf(true) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize().padding(32.dp).testTag("lock_gate"),
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.lock_gate_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Button(onClick = { asking = true }, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.lock_unlock)) }
    }
    if (asking) AccessDialog(onUnlocked = { asking = false }, onDismiss = { asking = false }, viewModel = viewModel)
}

@Composable
private fun AccessDialog(onUnlocked: () -> Unit, onDismiss: () -> Unit, viewModel: LockViewModel) {
    val hasPin by viewModel.hasPin.collectAsStateWithLifecycle()
    when (hasPin) {
        null -> Unit
        false -> PinSetupDialog(changing = false, onDone = onUnlocked, onDismiss = onDismiss, viewModel = viewModel)
        true -> UnlockDialog(onUnlocked = onUnlocked, onDismiss = onDismiss, viewModel = viewModel)
    }
}

@Composable
fun UnlockDialog(onUnlocked: () -> Unit, onDismiss: () -> Unit, viewModel: LockViewModel = hiltViewModel()) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val biometricEnabled by viewModel.biometricEnabled.collectAsStateWithLifecycle()
    val canUseBiometric = biometricEnabled && activity != null && remember { biometricAvailable(context) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<UnlockResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    val title = stringResource(R.string.lock_unlock_title)
    val cancel = stringResource(R.string.lock_use_pin)
    var biometricError by remember { mutableStateOf<String?>(null) }

    fun biometric() {
        biometricError = null
        promptBiometric(activity!!, title, cancel, onError = { biometricError = it }) {
            viewModel.unlockWithBiometric()
            onUnlocked()
        }
    }

    fun submit() {
        if (busy || pin.isEmpty()) return
        busy = true
        scope.launch {
            when (val result = viewModel.unlock(pin)) {
                UnlockResult.Unlocked -> onUnlocked()
                else -> {
                    error = result
                    pin = ""
                }
            }
            busy = false
        }
    }

    LaunchedEffect(canUseBiometric) { if (canUseBiometric) biometric() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column {
                PinField(pin, { pin = it.filter(Char::isDigit).take(LockManager.MAX_PIN_LENGTH) }, stringResource(R.string.lock_pin), onDone = ::submit)
                when (val e = error) {
                    is UnlockResult.WrongPin -> ErrorText(pluralStringResource(R.plurals.lock_wrong_pin, e.attemptsBeforeDelay, e.attemptsBeforeDelay))
                    is UnlockResult.TooManyAttempts -> {
                        val seconds = ((e.retryAt - viewModel.now()) / 1000).coerceAtLeast(1).toInt()
                        ErrorText(pluralStringResource(R.plurals.lock_too_many, seconds, seconds))
                    }
                    else -> Unit
                }
                biometricError?.let { ErrorText(it) }
                if (canUseBiometric) {
                    OutlinedButton(onClick = ::biometric, modifier = Modifier.padding(top = 12.dp)) {
                        Icon(Icons.Outlined.Fingerprint, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.lock_use_biometric))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit, enabled = pin.isNotEmpty() && !busy, modifier = Modifier.testTag("unlock_confirm")) {
                Text(stringResource(R.string.lock_unlock))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
fun PinSetupDialog(changing: Boolean, onDone: () -> Unit, onDismiss: () -> Unit, viewModel: LockViewModel = hiltViewModel()) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun digits(value: String) = value.filter(Char::isDigit).take(LockManager.MAX_PIN_LENGTH)

    fun submit() {
        if (busy) return
        error = when {
            !LockManager.isValidPin(pin) -> R.string.lock_pin_invalid
            pin != confirm -> R.string.lock_pin_mismatch
            else -> null
        }
        if (error != null) return
        busy = true
        scope.launch {
            when (viewModel.setPin(pin, current.takeIf { changing })) {
                SetPinResult.Saved -> onDone()
                SetPinResult.Invalid -> error = R.string.lock_pin_invalid
                SetPinResult.WrongCurrentPin -> error = R.string.lock_current_pin_wrong
            }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
        title = { Text(stringResource(if (changing) R.string.lock_change_pin else R.string.lock_create_pin)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.lock_pin_explanation), style = MaterialTheme.typography.bodyMedium)
                if (changing) PinField(current, { current = digits(it) }, stringResource(R.string.lock_current_pin), focus = true)
                PinField(pin, { pin = digits(it) }, stringResource(R.string.lock_new_pin), focus = !changing)
                PinField(confirm, { confirm = digits(it) }, stringResource(R.string.lock_confirm_pin), onDone = ::submit, focus = false)
                error?.let { ErrorText(stringResource(it)) }
            }
        },
        confirmButton = { TextButton(onClick = ::submit, enabled = !busy) { Text(stringResource(R.string.lock_save_pin)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/** Asks for the PIN a backup was made with. */
@Composable
fun BackupPinDialog(wrongPin: Boolean, onPin: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.restore_pin_title)) },
        text = {
            Column {
                Text(stringResource(R.string.restore_pin_message), style = MaterialTheme.typography.bodyMedium)
                PinField(pin, { pin = it.filter(Char::isDigit).take(LockManager.MAX_PIN_LENGTH) }, stringResource(R.string.lock_pin), onDone = { if (pin.isNotEmpty()) onPin(pin) })
                if (wrongPin) ErrorText(stringResource(R.string.restore_pin_wrong))
            }
        },
        confirmButton = { TextButton(onClick = { onPin(pin) }, enabled = pin.isNotEmpty()) { Text(stringResource(R.string.backup_restore)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun PinField(value: String, onValueChange: (String) -> Unit, label: String, onDone: (() -> Unit)? = null, focus: Boolean = true) {
    val focusRequester = remember { FocusRequester() }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(focusRequester).testTag("pin_field"),
    )
    if (focus) LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
}

/** Closing the prompt or choosing the PIN instead is not an error worth showing. */
private val DISMISSED_ERRORS = setOf(
    BiometricPrompt.BIOMETRIC_ERROR_CANCELED,
    BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED,
)
