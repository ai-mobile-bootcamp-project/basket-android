package com.basket.ui.item

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.basket.R
import com.basket.data.MessageCenter
import com.basket.data.UserMessage
import com.basket.databinding.FragmentItemFormBinding
import com.basket.domain.Category
import com.basket.ui.common.appLocale
import com.basket.ui.common.displayName
import com.basket.ui.common.formatMoney
import com.basket.ui.navigation.BasketNavigator
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Screen 3 — Add / edit item. A full-screen form; itemId 0 adds a new item to the list. */
@AndroidEntryPoint
class ItemFormFragment : Fragment() {

    @Inject lateinit var messageCenter: MessageCenter

    private var _binding: FragmentItemFormBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ItemFormViewModel by viewModels()
    private val navigator: BasketNavigator get() = requireActivity() as BasketNavigator

    private var categories: List<Category> = emptyList()
    private var suggestions: List<String> = emptyList()
    private var savingIcon: Drawable? = null
    private var backCallback: OnBackPressedCallback? = null
    private var discardDialog: AlertDialog? = null
    private var duplicateDialog: AlertDialog? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentItemFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.start(requireContext().appLocale)

        val callback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = viewModel.showDiscard()
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
        backCallback = callback

        savingIcon = IndeterminateDrawable.createCircularDrawable(
            requireContext(),
            CircularProgressIndicatorSpec(
                requireContext(),
                null,
                0,
                com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall,
            ).apply {
                indicatorColors = intArrayOf(MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnPrimary))
            },
        )

        binding.closeButton.setOnClickListener { close() }
        binding.deleteButton.setOnClickListener { viewModel.delete() }

        binding.nameInput.doAfterTextChanged { viewModel.onNameChanged(it?.toString().orEmpty()) }
        binding.nameInput.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) viewModel.onNameFocusLost() }
        binding.nameInput.setOnItemClickListener { parent, _, position, _ ->
            (parent.getItemAtPosition(position) as? String)?.let(viewModel::onSuggestionPicked)
        }

        binding.decreaseButton.setOnClickListener { viewModel.stepQuantity(-1) }
        binding.increaseButton.setOnClickListener { viewModel.stepQuantity(1) }
        binding.quantityInput.doAfterTextChanged { viewModel.onQuantityTyped(it?.toString().orEmpty()) }
        binding.quantityInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && binding.quantityInput.text.isNullOrBlank()) {
                binding.quantityInput.setText(viewModel.uiState.value.quantity.toString())
            }
        }

        binding.priceInput.doAfterTextChanged { viewModel.onPriceChanged(it?.toString().orEmpty()) }
        binding.categoryInput.setOnItemClickListener { _, _, position, _ ->
            categories.getOrNull(position)?.let { viewModel.onCategorySelected(it.id) }
        }
        binding.noteInput.doAfterTextChanged { viewModel.onNoteChanged(it?.toString().orEmpty()) }
        binding.saveButton.setOnClickListener { viewModel.save() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.events.collect(::onEvent) }
            }
        }
    }

    private fun render(state: ItemFormUiState) {
        val context = requireContext()
        binding.title.setText(if (state.isEdit) R.string.edit_item_title else R.string.add_item_title)
        binding.deleteButton.isVisible = state.isEdit

        // Name + suggestions
        if (state.suggestions != suggestions) {
            suggestions = state.suggestions
            binding.nameInput.setSimpleItems(suggestions.toTypedArray())
        }
        if (binding.nameInput.text.toString() != state.name) {
            binding.nameInput.setText(state.name, false)
            binding.nameInput.setSelection(binding.nameInput.length())
        }
        binding.nameLayout.setErrorIfChanged(if (state.showNameError) getString(R.string.error_name) else null)

        // Quantity
        val typed = binding.quantityInput.text.toString()
        val editingEmpty = binding.quantityInput.hasFocus() && typed.isEmpty()
        if (!editingEmpty && typed.toIntOrNull() != state.quantity) {
            binding.quantityInput.setTextKeepingEnd(state.quantity.toString())
        }

        // Unit price + line total
        if (binding.priceInput.text.toString() != state.fields.priceText) {
            binding.priceInput.setTextKeepingEnd(state.fields.priceText)
        }
        binding.priceLayout.setErrorIfChanged(
            if (state.priceValid) null else getString(R.string.error_price, context.formatMoney(1), context.formatMoney(999_999)),
        )
        val unit = state.unitPriceCents
        binding.lineTotal.isVisible = unit != null
        if (unit != null) {
            val quantity = state.quantity
            val total = unit * quantity
            binding.lineTotal.text = "$quantity × $" + String.format("%.2f", unit / 100.0) + " = $" + String.format("%.2f", total / 100.0)
        }

        // Category
        if (state.categories != categories) {
            categories = state.categories
            binding.categoryInput.setSimpleItems(categories.map { it.displayName(context) }.toTypedArray())
        }
        val categoryName = state.selectedCategory?.displayName(context).orEmpty()
        if (binding.categoryInput.text.toString() != categoryName) binding.categoryInput.setText(categoryName, false)

        // Note
        if (binding.noteInput.text.toString() != state.fields.note) {
            binding.noteInput.setTextKeepingEnd(state.fields.note)
        }

        // Save button
        binding.saveButton.text = if (state.isEdit) getString(R.string.save_changes) else getString(R.string.add_to_list, state.listName)
        binding.saveButton.isEnabled = state.canSave
        binding.saveButton.icon = if (state.saving) savingIcon else null

        backCallback?.isEnabled = state.hasChanges
        renderDialogs(state)
    }

    private fun renderDialogs(state: ItemFormUiState) {
        if (state.showDiscard && discardDialog == null) {
            discardDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.discard_title)
                .setNegativeButton(R.string.keep_editing, null)
                .setPositiveButton(R.string.discard) { _, _ ->
                    viewModel.dismissDiscard()
                    leave { navigator.navigateBack() }
                }
                .setOnDismissListener {
                    discardDialog = null
                    viewModel.dismissDiscard()
                }
                .show()
        }

        val duplicate = state.duplicate
        if (duplicate != null && duplicateDialog == null) {
            duplicateDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.duplicate_title, duplicate.name))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(resources.getQuantityString(R.plurals.duplicate_add_more, state.quantity, state.quantity)) { _, _ ->
                    viewModel.addToDuplicate()
                }
                .setOnDismissListener {
                    duplicateDialog = null
                    viewModel.dismissDuplicate()
                }
                .show()
        }
    }

    private fun onEvent(event: ItemFormEvent) {
        when (event) {
            is ItemFormEvent.Added -> {
                messageCenter.post(UserMessage(getString(R.string.item_added, event.name)))
                leave { navigator.openListDetail(viewModel.listId) }
            }
            ItemFormEvent.Saved -> {
                messageCenter.post(UserMessage(getString(R.string.changes_saved)))
                leave { navigator.openListDetail(viewModel.listId) }
            }
            is ItemFormEvent.Removed -> {
                messageCenter.post(UserMessage(getString(R.string.item_removed, event.name), undo = event.undo))
                leave { navigator.navigateBack() }
            }
            ItemFormEvent.NotFound -> navigator.navigateBack()
        }
    }

    private fun close() {
        if (viewModel.uiState.value.hasChanges) viewModel.showDiscard() else leave { navigator.navigateBack() }
    }

    /** Hides the keyboard before the form goes away. */
    private inline fun leave(navigate: () -> Unit) {
        _binding?.let { WindowCompat.getInsetsController(requireActivity().window, it.root).hide(WindowInsetsCompat.Type.ime()) }
        navigate()
    }

    private fun TextInputLayout.setErrorIfChanged(message: String?) {
        if (error?.toString() != message) error = message
    }

    private fun EditText.setTextKeepingEnd(value: String) {
        setText(value)
        setSelection(value.length)
    }

    override fun onDestroyView() {
        listOfNotNull(discardDialog, duplicateDialog).forEach {
            it.setOnDismissListener(null)
            it.dismiss()
        }
        discardDialog = null
        duplicateDialog = null
        backCallback = null
        savingIcon = null
        categories = emptyList()
        suggestions = emptyList()
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_LIST_ID = "listId"
        const val ARG_ITEM_ID = "itemId"

        fun args(listId: Long, itemId: Long): Bundle = bundleOf(ARG_LIST_ID to listId, ARG_ITEM_ID to itemId)
    }
}
