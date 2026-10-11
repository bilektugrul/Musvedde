package dev.aliarslan.musvedde.data.db

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ErrorLogDaoTest {
    private val db = inMemoryDatabase()
    private val dao = db.errorLogDao()

    @After
    fun tearDown() = db.close()

    @Test
    fun pruneToKeepsNewest() = runTest {
        repeat(510) { i ->
            dao.insert(ErrorLogEntity(timestamp = i.toLong(), level = "ERROR", source = "Test", message = "m$i", stackTrace = null))
        }

        dao.pruneTo(500)

        val all = dao.observeAll().first()
        assertThat(all).hasSize(500)
        assertThat(all.first().timestamp).isEqualTo(509)
        assertThat(all.last().timestamp).isEqualTo(10)
    }
}
