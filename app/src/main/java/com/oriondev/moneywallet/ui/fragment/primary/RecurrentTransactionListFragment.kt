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

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.broadcast.Message
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter
import com.oriondev.moneywallet.ui.adapter.recycler.RecurrentTransactionCursorAdapter
import com.oriondev.moneywallet.ui.fragment.base.CursorListFragment
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView

/**
 * Created by andrea on 04/11/18.
 */
class RecurrentTransactionListFragment : CursorListFragment(), RecurrentTransactionCursorAdapter.ActionListener {

    override fun onPrepareRecyclerView(recyclerView: AdvancedRecyclerView) {
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.setEmptyText(R.string.message_no_recurrence_found)
    }

    override fun onCreateAdapter(): AbstractCursorAdapter<*> {
        return RecurrentTransactionCursorAdapter(this)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        val act = activity ?: throw IllegalStateException("Activity is null")
        val uri: Uri = DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS
        val selection: String
        val arguments: Array<String>?
        val currentWallet = PreferenceManager.getCurrentWallet()
        if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) {
            selection = "${Contract.RecurrentTransaction.WALLET_COUNT_IN_TOTAL} = 1"
            arguments = null
        } else {
            selection = "${Contract.RecurrentTransaction.WALLET_ID} = ?"
            arguments = arrayOf(currentWallet.toString())
        }
        val sortOrder = "${Contract.RecurrentTransaction.NEXT_OCCURRENCE} IS NULL OR DATETIME(${Contract.RecurrentTransaction.NEXT_OCCURRENCE}) < DATETIME('now', 'localtime'), ${Contract.RecurrentTransaction.NEXT_OCCURRENCE} ASC"
        return CursorLoader(act, uri, null, selection, arguments, sortOrder)
    }

    override fun onRecurrentTransactionClick(id: Long) {
        val act = activity ?: return
        val intent = Intent(LocalAction.ACTION_ITEM_CLICK).apply {
            putExtra(Message.ITEM_ID, id)
            putExtra(Message.ITEM_TYPE, Message.TYPE_RECURRENT_TRANSACTION)
        }
        LocalBroadcastManager.getInstance(act).sendBroadcast(intent)
    }

    override fun shouldRefreshOnCurrentWalletChange(): Boolean {
        // this fragment content is dependant on the current
        // wallet when the loader is created, so the query
        // operation must be recreated from the beginning.
        return true
    }
}
