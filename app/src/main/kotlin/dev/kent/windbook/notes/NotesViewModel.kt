package dev.kent.windbook.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import dev.kent.windbook.core.data.AnnotationRepository
import dev.kent.windbook.core.data.db.WindBookDatabase
import dev.kent.windbook.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AnnotationDraft(val bookId: String, val fingerprint: String, val anchor: PdfTextAnchor?, val id: String? = null,
    val color: AnnotationColor = AnnotationColor.GOLD, val body: String = "", val originalBody: String = body,
    val quote: String = anchor?.quote.orEmpty())

/** Owns drafts independently of the page composition, so rotation and failed writes retain input. */
@HiltViewModel
class NotesViewModel @Inject constructor(db: WindBookDatabase): ViewModel() {
    private val repository=AnnotationRepository(db)
    val annotations=repository.observe().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    private val _editor=MutableStateFlow<AnnotationDraft?>(null)
    val editor=_editor.asStateFlow()
    private val _failed=MutableStateFlow(false)
    val failed=_failed.asStateFlow()
    private val _saving=MutableStateFlow(false)
    val saving=_saving.asStateFlow()
    private val _editorVisible=MutableStateFlow(false)
    val editorVisible=_editorVisible.asStateFlow()
    private val _saved=MutableStateFlow(0L)
    val saved=_saved.asStateFlow()

    suspend fun find(id: String): PdfTextAnnotation? = repository.find(id)

    fun begin(bookId: String,fingerprint: String,anchor: PdfTextAnchor,showEditor: Boolean=true) {
        if(_saving.value) return
        _editor.value=AnnotationDraft(bookId,fingerprint,anchor)
        _failed.value=false; _editorVisible.value=showEditor
    }
    fun edit(annotation: PdfTextAnnotation,showEditor: Boolean=true) {
        if(_saving.value) return
        _editor.value=AnnotationDraft(annotation.bookId,annotation.fingerprint,annotation.anchor,annotation.id,annotation.color,
            annotation.body.orEmpty(),quote=annotation.quote)
        _failed.value=false; _editorVisible.value=showEditor
    }
    fun changeBody(body: String) { if(!_saving.value) _editor.update { it?.copy(body=body.take(65_536)) } }
    fun save() { if(_editor.value?.body?.isNotBlank()==true) persist(_editor.value!!.color,allowHighlight=false) }
    fun highlight(color: AnnotationColor) = persist(color,allowHighlight=true)
    private fun persist(color: AnnotationColor,allowHighlight: Boolean) {
        val draft=_editor.value ?: return
        if(_saving.value) return
        val body=draft.body.trim().takeIf { it.isNotEmpty() }
        if(!allowHighlight && body==null) return
        _saving.value=true; _failed.value=false
        viewModelScope.launch {
            try {
                if(draft.id==null) repository.save(draft.bookId,draft.fingerprint,requireNotNull(draft.anchor),color,body)
                else repository.update(draft.id,color,body)
                _editor.value=null; _editorVisible.value=false; _saved.value++
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { _failed.value=true }
            finally { _saving.value=false }
        }
    }
    fun delete(id: String) {
        if(_saving.value) return
        _saving.value=true; _failed.value=false
        viewModelScope.launch {
            try {
                repository.delete(id)
                if(_editor.value?.id==id) { _editor.value=null; _editorVisible.value=false }
                _saved.value++
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { _failed.value=true }
            finally { _saving.value=false }
        }
    }
    fun cancel() { if(!_saving.value) { _editor.value=null; _editorVisible.value=false; _failed.value=false } }
}
