package dev.kent.windbook.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kent.windbook.core.data.LibraryRepository
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
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    repository: LibraryRepository,
    private val files: BookFileStore,
    private val locales: AppLocaleController,
    appInfo: AppInfo,
) : ViewModel() {

    private val language = MutableStateFlow(locales.current())

    val state: StateFlow<SettingsUiState> = combine(repository.observeLibrary(), language) { all, lang ->
        SettingsUiState(
            language = lang,
            bookCount = all.size,
            totalBytes = all.sumOf { it.book.fileSize },
            freeBytes = files.usableBytes(),
            versionName = appInfo.versionName,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(versionName = appInfo.versionName))

    fun setLanguage(choice: AppLanguage) {
        locales.set(choice)
        language.value = choice
    }
}
