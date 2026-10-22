package dev.kent.windbook.core.data.folders

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import dev.kent.windbook.core.model.DocumentKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** An empty name represents a provider row with no usable display name; it is still examined. */
data class TreeDocument(
    val key: DocumentKey,
    val uri: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long?,
    val modifiedAt: Long?,
)

fun interface TreeAccess {
    fun children(treeUri: String, directory: DocumentKey): Flow<TreeDocument>
}

/** Queries metadata only. No document stream, descriptor or writable operation is acquired. */
class DocumentsTreeAccess(
    private val resolver: ContentResolver,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TreeAccess {
    override fun children(treeUri: String, directory: DocumentKey): Flow<TreeDocument> = flow<Result<TreeDocument>> {
        val tree = requireTreeUri(treeUri)
        requireMember(tree, directory)
        currentCoroutineContext().ensureActive()
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, directory.documentId)
        val cursor = resolver.query(uri, PROJECTION, null, null)
            ?: throw IllegalStateException("Provider returned no cursor")
        cursor.use {
            val id = it.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
            val name = it.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
            val mime = it.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
            while (true) {
                currentCoroutineContext().ensureActive()
                if (!it.moveToNext()) break
                currentCoroutineContext().ensureActive()
                val key = DocumentKey(directory.authority,
                    it.getString(id)?.takeIf(String::isNotEmpty)
                        ?: throw IllegalStateException("Provider returned no document ID"))
                requireMember(tree, key)
                emit(Result.success(TreeDocument(key, DocumentsContract.buildDocumentUriUsingTree(tree, key.documentId).toString(),
                    it.getString(name).orEmpty(), it.getString(mime) == Document.MIME_TYPE_DIR,
                    it.optionalLong(Document.COLUMN_SIZE), it.optionalLong(Document.COLUMN_LAST_MODIFIED))))
            }
            if (it.extras.getBoolean(DocumentsContract.EXTRA_LOADING, false) ||
                it.extras.getString(DocumentsContract.EXTRA_ERROR) != null) {
                throw IllegalStateException("Provider returned an incomplete directory")
            }
        }
    }.catch { error ->
        if (error is CancellationException || error !is Exception) throw error
        // Carry provider failures across the dispatcher without cancelling queued metadata rows.
        emit(Result.failure(error))
    }.flowOn(dispatcher).buffer(0).map { it.getOrThrow() }

    private fun requireMember(tree: Uri, key: DocumentKey) {
        if (key.authority != tree.authority) throw SecurityException("Document authority differs from tree")
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        if (key.documentId != rootId && !DocumentsContract.isChildDocument(resolver,
                DocumentsContract.buildDocumentUriUsingTree(tree, rootId),
                DocumentsContract.buildDocumentUriUsingTree(tree, key.documentId))) {
            throw SecurityException("Document is outside granted tree")
        }
    }

    private fun Cursor.optionalLong(column: String): Long? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getLong(index)
    }

    private companion object {
        val PROJECTION = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)
    }
}

internal fun requireTreeUri(value: String): Uri = Uri.parse(value).also {
    require(it.scheme == ContentResolver.SCHEME_CONTENT && !it.authority.isNullOrBlank() &&
        it.pathSegments.size == 2 && DocumentsContract.isTreeUri(it) &&
        DocumentsContract.getTreeDocumentId(it).isNotEmpty() && it.query == null && it.fragment == null) {
        "A granted content tree URI is required"
    }
}
