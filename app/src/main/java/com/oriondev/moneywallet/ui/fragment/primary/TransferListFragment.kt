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
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.broadcast.Message
import com.oriondev.moneywallet.model.Group
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.storage.wrapper.TransferHeaderCursor
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter
import com.oriondev.moneywallet.ui.adapter.recycler.TransferCursorAdapter
import com.oriondev.moneywallet.ui.fragment.base.CursorListFragment
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView
import com.oriondev.moneywallet.utils.DateUtils
import java.util.Date

/**
 * Created by andrea on 03/03/18.
 */
class TransferListFragment : CursorListFragment(), TransferCursorAdapter.ActionListener {

    override fun onPrepareRecyclerView(recyclerView: AdvancedRecyclerView) {
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.setEmptyText(R.string.message_no_transfer_found)
    }

    override fun onCreateAdapter(): AbstractCursorAdapter<*> {
        return TransferCursorAdapter(this)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor>? {
        val act = activity ?: return null
        val uri = DataContentProvider.CONTENT_TRANSFERS
        val currentWallet = PreferenceManager.getCurrentWallet()
        val selection = walletSelection(currentWallet) +
            " AND DATETIME(" + Contract.Transfer.DATE + ") <= DATETIME('now', 'localtime')"
        val sortOrder = Contract.Transfer.DATE + " DESC"
        val groupType = PreferenceManager.getCurrentGroupType()
        return WrappedCursorLoader(act, uri, null, selection, null, sortOrder, groupType)
    }

    /**
     * An empty list here reads the same whether this wallet has no transfers or has some that are
     * all dated ahead of now, which the query hides without saying so. When it comes back empty,
     * ask what it is hiding.
     */
    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        val empty = cursor == null || cursor.count == 0
        if (empty) {
            // back to the plain message before the base makes it visible: a refined one from an
            // earlier load would otherwise stand on screen while this load is being explained
            setEmptyText(R.string.message_no_transfer_found)
        }
        super.onLoadFinished(loader, cursor)
        if (empty) {
            LoaderManager.getInstance(this).restartLoader(HIDDEN_TRANSFERS_LOADER_ID, null, mHiddenTransfersCallbacks)
        } else {
            LoaderManager.getInstance(this).destroyLoader(HIDDEN_TRANSFERS_LOADER_ID)
        }
    }

    private val mHiddenTransfersCallbacks = object : LoaderManager.LoaderCallbacks<Cursor> {
        override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
            val act = activity ?: requireActivity()
            val currentWallet = PreferenceManager.getCurrentWallet()
            // the same wallets, without the date test, newest first so the row most likely to
            // be dated ahead is the one read
            return CursorLoader(
                act,
                DataContentProvider.CONTENT_TRANSFERS,
                HIDDEN_PROJECTION,
                walletSelection(currentWallet),
                null,
                Contract.Transfer.DATE + " DESC"
            )
        }

        override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor?) {
            setEmptyText(
                if (isDatedAhead(data)) {
                    R.string.message_no_transfer_found_future
                } else {
                    R.string.message_no_transfer_found
                }
            )
        }

        override fun onLoaderReset(loader: Loader<Cursor>) {
            // the message is a resource id, and holds nothing of the cursor
        }
    }

    override fun onHeaderClick() {
    }

    override fun onTransferClick(id: Long) {
        val act = activity ?: return
        val intent = Intent(LocalAction.ACTION_ITEM_CLICK).apply {
            putExtra(Message.ITEM_ID, id)
            putExtra(Message.ITEM_TYPE, Message.TYPE_TRANSFER)
        }
        LocalBroadcastManager.getInstance(act).sendBroadcast(intent)
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
        private val mGroup: Group
    ) : CursorLoader(context, uri, projection, selection, selectionArgs, sortOrder) {

        override fun loadInBackground(): Cursor? {
            val cursor = super.loadInBackground() ?: return null
            return TransferHeaderCursor(cursor, mGroup)
        }
    }

    companion object {
        private const val HIDDEN_TRANSFERS_LOADER_ID = 60003

        private val HIDDEN_PROJECTION = arrayOf(Contract.Transfer.DATE)

        /**
         * The wallets this list is about. A transfer touches two, so both branches select on a pair
         * joined by OR. The brackets matter: AND binds tighter than OR, so without them the date test
         * the caller appends read as "the first term matches, OR the second term matches and the date
         * has passed". A transfer dated ahead was listed when it matched on its from side and hidden
         * when it matched only on its to side.
         *
         * The date test is not in here on purpose. It is the clause the other query asks about, so
         * that query is this selection on its own, and one method decides the wallet rule for both.
         * The wallet id is written into the text rather than bound, so there are no arguments to
         * keep in step with it either.
         *
         * On the Total wallet this selection hides rows of its own, from a wallet the user left out
         * of the total. Those are hidden from both queries alike, so nothing here reports them and
         * nothing here gets them wrong.
         */
        private fun walletSelection(currentWallet: Long): String {
            if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) {
                return "(" + Contract.Transfer.TRANSACTION_FROM_WALLET_COUNT_IN_TOTAL + " = 1 OR " +
                    Contract.Transfer.TRANSACTION_TO_WALLET_COUNT_IN_TOTAL + " = 1)"
            }
            return "(" + Contract.Transfer.TRANSACTION_FROM_WALLET_ID + " = " + currentWallet + " OR " +
                Contract.Transfer.TRANSACTION_TO_WALLET_ID + " = " + currentWallet + ")"
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
            val date = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transfer.DATE))
            return date != null && date.compareTo(DateUtils.getSQLDateTimeString(Date())) > 0
        }
    }
}
