package com.basket.data

import androidx.room.withTransaction
import com.basket.data.db.BasketDatabase
import com.basket.data.db.CategoryEntity
import com.basket.data.db.ListEntity
import com.basket.data.db.toDomain
import com.basket.data.db.toEntity
import com.basket.domain.Category
import com.basket.domain.CategoryKey
import com.basket.domain.ListItem
import com.basket.domain.Names
import com.basket.domain.ShoppingList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Wall clock, injectable so tests can pin it. */
@Singleton
class AppClock @Inject constructor() {
    fun now(): Long = System.currentTimeMillis()
}

/** Shopping lists and their items. Every change to an item also marks its list as changed. */
@Singleton
class ListsRepository @Inject constructor(
    private val db: BasketDatabase,
    private val clock: AppClock,
) {
    private val listDao = db.listDao()
    private val itemDao = db.itemDao()

    /** All lists, most recently changed first. */
    val lists: Flow<List<ShoppingList>> = listDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** Items of every list (for the Lists screen cards). */
    val allItems: Flow<List<ListItem>> = itemDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeList(id: Long): Flow<ShoppingList?> = listDao.observe(id).map { it?.toDomain() }

    fun observeItems(listId: Long): Flow<List<ListItem>> =
        itemDao.observeForList(listId).map { rows -> rows.map { it.toDomain() } }

    suspend fun getList(id: Long): ShoppingList? = listDao.get(id)?.toDomain()

    suspend fun getItems(listId: Long): List<ListItem> = itemDao.getForList(listId).map { it.toDomain() }

    suspend fun getItem(id: Long): ListItem? = itemDao.get(id)?.toDomain()

    // ---------------------------------------------------------------- Lists

    /** Creates an empty list and returns its id. */
    suspend fun createList(name: String): Long =
        listDao.insert(ListEntity(name = Names.clean(name), updatedAt = clock.now()))

    suspend fun renameList(id: Long, name: String) = listDao.rename(id, Names.clean(name), clock.now())

    /** Copies a list with every item unticked; the copy is the most recently changed list, so it shows first. */
    suspend fun duplicateList(id: Long, copyName: String): Long = db.withTransaction {
        val newId = listDao.insert(ListEntity(name = copyName, updatedAt = clock.now()))
        val items = itemDao.getForList(id).map { it.copy(id = 0, listId = newId, ticked = false) }
        items.forEach { itemDao.insert(it) }
        newId
    }

    suspend fun deleteList(id: Long) = listDao.delete(id)

    // ---------------------------------------------------------------- Items

    /** Inserts a new row (the id of [item] is ignored) and returns its id. */
    suspend fun addItem(item: ListItem): Long = db.withTransaction {
        val id = itemDao.insert(item.copy(id = 0).toEntity())
        listDao.touch(item.listId, clock.now())
        id
    }

    /** Updates the row with the id of [item]. */
    suspend fun updateItem(item: ListItem) = db.withTransaction {
        itemDao.update(item.toEntity())
        listDao.touch(item.listId, clock.now())
    }

    /** Puts a removed item back with its original id, tick and values. */
    suspend fun restoreItem(item: ListItem) = db.withTransaction {
        itemDao.insertAll(listOf(item.toEntity()))
        listDao.touch(item.listId, clock.now())
    }

    suspend fun deleteItem(item: ListItem) = db.withTransaction {
        itemDao.delete(item.id)
        listDao.touch(item.listId, clock.now())
    }

    suspend fun setTicked(item: ListItem, ticked: Boolean) = db.withTransaction {
        itemDao.setTicked(item.id, ticked)
        listDao.touch(item.listId, clock.now())
    }

    suspend fun setQuantity(item: ListItem, quantity: Int) = db.withTransaction {
        itemDao.setQuantity(item.id, quantity)
        listDao.touch(item.listId, clock.now())
    }

    /** Removes the ticked items of a list (Finish shopping → keep the rest). */
    suspend fun removeTicked(listId: Long) = db.withTransaction {
        itemDao.deleteTicked(listId)
        listDao.touch(listId, clock.now())
    }

    /** Removes every item of a list. */
    suspend fun clearItems(listId: Long) = db.withTransaction {
        itemDao.deleteForList(listId)
        listDao.touch(listId, clock.now())
    }
}

/** The aisle order from the Categories screen. */
@Singleton
class CategoriesRepository @Inject constructor(
    private val db: BasketDatabase,
) {
    private val categoryDao = db.categoryDao()
    private val itemDao = db.itemDao()

    /** Categories ordered by position. */
    val categories: Flow<List<Category>> = categoryDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** Item count per category id, across all lists. */
    val itemCounts: Flow<Map<Long, Int>> =
        itemDao.observeCountsByCategory().map { rows -> rows.associate { it.categoryId to it.count } }

    suspend fun getAll(): List<Category> = categoryDao.getAll().map { it.toDomain() }

    suspend fun get(id: Long): Category? = categoryDao.get(id)?.toDomain()

    suspend fun forKey(key: CategoryKey): Category? = categoryDao.getByKey(key.name)?.toDomain()

    /** The Other category, which every list falls back to. */
    suspend fun other(): Category = requireNotNull(forKey(CategoryKey.OTHER)) { "Other category missing" }

    /** Adds a category just before Other and returns its id. */
    suspend fun add(name: String): Long = db.withTransaction {
        val all = categoryDao.getAll()
        val position = (all.filter { it.key != CategoryKey.OTHER.name }.maxOfOrNull { it.position } ?: -1) + 1
        val other = all.firstOrNull { it.key == CategoryKey.OTHER.name }
        if (other != null && other.position <= position) categoryDao.update(other.copy(position = position + 1))
        categoryDao.insert(CategoryEntity(key = null, name = Names.clean(name), position = position))
    }

    suspend fun rename(category: Category, name: String) =
        categoryDao.update(category.toEntity().copy(name = Names.clean(name)))

    /** Saves a new aisle order; [orderedIds] lists every category, Other last. */
    suspend fun reorder(orderedIds: List<Long>) = db.withTransaction {
        val byId = categoryDao.getAll().associateBy { it.id }
        categoryDao.updateAll(orderedIds.mapIndexedNotNull { index, id -> byId[id]?.copy(position = index) })
    }

    suspend fun delete(category: Category) = categoryDao.delete(category.toEntity())

    /** Puts a deleted category back with its id and position. */
    suspend fun restore(category: Category) = categoryDao.insert(category.toEntity())
}
