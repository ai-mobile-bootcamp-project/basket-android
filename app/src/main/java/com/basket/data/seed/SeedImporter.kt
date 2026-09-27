package com.basket.data.seed

import android.content.Context
import androidx.room.withTransaction
import com.basket.data.AppClock
import com.basket.data.SettingsRepository
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
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    /** Imports the sample data on the very first launch. */
    suspend fun importOnFirstLaunch() {
        if (settings.isSampleDataImported()) return
        importSampleData()
        settings.markSampleDataImported()
    }

    /** Replaces all lists and categories with the sample data (Settings → Reset sample data). */
    suspend fun importSampleData() = withContext(Dispatchers.IO) {
        val seed = json.decodeFromString<SeedFile>(context.assets.open("sample-data.json").bufferedReader().use { it.readText() })
        db.withTransaction {
            db.itemDao().deleteAll()
            db.listDao().deleteAll()
            db.categoryDao().deleteAll()

            val categoryIds = seed.categories.mapIndexed { index, key ->
                key to db.categoryDao().insert(CategoryEntity(key = key, name = null, position = index))
            }.toMap()

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
}
