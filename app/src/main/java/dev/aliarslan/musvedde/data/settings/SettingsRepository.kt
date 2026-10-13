package dev.aliarslan.musvedde.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.aliarslan.musvedde.data.backup.BackupSettingsStore
import dev.aliarslan.musvedde.domain.AnimationSpeed
import dev.aliarslan.musvedde.domain.BackupFrequency
import dev.aliarslan.musvedde.domain.ContrastLevel
import dev.aliarslan.musvedde.domain.PaletteStyleOption
import dev.aliarslan.musvedde.domain.ThemePalette
import dev.aliarslan.musvedde.domain.EditorAlign
import dev.aliarslan.musvedde.domain.EditorFont
import dev.aliarslan.musvedde.domain.ListLayout
import dev.aliarslan.musvedde.domain.MonetTuning
import dev.aliarslan.musvedde.domain.PreviewSize
import dev.aliarslan.musvedde.domain.Settings
import dev.aliarslan.musvedde.domain.ThemeMode
import dev.aliarslan.musvedde.domain.portable
import dev.aliarslan.musvedde.domain.restoredOnto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : BackupSettingsStore {
    val settings: Flow<Settings> = dataStore.data.map { it.toSettings() }.distinctUntilChanged()

    suspend fun update(transform: (Settings) -> Settings) {
        dataStore.edit { prefs -> prefs.write(transform(prefs.toSettings()).sanitized()) }
    }

    override suspend fun current(): Settings = settings.first().portable()

    override suspend fun restore(settings: Settings) = update { settings.restoredOnto(it) }

    private object Keys {
        val themeMode = stringPreferencesKey("themeMode")
        val legacyDynamicColor = booleanPreferencesKey("dynamicColor")
        val themePalette = stringPreferencesKey("themePalette")
        val paletteStyle = stringPreferencesKey("paletteStyle")
        val contrast = stringPreferencesKey("contrast")
        val monetAccent = intPreferencesKey("monetAccent")
        val monetCustomBackground = booleanPreferencesKey("monetCustomBackground")
        val monetBackground = intPreferencesKey("monetBackground")
        val monetLuminance = intPreferencesKey("monetLuminance")
        val monetChroma = intPreferencesKey("monetChroma")
        val monetWholePalette = booleanPreferencesKey("monetWholePalette")
        val monetTintedBackground = booleanPreferencesKey("monetTintedBackground")
        val listLayout = stringPreferencesKey("listLayout")
        val uiTextScale = floatPreferencesKey("uiTextScale")
        val editorFontSizeSp = floatPreferencesKey("editorFontSizeSp")
        val editorLineHeight = floatPreferencesKey("editorLineHeight")
        val editorFont = stringPreferencesKey("editorFont")
        val editorAlign = stringPreferencesKey("editorAlign")
        val pinchZoom = booleanPreferencesKey("pinchZoom")
        val previewSize = stringPreferencesKey("previewSize")
        val reduceMotion = booleanPreferencesKey("reduceMotion")
        val animationSpeed = stringPreferencesKey("animationSpeed")
        val highContrast = booleanPreferencesKey("highContrast")
        val alwaysFocusMode = booleanPreferencesKey("alwaysFocusMode")
        val restoreDismissedPins = booleanPreferencesKey("restoreDismissedPins")
        val backupFolderUri = stringPreferencesKey("backupFolderUri")
        val driveEnabled = booleanPreferencesKey("driveEnabled")
        val driveAccountEmail = stringPreferencesKey("driveAccountEmail")
        val backupFrequency = stringPreferencesKey("backupFrequency")
        val backupRetention = intPreferencesKey("backupRetention")
        val lastFolderBackupAt = longPreferencesKey("lastFolderBackupAt")
        val lastFolderBackupError = stringPreferencesKey("lastFolderBackupError")
        val lastDriveBackupAt = longPreferencesKey("lastDriveBackupAt")
        val lastDriveBackupError = stringPreferencesKey("lastDriveBackupError")
        val autoRestoreChecked = booleanPreferencesKey("autoRestoreChecked")
    }

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            themeMode = enumOr(this[Keys.themeMode], d.themeMode),
            themePalette = this[Keys.themePalette]?.let { enumOr(it, d.themePalette) }
                ?: if (this[Keys.legacyDynamicColor] == false) ThemePalette.PAPER else d.themePalette,
            paletteStyle = enumOr(this[Keys.paletteStyle], d.paletteStyle),
            contrast = enumOr(this[Keys.contrast], d.contrast),
            monet = MonetTuning(
                accentColor = this[Keys.monetAccent] ?: d.monet.accentColor,
                customBackground = this[Keys.monetCustomBackground] ?: d.monet.customBackground,
                backgroundColor = this[Keys.monetBackground] ?: d.monet.backgroundColor,
                luminance = this[Keys.monetLuminance] ?: d.monet.luminance,
                chroma = this[Keys.monetChroma] ?: d.monet.chroma,
                wholePalette = this[Keys.monetWholePalette] ?: d.monet.wholePalette,
                tintedBackground = this[Keys.monetTintedBackground] ?: d.monet.tintedBackground,
            ),
            listLayout = enumOr(this[Keys.listLayout], d.listLayout),
            uiTextScale = this[Keys.uiTextScale] ?: d.uiTextScale,
            editorFontSizeSp = this[Keys.editorFontSizeSp] ?: d.editorFontSizeSp,
            editorLineHeight = this[Keys.editorLineHeight] ?: d.editorLineHeight,
            editorFont = enumOr(this[Keys.editorFont], d.editorFont),
            editorAlign = enumOr(this[Keys.editorAlign], d.editorAlign),
            pinchZoom = this[Keys.pinchZoom] ?: d.pinchZoom,
            previewSize = enumOr(this[Keys.previewSize], d.previewSize),
            reduceMotion = this[Keys.reduceMotion] ?: d.reduceMotion,
            animationSpeed = enumOr(this[Keys.animationSpeed], d.animationSpeed),
            highContrast = this[Keys.highContrast] ?: d.highContrast,
            alwaysFocusMode = this[Keys.alwaysFocusMode] ?: d.alwaysFocusMode,
            restoreDismissedPins = this[Keys.restoreDismissedPins] ?: d.restoreDismissedPins,
            backupFolderUri = this[Keys.backupFolderUri],
            driveEnabled = this[Keys.driveEnabled] ?: d.driveEnabled,
            driveAccountEmail = this[Keys.driveAccountEmail],
            backupFrequency = enumOr(this[Keys.backupFrequency], d.backupFrequency),
            backupRetention = this[Keys.backupRetention] ?: d.backupRetention,
            lastFolderBackupAt = this[Keys.lastFolderBackupAt],
            lastFolderBackupError = this[Keys.lastFolderBackupError],
            lastDriveBackupAt = this[Keys.lastDriveBackupAt],
            lastDriveBackupError = this[Keys.lastDriveBackupError],
            autoRestoreChecked = this[Keys.autoRestoreChecked] ?: d.autoRestoreChecked,
        ).sanitized()
    }

    private fun MutablePreferences.write(s: Settings) {
        this[Keys.themeMode] = s.themeMode.name
        this[Keys.themePalette] = s.themePalette.name
        this[Keys.paletteStyle] = s.paletteStyle.name
        this[Keys.contrast] = s.contrast.name
        this[Keys.monetAccent] = s.monet.accentColor
        this[Keys.monetCustomBackground] = s.monet.customBackground
        this[Keys.monetBackground] = s.monet.backgroundColor
        this[Keys.monetLuminance] = s.monet.luminance
        this[Keys.monetChroma] = s.monet.chroma
        this[Keys.monetWholePalette] = s.monet.wholePalette
        this[Keys.monetTintedBackground] = s.monet.tintedBackground
        this[Keys.listLayout] = s.listLayout.name
        this[Keys.uiTextScale] = s.uiTextScale
        this[Keys.editorFontSizeSp] = s.editorFontSizeSp
        this[Keys.editorLineHeight] = s.editorLineHeight
        this[Keys.editorFont] = s.editorFont.name
        this[Keys.editorAlign] = s.editorAlign.name
        this[Keys.pinchZoom] = s.pinchZoom
        this[Keys.previewSize] = s.previewSize.name
        this[Keys.reduceMotion] = s.reduceMotion
        this[Keys.animationSpeed] = s.animationSpeed.name
        this[Keys.highContrast] = s.highContrast
        this[Keys.alwaysFocusMode] = s.alwaysFocusMode
        this[Keys.restoreDismissedPins] = s.restoreDismissedPins
        setOrRemove(Keys.backupFolderUri, s.backupFolderUri)
        this[Keys.driveEnabled] = s.driveEnabled
        setOrRemove(Keys.driveAccountEmail, s.driveAccountEmail)
        this[Keys.backupFrequency] = s.backupFrequency.name
        this[Keys.backupRetention] = s.backupRetention
        setOrRemove(Keys.lastFolderBackupAt, s.lastFolderBackupAt)
        setOrRemove(Keys.lastFolderBackupError, s.lastFolderBackupError)
        setOrRemove(Keys.lastDriveBackupAt, s.lastDriveBackupAt)
        setOrRemove(Keys.lastDriveBackupError, s.lastDriveBackupError)
        this[Keys.autoRestoreChecked] = s.autoRestoreChecked
    }

    private fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
