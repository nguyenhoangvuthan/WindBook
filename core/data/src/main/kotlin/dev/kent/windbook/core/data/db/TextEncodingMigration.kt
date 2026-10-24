package dev.kent.windbook.core.data.db

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Preserve raw fingerprints and all prior encoding interpretations. */
object TextEncodingMigration : Migration(5, 6) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE books ADD COLUMN active_text_encoding TEXT")
        connection.execSQL("ALTER TABLE book_sources ADD COLUMN txt_encoding_override TEXT")
        connection.execSQL("CREATE TABLE IF NOT EXISTS text_reading_states (book_id TEXT NOT NULL, encoding TEXT NOT NULL, locator_json TEXT NOT NULL, progression REAL NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(book_id, encoding), FOREIGN KEY(book_id) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
    }
}
