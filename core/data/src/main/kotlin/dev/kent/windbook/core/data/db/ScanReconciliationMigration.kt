package dev.kent.windbook.core.data.db

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Additive: historical sources, memberships and run counters keep their identities. */
object ScanReconciliationMigration : Migration(3, 4) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE folder_memberships ADD COLUMN present INTEGER NOT NULL DEFAULT 1")
        connection.execSQL("ALTER TABLE scan_runs ADD COLUMN started_at INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE scan_runs ADD COLUMN current_path TEXT NOT NULL DEFAULT ''")
    }
}
