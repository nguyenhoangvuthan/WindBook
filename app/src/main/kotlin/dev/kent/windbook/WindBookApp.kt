package dev.kent.windbook

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.kent.windbook.core.data.folders.FolderRepository
import dev.kent.windbook.reader.source.SourceAccess
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class WindBookApp : Application() {
    @Inject lateinit var folders: FolderRepository
    @Inject lateinit var sources: SourceAccess
    @Inject lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        startStorageMaintenance(applicationScope, folders::initialize, sources::initialize)
    }
}

/** Both cleanups run at application startup, independently of navigation and each other. */
internal fun startStorageMaintenance(
    scope: CoroutineScope,
    initializeFolders: suspend () -> Unit,
    initializeSources: suspend () -> Unit,
) {
    scope.launch { initializeFolders() }
    scope.launch { initializeSources() }
}
