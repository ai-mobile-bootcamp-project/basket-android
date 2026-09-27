package com.basket.ui.categories

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.basket.R
import com.basket.databinding.DialogCategoryNameBinding
import com.basket.databinding.FragmentCategoriesBinding
import com.basket.domain.Category
import com.basket.domain.Names
import com.basket.ui.common.displayName
import com.basket.ui.navigation.BasketNavigator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CategoriesFragment : Fragment() {

    private var _binding: FragmentCategoriesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CategoriesViewModel by viewModels()
    private val navigator get() = requireActivity() as BasketNavigator

    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var footerAdapter: AddCategoryFooterAdapter
    private lateinit var touchHelper: ItemTouchHelper

    private var dragging = false
    private var awaitingReorder = false
    private var pendingRows: List<CategoryRow>? = null

    private var nameDialog: AlertDialog? = null
    private var validateNameDialog: (() -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCategoriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { navigator.navigateBack() }

        categoryAdapter = CategoryAdapter(
            onStartDrag = { holder -> touchHelper.startDrag(holder) },
            onMenu = ::showRowMenu,
        )
        footerAdapter = AddCategoryFooterAdapter(onAdd = {
            viewModel.startAdd()
            showNameDialog()
        })
        touchHelper = ItemTouchHelper(
            CategoryDragCallback(
                adapter = categoryAdapter,
                onDragStateChanged = ::onDragStateChanged,
                onDrop = { ids ->
                    awaitingReorder = true
                    viewModel.reorder(ids)
                },
            ),
        )
        binding.categoryList.layoutManager = LinearLayoutManager(requireContext())
        binding.categoryList.adapter = ConcatAdapter(categoryAdapter, footerAdapter)
        touchHelper.attachToRecyclerView(binding.categoryList)

        if (viewModel.editingId != null) showNameDialog()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.rows.collect { rows -> if (rows != null) render(rows) }
            }
        }
    }

    private fun render(rows: List<CategoryRow>) {
        if (dragging) {
            pendingRows = rows
            return
        }
        if (awaitingReorder) {
            val shown = categoryAdapter.currentRows.map { it.category.id }
            if (rows.map { it.category.id } != shown) return
            awaitingReorder = false
        }
        categoryAdapter.submit(rows)
        footerAdapter.visible = true
        validateNameDialog?.invoke()
    }

    private fun onDragStateChanged(isDragging: Boolean) {
        dragging = isDragging
        if (!isDragging) {
            val rows = pendingRows ?: viewModel.rows.value
            pendingRows = null
            if (rows != null) render(rows)
        }
    }

    // ---------------------------------------------------------------- Row menu

    private fun showRowMenu(anchor: View, position: Int) {
        val category = categoryAdapter.rowAt(position)?.category ?: return
        if (category.isOther) return
        val lastMovable = categoryAdapter.movableCount - 1
        PopupMenu(requireContext(), anchor).apply {
            menu.add(Menu.NONE, MENU_RENAME, 0, R.string.action_rename)
            menu.add(Menu.NONE, MENU_MOVE_UP, 1, R.string.move_up).isEnabled = position > 0
            menu.add(Menu.NONE, MENU_MOVE_DOWN, 2, R.string.move_down).isEnabled = position < lastMovable
            menu.add(Menu.NONE, MENU_DELETE, 3, R.string.action_delete)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_RENAME -> {
                        viewModel.startRename(category, category.displayName(requireContext()))
                        showNameDialog()
                    }
                    MENU_MOVE_UP -> viewModel.move(category, -1)
                    MENU_MOVE_DOWN -> viewModel.move(category, +1)
                    MENU_DELETE -> deleteCategory(category)
                }
                true
            }
            show()
        }
    }

    private fun deleteCategory(category: Category) {
        val name = category.displayName(requireContext())
        viewModel.delete(category)
        Snackbar.make(binding.root, getString(R.string.category_deleted, name), Snackbar.LENGTH_LONG)
            .setAction(R.string.action_undo) { viewModel.restore(category) }
            .show()
    }

    // ---------------------------------------------------------------- Add / Rename dialog

    private fun showNameDialog() {
        val editingId = viewModel.editingId ?: return
        nameDialog?.let {
            it.setOnDismissListener(null)
            it.dismiss()
        }
        val isNew = editingId == CategoriesViewModel.NEW_CATEGORY
        val dialogBinding = DialogCategoryNameBinding.inflate(layoutInflater)
        dialogBinding.nameInput.setText(viewModel.draftName)
        dialogBinding.nameInput.setSelection(dialogBinding.nameInput.length())

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (isNew) R.string.add_category else R.string.rename_category)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(if (isNew) R.string.add_category_confirm else R.string.action_save) { _, _ ->
                saveName(dialogBinding.nameInput.text?.toString().orEmpty())
            }
            .create()
        dialog.setOnDismissListener {
            viewModel.finishEditing()
            nameDialog = null
            validateNameDialog = null
        }

        val validate: () -> Unit = validate@{
            if (!isAdded) return@validate
            val name = Names.clean(dialogBinding.nameInput.text?.toString().orEmpty())
            val existing = viewModel.rows.value.orEmpty()
                .map { it.category }
                .filter { it.id != editingId }
                .map { it.displayName(requireContext()) }
                .firstOrNull { Names.sameName(it, name) }
            dialogBinding.nameLayout.error = existing?.let { getString(R.string.category_exists, it) }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = Names.isValid(name) && existing == null
        }
        dialog.setOnShowListener { validate() }
        dialogBinding.nameInput.doAfterTextChanged {
            viewModel.draftName = it?.toString().orEmpty()
            validate()
        }
        dialogBinding.nameInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            if (positive?.isEnabled == true) positive.performClick()
            true
        }

        nameDialog = dialog
        validateNameDialog = validate
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        dialogBinding.nameInput.requestFocus()
    }

    private fun saveName(text: String) {
        val editingId = viewModel.editingId ?: return
        val name = Names.clean(text)
        val current = viewModel.rows.value?.firstOrNull { it.category.id == editingId }?.category
        if (current != null && current.displayName(requireContext()) == name) return
        viewModel.saveName(name)
    }

    override fun onDestroyView() {
        nameDialog?.let {
            it.setOnDismissListener(null)
            it.dismiss()
        }
        nameDialog = null
        validateNameDialog = null
        pendingRows = null
        dragging = false
        awaitingReorder = false
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val MENU_RENAME = 1
        const val MENU_MOVE_UP = 2
        const val MENU_MOVE_DOWN = 3
        const val MENU_DELETE = 4
    }
}
