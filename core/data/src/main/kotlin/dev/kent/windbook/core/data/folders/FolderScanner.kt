package dev.kent.windbook.core.data.folders

import android.net.Uri
import android.provider.DocumentsContract
import dev.kent.windbook.core.model.DocumentKey
import dev.kent.windbook.core.model.ScanOptions
import dev.kent.windbook.core.model.accepts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

sealed interface ScanEvent {
    data class Found(val document: TreeDocument, val relativePath: String, val parentKey: DocumentKey) : ScanEvent
    data class Examined(val count: Long) : ScanEvent
    data class Unreadable(val key: DocumentKey) : ScanEvent
    data class DirectoryFailed(val key: DocumentKey, val reason: String) : ScanEvent
    data class Finished(val complete: Boolean) : ScanEvent
}

class FolderScanner(private val access: TreeAccess) {
    fun scan(treeUri: String, root: DocumentKey, options: ScanOptions): Flow<ScanEvent> = flow {
        val tree = try {
            requireTreeUri(treeUri).also {
                require(root.authority == it.authority && root.documentId == DocumentsContract.getTreeDocumentId(it)) {
                    "Scan root differs from granted tree"
                }
            }
        } catch (error: IllegalArgumentException) {
            emit(ScanEvent.DirectoryFailed(root, error.javaClass.simpleName))
            emit(ScanEvent.Finished(false))
            return@flow
        }
        val pending = ArrayDeque<Pair<DocumentKey, String>>()
        val visited = mutableSetOf(root)
        pending.add(root to "")
        var examined = 0L
        var complete = true
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (directory, path) = pending.removeLast()
            var failure: Throwable? = null
            access.children(treeUri, directory).map { document ->
                require(document.key.authority == tree.authority)
                val documentUri = Uri.parse(document.uri)
                require(documentUri.scheme == "content" && documentUri.authority == tree.authority &&
                    DocumentsContract.getTreeDocumentId(documentUri) == DocumentsContract.getTreeDocumentId(tree) &&
                    DocumentsContract.getDocumentId(documentUri) == document.key.documentId)
                document
            }.catch { error ->
                if (error is CancellationException || error !is Exception) throw error
                failure = error
            }.collect { document ->
                currentCoroutineContext().ensureActive()
                emit(ScanEvent.Examined(++examined))
                if (document.name.isBlank()) {
                    complete = false
                    emit(ScanEvent.Unreadable(document.key))
                } else {
                    val relativePath = if (path.isEmpty()) document.name else "$path/${document.name}"
                    if (document.isDirectory) {
                        if (options.recursive && visited.add(document.key)) pending.add(document.key to relativePath)
                    } else if (options.accepts(document.name)) {
                        emit(ScanEvent.Found(document, relativePath, directory))
                    }
                }
            }
            failure?.let {
                complete = false
                emit(ScanEvent.DirectoryFailed(directory, it.javaClass.simpleName))
            }
        }
        emit(ScanEvent.Finished(complete))
    }
}
