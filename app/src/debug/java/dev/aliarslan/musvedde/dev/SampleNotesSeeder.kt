package dev.aliarslan.musvedde.dev

import androidx.room.withTransaction
import dev.aliarslan.musvedde.core.Clock
import dev.aliarslan.musvedde.data.db.MusveddeDatabase
import dev.aliarslan.musvedde.data.db.NoteEntity
import dev.aliarslan.musvedde.data.note.Positions
import dev.aliarslan.musvedde.domain.NoteColor
import javax.inject.Inject
import kotlin.random.Random

class SampleNotesSeeder @Inject constructor(
    private val db: MusveddeDatabase,
    private val clock: Clock,
) {
    suspend fun seed(count: Int) {
        val random = Random(SEED)
        val dao = db.noteDao()
        var position = (dao.maxPosition() ?: 0) + Positions.STEP
        val now = clock.now()
        (0 until count).chunked(BATCH).forEach { batch ->
            val notes = batch.map { index ->
                NoteEntity(
                    title = if (random.nextInt(4) == 0) "" else words(random, 1 + random.nextInt(4)).replaceFirstChar(Char::uppercase),
                    body = sampleBody(random),
                    color = NoteColor.entries[random.nextInt(NoteColor.entries.size)].name,
                    pinnedToTop = random.nextInt(20) == 0,
                    pinnedToNotification = false,
                    state = "ACTIVE",
                    position = position.also { position += Positions.STEP },
                    createdAt = now - index,
                    updatedAt = now - index,
                    trashedAt = null,
                )
            }
            db.withTransaction { dao.insertAll(notes) }
        }
    }

    private fun sampleBody(random: Random): String {
        val lines = 4 + random.nextInt(37)
        return buildString {
            for (line in 0 until lines) {
                if (line > 0) append('\n')
                val paragraphBreak = line > 0 && line % 5 == 4 && line != lines - 1
                if (!paragraphBreak) append(words(random, 3 + random.nextInt(6)).replaceFirstChar(Char::uppercase))
            }
        }
    }

    private fun words(random: Random, count: Int) = (0 until count).joinToString(" ") { WORDS[random.nextInt(WORDS.size)] }

    private companion object {
        const val SEED = 42
        const val BATCH = 500
        val WORDS = listOf(
            "gece", "deniz", "rüzgâr", "ay", "yıldız", "sessizlik", "kalp", "yağmur", "şehir", "sokak",
            "hatıra", "gölge", "ışık", "sabah", "akşam", "yol", "kuş", "dal", "yaprak", "toprak",
            "su", "ateş", "taş", "kapı", "pencere", "sen", "ben", "bir", "ve", "ile",
            "gibi", "uzak", "yakın", "derin", "sıcak", "soğuk", "mavi", "kırmızı", "beyaz", "eski",
            "yeni", "sonsuz", "kayıp", "bulut", "dağ", "ırmak", "liman", "gemi", "mektup", "zaman",
        )
    }
}
