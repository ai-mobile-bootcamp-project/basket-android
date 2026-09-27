package com.basket.ui.item

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.basket.data.CategoriesRepository
import com.basket.data.ListsRepository
import com.basket.data.catalog.CatalogRepository
import com.basket.domain.Category
import com.basket.domain.Duplicates
import com.basket.domain.Grouping
import com.basket.domain.ListItem
import com.basket.domain.Names
import com.basket.domain.PriceInput
import com.basket.domain.PriceInputs
import com.basket.domain.Quantity
import com.basket.domain.Sorting
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToLong

/** What the user typed; kept in the SavedStateHandle so it survives rotation and process death. */
data class ItemFormFields(
    val name: String = "",
    val quantity: Int = Quantity.MIN,
    val priceText: String = "",
    /** 0 until the categories are known. */
    val categoryId: Long = 0L,
    val note: String = "",
)

data class ItemFormUiState(
    val isEdit: Boolean = false,
    val loaded: Boolean = false,
    val listName: String = "",
    val fields: ItemFormFields = ItemFormFields(),
    val price: PriceInput = PriceInput.Empty,
    /** Categories in aisle order, Other last. */
    val categories: List<Category> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val showNameError: Boolean = false,
    val saving: Boolean = false,
    val hasChanges: Boolean = false,
    /** The row that already has this name, while the "already on this list" dialog shows. */
    val duplicate: ListItem? = null,
    val showDiscard: Boolean = false,
) {
    val name: String get() = fields.name
    val quantity: Int get() = fields.quantity
    val nameValid: Boolean get() = Names.isValid(fields.name)
    val priceValid: Boolean get() = price !is PriceInput.Invalid
    val unitPriceCents: Long? get() = (price as? PriceInput.Valid)?.cents
    val selectedCategory: Category? get() = categories.firstOrNull { it.id == fields.categoryId }
    val canSave: Boolean get() = loaded && nameValid && priceValid
}

sealed interface ItemFormEvent {
    data class Added(val name: String) : ItemFormEvent
    data object Saved : ItemFormEvent
    data class Removed(val name: String, val undo: suspend () -> Unit) : ItemFormEvent
    data object NotFound : ItemFormEvent
}

