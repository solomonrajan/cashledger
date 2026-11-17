/*
 * Copyright (c) 2018.
 *
 * This file is part of MoneyWallet.
 *
 * MoneyWallet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MoneyWallet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MoneyWallet.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oriondev.moneywallet.ui.fragment.multipanel

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.appcompat.widget.Toolbar
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.picker.CategoryPicker
import com.oriondev.moneywallet.picker.WalletPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter
import com.oriondev.moneywallet.ui.adapter.recycler.TransactionCursorAdapter
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelCursorListItemFragment
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment
import com.oriondev.moneywallet.ui.fragment.base.TransactionSelectionMode
import com.oriondev.moneywallet.ui.fragment.secondary.TransactionItemFragment
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import java.util.Date

/**
 * The search screen. A result opens in the same transaction panel the transaction list and the
 * calendar open, so a result can be duplicated and deleted and not only edited.
 */
class SearchMultiPanelFragment : MultiPanelCursorListItemFragment(),
    TransactionCursorAdapter.ActionListener,
    WalletPicker.SingleWalletController,
    CategoryPicker.Controller {

    private var searchFlags: BooleanArray = booleanArrayOf(
        true, // description
        true, // category
        true, // date
        true, // money
        true, // note
        true, // event
        true  // place
    )

    private var query: String = ""
    private var searchEditText: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            val flags = savedInstanceState.getBooleanArray(SS_SEARCH_FLAGS)
            if (flags != null) {
                searchFlags = flags
            }
        }
    }

    override fun setupPrimaryToolbar(toolbar: Toolbar?) {
        super.setupPrimaryToolbar(toolbar)
        toolbar?.title = null
        if (toolbar != null) {
            val view = layoutInflater.inflate(R.layout.layout_toolbar_search_view, toolbar, true)
            val editText: EditText = view.findViewById(R.id.search_edit_text)
            searchEditText = editText
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    query = s?.toString() ?: ""
                    recreateLoader()
                }

                override fun afterTextChanged(s: Editable?) {}
            })
        }
    }

    override fun onPrepareRecyclerView(recyclerView: AdvancedRecyclerView) {
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.setEmptyText(R.string.message_no_transaction_found)
    }

    override fun onCreateAdapter(): AbstractCursorAdapter<*> {
        val adapter = TransactionCursorAdapter(this)
        selectionMode = TransactionSelectionMode(this, this, adapter)
        return adapter
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectionMode?.onRestoreInstanceState(savedInstanceState)
    }

    override fun onSelectionModeChanged(active: Boolean) {
        searchEditText?.visibility = if (active) View.GONE else View.VISIBLE
    }

    override fun onCreateSecondaryPanel(): SecondaryPanelFragment = TransactionItemFragment()

    override fun getSecondaryFragmentTag(): String = SECONDARY_PANEL_TAG

    override fun getTitleRes(): Int = R.string.title_activity_search_transaction

    override fun isFloatingActionButtonEnabled(): Boolean = false

    override fun onInflateMenu(): Int = R.menu.menu_search_activity

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_filter) {
            showFilterDialog()
        }
        return false
    }

    private fun showFilterDialog() {
        val items = arrayOf(
            getString(R.string.hint_description),
            getString(R.string.hint_category),
            getString(R.string.hint_date),
            getString(R.string.hint_money),
            getString(R.string.hint_note),
            getString(R.string.hint_event),
            getString(R.string.hint_place)
        )
        val checked = searchFlags.clone()
        ThemedDialog.buildMaterialDialog(requireActivity())
            .setTitle(R.string.dialog_filter_search_title)
            .setMultiChoiceItems(items, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                searchFlags = checked
                recreateLoader()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor>? {
        val act = activity
        if (act != null) {
            val uri: Uri = DataContentProvider.CONTENT_TRANSACTIONS
            val selection = StringBuilder()
            if (searchFlags[0]) appendSelection(selection, Contract.Transaction.DESCRIPTION)
            if (searchFlags[1]) appendSelection(selection, Contract.Transaction.CATEGORY_NAME)
            if (searchFlags[2]) appendSelection(selection, Contract.Transaction.DATE)
            if (searchFlags[3]) appendSelection(selection, Contract.Transaction.MONEY)
            if (searchFlags[4]) appendSelection(selection, Contract.Transaction.NOTE)
            if (searchFlags[5]) appendSelection(selection, Contract.Transaction.EVENT_NAME)
            if (searchFlags[6]) appendSelection(selection, Contract.Transaction.PLACE_NAME)
            val selectionArgs = getSelectionArguments(query)
            val sortOrder = "${Contract.Transaction.DATE} DESC"
            return CursorLoader(act, uri, null, selection.toString(), selectionArgs, sortOrder)
        }
        return null
    }

    private fun appendSelection(builder: StringBuilder, column: String) {
        if (builder.isNotEmpty()) {
            builder.append(" OR ")
        }
        builder.append(column)
        builder.append(" LIKE '%'||?||'%'")
    }

    private fun getSelectionArguments(searchQuery: String): Array<String> {
        val arguments = ArrayList<String>()
        for (flag in searchFlags) {
            if (flag) {
                arguments.add(searchQuery)
            }
        }
        return arguments.toTypedArray()
    }

    override fun onHeaderClick(startDate: Date, endDate: Date) {
        // this method will never be called by the adapter!
    }

    override fun onTransactionClick(id: Long) {
        showItemId(id)
        showSecondaryPanel()
    }

    override fun onSelectionChanged(count: Int) {
        selectionMode?.onSelectionChanged(count)
    }

    override fun onWalletChanged(tag: String?, wallet: Wallet?) {
        selectionMode?.onWalletPicked(wallet)
    }

    override fun onCategoryChanged(tag: String?, category: Category?) {
        selectionMode?.onCategoryPicked(category)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBooleanArray(SS_SEARCH_FLAGS, searchFlags)
        selectionMode?.onSaveInstanceState(outState)
    }

    companion object {
        private const val SS_SEARCH_FLAGS = "SearchMultiPanelFragment::SavedState::SearchFlags"
        private const val SECONDARY_PANEL_TAG = "SearchMultiPanelFragment::Tag::SecondaryPanel"
    }
}
