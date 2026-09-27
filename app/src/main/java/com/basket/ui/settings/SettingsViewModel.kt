package com.basket.ui.settings

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.basket.R
import com.basket.data.SettingsRepository
import com.basket.data.catalog.CatalogRepository
import com.basket.data.seed.SeedImporter
import com.basket.ui.theme.ThemeMode
import com.basket.ui.theme.ThemeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

sealed interface SettingsEvent {
    /** A snackbar message: "Catalog updated" or why the download failed. */
    data class Message(@StringRes val text: Int) : SettingsEvent

    /** Sample data was imported again; go back to Lists. */
    data object ResetDone : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val catalogRepository: CatalogRepository,
    private val seedImporter: SeedImporter,
) : ViewModel() {

    /** "Move ticked items down", or null until DataStore has been read. */
    val moveTickedDown: StateFlow<Boolean?> =
        settingsRepository.moveTickedDown.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Time of the last successful catalog download, or null when it was never downloaded. */
    val catalogLastUpdated: StateFlow<Long?> = catalogRepository.lastUpdated

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _resetting = MutableStateFlow(false)
    val resetting: StateFlow<Boolean> = _resetting.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    val themeMode: ThemeMode get() = ThemeState.mode

    fun setTheme(mode: ThemeMode) {
        ThemeState.mode = mode
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                ThemeMode.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                ThemeMode.Light -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.Dark -> AppCompatDelegate.MODE_NIGHT_YES
            },
        )
    }

    fun setMoveTickedDown(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setMoveTickedDown(enabled) }
    }

    /** Downloads the catalog again. A second tap while it runs does nothing. */
    fun refreshCatalog() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            val message = try {
                catalogRepository.refresh()
                R.string.catalog_updated
            } catch (e: IOException) {
                R.string.error_offline_message
            } catch (e: HttpException) {
                R.string.error_server_message
            } catch (e: SerializationException) {
                R.string.error_server_message
            } finally {
                _refreshing.value = false
            }
            _events.send(SettingsEvent.Message(message))
        }
    }

    /** Replaces every list and category with the sample data. */
    fun resetSampleData() {
        if (_resetting.value) return
        _resetting.value = true
        viewModelScope.launch {
            try {
                seedImporter.importSampleData()
            } finally {
                _resetting.value = false
            }
            _events.send(SettingsEvent.ResetDone)
        }
    }
}
