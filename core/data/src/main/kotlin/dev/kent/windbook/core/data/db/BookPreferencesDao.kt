package dev.kent.windbook.core.data.db

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookPreferencesDao {
    @Query("SELECT json FROM book_preferences WHERE book_id = :bookId")
    fun observeJson(bookId: String): Flow<String?>

    @Query("SELECT json FROM book_preferences WHERE book_id = :bookId")
    suspend fun json(bookId: String): String?

    @Upsert
    suspend fun upsert(entity: BookPreferencesEntity)
}
