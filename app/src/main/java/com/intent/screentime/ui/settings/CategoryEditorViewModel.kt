package com.intent.screentime.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intent.screentime.data.local.entity.CategoryEntity
import com.intent.screentime.data.local.entity.CategoryKind
import com.intent.screentime.data.repository.UsageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryRow(
    val category: CategoryEntity,
    val appCount: Int,
)

data class CategoryEditorUiState(
    val loading: Boolean = true,
    val rows: List<CategoryRow> = emptyList(),
)

/**
 * The category editor.
 *
 * Kind is the only field with real consequences — it decides which side of the
 * production/consumption split an app lands on — so it is editable everywhere, while
 * colour is chosen from a fixed palette so the app's charts cannot be made unreadable.
 */
class CategoryEditorViewModel(
    private val repository: UsageRepository,
) : ViewModel() {

    private val dataVersion = MutableStateFlow(0)

    val state: StateFlow<CategoryEditorUiState> = combine(
        repository.observeCategories(),
        dataVersion,
    ) { categories, _ -> categories }
        .map { categories ->
            CategoryEditorUiState(
                loading = false,
                rows = categories.map { row ->
                    CategoryRow(row, repository.categoryAppCount(row.id))
                },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryEditorUiState())

    fun save(id: String, name: String, kind: CategoryKind, colorHex: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            repository.updateCategory(id, trimmed, kind, colorHex)
            dataVersion.value += 1
        }
    }

    fun add(name: String, kind: CategoryKind, colorHex: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            repository.addCategory(
                CategoryEntity(
                    id = "custom_${slug(trimmed)}_${System.currentTimeMillis()}",
                    name = trimmed,
                    kind = kind,
                    colorHex = colorHex,
                    isDefault = false,
                ),
            )
            dataVersion.value += 1
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.deleteCategory(id)
            dataVersion.value += 1
        }
    }

    private fun slug(name: String): String =
        name.lowercase().filter { it.isLetterOrDigit() }.take(24).ifEmpty { "category" }

    companion object {
        /** Fixed palette, so custom categories stay inside the visual system. */
        val PALETTE: List<String> = listOf(
            "#D93B12",
            "#C77A05",
            "#A62B84",
            "#6B5B95",
            "#2F7D7A",
            "#2C6BA8",
            "#5E8C2A",
            "#B03060",
        )
    }
}
