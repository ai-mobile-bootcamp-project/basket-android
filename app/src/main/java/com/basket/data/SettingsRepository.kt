package com.basket.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "basket_settings")

data class UserSettings(
    /** "Move ticked items down": ticked items go to In basket at the bottom of each list. */
    val moveTickedDown: Boolean = true,
)

/** List behaviour preferences, stored with DataStore. The app language is stored by AppCompat. */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val MOVE_TICKED_DOWN = booleanPreferencesKey("move_ticked_down")
        val SAMPLE_DATA_IMPORTED = booleanPreferencesKey("sample_data_imported")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        UserSettings(moveTickedDown = p[Keys.MOVE_TICKED_DOWN] ?: true)
    }

    val moveTickedDown: Flow<Boolean> = settings.map { it.moveTickedDown }

    suspend fun setMoveTickedDown(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MOVE_TICKED_DOWN] = enabled }
    }

    suspend fun isSampleDataImported(): Boolean = context.dataStore.data.first()[Keys.SAMPLE_DATA_IMPORTED] ?: false

    suspend fun markSampleDataImported() {
        context.dataStore.edit { it[Keys.SAMPLE_DATA_IMPORTED] = true }
    }
}
