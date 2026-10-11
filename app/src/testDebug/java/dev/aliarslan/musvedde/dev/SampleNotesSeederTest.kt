package dev.aliarslan.musvedde.dev

import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.data.db.inMemoryDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SampleNotesSeederTest {
    private val db = inMemoryDatabase()

    @After
    fun tearDown() = db.close()

    @Test
    fun seeds10000InBatches() = runTest {
        SampleNotesSeeder(db, clock = { 5L }).seed(10_000)

        assertThat(db.noteDao().count()).isEqualTo(10_000)
        val sample = db.noteDao().pageAll(50, 0)
        assertThat(sample.map { it.position }.toSet()).hasSize(50)
        assertThat(sample.all { it.body.lines().size in 4..40 }).isTrue()
    }

    @Test
    fun seedingIsDeterministic() = runTest {
        val other = inMemoryDatabase()
        SampleNotesSeeder(db, clock = { 5L }).seed(20)
        SampleNotesSeeder(other, clock = { 5L }).seed(20)

        assertThat(db.noteDao().pageAll(20, 0).map { it.body }).isEqualTo(other.noteDao().pageAll(20, 0).map { it.body })
        other.close()
    }
}
