package dev.aliarslan.musvedde.baselineprofile

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val PACKAGE_NAME = "dev.aliarslan.musvedde"
const val SEED_COUNT = 10_000
private const val TIMEOUT_MS = 30_000L

// `input text` turns %s into a space, so this types "kalem gece deniz " (17 characters).
private const val TYPING_CHUNK = "kalem%sgece%sdeniz%s"
private const val TYPING_CHUNK_LENGTH = 17

fun MacrobenchmarkScope.seedNotes(count: Int = SEED_COUNT) {
    val intent = Intent()
        .setComponent(ComponentName(PACKAGE_NAME, "$PACKAGE_NAME.dev.SeedActivity"))
        .putExtra("count", count)
    startActivityAndWait(intent)
    device.wait(Until.gone(By.pkg(PACKAGE_NAME).depth(0)), TIMEOUT_MS)
}

fun MacrobenchmarkScope.waitForNotes() {
    device.wait(Until.hasObject(By.res("note_card")), TIMEOUT_MS)
}

fun MacrobenchmarkScope.flingNotes() {
    val grid = device.findObject(By.res("notes_grid")) ?: return
    grid.setGestureMargin(device.displayWidth / 5)
    repeat(3) {
        grid.fling(Direction.DOWN)
        device.waitForIdle()
    }
}

fun MacrobenchmarkScope.openFirstNote() {
    device.findObject(By.res("note_card"))?.click()
    device.wait(Until.hasObject(By.res("editor_body")), TIMEOUT_MS)
}

fun MacrobenchmarkScope.typeInEditor(characters: Int) {
    device.findObject(By.res("editor_body"))?.click()
    var typed = 0
    while (typed < characters) {
        device.executeShellCommand("input text $TYPING_CHUNK")
        typed += TYPING_CHUNK_LENGTH
    }
    device.waitForIdle()
}
