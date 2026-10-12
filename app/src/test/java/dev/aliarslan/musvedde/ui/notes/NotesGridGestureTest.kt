package dev.aliarslan.musvedde.ui.notes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.domain.ListLayout
import dev.aliarslan.musvedde.domain.NoteColor
import dev.aliarslan.musvedde.domain.NoteSummary
import dev.aliarslan.musvedde.ui.NotesSection
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotesGridGestureTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<Long>()
    private val selected = mutableListOf<Long>()
    private val dragStarted = mutableListOf<Long>()
    private val moved = mutableListOf<Long>()

    private fun note(id: Long) = NoteSummary(
        id = id,
        title = "Not $id",
        preview = "satır\nsatır",
        color = NoteColor.DEFAULT,
        pinnedToTop = false,
        pinnedToNotification = false,
        position = 10 - id,
        updatedAt = 0,
        labelIds = persistentListOf(),
    )

    private fun show(canReorder: Boolean, selection: ImmutableSet<Long> = persistentSetOf()) {
        compose.setContent {
            NotesGrid(
                state = NotesUiState(
                    section = NotesSection.ACTIVE,
                    loaded = true,
                    others = (1L..3L).map(::note).toImmutableList(),
                    layout = ListLayout.LIST,
                    selection = selection,
                    canReorder = canReorder,
                ),
                labelNames = persistentMapOf(),
                onOpen = { opened += it },
                onSelect = { selected += it },
                onDragStart = { dragStarted += it },
                onMove = { id, _, _ -> moved += id },
                onEmptyTrash = {},
            )
        }
    }

    private fun firstCard() = compose.onAllNodesWithTag("note_card")[0]

    @Test
    fun longPressWithoutMovingSelectsAndDoesNotOpen() {
        show(canReorder = true)

        firstCard().performTouchInput { longClick() }
        compose.waitForIdle()

        assertThat(dragStarted).contains(1L)
        assertThat(opened).isEmpty()
        assertThat(moved).isEmpty()
    }

    @Test
    fun longPressAndDragMovesTheNote() {
        show(canReorder = true)

        firstCard().performTouchInput {
            down(center)
            advanceEventTime(1_000)
            repeat(20) { moveBy(Offset(0f, height * 0.15f)) }
            up()
        }
        compose.waitForIdle()

        assertThat(moved).containsExactly(1L)
        assertThat(opened).isEmpty()
    }

    @Test
    fun withoutReorderingLongPressSelects() {
        show(canReorder = false)

        firstCard().performTouchInput { longClick() }
        compose.waitForIdle()

        assertThat(selected).containsExactly(1L)
        assertThat(opened).isEmpty()
    }

    @Test
    fun tapOpensTheNote() {
        show(canReorder = true)

        firstCard().performTouchInput { click() }
        compose.waitForIdle()

        assertThat(opened).containsExactly(1L)
    }
}