@HiltViewModel
class ItemFormViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val listsRepository: ListsRepository,
    private val categoriesRepository: CategoriesRepository,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    val listId: Long = checkNotNull(savedStateHandle[ItemFormFragment.ARG_LIST_ID])
    private val itemId: Long = savedStateHandle[ItemFormFragment.ARG_ITEM_ID] ?: 0L
    val isEdit: Boolean get() = itemId != 0L

    private var locale: Locale = Locale.getDefault()
    private var started = false
    private var deleting = false

    private var original: ListItem? = null
    private var existingItems: List<ListItem> = emptyList()

    private val ready = MutableStateFlow(false)
    private val listName = MutableStateFlow("")
    private val suggestions = MutableStateFlow<List<String>>(emptyList())
    private val initial = MutableStateFlow(readFields(INITIAL))
    private val saving = MutableStateFlow(false)

    private val _events = Channel<ItemFormEvent>(Channel.BUFFERED)
    val events: Flow<ItemFormEvent> = _events.receiveAsFlow()

    private val fields: Flow<ItemFormFields> = combine(
        savedStateHandle.getStateFlow(CURRENT + NAME, ""),
        savedStateHandle.getStateFlow(CURRENT + QUANTITY, Quantity.MIN),
        savedStateHandle.getStateFlow(CURRENT + PRICE, ""),
        savedStateHandle.getStateFlow(CURRENT + CATEGORY, 0L),
        savedStateHandle.getStateFlow(CURRENT + NOTE, ""),
    ) { name, quantity, price, categoryId, note -> ItemFormFields(name, quantity, price, categoryId, note) }

    private data class Flags(val nameTouched: Boolean, val duplicateId: Long, val showDiscard: Boolean, val saving: Boolean)

    private val flags: Flow<Flags> = combine(
        savedStateHandle.getStateFlow(KEY_NAME_TOUCHED, false),
        savedStateHandle.getStateFlow(KEY_DUPLICATE_ID, 0L),
        savedStateHandle.getStateFlow(KEY_SHOW_DISCARD, false),
        saving,
    ) { nameTouched, duplicateId, showDiscard, isSaving -> Flags(nameTouched, duplicateId, showDiscard, isSaving) }

    private data class FormContext(
        val ready: Boolean,
        val listName: String,
        val categories: List<Category>,
        val suggestions: List<String>,
        val initial: ItemFormFields?,
    )

    private val context: Flow<FormContext> = combine(
        ready,
        listName,
        categoriesRepository.categories.map(Grouping::aisleOrder),
        suggestions,
        initial,
    ) { isReady, name, categories, names, initialFields -> FormContext(isReady, name, categories, names, initialFields) }

    val uiState: StateFlow<ItemFormUiState> = combine(fields, flags, context) { current, flags, context ->
        ItemFormUiState(
            isEdit = isEdit,
            loaded = context.ready,
            listName = context.listName,
            fields = current,
            price = parsePrice(current.priceText),
            categories = context.categories,
            suggestions = context.suggestions,
            showNameError = flags.nameTouched && !Names.isValid(current.name),
            saving = flags.saving,
            hasChanges = context.initial != null && current != context.initial,
            duplicate = existingItems.firstOrNull { it.id == flags.duplicateId },
            showDiscard = flags.showDiscard,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemFormUiState(isEdit = isEdit))

    /** The list, the item being edited and the name suggestions. */
    private val loading: Job

    init {
        loading = viewModelScope.launch {
            listName.value = listsRepository.getList(listId)?.name.orEmpty()
            existingItems = listsRepository.getItems(listId)
            original = if (isEdit) listsRepository.getItem(itemId) else null
            suggestions.value = (existingItems.map { it.name } + catalogRepository.products.value.orEmpty().map { it.title })
                .distinctBy { Sorting.key(it) }
                .sortedWith(Sorting.byName { it })
        }
    }

    /** Prefills the form the first time it opens; after rotation or process death the typed values stay. */
    fun start(locale: Locale) {
        this.locale = locale
        if (started) return
        started = true
        viewModelScope.launch {
            loading.join()
            val item = original
            if (isEdit && item == null) {
                _events.send(ItemFormEvent.NotFound)
                return@launch
            }
            if (savedStateHandle.get<Boolean>(KEY_LOADED) != true) {
                val prefill = if (item != null) {
                    ItemFormFields(
                        name = item.name,
                        quantity = item.quantity,
                        priceText = item.unitPriceCents?.let { PriceInputs.toFieldText(it, locale) }.orEmpty(),
                        categoryId = item.categoryId,
                        note = item.note.orEmpty(),
                    )
                } else {
                    ItemFormFields(categoryId = categoriesRepository.other().id)
                }
                writeFields(CURRENT, prefill)
                writeFields(INITIAL, prefill)
                savedStateHandle[KEY_LOADED] = true
            }
            initial.value = readFields(INITIAL)
            ready.value = true
        }
    }

    // ---------------------------------------------------------------- Fields

    fun onNameChanged(text: String) {
        if (text == savedStateHandle.get<String>(CURRENT + NAME).orEmpty()) return
        savedStateHandle[CURRENT + NAME] = text
        savedStateHandle[KEY_NAME_TOUCHED] = true
    }

    fun onNameFocusLost() {
        savedStateHandle[KEY_NAME_TOUCHED] = true
    }

    /** A catalog suggestion also picks the product's aisle, unless the user already chose one. */
    fun onSuggestionPicked(title: String) {
        if (isEdit) return
        val product = catalogRepository.products.value?.firstOrNull { Names.sameName(it.title, title) } ?: return
        if (currentFields().categoryId != initial.value?.categoryId) return
        viewModelScope.launch {
            categoriesRepository.forKey(product.categoryKey)?.let { savedStateHandle[CURRENT + CATEGORY] = it.id }
        }
    }

    fun stepQuantity(delta: Int) {
        val quantity = currentFields().quantity
        savedStateHandle[CURRENT + QUANTITY] = quantity + delta
    }

    fun onQuantityTyped(text: String) {
        val quantity = currentFields().quantity
        savedStateHandle[CURRENT + QUANTITY] = text.trim().toIntOrNull() ?: quantity
    }

    fun onPriceChanged(text: String) {
        savedStateHandle[CURRENT + PRICE] = text
    }

    fun onCategorySelected(categoryId: Long) {
        savedStateHandle[CURRENT + CATEGORY] = categoryId
    }

    fun onNoteChanged(text: String) {
        savedStateHandle[CURRENT + NOTE] = text
    }

    // ---------------------------------------------------------------- Dialogs

    fun showDiscard() {
        savedStateHandle[KEY_SHOW_DISCARD] = true
    }

    fun dismissDiscard() {
        savedStateHandle[KEY_SHOW_DISCARD] = false
    }

    fun dismissDuplicate() {
        savedStateHandle[KEY_DUPLICATE_ID] = 0L
    }

    // ---------------------------------------------------------------- Save and delete

    fun save() {
        val fields = currentFields()
        val price = parsePrice(fields.priceText)
        if (!ready.value || !Names.isValid(fields.name) || price is PriceInput.Invalid) {
            savedStateHandle[KEY_NAME_TOUCHED] = true
            return
        }
        viewModelScope.launch {
            saving.value = true
            try {
                val name = Names.clean(fields.name)
                val note = fields.note.trim().ifEmpty { null }
                val unitPriceCents = (price as? PriceInput.Valid)?.cents
                val categoryId = fields.categoryId.takeIf { it != 0L } ?: categoriesRepository.other().id
                val item = original
                if (item != null) {
                    listsRepository.addItem(
                        item.copy(
                            name = name,
                            quantity = fields.quantity,
                            unitPriceCents = unitPriceCents,
                            categoryId = categoryId,
                            note = note,
                        ),
                    )
                    _events.send(ItemFormEvent.Saved)
                } else {
                    val duplicate = Duplicates.find(existingItems, name)
                    if (duplicate != null) {
                        savedStateHandle[KEY_DUPLICATE_ID] = duplicate.id
                    } else {
                        listsRepository.addItem(
                            ListItem(
                                id = 0,
                                listId = listId,
                                name = name,
                                quantity = fields.quantity,
                                unitPriceCents = unitPriceCents,
                                categoryId = categoryId,
                                note = note,
                            ),
                        )
                        _events.send(ItemFormEvent.Added(name))
                    }
                }
            } finally {
                saving.value = false
            }
        }
    }

    /** "Add 2 more": raises the quantity of the row that already has this name. */
    fun addToDuplicate() {
        val duplicateId = savedStateHandle.get<Long>(KEY_DUPLICATE_ID) ?: 0L
        if (duplicateId == 0L) return
        savedStateHandle[KEY_DUPLICATE_ID] = 0L
        val added = currentFields().quantity
        viewModelScope.launch {
            saving.value = true
            try {
                val existing = listsRepository.getItem(duplicateId) ?: existingItems.firstOrNull { it.id == duplicateId }
                if (existing != null) {
                    listsRepository.updateItem(existing.copy(quantity = Quantity.add(existing.quantity, added)))
                    _events.send(ItemFormEvent.Added(existing.name))
                }
            } finally {
                saving.value = false
            }
        }
    }

    fun delete() {
        val item = original ?: return
        if (deleting) return
        deleting = true
        val repository = listsRepository
        viewModelScope.launch {
            repository.deleteItem(item)
            _events.send(ItemFormEvent.Removed(item.name) { repository.restoreItem(item) })
        }
    }

    // ---------------------------------------------------------------- Helpers

    private fun parsePrice(text: String): PriceInput {
        if (text.isBlank()) return PriceInput.Empty
        val value = text.trim().replace(",", "").toDoubleOrNull() ?: return PriceInput.Invalid
        val cents = (value * 100).roundToLong()
        return if (cents in PriceInputs.MIN_CENTS..PriceInputs.MAX_CENTS) PriceInput.Valid(cents) else PriceInput.Invalid
    }

    private fun currentFields(): ItemFormFields = readFields(CURRENT) ?: ItemFormFields()

    private fun readFields(prefix: String): ItemFormFields? {
        if (savedStateHandle.get<Boolean>(KEY_LOADED) != true) return null
        return ItemFormFields(
            name = savedStateHandle[prefix + NAME] ?: "",
            quantity = savedStateHandle[prefix + QUANTITY] ?: Quantity.MIN,
            priceText = savedStateHandle[prefix + PRICE] ?: "",
            categoryId = savedStateHandle[prefix + CATEGORY] ?: 0L,
            note = savedStateHandle[prefix + NOTE] ?: "",
        )
    }

    private fun writeFields(prefix: String, fields: ItemFormFields) {
        savedStateHandle[prefix + NAME] = fields.name
        savedStateHandle[prefix + QUANTITY] = fields.quantity
        savedStateHandle[prefix + PRICE] = fields.priceText
        savedStateHandle[prefix + CATEGORY] = fields.categoryId
        savedStateHandle[prefix + NOTE] = fields.note
    }

    private companion object {
        const val CURRENT = "form_"
        const val INITIAL = "initial_"
        const val NAME = "name"
        const val QUANTITY = "quantity"
        const val PRICE = "price"
        const val CATEGORY = "category"
        const val NOTE = "note"
        const val KEY_LOADED = "form_loaded"
        const val KEY_NAME_TOUCHED = "name_touched"
        const val KEY_DUPLICATE_ID = "duplicate_id"
        const val KEY_SHOW_DISCARD = "show_discard"
    }
}
