package dev.kent.windbook.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.kent.windbook.core.data.BookSourcesRepository
import dev.kent.windbook.core.data.DisplayPreferencesRepository
import dev.kent.windbook.core.data.LibraryPreferences
import dev.kent.windbook.core.data.LibraryRepository
import dev.kent.windbook.core.data.db.WindBookDatabase
import dev.kent.windbook.core.data.files.BookFileStore
import dev.kent.windbook.core.data.folders.FolderGrants
import dev.kent.windbook.core.data.folders.FolderRepository
import dev.kent.windbook.core.data.folders.FolderScanCoordinator
import dev.kent.windbook.core.data.folders.FolderScanner
import dev.kent.windbook.core.data.folders.DocumentsTreeAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import dev.kent.windbook.core.data.importer.BookImporter
import dev.kent.windbook.core.data.libraryDataStore
import dev.kent.windbook.input.KeyEventDispatcher
import dev.kent.windbook.library.ContentResolverImportSourceFactory
import dev.kent.windbook.library.ImportSourceFactory
import dev.kent.windbook.reader.BookOpener
import dev.kent.windbook.reader.ReaderEngine
import dev.kent.windbook.reader.ReadiumMetadataExtractor
import dev.kent.windbook.reader.ReadiumServices
import dev.kent.windbook.settings.AppCompatLocaleController
import dev.kent.windbook.settings.AppInfo
import dev.kent.windbook.settings.AppLocaleController
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): WindBookDatabase = WindBookDatabase.create(context)

    @Provides @Singleton
    fun fileStore(@ApplicationContext context: Context): BookFileStore = BookFileStore(context.filesDir)

    @Provides @Singleton
    fun readium(@ApplicationContext context: Context): ReadiumServices = ReadiumServices(context)

    @Provides @Singleton
    fun bookOpener(readium: ReadiumServices): BookOpener = ReaderEngine(readium)

    @Provides @Singleton
    fun importer(db: WindBookDatabase, files: BookFileStore, readium: ReadiumServices): BookImporter =
        BookImporter(db.libraryDao(), files, ReadiumMetadataExtractor(readium))

    @Provides @Singleton
    fun libraryRepository(db: WindBookDatabase, files: BookFileStore): LibraryRepository =
        LibraryRepository(db.libraryDao(), files)

    @Provides @Singleton
    fun folderGrants(@ApplicationContext context: Context): FolderGrants = FolderGrants(context.contentResolver)

    @Provides @Singleton
    fun folders(db: WindBookDatabase, grants: FolderGrants): FolderRepository =
        FolderRepository(db, grants::persistedReadTrees, grants::releaseRead, grants::persistRead)

    @Provides @Singleton
    fun folderScans(folders: FolderRepository, @ApplicationContext context: Context): FolderScanCoordinator =
        FolderScanCoordinator(folders, FolderScanner(DocumentsTreeAccess(context.contentResolver)),
            CoroutineScope(SupervisorJob() + Dispatchers.IO))

    @Provides @Singleton
    fun bookSources(db: WindBookDatabase, folders: FolderRepository): BookSourcesRepository =
        BookSourcesRepository(db, folders)

    @Provides @Singleton
    fun displayPreferences(db: WindBookDatabase): DisplayPreferencesRepository =
        DisplayPreferencesRepository(db.bookPreferencesDao())

    @Provides @Singleton
    fun libraryPreferences(@ApplicationContext context: Context): LibraryPreferences =
        LibraryPreferences(context.libraryDataStore)

    @Provides @Singleton
    fun importSources(@ApplicationContext context: Context): ImportSourceFactory =
        ContentResolverImportSourceFactory(context.contentResolver)

    @Provides @Singleton
    fun keyEventDispatcher(): KeyEventDispatcher = KeyEventDispatcher()

    @Provides @Singleton
    fun appLocales(): AppLocaleController = AppCompatLocaleController()

    @Provides @Singleton
    fun appInfo(@ApplicationContext context: Context): AppInfo =
        AppInfo(context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty())
}
