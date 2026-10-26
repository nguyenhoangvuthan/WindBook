package dev.kent.windbook.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kent.windbook.core.data.LibraryRepository
import dev.kent.windbook.core.data.LibraryPreferences
import dev.kent.windbook.core.model.ScanOptions
import dev.kent.windbook.core.model.BookFormat
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dev.kent.windbook.core.data.files.BookFileStore
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AppInfo(val versionName: String)

data class SettingsUiState(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val bookCount: Int = 0,
    val totalBytes: Long = 0,
    val freeBytes: Long = 0,
    val versionName: String = "",
    val scanOptions: ScanOptions = ScanOptions(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    repository: LibraryRepository,
    private val files: BookFileStore,
    private val locales: AppLocaleController,
    appInfo: AppInfo,
    private val preferences: LibraryPreferences,
) : ViewModel() {

    private val scanCommands = Mutex()
    private val language = MutableStateFlow(locales.current())

    val state: StateFlow<SettingsUiState> = combine(repository.observeLibrary(), language, preferences.scanOptions) { all, lang, scanOptions ->
        SettingsUiState(
            language = lang,
            scanOptions = scanOptions,
            bookCount = all.size,
            totalBytes = all.sumOf { it.book.fileSize },
            freeBytes = files.usableBytes(),
            versionName = appInfo.versionName,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(language = language.value, versionName = appInfo.versionName),
    )

    fun setScanOptions(options: ScanOptions) = updateScanOptions { options }
    fun setScanRecursive(recursive: Boolean) = updateScanOptions { it.copy(recursive = recursive) }
    fun toggleScanExtension(format: BookFormat) = updateScanOptions {
        it.copy(extensions = if (format in it.extensions) it.extensions - format else it.extensions + format)
    }
    fun setScanExtensions(formats: Set<BookFormat>) = updateScanOptions { it.copy(extensions = formats) }

    private fun updateScanOptions(change: (ScanOptions) -> ScanOptions) = viewModelScope.launch {
        scanCommands.withLock { preferences.setScanOptions(change(preferences.scanOptions.first())) }
    }

    /** The language can also change from Android's "App languages" screen while this ViewModel lives on. */
    fun refreshLanguage() {
        language.value = locales.current()
    }

    fun setLanguage(choice: AppLanguage) {
        locales.set(choice)
        language.value = choice
    }
}
