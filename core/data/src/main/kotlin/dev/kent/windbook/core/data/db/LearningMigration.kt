package dev.kent.windbook.core.data.db

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object LearningMigration:Migration(9,10) {
    override suspend fun migrate(connection:SQLiteConnection) {
        connection.execSQL("CREATE TABLE learning_answers (id TEXT NOT NULL PRIMARY KEY, annotation_id TEXT NOT NULL, fingerprint TEXT NOT NULL, model TEXT NOT NULL, prompt_version INTEGER NOT NULL, context_version TEXT NOT NULL, payload TEXT NOT NULL, created_at INTEGER NOT NULL, FOREIGN KEY(annotation_id) REFERENCES annotations(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        connection.execSQL("CREATE INDEX index_learning_answers_annotation_id ON learning_answers(annotation_id)")
        connection.execSQL("CREATE TABLE vocabulary (id TEXT NOT NULL PRIMARY KEY, answer_id TEXT NOT NULL, term TEXT NOT NULL, meaning TEXT NOT NULL, created_at INTEGER NOT NULL, FOREIGN KEY(answer_id) REFERENCES learning_answers(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        connection.execSQL("CREATE INDEX index_vocabulary_answer_id ON vocabulary(answer_id)")
        connection.execSQL("CREATE TABLE learning_preparations (book_id TEXT NOT NULL PRIMARY KEY, fingerprint TEXT NOT NULL, model TEXT NOT NULL, version INTEGER NOT NULL, next_index INTEGER NOT NULL, complete INTEGER NOT NULL, FOREIGN KEY(book_id) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        connection.execSQL("CREATE TABLE learning_summaries (book_id TEXT NOT NULL, section_index INTEGER NOT NULL, location TEXT NOT NULL, summary TEXT NOT NULL, PRIMARY KEY(book_id,section_index), FOREIGN KEY(book_id) REFERENCES learning_preparations(book_id) ON UPDATE NO ACTION ON DELETE CASCADE)")
    }
}
