package com.basket.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.basket.data.CategoriesRepository
import com.basket.data.ListsRepository
import com.basket.data.MessageCenter
import com.basket.data.UserMessage
import com.basket.domain.Category
import com.basket.domain.ListItem
import com.basket.domain.ListTotals
import com.basket.domain.Names
import com.basket.domain.Share
import com.basket.domain.ShareLabels
import com.basket.domain.Totals
import com.basket.ui.navigation.ListDetailDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListDetailUiState(
    val loading: Boolean = true,
    val listName: String = "",
    val items: List<ListItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val totals: ListTotals = Totals.of(emptyList()),
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

sealed interface ListDetailEvent {
    data class ItemRemoved(val name: String) : ListDetailEvent
    data class ShareText(val text: String) : ListDetailEvent
}

@HiltViewModel
class ListDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val listsRepository: ListsRepository,
    private val categoriesRepository: CategoriesRepository,
    private val messageCenter: MessageCenter,
) : ViewModel() {

    private val listId: Long = savedStateHandle.toRoute<ListDetailDestination>().listId

    private val _items = MutableStateFlow<List<ListItem>>(emptyList())
    val items: StateFlow<List<ListItem>> = _items.asStateFlow()

    private val loaded = MutableStateFlow(false)

    private val _events = Channel<ListDetailEvent>(Channel.BUFFERED)
    val events: Flow<ListDetailEvent> = _events.receiveAsFlow()

    /** Messages posted by other screens (e.g. "Milk added" from the item form). */
    val messages: Flow<UserMessage> = messageCenter.messages

    init {
        viewModelScope.launch {
            listsRepository.observeItems(listId).collect { rows ->
                _items.value = rows.map { it.copy(unitPriceCents = it.unitPriceCents ?: 0L) }
                loaded.value = true
            }
        }
    }

    val uiState: StateFlow<ListDetailUiState> = combine(
        listsRepository.observeList(listId),
        items,
        categoriesRepository.categories,
        loaded,
    ) { list, items, categories, loaded ->
        ListDetailUiState(
            loading = !loaded,
            listName = list?.name.orEmpty(),
            items = items,
            categories = categories,
            totals = Totals.of(items),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListDetailUiState())

    fun setTicked(item: ListItem, ticked: Boolean) {
        viewModelScope.launch { listsRepository.setTicked(item, ticked) }
    }

    fun removeAt(index: Int) {
        val item = items.value.getOrNull(index) ?: return
        viewModelScope.launch {
            listsRepository.deleteItem(item)
            _events.send(ListDetailEvent.ItemRemoved(item.name))
        }
    }

    fun undoRemove() {}

    /** Removes one item by id and offers Undo through the message center. */
    fun removeItem(itemId: Long, message: String) {
        viewModelScope.launch {
            val item = listsRepository.getItem(itemId) ?: return@launch
            listsRepository.deleteItem(item)
            messageCenter.post(UserMessage(message) { listsRepository.restoreItem(item) })
        }
    }

    fun undo(message: UserMessage) = messageCenter.undo(message)

    fun rename(name: String) {
        if (!Names.isValid(name)) return
        viewModelScope.launch { listsRepository.renameList(listId, Names.clean(name)) }
    }

    fun clearBasket() {
        viewModelScope.launch { listsRepository.clearItems(listId) }
    }

    /** Finish shopping → "Keep the rest on this list": the bought items go at once, with Undo. */
    fun keepRest(message: (Int) -> String) {
        viewModelScope.launch {
            val bought = listsRepository.getItems(listId).filter { it.ticked }
            if (bought.isEmpty()) return@launch
            listsRepository.removeTicked(listId)
            messageCenter.post(
                UserMessage(message(bought.size)) { listsRepository.restoreItems(bought) },
            )
        }
    }

    fun share(labels: ShareLabels, money: (Long) -> String) {
        viewModelScope.launch {
            val listId = items.value.first().listId
            val list = listsRepository.getList(listId) ?: return@launch
            val text = Share.text(list.name, items.value, categoriesRepository.getAll(), labels, money)
            _events.send(ListDetailEvent.ShareText(text))
        }
    }
}
