package dev.aliarslan.musvedde.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlin.math.abs

private const val COMMIT_PROGRESS = 1f / 3f
private const val COMMIT_VELOCITY_DP = 600f

fun shouldCommitBack(progress: Float, velocityDpPerSecond: Float): Boolean = when {
    velocityDpPerSecond < -COMMIT_VELOCITY_DP -> false
    velocityDpPerSecond > COMMIT_VELOCITY_DP -> true
    else -> progress > COMMIT_PROGRESS
}

fun isBackSwipeStart(dx: Float, dy: Float, touchSlop: Float): Boolean = dx > touchSlop && dx > 2 * abs(dy)

/**
 * Turns a rightward drag anywhere on the screen into a predictive back gesture, so the navigation
 * transition follows the finger the same way the system edge swipe does.
 */
@Composable
fun Modifier.swipeToGoBack(enabled: Boolean): Modifier {
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher ?: return this
    val input = remember(dispatcher) { DirectNavigationEventInput() }
    DisposableEffect(dispatcher, input) {
        dispatcher.addInput(input)
        onDispose { dispatcher.removeInput(input) }
    }
    val isEnabled by rememberUpdatedState(enabled)

    fun event(x: Float, y: Float, progress: Float) =
        NavigationEvent(touchX = x, touchY = y, progress = progress, swipeEdge = NavigationEvent.EDGE_LEFT)

    return pointerInput(input) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            if (!isEnabled) return@awaitEachGesture
            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            val width = size.width.toFloat().coerceAtLeast(1f)
            var dx = 0f
            var dy = 0f
            var started = false
            var progress = 0f
            var finished = false
            try {
                while (true) {
                    // Final pass: sliders, text selection and pinch zoom see the event first and win by consuming it.
                    val pointerEvent = awaitPointerEvent(PointerEventPass.Final)
                    val change = pointerEvent.changes.firstOrNull { it.id == down.id } ?: break
                    if (pointerEvent.changes.count { it.pressed } > 1) break
                    if (!change.pressed) {
                        if (started) {
                            val velocity = tracker.calculateVelocity().x / density
                            if (shouldCommitBack(progress, velocity)) input.backCompleted() else input.backCancelled()
                            finished = true
                        }
                        break
                    }
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (!started) {
                        if (change.isConsumed) break
                        val delta = change.positionChange()
                        dx += delta.x
                        dy += delta.y
                        if (abs(dy) > viewConfiguration.touchSlop && abs(dy) > abs(dx)) break
                        if (change.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) break
                        if (!isBackSwipeStart(dx, dy, viewConfiguration.touchSlop)) continue
                        started = true
                        input.backStarted(event(change.position.x, change.position.y, 0f))
                    }
                    progress = ((change.position.x - down.position.x) / width).coerceIn(0f, 1f)
                    input.backProgressed(event(change.position.x, change.position.y, progress))
                    change.consume()
                }
            } finally {
                if (started && !finished) input.backCancelled()
            }
        }
    }
}
