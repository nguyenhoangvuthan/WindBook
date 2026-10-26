package dev.kent.windbook.core.data.db

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Additive only: preserve every existing source, fingerprint and reading preference. */
object AnnotationMigration : Migration(6,7) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS annotations (id TEXT NOT NULL, book_id TEXT NOT NULL, fingerprint TEXT NOT NULL, anchor_json TEXT NOT NULL, quote TEXT NOT NULL, color TEXT NOT NULL, body TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(book_id) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_annotations_book_id ON annotations (book_id)")
    }
}
