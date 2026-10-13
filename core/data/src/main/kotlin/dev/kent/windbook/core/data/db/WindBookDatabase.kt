package dev.kent.windbook.core.data.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [BookEntity::class, ReadingProgressEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class WindBookDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        const val FILE_NAME = "windbook.db"

        fun create(context: Context): WindBookDatabase =
            Room.databaseBuilder<WindBookDatabase>(
                context = context,
                name = context.getDatabasePath(FILE_NAME).absolutePath,
            )
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()

        fun inMemory(context: Context, driver: SQLiteDriver): WindBookDatabase =
            Room.inMemoryDatabaseBuilder<WindBookDatabase>(context)
                .setDriver(driver)
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
    }
}
