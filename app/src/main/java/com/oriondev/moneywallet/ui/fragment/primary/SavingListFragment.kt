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
import com.oriondev.moneywallet.ui.activity.NewEditTransactionActivity
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter
import com.oriondev.moneywallet.ui.adapter.recycler.SavingCursorAdapter
import com.oriondev.moneywallet.ui.fragment.base.CursorListFragment
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView

/**
 * Created by andrea on 03/03/18.
 */
class SavingListFragment : CursorListFragment(), SavingCursorAdapter.ActionListener {

    override fun onPrepareRecyclerView(recyclerView: AdvancedRecyclerView) {
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.setEmptyText(R.string.message_no_saving_found)
    }

    override fun onCreateAdapter(): AbstractCursorAdapter {
        return SavingCursorAdapter(this)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor>? {
        val act = activity ?: return null
        val arguments = arguments ?: return null

        // unpack the argument bundle
        val completed = arguments.getBoolean(ARG_SAVING_COMPLETED, true)
        // query the content provider
        val uri: Uri = DataContentProvider.CONTENT_SAVINGS
        val projection = arrayOf(
            Contract.Saving.ID,
            Contract.Saving.ICON,
            Contract.Saving.DESCRIPTION,
            Contract.Saving.COMPLETE,
            Contract.Saving.START_MONEY,
            Contract.Saving.END_MONEY,
            Contract.Saving.WALLET_CURRENCY,
            Contract.Saving.PROGRESS
        )
        val selection: String
        val selectionArgs: Array<String>
        val currentWallet = PreferenceManager.getCurrentWallet()
        if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) {
            selection = "${Contract.Saving.WALLET_COUNT_IN_TOTAL} = 1 AND ${Contract.Saving.COMPLETE} = ?"
            selectionArgs = arrayOf(if (completed) "1" else "0")
        } else {
            selection = "${Contract.Saving.WALLET_ID} = ? AND ${Contract.Saving.COMPLETE} = ?"
            selectionArgs = arrayOf(currentWallet.toString(), if (completed) "1" else "0")
        }
        val sortOrder = "${Contract.Saving.ID} DESC"
        return CursorLoader(act, uri, projection, selection, selectionArgs, sortOrder)
    }

    override fun onSavingClick(id: Long) {
        val act = activity ?: return
        val intent = Intent(LocalAction.ACTION_ITEM_CLICK).apply {
            putExtra(Message.ITEM_ID, id)
            putExtra(Message.ITEM_TYPE, Message.TYPE_SAVING)
        }
        LocalBroadcastManager.getInstance(act).sendBroadcast(intent)
    }

    override fun onWithdrawEverything(id: Long) {
        val intent = Intent(activity, NewEditTransactionActivity::class.java).apply {
            putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_SAVING)
            putExtra(NewEditTransactionActivity.SAVING_ID, id)
            putExtra(NewEditTransactionActivity.SAVING_ACTION, NewEditTransactionActivity.SAVING_WITHDRAW_EVERYTHING)
        }
        startActivity(intent)
    }

    override fun onWithdraw(id: Long) {
        val intent = Intent(activity, NewEditTransactionActivity::class.java).apply {
            putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_SAVING)
            putExtra(NewEditTransactionActivity.SAVING_ID, id)
            putExtra(NewEditTransactionActivity.SAVING_ACTION, NewEditTransactionActivity.SAVING_WITHDRAW)
        }
        startActivity(intent)
    }

    override fun onDeposit(id: Long) {
        val intent = Intent(activity, NewEditTransactionActivity::class.java).apply {
            putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_SAVING)
            putExtra(NewEditTransactionActivity.SAVING_ID, id)
            putExtra(NewEditTransactionActivity.SAVING_ACTION, NewEditTransactionActivity.SAVING_DEPOSIT)
        }
        startActivity(intent)
    }

    override fun shouldRefreshOnCurrentWalletChange(): Boolean {
        // this fragment content is dependant on the current
        // wallet when the loader is created, so the query
        // operation must be recreated from the beginning.
        return true
    }

    companion object {
        private const val ARG_SAVING_COMPLETED = "SavingListFragment::Arguments::SavingCompleted"

        @JvmStatic
        fun newInstance(completed: Boolean): SavingListFragment {
            val arguments = Bundle().apply {
                putBoolean(ARG_SAVING_COMPLETED, completed)
            }
            val fragment = SavingListFragment()
            fragment.arguments = arguments
            return fragment
        }
    }
}
