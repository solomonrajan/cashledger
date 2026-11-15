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

package com.oriondev.moneywallet.ui.fragment.primary

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.broadcast.Message
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.Group
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.picker.CategoryPicker
import com.oriondev.moneywallet.picker.WalletPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.storage.wrapper.TransactionHeaderCursor
import com.oriondev.moneywallet.ui.activity.PeriodDetailActivity
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter
import com.oriondev.moneywallet.ui.adapter.recycler.TransactionCursorAdapter
import com.oriondev.moneywallet.ui.fragment.base.CursorListFragment
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelFragment
import com.oriondev.moneywallet.ui.fragment.base.TransactionSelectionMode
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView
import com.oriondev.moneywallet.utils.DateUtils
import java.util.Date

/**
 * Created by andrea on 03/03/18.
 */
class TransactionListFragment :
    CursorListFragment(),
    TransactionCursorAdapter.ActionListener,
    WalletPicker.SingleWalletController,
    CategoryPicker.Controller {

    private var mAdapter: TransactionCursorAdapter? = null
    private var mSelectionMode: TransactionSelectionMode? = null

    override fun onPrepareRecyclerView(recyclerView: AdvancedRecyclerView) {
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.setEmptyText(R.string.message_no_transaction_found)
    }

    override fun onCreateAdapter(): AbstractCursorAdapter {
        // the only one of the four that opens the report from a header click
        val adapter = TransactionCursorAdapter(this, true)
        mAdapter = adapter
        // a page of the transactions pager, whose toolbar belongs to the pager fragment
        val parent = parentFragment
        if (parent is MultiPanelFragment) {
            mSelectionMode = TransactionSelectionMode(this, parent, adapter)
        }
        return adapter
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mSelectionMode?.onRestoreInstanceState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mSelectionMode?.onSaveInstanceState(outState)
    }

    /**
     * Which periods have their transactions hidden is stored once for the whole application, and
     * this list is not the only one reading it, since a filtered transactions list opens over this
     * one. Asking again here is what stops the one underneath drawing a state another has already
     * changed, which would make its next arrow tap do nothing. The rows themselves need no
     * reloading, since the loader is watching the transactions already.
     */
    override fun onResume() {
        super.onResume()
        mAdapter?.reloadCollapsedPeriods()
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor>? {
        val act = activity ?: return null
        val uri = DataContentProvider.CONTENT_TRANSACTIONS
        val currentWallet = PreferenceManager.getCurrentWallet()
        val selection = walletSelection(currentWallet) +
            " AND DATETIME(" + Contract.Transaction.DATE + ") <= DATETIME('now', 'localtime')"
        val sortOrder = Contract.Transaction.DATE + " DESC"
        val groupType = PreferenceManager.getCurrentGroupType()
        return WrappedCursorLoader(act, uri, null, selection, null, sortOrder, groupType, null, null)
    }

    /**
     * An empty list here reads the same whether this wallet has no transactions or has some that
     * are all dated ahead of now, which the query hides without saying so. When it comes back
     * empty, ask what it is hiding.
     */
    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        val empty = cursor == null || cursor.count == 0
        if (empty) {
            // back to the plain message before the base makes it visible: a refined one from an
            // earlier load would otherwise stand on screen while this load is being explained
            setEmptyText(R.string.message_no_transaction_found)
        }
        super.onLoadFinished(loader, cursor)
        if (empty) {
            LoaderManager.getInstance(this).restartLoader(HIDDEN_TRANSACTIONS_LOADER_ID, null, mHiddenTransactionsCallbacks)
        } else {
            LoaderManager.getInstance(this).destroyLoader(HIDDEN_TRANSACTIONS_LOADER_ID)
        }
    }

    private val mHiddenTransactionsCallbacks = object : LoaderManager.LoaderCallbacks<Cursor> {
        override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
            val act = activity ?: requireActivity()
            val currentWallet = PreferenceManager.getCurrentWallet()
            // the same wallet, without the date test, newest first so the row most likely to be
            // dated ahead is the one read
            return CursorLoader(
                act,
                DataContentProvider.CONTENT_TRANSACTIONS,
                HIDDEN_PROJECTION,
                walletSelection(currentWallet),
                null,
                Contract.Transaction.DATE + " DESC"
            )
        }

        override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor?) {
            setEmptyText(
                if (isDatedAhead(data)) {
                    R.string.message_no_transaction_found_future
                } else {
                    R.string.message_no_transaction_found
                }
            )
        }

        override fun onLoaderReset(loader: Loader<Cursor>) {
            // the message is a resource id, and holds nothing of the cursor
        }
    }

    override fun onHeaderClick(startDate: Date?, endDate: Date?) {
        val intent = Intent(activity, PeriodDetailActivity::class.java).apply {
            putExtra(PeriodDetailActivity.START_DATE, startDate)
            putExtra(PeriodDetailActivity.END_DATE, endDate)
        }
        startActivity(intent)
    }

    override fun onTransactionClick(id: Long) {
        val act = activity ?: return
        val intent = Intent(LocalAction.ACTION_ITEM_CLICK).apply {
            putExtra(Message.ITEM_ID, id)
            putExtra(Message.ITEM_TYPE, Message.TYPE_TRANSACTION)
        }
        LocalBroadcastManager.getInstance(act).sendBroadcast(intent)
    }

    override fun onSelectionChanged(count: Int) {
        mSelectionMode?.onSelectionChanged(count)
    }

    override fun onWalletChanged(tag: String?, wallet: Wallet?) {
        if (wallet != null) {
            mSelectionMode?.onWalletPicked(wallet)
        }
    }

    override fun onCategoryChanged(tag: String?, category: Category?) {
        if (category != null) {
            mSelectionMode?.onCategoryPicked(category)
        }
    }

    override fun onCurrentWalletChanged(walletId: Long) {
        mAdapter?.clearSelection()
        super.onCurrentWalletChanged(walletId)
    }

    override fun shouldRefreshOnCurrentWalletChange(): Boolean {
        // this fragment content is dependant on the current
        // wallet when the loader is created, so the query
        // operation must be recreated from the beginning.
        return true
    }

    private class WrappedCursorLoader(
        context: Context,
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
        private val mGroup: Group,
        private val mStartDate: Date?,
        private val mEndDate: Date?
    ) : CursorLoader(context, uri, projection, selection, selectionArgs, sortOrder) {

        override fun loadInBackground(): Cursor? {
            val cursor = super.loadInBackground() ?: return null
            return TransactionHeaderCursor(cursor, mGroup, mStartDate, mEndDate)
        }
    }

    companion object {
        private const val HIDDEN_TRANSACTIONS_LOADER_ID = 60004

        private val HIDDEN_PROJECTION = arrayOf(Contract.Transaction.DATE)

        /**
         * The wallet this list is about. The date test is not in here on purpose: it is the clause
         * the other query asks about, so that query is this selection on its own, and one method
         * decides the wallet rule for both. The wallet id is written into the text rather than bound,
         * so there are no arguments to keep in step with it either.
         *
         * On the Total wallet this selection hides rows of its own, from a wallet the user left out
         * of the total. Those are hidden from both queries alike, so nothing here reports them and
         * nothing here gets them wrong.
         */
        private fun walletSelection(currentWallet: Long): String {
            if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) {
                return Contract.Transaction.WALLET_COUNT_IN_TOTAL + " = 1"
            }
            return Contract.Transaction.WALLET_ID + " = " + currentWallet
        }

        /**
         * Whether the newest row here is dated ahead of now. The message this decides is about the
         * date, so the date is what it reads. A row is missing from the list whenever the query held
         * it back, which is a wider question than the one being asked.
         *
         * Compared as text rather than parsed, because the column and this string are both
         * yyyy-MM-dd HH:mm:ss in local time. That is the comparison the item screens make. A value
         * the database cannot read is normalised by neither side, so it is compared as it was
         * stored and can fall either side of now.
         */
        private fun isDatedAhead(cursor: Cursor?): Boolean {
            if (cursor == null || !cursor.moveToFirst()) {
                return false
            }
            val date = cursor.getString(cursor.getColumnIndex(Contract.Transaction.DATE))
            return date != null && date.compareTo(DateUtils.getSQLDateTimeString(Date())) > 0
        }
    }
}
