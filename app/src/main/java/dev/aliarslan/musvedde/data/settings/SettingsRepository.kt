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
import dev.aliarslan.musvedde.domain.AnimationSpeed
import dev.aliarslan.musvedde.domain.BackupFrequency
import dev.aliarslan.musvedde.domain.EditorAlign
import dev.aliarslan.musvedde.domain.EditorFont
import dev.aliarslan.musvedde.domain.ListLayout
import dev.aliarslan.musvedde.domain.PreviewSize
import dev.aliarslan.musvedde.domain.Settings
import dev.aliarslan.musvedde.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<Settings> = dataStore.data.map { it.toSettings() }.distinctUntilChanged()

    suspend fun update(transform: (Settings) -> Settings) {
        dataStore.edit { prefs -> prefs.write(transform(prefs.toSettings()).sanitized()) }
    }

    private object Keys {
        val themeMode = stringPreferencesKey("themeMode")
        val dynamicColor = booleanPreferencesKey("dynamicColor")
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
    }

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            themeMode = enumOr(this[Keys.themeMode], d.themeMode),
            dynamicColor = this[Keys.dynamicColor] ?: d.dynamicColor,
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
        ).sanitized()
    }

    private fun MutablePreferences.write(s: Settings) {
        this[Keys.themeMode] = s.themeMode.name
        this[Keys.dynamicColor] = s.dynamicColor
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
    }

    private fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
