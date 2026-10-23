package dev.kent.windbook.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object LibraryKey : NavKey

@Serializable
data class ReaderKey(val bookId: String) : NavKey

@Serializable
data object ReadingKey : NavKey

@Serializable
data object NotesKey : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data class FolderKey(val folderId: String? = null) : NavKey
