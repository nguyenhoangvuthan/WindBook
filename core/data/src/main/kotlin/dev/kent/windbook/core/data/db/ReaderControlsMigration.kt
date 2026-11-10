package dev.kent.windbook.core.data.db

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object ReaderControlsMigration : Migration(7,8) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (id TEXT NOT NULL, book_id TEXT NOT NULL, fingerprint TEXT NOT NULL, locator_json TEXT NOT NULL, progression REAL NOT NULL, label TEXT NOT NULL, text_encoding TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER, PRIMARY KEY(id), FOREIGN KEY(book_id) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_book_id ON bookmarks (book_id)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS pdf_contents (book_id TEXT NOT NULL, fingerprint TEXT NOT NULL, payload_version INTEGER NOT NULL, status TEXT NOT NULL, contents_json TEXT NOT NULL, PRIMARY KEY(book_id), FOREIGN KEY(book_id) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
    }
}
