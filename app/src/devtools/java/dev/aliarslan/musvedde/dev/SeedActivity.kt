package dev.aliarslan.musvedde.dev

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.aliarslan.musvedde.data.db.MusveddeDatabase
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SeedActivity : ComponentActivity() {

    @Inject
    lateinit var seeder: SampleNotesSeeder

    @Inject
    lateinit var db: MusveddeDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val count = intent.getIntExtra(EXTRA_COUNT, 0)
        lifecycleScope.launch {
            if (db.noteDao().count() != count) {
                db.noteDao().deleteAll()
                seeder.seed(count)
            }
            finish()
        }
    }

    companion object {
        const val EXTRA_COUNT = "count"
    }
}
