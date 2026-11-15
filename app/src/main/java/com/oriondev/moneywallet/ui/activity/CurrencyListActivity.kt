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

package com.oriondev.moneywallet.ui.activity

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.activity.base.SinglePanelSimpleListActivity
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter
import com.oriondev.moneywallet.ui.adapter.recycler.CurrencyCursorAdapter
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView
import com.oriondev.moneywallet.utils.CurrencyManager

/**
 * Created by andrea on 03/02/18.
 */
class CurrencyListActivity : SinglePanelSimpleListActivity(), CurrencyCursorAdapter.CurrencyActionListener {

    private var mActivityMode: Int = CURRENCY_MANAGER
    private var mQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        // The base runs the first load before the toolbar exists, so a restored query has to be
        // in the field already when it does.
        if (savedInstanceState != null) {
            mQuery = savedInstanceState.getString(SS_QUERY, "")
        }
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(SS_QUERY, mQuery)
    }

    override fun onToolbarReady(toolbar: Toolbar) {
        toolbar.title = null
        val view = layoutInflater.inflate(R.layout.layout_toolbar_search_view, toolbar, true)
        val searchEditText: EditText = view.findViewById(R.id.search_edit_text)
        searchEditText.setText(mQuery)
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                // The view state restore after a rotation sets the same text again and would
                // reload for nothing.
                if (query != mQuery) {
                    mQuery = query
                    recreateLoader()
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onPrepareRecyclerView(recyclerView: AdvancedRecyclerView) {
        val intent = intent
        mActivityMode = intent?.getIntExtra(ACTIVITY_MODE, CURRENCY_MANAGER) ?: CURRENCY_MANAGER
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.setEmptyText(R.string.message_no_currency_found)
        if (mActivityMode == CURRENCY_MANAGER) {
            // The button covers the star of the last row otherwise, and nothing can scroll it out
            // from under it.
            recyclerView.getRecyclerView().addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    val lastItem = parent.getChildAdapterPosition(view) == state.itemCount - 1
                    outRect.bottom = if (lastItem) resources.getDimensionPixelSize(R.dimen.currency_list_fab_clearance) else 0
                }
            })
        }
    }

    override fun onCreateAdapter(): AbstractCursorAdapter<*> {
        return CurrencyCursorAdapter(this)
    }

    @StringRes
    override fun getActivityTitleRes(): Int = R.string.title_activity_currency_list

    override fun isFloatingActionButtonEnabled(): Boolean {
        return mActivityMode == CURRENCY_MANAGER
    }

    override fun onFloatingActionButtonClick() {
        val intent = Intent(this, NewEditCurrencyActivity::class.java).apply {
            putExtra(NewEditCurrencyActivity.MODE, NewEditCurrencyActivity.Mode.NEW_ITEM)
        }
        startActivity(intent)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        val uri: Uri = DataContentProvider.CONTENT_CURRENCIES
        val projection = arrayOf(
            Contract.Currency.ISO,
            Contract.Currency.NAME,
            Contract.Currency.SYMBOL,
            Contract.Currency.DECIMALS,
            Contract.Currency.FAVOURITE
        )
        var selection: String? = null
        var selectionArgs: Array<String>? = null
        if (mQuery.isNotEmpty()) {
            selection = "${Contract.Currency.NAME} LIKE '%'||?||'%' OR ${Contract.Currency.ISO} LIKE '%'||?||'%'"
            selectionArgs = arrayOf(mQuery, mQuery)
        }
        val sortBy = "${Contract.Currency.FAVOURITE} DESC, ${Contract.Currency.NAME} ASC"
        return CursorLoader(this, uri, projection, selection, selectionArgs, sortBy)
    }

    override fun onCurrencyClick(iso: String) {
        if (mActivityMode == CURRENCY_MANAGER) {
            val intent = Intent(this, NewEditCurrencyActivity::class.java).apply {
                putExtra(NewEditCurrencyActivity.MODE, NewEditCurrencyActivity.Mode.EDIT_ITEM)
                putExtra(NewEditCurrencyActivity.ISO, iso)
            }
            startActivity(intent)
        } else if (mActivityMode == CURRENCY_PICKER) {
            val currency: CurrencyUnit? = CurrencyManager.getCurrency(iso)
            val intent = Intent().apply {
                putExtra(RESULT_CURRENCY, currency)
            }
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }

    override fun onCurrencyFavourite(iso: String, newValue: Boolean) {
        val values = ContentValues().apply {
            put(Contract.Currency.FAVOURITE, newValue)
        }
        val uri: Uri = Uri.withAppendedPath(DataContentProvider.CONTENT_CURRENCIES, iso)
        contentResolver.update(uri, values, null, null)
    }

    companion object {
        const val ACTIVITY_MODE = "CurrencyListActivity::ActivityMode"
        const val RESULT_CURRENCY = "CurrencyListActivity::Result::SelectedCurrency"

        const val CURRENCY_MANAGER = 0
        const val CURRENCY_PICKER = 1

        private const val SS_QUERY = "CurrencyListActivity::SavedState::Query"
    }
}
