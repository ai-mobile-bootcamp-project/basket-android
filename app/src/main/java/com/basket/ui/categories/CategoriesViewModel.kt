package com.basket.ui.categories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.basket.data.CategoriesRepository
import com.basket.domain.Category
import com.basket.domain.Grouping
import com.basket.domain.Names
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row of the Categories screen: the category and how many items it has across all lists. */
data class CategoryRow(
    val category: Category,
    val itemCount: Int,
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoriesRepository: CategoriesRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Categories in aisle order ("Other" last), or null until the first load. */
    val rows: StateFlow<List<CategoryRow>?> =
        combine(categoriesRepository.categories, categoriesRepository.itemCounts) { categories, counts ->
            Grouping.aisleOrder(categories).map { CategoryRow(it, counts[it.id] ?: 0) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Category shown in the name dialog: [NEW_CATEGORY] for Add, a category id for Rename, null when closed. */
    val editingId: Long?
        get() = savedStateHandle[KEY_EDITING_ID]

    /** Text typed in the name dialog, kept across rotation. */
    var draftName: String
        get() = savedStateHandle[KEY_DRAFT_NAME] ?: ""
        set(value) {
            savedStateHandle[KEY_DRAFT_NAME] = value
        }

    fun startAdd() {
        savedStateHandle[KEY_EDITING_ID] = NEW_CATEGORY
        draftName = ""
    }

    fun startRename(category: Category, currentName: String) {
        savedStateHandle[KEY_EDITING_ID] = category.id
        draftName = currentName
    }

    fun finishEditing() {
        savedStateHandle.remove<Long>(KEY_EDITING_ID)
        savedStateHandle.remove<String>(KEY_DRAFT_NAME)
    }

    /** Saves the name dialog: adds a category before "Other", or renames the one being edited. */
    fun saveName(name: String) {
        val id = editingId ?: return
        val cleaned = Names.clean(name)
        if (!Names.isValid(cleaned)) return
        viewModelScope.launch {
            if (id == NEW_CATEGORY) {
                categoriesRepository.add(cleaned)
            } else {
                val category = categoriesRepository.get(id) ?: return@launch
                if (category.isOther || category.name == cleaned) return@launch
                categoriesRepository.rename(category, cleaned)
            }
        }
    }

    /** Moves [category] one place up ([offset] -1) or down (+1). "Other" stays last. */
    fun move(category: Category, offset: Int) {
        val ordered = rows.value?.map { it.category } ?: return
        val movable = ordered.filterNot { it.isOther }.toMutableList()
        val from = movable.indexOfFirst { it.id == category.id }
        val to = from + offset
        if (from < 0 || to !in movable.indices) return
        movable.add(to, movable.removeAt(from))
        reorder(movable.map { it.id } + ordered.filter { it.isOther }.map { it.id })
    }

    /** Saves the aisle order after a drag; [orderedIds] lists every category, "Other" last. */
    fun reorder(orderedIds: List<Long>) {
        viewModelScope.launch { categoriesRepository.reorder(orderedIds) }
    }

    fun delete(category: Category) {
        viewModelScope.launch { categoriesRepository.delete(category) }
    }

    fun restore(category: Category) {
        viewModelScope.launch { categoriesRepository.restore(category) }
    }

    companion object {
        const val NEW_CATEGORY = 0L
        private const val KEY_EDITING_ID = "editing_id"
        private const val KEY_DRAFT_NAME = "draft_name"
    }
}
