package dev.kent.windbook.core.data.db

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [BookEntity::class, ReadingProgressEntity::class, BookPreferencesEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class WindBookDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    abstract fun bookPreferencesDao(): BookPreferencesDao

    companion object {
        const val FILE_NAME = "windbook.db"

        fun create(context: Context): WindBookDatabase =
            open(context, context.getDatabasePath(FILE_NAME).absolutePath, BundledSQLiteDriver())

        /** Opens (and migrates) the database file at [path]; tests pass the framework driver. */
        fun open(context: Context, path: String, driver: SQLiteDriver): WindBookDatabase =
            Room.databaseBuilder<WindBookDatabase>(context = context, name = path)
                .setDriver(driver)
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()

        fun inMemory(context: Context, driver: SQLiteDriver): WindBookDatabase =
            Room.inMemoryDatabaseBuilder<WindBookDatabase>(context)
                .setDriver(driver)
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
    }
}
