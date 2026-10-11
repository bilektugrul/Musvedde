package dev.aliarslan.musvedde.ui.editor

import androidx.compose.ui.geometry.Rect

sealed interface TapAction {
    data object Ignore : TapAction
    data object TitleEnd : TapAction
    data object BodyEnd : TapAction
    data class BodyLineEnd(val yInBody: Float) : TapAction
}

/**
 * Decides where the cursor goes when the editor is tapped outside the text fields. Taps on the fields
 * themselves are left to the text field, which already places the cursor at the touched character.
 */
fun resolveTap(x: Float, y: Float, title: Rect?, body: Rect): TapAction = when {
    body.contains(androidx.compose.ui.geometry.Offset(x, y)) -> TapAction.Ignore
    title != null && title.contains(androidx.compose.ui.geometry.Offset(x, y)) -> TapAction.Ignore
    y >= body.top && y <= body.bottom -> TapAction.BodyLineEnd(y - body.top)
    title != null && y < body.top -> TapAction.TitleEnd
    else -> TapAction.BodyEnd
}
