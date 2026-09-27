package com.basket.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.basket.BuildConfig
import com.basket.R
import com.basket.domain.Locales
import com.basket.databinding.FragmentSettingsBinding
import com.basket.ui.common.formatShortDateTime
import com.basket.ui.navigation.BasketNavigator
import com.basket.ui.theme.ThemeMode
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()
    private val navigator get() = requireActivity() as BasketNavigator

    private var openDialog: String? = null
    private var dialog: AlertDialog? = null
    private var switchInitialised = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { navigator.navigateBack() }

        binding.themeValue.text = getString(themeLabel(viewModel.themeMode))
        binding.themeRow.setOnClickListener { showThemeDialog() }

        binding.languageValue.text = getString(languageLabel(currentLanguage()))
        binding.languageRow.setOnClickListener { showLanguageDialog() }

        binding.moveTickedRow.setOnClickListener {
            val current = viewModel.moveTickedDown.value ?: return@setOnClickListener
            binding.moveTickedSwitch.isChecked = !current
            viewModel.setMoveTickedDown(!current)
        }
        ViewCompat.setAccessibilityDelegate(binding.moveTickedRow, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Switch::class.java.name
                info.isCheckable = true
                info.isChecked = _binding?.moveTickedSwitch?.isChecked == true
            }
        })

        binding.categoriesRow.setOnClickListener { navigator.openCategories() }
        binding.refreshRow.setOnClickListener { viewModel.refreshCatalog() }
        binding.resetRow.setOnClickListener { showResetDialog() }

        binding.versionText.text = getString(R.string.version, BuildConfig.VERSION_NAME)
        binding.dataCreditRow.setOnClickListener { openDataSource() }

        when (savedInstanceState?.getString(KEY_OPEN_DIALOG)) {
            DIALOG_THEME -> showThemeDialog()
            DIALOG_LANGUAGE -> showLanguageDialog()
            DIALOG_RESET -> showResetDialog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.moveTickedDown.collect(::renderMoveTicked) }
                launch { viewModel.catalogLastUpdated.collect(::renderLastUpdated) }
                launch { viewModel.refreshing.collect(::renderRefreshing) }
                launch { viewModel.resetting.collect { binding.resetRow.isEnabled = !it } }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun renderMoveTicked(enabled: Boolean?) {
        if (enabled == null) return
        binding.moveTickedSwitch.isChecked = enabled
        if (!switchInitialised) {
            binding.moveTickedSwitch.jumpDrawablesToCurrentState()
            switchInitialised = true
        }
    }

    private fun renderLastUpdated(epochMillis: Long?) {
        binding.lastUpdated.text = if (epochMillis == null) {
            getString(R.string.catalog_not_downloaded)
        } else {
            getString(R.string.last_updated, requireContext().formatShortDateTime(epochMillis))
        }
    }

    private fun renderRefreshing(refreshing: Boolean) {
        binding.refreshRow.isEnabled = !refreshing
        binding.refreshProgress.isVisible = refreshing
        val alpha = if (refreshing) DISABLED_ALPHA else 1f
        binding.refreshTitle.alpha = alpha
        binding.lastUpdated.alpha = alpha
    }

    private fun handleEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.Message -> Snackbar.make(binding.root, event.text, Snackbar.LENGTH_LONG).show()
            SettingsEvent.ResetDone -> navigator.backToLists()
        }
    }

    // ---------------------------------------------------------------- Theme

    private fun showThemeDialog() {
        val modes = ThemeMode.entries
        val labels = modes.map { getString(themeLabel(it)) }.toTypedArray()
        show(
            DIALOG_THEME,
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.setting_theme)
                .setSingleChoiceItems(labels, modes.indexOf(viewModel.themeMode)) { dialog, which ->
                    closeDialog(dialog as AlertDialog)
                    val mode = modes[which]
                    binding.themeValue.text = getString(themeLabel(mode))
                    if (mode != viewModel.themeMode) viewModel.setTheme(mode)
                }
                .setNegativeButton(R.string.action_cancel, null)
                .create(),
        )
    }

    @StringRes
    private fun themeLabel(mode: ThemeMode): Int = when (mode) {
        ThemeMode.System -> R.string.theme_system
        ThemeMode.Light -> R.string.theme_light
        ThemeMode.Dark -> R.string.theme_dark
    }

    // ---------------------------------------------------------------- Language

    /** Language tag of the per-app language, or null when the app follows the system language. */
    private fun currentLanguage(): String? {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) null else locales[0]?.language
    }

    private fun showLanguageDialog() {
        val current = currentLanguage()
        val labels = LANGUAGES.map { getString(languageLabel(it)) }.toTypedArray()
        val checked = LANGUAGES.indexOf(current).coerceAtLeast(0)
        show(
            DIALOG_LANGUAGE,
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.setting_language)
                .setSingleChoiceItems(labels, checked) { dialog, which ->
                    closeDialog(dialog as AlertDialog)
                    val language = LANGUAGES[which]
                    if (language == current) return@setSingleChoiceItems
                    binding.languageValue.text = getString(languageLabel(language))
                    AppCompatDelegate.setApplicationLocales(
                        if (language == null) {
                            LocaleListCompat.getEmptyLocaleList()
                        } else {
                            // Western digits in every language, including Arabic ("ar-u-nu-latn").
                            LocaleListCompat.forLanguageTags(Locales.withLatinDigits(Locale.forLanguageTag(language)).toLanguageTag())
                        },
                    )
                }
                .setNegativeButton(R.string.action_cancel, null)
                .create(),
        )
    }

    @StringRes
    private fun languageLabel(language: String?): Int = when (language) {
        "en" -> R.string.language_english
        "es" -> R.string.language_spanish
        "ar" -> R.string.language_arabic
        else -> R.string.language_system
    }

    // ---------------------------------------------------------------- Reset

    private fun showResetDialog() {
        val resetDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reset_title)
            .setMessage(R.string.reset_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.reset_confirm) { _, _ -> viewModel.resetSampleData() }
            .create()
        resetDialog.setOnShowListener {
            resetDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                ?.setTextColor(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorError))
        }
        show(DIALOG_RESET, resetDialog)
    }

    // ---------------------------------------------------------------- About

    private fun openDataSource() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.data_credit_url)))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.settings_link_unavailable, Snackbar.LENGTH_LONG).show()
        }
    }

    // ---------------------------------------------------------------- Dialogs

    private fun show(name: String, newDialog: AlertDialog) {
        dialog?.let(::closeDialog)
        newDialog.setOnDismissListener {
            if (dialog === newDialog) {
                dialog = null
                openDialog = null
            }
        }
        dialog = newDialog
        openDialog = name
        newDialog.show()
    }

    private fun closeDialog(target: AlertDialog) {
        target.setOnDismissListener(null)
        target.dismiss()
        if (dialog === target) {
            dialog = null
            openDialog = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        openDialog?.let { outState.putString(KEY_OPEN_DIALOG, it) }
    }

    override fun onDestroyView() {
        dialog?.let {
            it.setOnDismissListener(null)
            it.dismiss()
        }
        dialog = null
        switchInitialised = false
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val KEY_OPEN_DIALOG = "open_dialog"
        const val DIALOG_THEME = "theme"
        const val DIALOG_LANGUAGE = "language"
        const val DIALOG_RESET = "reset"
        const val DISABLED_ALPHA = 0.38f

        /** Language choices: null = System, then the per-app languages. */
        val LANGUAGES = listOf(null, "en", "es", "ar")
    }
}
