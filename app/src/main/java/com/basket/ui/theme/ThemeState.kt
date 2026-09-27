package com.basket.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The theme picked in Settings. */
object ThemeState {
    var mode by mutableStateOf(ThemeMode.System)
}
