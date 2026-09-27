package com.basket

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.basket.data.SettingsRepository
import com.basket.data.seed.SeedImporter
import com.basket.ui.navigation.BasketNavHost
import com.basket.ui.navigation.BasketNavigator
import com.basket.ui.navigation.CategoriesDestination
import com.basket.ui.navigation.ListDetailDestination
import com.basket.ui.navigation.ListsDestination
import com.basket.ui.navigation.WelcomeDestination
import com.basket.ui.theme.BasketTheme
import com.basket.ui.theme.ThemeState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), BasketNavigator {

    @Inject lateinit var seedImporter: SeedImporter
    @Inject lateinit var settingsRepository: SettingsRepository

    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val onboardingDone = runBlocking {
            if (savedInstanceState == null) seedImporter.ensureDefaultCategories()
            settingsRepository.isOnboardingDone()
        }
        setContent {
            BasketTheme(mode = ThemeState.mode) {
                val nav = rememberNavController()
                navController = nav
                BasketNavHost(nav, startDestination = if (onboardingDone) ListsDestination else WelcomeDestination)
            }
        }
    }

    // ---------------------------------------------------------------- BasketNavigator (XML screens)

    override fun navigateBack() {
        navController?.popBackStack()
    }

    override fun openListDetail(listId: Long) {
        navController?.navigate(ListDetailDestination(listId))
    }

    override fun openCategories() {
        navController?.navigate(CategoriesDestination)
    }

    override fun backToLists() {
        navController?.popBackStack(ListsDestination, inclusive = false)
    }
}
