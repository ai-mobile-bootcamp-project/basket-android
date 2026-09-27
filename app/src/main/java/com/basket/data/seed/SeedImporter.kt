package com.basket.data.seed

import android.content.Context
import androidx.room.withTransaction
import com.basket.data.AppClock
import com.basket.data.db.BasketDatabase
import com.basket.data.db.CategoryEntity
import com.basket.data.db.ItemEntity
import com.basket.data.db.ListEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class SeedFile(
    val categories: List<String>,
    val lists: List<SeedList>,
)

@Serializable
private data class SeedList(val name: String, val items: List<SeedItem>)

@Serializable
private data class SeedItem(
    val name: String,
    val category: String,
    val quantity: Int,
    val priceCents: Long? = null,
    val note: String? = null,
    val ticked: Boolean = false,
    val catalogProductId: Int? = null,
)

/** Imports assets/sample-data.json (the lists from the design brief) into the database. */
@Singleton
class SeedImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: BasketDatabase,
    private val json: Json,
    private val clock: AppClock,
) {
    /** Makes sure the default aisles exist; they are needed even when the user starts without sample lists. */
    suspend fun ensureDefaultCategories() = withContext(Dispatchers.IO) {
        if (db.categoryDao().getAll().isNotEmpty()) return@withContext
        db.withTransaction { insertCategories(readSeed()) }
    }

    /** Replaces all lists and categories with the sample data (Welcome → Get started, Settings → Reset sample data). */
    suspend fun importSampleData() = withContext(Dispatchers.IO) {
        val seed = readSeed()
        db.withTransaction {
            db.itemDao().deleteAll()
            db.listDao().deleteAll()
            db.categoryDao().deleteAll()

            val categoryIds = insertCategories(seed)

            // The first list in the file is the most recently changed one.
            val now = clock.now()
            seed.lists.forEachIndexed { index, list ->
                val listId = db.listDao().insert(ListEntity(name = list.name, updatedAt = now - index * 60_000L))
                list.items.forEach { item ->
                    db.itemDao().insert(
                        ItemEntity(
                            listId = listId,
                            name = item.name,
                            quantity = item.quantity,
                            priceCents = item.priceCents,
                            categoryId = requireNotNull(categoryIds[item.category]) { "Unknown category ${item.category}" },
                            note = item.note,
                            ticked = item.ticked,
                            catalogProductId = item.catalogProductId,
                        ),
                    )
                }
            }
        }
    }

    private fun readSeed(): SeedFile =
        json.decodeFromString(context.assets.open("sample-data.json").bufferedReader().use { it.readText() })

    /** Inserts the default aisles in their default order; returns category key → id. */
    private suspend fun insertCategories(seed: SeedFile): Map<String, Long> =
        seed.categories.mapIndexed { index, key ->
            key to db.categoryDao().insert(CategoryEntity(key = key, name = null, position = index))
        }.toMap()
}
