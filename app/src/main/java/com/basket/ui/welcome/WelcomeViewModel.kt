package com.basket.ui.welcome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.basket.data.SettingsRepository
import com.basket.data.seed.SeedImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val seedImporter: SeedImporter,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)

    /** True while a choice is being applied; further taps are ignored. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _done = MutableStateFlow(false)

    /** True once the choice is saved; the screen then opens Lists (also after a configuration change). */
    val done: StateFlow<Boolean> = _done.asStateFlow()

    /** Get started: the sample lists, then Lists. */
    fun getStarted() = finish { seedImporter.importSampleData() }

    /** Start without sample lists: only the default aisles, then an empty Lists screen. */
    fun startEmpty() = finish { seedImporter.ensureDefaultCategories() }

    private fun finish(prepare: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            prepare()
            settingsRepository.markOnboardingDone()
            _done.value = true
        }
    }
}

/** Screen 0. [onDone] opens Lists and removes Welcome from the back stack. */
@Composable
fun WelcomeRoute(onDone: () -> Unit, viewModel: WelcomeViewModel = hiltViewModel()) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    LaunchedEffect(done) {
        if (done) onDone()
    }
    WelcomeScreen(
        onGetStarted = { if (!busy) viewModel.getStarted() },
        onStartEmpty = { if (!busy) viewModel.startEmpty() },
    )
}
