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
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

        /** Set by 1.0.0 after importing the sample data on first launch; such installs have seen the app already. */
        val SAMPLE_DATA_IMPORTED = booleanPreferencesKey("sample_data_imported")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        UserSettings(moveTickedDown = p[Keys.MOVE_TICKED_DOWN] ?: true)
    }

    val moveTickedDown: Flow<Boolean> = settings.map { it.moveTickedDown }

    suspend fun setMoveTickedDown(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MOVE_TICKED_DOWN] = enabled }
    }

    /** True once the user has left the Welcome screen (either button). Reset sample data keeps it. */
    suspend fun isOnboardingDone(): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[Keys.ONBOARDING_DONE] ?: prefs[Keys.SAMPLE_DATA_IMPORTED] ?: false
    }

    suspend fun markOnboardingDone() {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    }
}
