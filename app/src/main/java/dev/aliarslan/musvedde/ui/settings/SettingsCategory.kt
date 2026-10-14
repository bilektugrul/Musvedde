package dev.aliarslan.musvedde.ui.settings

import androidx.annotation.Keep
import dev.aliarslan.musvedde.domain.Settings
import kotlinx.serialization.Serializable

@Keep
@Serializable
enum class SettingsCategory {
    APPEARANCE,
    COLORS,
    ACCESSIBILITY,
    EDITOR,
    NOTIFICATIONS,
    SECURITY,
    BACKUP,
    WEB_EDITOR,
    STORAGE,
    TROUBLESHOOTING,
    ABOUT,
    ;

    /** Returns [settings] with this category's values back at their defaults, or null if it has none. */
    fun reset(settings: Settings): Settings? {
        val d = DEFAULTS
        return when (this) {
            APPEARANCE -> settings.copy(themeMode = d.themeMode, listLayout = d.listLayout)
            COLORS -> settings.copy(
                themePalette = d.themePalette,
                paletteStyle = d.paletteStyle,
                contrast = d.contrast,
                monet = d.monet,
            )
            ACCESSIBILITY -> settings.copy(
                uiTextScale = d.uiTextScale,
                editorFontSizeSp = d.editorFontSizeSp,
                editorLineHeight = d.editorLineHeight,
                editorFont = d.editorFont,
                editorAlign = d.editorAlign,
                pinchZoom = d.pinchZoom,
                previewSize = d.previewSize,
                animationSpeed = d.animationSpeed,
                reduceMotion = d.reduceMotion,
                highContrast = d.highContrast,
            )
            EDITOR -> settings.copy(alwaysFocusMode = d.alwaysFocusMode)
            NOTIFICATIONS -> settings.copy(restoreDismissedPins = d.restoreDismissedPins)
            BACKUP -> settings.copy(backupFrequency = d.backupFrequency, backupRetention = d.backupRetention)
            SECURITY, WEB_EDITOR, STORAGE, TROUBLESHOOTING, ABOUT -> null
        }
    }

    companion object {
        val DEFAULTS = Settings()
    }
}
