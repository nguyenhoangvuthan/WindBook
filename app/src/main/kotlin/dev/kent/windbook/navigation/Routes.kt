package dev.kent.windbook.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object LibraryKey : NavKey

@Serializable
data class ReaderKey(val bookId: String) : NavKey
