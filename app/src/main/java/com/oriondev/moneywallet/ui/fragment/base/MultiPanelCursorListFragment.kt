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

package com.oriondev.moneywallet.ui.fragment.base

import android.database.Cursor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView

/**
 * Created by andrea on 09/02/18.
 */
abstract class MultiPanelCursorListFragment : MultiPanelFragment(),
    SwipeRefreshLayout.OnRefreshListener,
    LoaderManager.LoaderCallbacks<Cursor> {

    companion object {
        private const val DEFAULT_LOADER_ID = 24
    }

    private var mAdvancedRecyclerView: AdvancedRecyclerView? = null
    private var mAbstractCursorAdapter: AbstractCursorAdapter<*>? = null

    override fun onCreatePrimaryPanel(
        inflater: LayoutInflater,
        primaryPanel: ViewGroup,
        savedInstanceState: Bundle?
    ) {
        val view = inflater.inflate(R.layout.layout_fab_list_primary_panel, primaryPanel, true)
        val recyclerView: AdvancedRecyclerView? = view.findViewById(R.id.advanced_recycler_view)
        mAdvancedRecyclerView = recyclerView
        if (recyclerView != null) {
            onPrepareRecyclerView(recyclerView)
            val adapter = onCreateAdapter()
            mAbstractCursorAdapter = adapter
            recyclerView.adapter = adapter
            recyclerView.setOnRefreshListener(this)
            LoaderManager.getInstance(this).initLoader(DEFAULT_LOADER_ID, null, this)
            recyclerView.setState(AdvancedRecyclerView.State.LOADING)
        }
    }

    protected abstract fun onPrepareRecyclerView(recyclerView: AdvancedRecyclerView)

    /**
     * Replace the message shown when the list has nothing in it. Available after the primary
     * panel has been created, which is where the recycler view is inflated.
     */
    protected fun setEmptyText(@StringRes resId: Int) {
        mAdvancedRecyclerView?.setEmptyText(resId)
    }

    protected abstract fun onCreateAdapter(): AbstractCursorAdapter<*>

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        mAbstractCursorAdapter?.changeCursor(cursor)
        val recyclerView = mAdvancedRecyclerView
        if (recyclerView != null) {
            if (cursor != null && cursor.count > 0) {
                recyclerView.setState(AdvancedRecyclerView.State.READY)
            } else {
                recyclerView.setState(AdvancedRecyclerView.State.EMPTY)
            }
        }
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        mAbstractCursorAdapter?.changeCursor(null)
    }

    override fun onRefresh() {
        LoaderManager.getInstance(this).restartLoader(DEFAULT_LOADER_ID, null, this)
        mAdvancedRecyclerView?.setState(AdvancedRecyclerView.State.REFRESHING)
    }

    protected fun recreateLoader() {
        LoaderManager.getInstance(this).restartLoader(DEFAULT_LOADER_ID, null, this)
        mAdvancedRecyclerView?.setState(AdvancedRecyclerView.State.LOADING)
    }
}
