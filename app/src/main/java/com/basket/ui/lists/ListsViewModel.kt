package com.basket.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.basket.data.ListsRepository
import com.basket.domain.ListItem
import com.basket.domain.Names
import com.basket.domain.ShoppingList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One card on the Lists screen. */
data class ListCardUi(
    val id: Long,
    val name: String,
    val itemCount: Int,
    val tickedCount: Int,
    /** 0–100. */
    val percentDone: Int,
    val totalCents: Long,
    val withoutPriceCount: Int,
) {
    val isEmpty: Boolean get() = itemCount == 0
    val isDone: Boolean get() = itemCount > 0 && tickedCount == itemCount
}

data class ListsUiState(
    val loading: Boolean = true,
    val cards: List<ListCardUi> = emptyList(),
)

@HiltViewModel
class ListsViewModel @Inject constructor(
    private val listsRepository: ListsRepository,
) : ViewModel() {

    val uiState: StateFlow<ListsUiState> = combine(
        listsRepository.lists,
        listsRepository.allItems,
    ) { lists, allItems ->
        val itemsByList = allItems.groupBy { it.listId }
        ListsUiState(
            loading = false,
            cards = lists.map { list -> list.toCard(itemsByList[list.id].orEmpty()) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListsUiState())

    private val _openList = Channel<Long>(Channel.BUFFERED)

    /** The id of a list that was just created; the screen opens it. */
    val openList: Flow<Long> = _openList.receiveAsFlow()

    private var creating = false

    fun createList(name: String) {
        if (creating || !Names.isValid(name)) return
        creating = true
        viewModelScope.launch {
            try {
                val id = listsRepository.createList(Names.clean(name))
                _openList.send(id)
            } finally {
                creating = false
            }
        }
    }

    fun renameList(id: Long, name: String) {
        if (!Names.isValid(name)) return
        viewModelScope.launch { listsRepository.renameList(id, Names.clean(name)) }
    }

    fun duplicateList(id: Long, copyName: String) {
        viewModelScope.launch { listsRepository.duplicateList(id, copyName) }
    }

    fun deleteList(id: Long) {
        viewModelScope.launch { listsRepository.deleteList(id) }
    }

    private fun ShoppingList.toCard(items: List<ListItem>): ListCardUi {
        val itemCount = items.size
        val tickedCount = items.count { it.ticked }
        return ListCardUi(
            id = id,
            name = name,
            itemCount = itemCount,
            tickedCount = tickedCount,
            percentDone = if (itemCount == 0) 0 else tickedCount * 100 / itemCount,
            totalCents = items.sumOf { it.unitPriceCents ?: 0L },
            withoutPriceCount = items.count { it.unitPriceCents == null },
        )
    }
}
