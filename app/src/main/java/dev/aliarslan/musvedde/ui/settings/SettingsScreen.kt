package dev.aliarslan.musvedde.ui.settings

import android.app.LocaleManager
import android.content.Intent
import android.os.Build
import android.os.LocaleList
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.RequiresApi
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aliarslan.musvedde.R
import dev.aliarslan.musvedde.domain.AnimationSpeed
import dev.aliarslan.musvedde.domain.BackupFrequency
import dev.aliarslan.musvedde.domain.ContrastLevel
import dev.aliarslan.musvedde.domain.EditorAlign
import dev.aliarslan.musvedde.domain.EditorFont
import dev.aliarslan.musvedde.domain.ListLayout
import dev.aliarslan.musvedde.domain.MonetTuning
import dev.aliarslan.musvedde.domain.PaletteStyleOption
import dev.aliarslan.musvedde.domain.PreviewSize
import dev.aliarslan.musvedde.domain.Settings
import dev.aliarslan.musvedde.domain.ThemeMode
import dev.aliarslan.musvedde.domain.ThemePalette
import dev.aliarslan.musvedde.notification.hasNotificationPermission
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    onBack: () -> Unit,
    onCategory: (SettingsCategory) -> Unit,
    onStorage: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(SettingsCategory.entries) { category ->
                CategoryRow(
                    icon = category.icon(),
                    title = stringResource(category.titleRes()),
                    summary = stringResource(category.summaryRes()),
                    onClick = { if (category == SettingsCategory.STORAGE) onStorage() else onCategory(category) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCategoryScreen(
    category: SettingsCategory,
    onBack: () -> Unit,
    onErrorLog: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val backupRunning by viewModel.backupRunning.collectAsStateWithLifecycle()
    val restoring by viewModel.restoring.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current
    var showRestore by rememberSaveable { mutableStateOf(false) }
    var showDriveSetup by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onDriveConsentResult(it.data)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.DriveConsent ->
                    consentLauncher.launch(IntentSenderRequest.Builder(event.pendingIntent.intentSender).build())
                SettingsEvent.DriveConnected -> snackbar.showSnackbar(resources.getString(R.string.drive_connected))
                is SettingsEvent.DriveFailed -> {
                    val result = snackbar.showSnackbar(
                        resources.getString(R.string.drive_failed, event.message),
                        actionLabel = resources.getString(R.string.drive_setup_how),
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) showDriveSetup = true
                }
                is SettingsEvent.Restored ->
                    snackbar.showSnackbar(resources.getQuantityString(R.plurals.restore_done, event.count, event.count))
                is SettingsEvent.RestoreFailed -> snackbar.showSnackbar(resources.getString(R.string.restore_failed, event.message))
            }
        }
    }

    val title = stringResource(category.titleRes())
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val s = settings ?: return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                when (category) {
                    SettingsCategory.APPEARANCE -> AppearanceSection(s, viewModel::update)
                    SettingsCategory.COLORS -> ColorsSection(s, viewModel::update)
                    SettingsCategory.ACCESSIBILITY -> AccessibilitySection(s, viewModel::update)
                    SettingsCategory.EDITOR -> EditorSection(s, viewModel::update)
                    SettingsCategory.NOTIFICATIONS -> NotificationsSection(s, viewModel::update)
                    SettingsCategory.BACKUP -> BackupSection(
                        settings = s,
                        viewModel = viewModel,
                        backupRunning = backupRunning,
                        restoring = restoring,
                        onRestore = { showRestore = true },
                        onDriveSetup = { showDriveSetup = true },
                    )
                    SettingsCategory.TROUBLESHOOTING -> {
                        ActionRow(stringResource(R.string.nav_error_log), stringResource(R.string.settings_error_log_summary), onClick = onErrorLog)
                        DeveloperSection()
                    }
                    SettingsCategory.ABOUT -> AboutSection()
                    SettingsCategory.STORAGE -> Unit
                }
            }
            if (category.reset(s) != null) {
                item {
                    OutlinedButton(
                        onClick = { confirmReset = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                    ) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.settings_reset_category))
                    }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            text = { Text(stringResource(R.string.settings_reset_category_confirm, title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    viewModel.resetCategory(category)
                    if (category == SettingsCategory.APPEARANCE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.getEmptyLocaleList()
                    }
                }) { Text(stringResource(R.string.settings_reset_category)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    if (showDriveSetup) {
        DriveSetupDialog(onConnect = viewModel::connectDrive, onDismiss = { showDriveSetup = false })
    }
    if (showRestore) {
        RestoreDialog(viewModel = viewModel, onDismiss = { showRestore = false })
    }
}

private fun SettingsCategory.titleRes() = when (this) {
    SettingsCategory.APPEARANCE -> R.string.settings_section_appearance
    SettingsCategory.COLORS -> R.string.settings_section_colors
    SettingsCategory.ACCESSIBILITY -> R.string.settings_section_accessibility
    SettingsCategory.EDITOR -> R.string.settings_section_editor
    SettingsCategory.NOTIFICATIONS -> R.string.settings_section_notifications
    SettingsCategory.BACKUP -> R.string.settings_section_backup
    SettingsCategory.STORAGE -> R.string.settings_section_storage
    SettingsCategory.TROUBLESHOOTING -> R.string.settings_section_troubleshooting
    SettingsCategory.ABOUT -> R.string.settings_section_about
}

private fun SettingsCategory.summaryRes() = when (this) {
    SettingsCategory.APPEARANCE -> R.string.settings_summary_appearance
    SettingsCategory.COLORS -> R.string.settings_summary_colors
    SettingsCategory.ACCESSIBILITY -> R.string.settings_summary_accessibility
    SettingsCategory.EDITOR -> R.string.settings_summary_editor
    SettingsCategory.NOTIFICATIONS -> R.string.settings_summary_notifications
    SettingsCategory.BACKUP -> R.string.settings_summary_backup
    SettingsCategory.STORAGE -> R.string.settings_storage_summary
    SettingsCategory.TROUBLESHOOTING -> R.string.settings_summary_troubleshooting
    SettingsCategory.ABOUT -> R.string.settings_summary_about
}

private fun SettingsCategory.icon(): ImageVector = when (this) {
    SettingsCategory.APPEARANCE -> Icons.Outlined.DarkMode
    SettingsCategory.COLORS -> Icons.Outlined.Palette
    SettingsCategory.ACCESSIBILITY -> Icons.Outlined.Accessibility
    SettingsCategory.EDITOR -> Icons.Outlined.EditNote
    SettingsCategory.NOTIFICATIONS -> Icons.Outlined.Notifications
    SettingsCategory.BACKUP -> Icons.Outlined.Backup
    SettingsCategory.STORAGE -> Icons.Outlined.Storage
    SettingsCategory.TROUBLESHOOTING -> Icons.Outlined.BugReport
    SettingsCategory.ABOUT -> Icons.Outlined.Info
}

@Composable
private fun AppearanceSection(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    ChoiceRow(
        title = stringResource(R.string.settings_theme),
        options = ThemeMode.entries,
        selected = s.themeMode,
        label = {
            stringResource(
                when (it) {
                    ThemeMode.SYSTEM -> R.string.theme_system
                    ThemeMode.LIGHT -> R.string.theme_light
                    ThemeMode.DARK -> R.string.theme_dark
                    ThemeMode.AMOLED -> R.string.theme_amoled
                },
            )
        },
        onSelect = { mode -> update { it.copy(themeMode = mode) } },
    )
    ChoiceRow(
        title = stringResource(R.string.settings_layout),
        options = ListLayout.entries,
        selected = s.listLayout,
        label = { stringResource(if (it == ListLayout.GRID) R.string.action_grid_view else R.string.action_list_view) },
        onSelect = { layout -> update { it.copy(listLayout = layout) } },
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) LanguageRow()
}

@Composable
private fun ColorsSection(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    val monet = s.monet
    fun tune(transform: (MonetTuning) -> MonetTuning) = update { it.copy(monet = transform(it.monet)) }
    PalettePicker(s.themePalette, monet.accentColor) { palette -> update { it.copy(themePalette = palette) } }
    if (s.themePalette == ThemePalette.CUSTOM) {
        ColorSettingRow(stringResource(R.string.monet_accent), monet.accentColor, MonetTuning.DEFAULT_ACCENT) { color ->
            tune { it.copy(accentColor = color) }
        }
    }
    ChoiceRow(
        title = stringResource(R.string.settings_palette_style),
        options = PaletteStyleOption.entries,
        selected = s.paletteStyle,
        label = { paletteStyleLabel(it) },
        onSelect = { style -> update { it.copy(paletteStyle = style) } },
    )
    ChoiceRow(
        title = stringResource(R.string.settings_contrast),
        options = ContrastLevel.entries,
        selected = s.contrast,
        label = { contrastLabel(it) },
        onSelect = { contrast -> update { it.copy(contrast = contrast) } },
    )
    SwitchRow(stringResource(R.string.monet_custom_background), monet.customBackground, stringResource(R.string.monet_custom_background_summary)) { v ->
        tune { it.copy(customBackground = v) }
    }
    if (monet.customBackground) {
        ColorSettingRow(stringResource(R.string.monet_background), monet.backgroundColor, MonetTuning.DEFAULT_BACKGROUND) { color ->
            tune { it.copy(backgroundColor = color) }
        }
    }
    SliderRow(
        title = stringResource(R.string.monet_luminance),
        value = monet.luminance.toFloat(),
        range = MonetTuning.RANGE.first.toFloat()..MonetTuning.RANGE.last.toFloat(),
        step = 5f,
        format = { "%+d %%".format(it.roundToInt()) },
        defaultValue = 0f,
        summary = stringResource(R.string.monet_luminance_summary),
        onValueChangeFinished = { v -> tune { it.copy(luminance = v.roundToInt()) } },
    )
    SliderRow(
        title = stringResource(R.string.monet_chroma),
        value = monet.chroma.toFloat(),
        range = MonetTuning.RANGE.first.toFloat()..MonetTuning.RANGE.last.toFloat(),
        step = 5f,
        format = { "%+d %%".format(it.roundToInt()) },
        defaultValue = 0f,
        summary = stringResource(R.string.monet_chroma_summary),
        onValueChangeFinished = { v -> tune { it.copy(chroma = v.roundToInt()) } },
    )
    SwitchRow(stringResource(R.string.monet_whole_palette), monet.wholePalette, stringResource(R.string.monet_whole_palette_summary)) { v ->
        tune { it.copy(wholePalette = v) }
    }
    SwitchRow(stringResource(R.string.monet_tinted_background), monet.tintedBackground, stringResource(R.string.monet_tinted_background_summary)) { v ->
        tune { it.copy(tintedBackground = v) }
    }
}

@Composable
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun LanguageRow() {
    val context = LocalContext.current
    val localeManager = remember { context.getSystemService(LocaleManager::class.java) }
    var current by remember { mutableStateOf(localeManager.applicationLocales.toLanguageTags()) }
    val options = listOf("", "tr", "en")
    ChoiceRow(
        title = stringResource(R.string.settings_language),
        options = options,
        selected = options.firstOrNull { it.isNotEmpty() && current.startsWith(it) } ?: "",
        label = {
            when (it) {
                "tr" -> "Türkçe"
                "en" -> "English"
                else -> stringResource(R.string.language_system)
            }
        },
        onSelect = { tag ->
            current = tag
            localeManager.applicationLocales = if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        },
    )
}

@Composable
private fun AccessibilitySection(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    SliderRow(
        title = stringResource(R.string.settings_ui_text_size),
        value = s.uiTextScale,
        range = Settings.UI_TEXT_SCALE_RANGE,
        step = 0.05f,
        format = { "${(it * 100).roundToInt()}%" },
        defaultValue = SettingsCategory.DEFAULTS.uiTextScale,
        onValueChangeFinished = { v -> update { it.copy(uiTextScale = v) } },
    )
    SliderRow(
        title = stringResource(R.string.settings_editor_font_size),
        value = s.editorFontSizeSp,
        range = Settings.EDITOR_FONT_SIZE_RANGE,
        step = 1f,
        format = { "${it.roundToInt()} sp" },
        defaultValue = SettingsCategory.DEFAULTS.editorFontSizeSp,
        onValueChangeFinished = { v -> update { it.copy(editorFontSizeSp = v) } },
    )
    SliderRow(
        title = stringResource(R.string.settings_editor_line_height),
        value = s.editorLineHeight,
        range = Settings.EDITOR_LINE_HEIGHT_RANGE,
        step = 0.1f,
        format = { "%.1f×".format(it) },
        defaultValue = SettingsCategory.DEFAULTS.editorLineHeight,
        onValueChangeFinished = { v -> update { it.copy(editorLineHeight = v) } },
    )
    ChoiceRow(
        title = stringResource(R.string.settings_editor_font),
        options = EditorFont.entries,
        selected = s.editorFont,
        label = {
            stringResource(
                when (it) {
                    EditorFont.SERIF -> R.string.font_serif
                    EditorFont.SYSTEM -> R.string.font_system
                    EditorFont.GOOGLE_SANS -> R.string.font_google_sans
                    EditorFont.MONO -> R.string.font_mono
                },
            )
        },
        onSelect = { font -> update { it.copy(editorFont = font) } },
    )
    ChoiceRow(
        title = stringResource(R.string.settings_editor_align),
        options = EditorAlign.entries,
        selected = s.editorAlign,
        label = { stringResource(if (it == EditorAlign.START) R.string.align_start else R.string.align_center) },
        onSelect = { align -> update { it.copy(editorAlign = align) } },
    )
    SwitchRow(stringResource(R.string.settings_pinch_zoom), s.pinchZoom, stringResource(R.string.settings_pinch_zoom_summary)) { v ->
        update { it.copy(pinchZoom = v) }
    }
    ChoiceRow(
        title = stringResource(R.string.settings_preview_size),
        options = PreviewSize.entries,
        selected = s.previewSize,
        label = {
            stringResource(
                when (it) {
                    PreviewSize.SMALL -> R.string.size_small
                    PreviewSize.MEDIUM -> R.string.size_medium
                    PreviewSize.LARGE -> R.string.size_large
                },
            )
        },
        onSelect = { size -> update { it.copy(previewSize = size) } },
    )
    ChoiceRow(
        title = stringResource(R.string.settings_animation_speed),
        options = AnimationSpeed.entries,
        selected = s.animationSpeed,
        label = {
            stringResource(
                when (it) {
                    AnimationSpeed.SLOW -> R.string.speed_slow
                    AnimationSpeed.NORMAL -> R.string.speed_normal
                    AnimationSpeed.FAST -> R.string.speed_fast
                },
            )
        },
        onSelect = { speed -> update { it.copy(animationSpeed = speed) } },
    )
    SwitchRow(stringResource(R.string.settings_reduce_motion), s.reduceMotion, stringResource(R.string.settings_reduce_motion_summary)) { v ->
        update { it.copy(reduceMotion = v) }
    }
    SwitchRow(stringResource(R.string.settings_high_contrast), s.highContrast, stringResource(R.string.settings_high_contrast_summary)) { v ->
        update { it.copy(highContrast = v) }
    }
}

@Composable
private fun EditorSection(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    SwitchRow(stringResource(R.string.settings_always_focus), s.alwaysFocusMode, stringResource(R.string.settings_always_focus_summary)) { v ->
        update { it.copy(alwaysFocusMode = v) }
    }
}

@Composable
private fun NotificationsSection(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    val context = LocalContext.current
    SwitchRow(
        stringResource(R.string.settings_restore_dismissed),
        s.restoreDismissedPins,
        stringResource(R.string.settings_restore_dismissed_summary),
    ) { v -> update { it.copy(restoreDismissedPins = v) } }
    val granted = hasNotificationPermission(context)
    ActionRow(
        stringResource(R.string.settings_notification_permission),
        stringResource(if (granted) R.string.permission_granted else R.string.permission_denied),
    ) {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
}

@Composable
private fun BackupSection(
    settings: Settings,
    viewModel: SettingsViewModel,
    backupRunning: Boolean,
    restoring: Boolean,
    onRestore: () -> Unit,
    onDriveSetup: () -> Unit,
) {
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.setBackupFolder(uri)
    }
    val folderName = remember(settings.backupFolderUri) { viewModel.folderName(settings.backupFolderUri) }
    ActionRow(
        stringResource(R.string.backup_folder),
        folderName ?: stringResource(R.string.backup_folder_none),
    ) { folderPicker.launch(null) }
    StatusLine(settings.lastFolderBackupAt, settings.lastFolderBackupError, settings.backupFolderUri != null)

    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }
    if (settings.driveEnabled) {
        ActionRow(stringResource(R.string.backup_drive), stringResource(R.string.backup_drive_disconnect)) { confirmDisconnect = true }
    } else {
        ActionRow(stringResource(R.string.backup_drive), stringResource(R.string.backup_drive_connect)) { viewModel.connectDrive() }
    }
    ActionRow(stringResource(R.string.drive_setup_title), stringResource(R.string.drive_setup_summary), onClick = onDriveSetup)
    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text(stringResource(R.string.drive_disconnect_title)) },
            text = { Text(stringResource(R.string.drive_disconnect_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    viewModel.disconnectDrive()
                }) { Text(stringResource(R.string.drive_disconnect)) }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    StatusLine(settings.lastDriveBackupAt, settings.lastDriveBackupError, settings.driveEnabled)

    ChoiceRow(
        title = stringResource(R.string.backup_frequency),
        options = BackupFrequency.entries,
        selected = settings.backupFrequency,
        label = {
            stringResource(
                when (it) {
                    BackupFrequency.OFF -> R.string.frequency_off
                    BackupFrequency.DAILY -> R.string.frequency_daily
                    BackupFrequency.WEEKLY -> R.string.frequency_weekly
                    BackupFrequency.MONTHLY -> R.string.frequency_monthly
                },
            )
        },
        onSelect = { f -> viewModel.update { it.copy(backupFrequency = f) } },
    )
    ChoiceRow(
        title = stringResource(R.string.backup_retention),
        options = viewModel.retentionOptions,
        selected = settings.backupRetention,
        label = { pluralStringResource(R.plurals.backup_retention_value, it, it) },
        onSelect = { n -> viewModel.update { it.copy(backupRetention = n) } },
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        val anyTarget = settings.backupFolderUri != null || settings.driveEnabled
        Button(onClick = viewModel::backupNow, enabled = anyTarget && !backupRunning) {
            if (backupRunning) CircularProgressIndicator(Modifier.padding(end = 8.dp).then(Modifier), strokeWidth = 2.dp)
            Text(stringResource(R.string.backup_now))
        }
        OutlinedButton(onClick = onRestore, enabled = !restoring) {
            Text(stringResource(R.string.backup_restore))
        }
    }
}

@Composable
private fun StatusLine(lastAt: Long?, error: String?, enabled: Boolean) {
    if (!enabled) return
    val text = when {
        error != null -> stringResource(R.string.backup_status_error, error)
        lastAt != null -> stringResource(
            R.string.backup_status_last,
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(lastAt)),
        )
        else -> stringResource(R.string.backup_status_never)
    }
    Text(
        text,
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        color = if (error != null) androidx.compose.material3.MaterialTheme.colorScheme.error else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
    )
}
