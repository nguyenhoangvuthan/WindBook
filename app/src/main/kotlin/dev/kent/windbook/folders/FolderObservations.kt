package dev.kent.windbook.folders

import dev.kent.windbook.core.data.folders.FolderEntry
import dev.kent.windbook.core.data.folders.FolderRepository
import dev.kent.windbook.core.data.folders.FolderScanCoordinator
import dev.kent.windbook.core.data.folders.ScanProgress
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Read-only inputs for the selected folder; scan ownership remains with the coordinator. */
interface FolderObservations {
    fun entries(folderId: String): Flow<List<FolderEntry>>
    fun progress(folderId: String): Flow<ScanProgress>
}

class RepositoryFolderObservations @Inject constructor(
    private val folders: FolderRepository,
    private val scans: FolderScanCoordinator,
) : FolderObservations {
    override fun entries(folderId: String) = folders.observeEntries(folderId, "", emptySet(), includeHidden = true)
    override fun progress(folderId: String) = scans.observe(folderId)
}
