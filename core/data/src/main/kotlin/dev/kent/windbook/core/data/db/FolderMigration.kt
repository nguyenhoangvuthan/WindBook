package dev.kent.windbook.core.data.db

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Room runs this migration in one transaction, including the dependent-table backups. */
object FolderMigration : Migration(2, 3) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // Dropping books with foreign keys enabled would cascade into progress/preferences.
        // Preserve and recreate both v2 dependents explicitly; never toggle FK checks in a transaction.
        connection.execSQL("CREATE TABLE _folder_reading_progress AS SELECT * FROM reading_progress")
        connection.execSQL("DROP TABLE reading_progress")
        connection.execSQL("CREATE TABLE _folder_book_preferences AS SELECT * FROM book_preferences")
        connection.execSQL("DROP TABLE book_preferences")
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `_folder_books` (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `author` TEXT,
                `format` TEXT NOT NULL,
                `file_path` TEXT,
                `file_size` INTEGER NOT NULL,
                `sha256` TEXT,
                `cover_path` TEXT,
                `source_uri` TEXT,
                `status` TEXT NOT NULL,
                `added_at` INTEGER NOT NULL,
                `last_opened_at` INTEGER,
                `updated_at` INTEGER NOT NULL,
                `deleted_at` INTEGER,
                PRIMARY KEY(`id`))
            """.trimIndent(),
        )
        connection.execSQL("INSERT INTO _folder_books SELECT * FROM books")
        connection.execSQL("DROP TABLE books")
        connection.execSQL("ALTER TABLE _folder_books RENAME TO books")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_books_sha256` ON `books` (`sha256`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_books_last_opened_at` ON `books` (`last_opened_at`)")
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reading_progress` (
                `book_id` TEXT NOT NULL,
                `locator_json` TEXT NOT NULL,
                `progression` REAL NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`book_id`),
                FOREIGN KEY(`book_id`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
            """.trimIndent(),
        )
        connection.execSQL("INSERT INTO reading_progress SELECT * FROM _folder_reading_progress")
        connection.execSQL("DROP TABLE _folder_reading_progress")
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `book_preferences` (
                `book_id` TEXT NOT NULL,
                `json` TEXT NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`book_id`),
                FOREIGN KEY(`book_id`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
            """.trimIndent(),
        )
        connection.execSQL("INSERT INTO book_preferences SELECT * FROM _folder_book_preferences")
        connection.execSQL("DROP TABLE _folder_book_preferences")
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS folders (
                id TEXT NOT NULL PRIMARY KEY,
                tree_uri TEXT NOT NULL,
                display_location TEXT NOT NULL,
                extensions_json TEXT NOT NULL,
                recursive INTEGER NOT NULL,
                disconnected_at INTEGER,
                last_scan_at INTEGER)
            """.trimIndent(),
        )
        connection.execSQL("CREATE UNIQUE INDEX index_folders_tree_uri ON folders (tree_uri)")
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS book_sources (
                id TEXT NOT NULL PRIMARY KEY,
                book_id TEXT NOT NULL,
                kind TEXT NOT NULL,
                local_path TEXT,
                uri TEXT,
                authority TEXT,
                document_id TEXT,
                size INTEGER,
                mtime INTEGER,
                verified_sha256 TEXT,
                availability TEXT NOT NULL,
                FOREIGN KEY(book_id) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE)
            """.trimIndent(),
        )
        connection.execSQL("CREATE INDEX index_book_sources_book_id ON book_sources (book_id)")
        connection.execSQL("CREATE UNIQUE INDEX index_book_sources_authority_document_id ON book_sources (authority, document_id)")
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS folder_memberships (
                folder_id TEXT NOT NULL,
                source_id TEXT NOT NULL,
                relative_path TEXT NOT NULL,
                parent_document_id TEXT NOT NULL,
                last_seen_run TEXT NOT NULL,
                PRIMARY KEY(folder_id, source_id),
                FOREIGN KEY(folder_id) REFERENCES folders(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(source_id) REFERENCES book_sources(id) ON UPDATE NO ACTION ON DELETE CASCADE)
            """.trimIndent(),
        )
        connection.execSQL("CREATE INDEX index_folder_memberships_source_id ON folder_memberships (source_id)")
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS scan_runs (
                id TEXT NOT NULL PRIMARY KEY,
                folder_id TEXT NOT NULL,
                options_json TEXT NOT NULL,
                state TEXT NOT NULL,
                examined INTEGER NOT NULL,
                matched INTEGER NOT NULL,
                added INTEGER NOT NULL,
                known INTEGER NOT NULL,
                unreadable INTEGER NOT NULL,
                directory_errors INTEGER NOT NULL,
                FOREIGN KEY(folder_id) REFERENCES folders(id) ON UPDATE NO ACTION ON DELETE CASCADE)
            """.trimIndent(),
        )
        connection.execSQL("CREATE INDEX index_scan_runs_folder_id ON scan_runs (folder_id)")
        connection.execSQL(
            """
            INSERT INTO book_sources (id, book_id, kind, local_path, size, verified_sha256, availability)
            SELECT 'owned:' || id, id, 'OWNED', file_path, file_size, sha256,
                CASE WHEN deleted_at IS NULL THEN 'AVAILABLE' ELSE 'MISSING' END
            FROM books
            """.trimIndent(),
        )
    }
}
