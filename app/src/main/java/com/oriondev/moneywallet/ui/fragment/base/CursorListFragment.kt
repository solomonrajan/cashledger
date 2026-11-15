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

import android.content.BroadcastReceiver
import android.content.Context
import android.database.Cursor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.preference.CurrentWalletController
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView
import com.oriondev.moneywallet.utils.SystemBars

/**
 * Created by andrea on 11/02/18.
 */
abstract class CursorListFragment : Fragment(),
    SwipeRefreshLayout.OnRefreshListener,
    LoaderManager.LoaderCallbacks<Cursor>,
    CurrentWalletController {

    companion object {
        private const val DEFAULT_LOADER_ID = 24
    }

    private var mAdvancedRecyclerView: AdvancedRecyclerView? = null
    private var mAbstractCursorAdapter: AbstractCursorAdapter<*>? = null
    private var mBroadcastReceiver: BroadcastReceiver? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mBroadcastReceiver = PreferenceManager.registerCurrentWalletObserver(context, this)
    }

    override fun onDetach() {
        val receiver = mBroadcastReceiver
        if (receiver != null) {
            PreferenceManager.unregisterCurrentWalletObserver(activity, receiver)
            mBroadcastReceiver = null
        }
        super.onDetach()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val recyclerView = AdvancedRecyclerView(requireContext())
        mAdvancedRecyclerView = recyclerView
        SystemBars.pad(
            recyclerView.getRecyclerView(),
            false,
            resources.getBoolean(R.bool.panel_fills_window),
            true
        )
        onPrepareRecyclerView(recyclerView)
        val adapter = onCreateAdapter()
        mAbstractCursorAdapter = adapter
        recyclerView.adapter = adapter
        recyclerView.setOnRefreshListener(this)
        recyclerView.setState(AdvancedRecyclerView.State.LOADING)
        return recyclerView
    }

    protected abstract fun onPrepareRecyclerView(recyclerView: AdvancedRecyclerView)

    protected abstract fun onCreateAdapter(): AbstractCursorAdapter<*>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LoaderManager.getInstance(this).restartLoader(DEFAULT_LOADER_ID, null, this)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        mAbstractCursorAdapter?.changeCursor(cursor)
        showListState()
    }

    /**
     * Put the list into READY or EMPTY by what the adapter shows, which can be fewer rows than
     * the cursor holds.
     */
    protected open fun showListState() {
        val adapter = mAbstractCursorAdapter
        val recyclerView = mAdvancedRecyclerView
        if (adapter != null && recyclerView != null) {
            if (adapter.itemCount > 0) {
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

    /**
     * Change the text shown when the list comes back with nothing in it. The view takes it at
     * once, so this reaches an empty list that is already on screen as well as the next one.
     */
    protected fun setEmptyText(@StringRes stringRes: Int) {
        mAdvancedRecyclerView?.setEmptyText(stringRes)
    }

    /**
     * Run the query behind this list again, for when something outside the list changes what it
     * should hold. Does nothing before the view exists, since the query is run from
     * onViewCreated anyway once it does.
     */
    open fun reloadList() {
        if (mAdvancedRecyclerView != null) {
            LoaderManager.getInstance(this).restartLoader(DEFAULT_LOADER_ID, null, this)
            mAdvancedRecyclerView?.setState(AdvancedRecyclerView.State.LOADING)
        }
    }

    protected open fun shouldRefreshOnCurrentWalletChange(): Boolean = false

    override fun onCurrentWalletChanged(walletId: Long) {
        if (shouldRefreshOnCurrentWalletChange()) {
            LoaderManager.getInstance(this).restartLoader(DEFAULT_LOADER_ID, null, this)
            mAdvancedRecyclerView?.setState(AdvancedRecyclerView.State.LOADING)
        }
    }
}
