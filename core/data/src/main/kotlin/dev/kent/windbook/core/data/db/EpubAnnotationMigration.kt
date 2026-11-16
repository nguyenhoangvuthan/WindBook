package dev.kent.windbook.core.data.db

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object EpubAnnotationMigration : Migration(8,9) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE annotations ADD COLUMN kind TEXT NOT NULL DEFAULT 'PDF_TEXT'")
    }
}
