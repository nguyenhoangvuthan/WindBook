package dev.kent.windbook.core.data.db

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Distinct read copies retain their fingerprints even after their external source changes. */
object RevisionIdentityMigration : Migration(4, 5) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP INDEX index_books_sha256")
        connection.execSQL("CREATE INDEX index_books_sha256 ON books (sha256)")
    }
}
