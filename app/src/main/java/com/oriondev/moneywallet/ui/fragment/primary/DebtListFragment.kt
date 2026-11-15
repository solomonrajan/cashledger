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
import com.oriondev.moneywallet.storage.wrapper.DebtHeaderCursor
import com.oriondev.moneywallet.ui.activity.NewEditTransactionActivity
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter
import com.oriondev.moneywallet.ui.adapter.recycler.DebtCursorAdapter
import com.oriondev.moneywallet.ui.fragment.base.CursorListFragment
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView

/**
 * Created by andrea on 05/03/18.
 */
class DebtListFragment : CursorListFragment(), DebtCursorAdapter.ActionListener {

    override fun onPrepareRecyclerView(recyclerView: AdvancedRecyclerView) {
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.setEmptyText(R.string.message_no_debt_found)
    }

    override fun onCreateAdapter(): AbstractCursorAdapter {
        return DebtCursorAdapter(this)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor>? {
        val act = activity ?: return null
        val arguments = arguments ?: return null
        val debtType = arguments.getSerializable(ARG_DEBT_TYPE) as? Contract.DebtType ?: return null

        val uri = DataContentProvider.CONTENT_DEBTS
        val projection = arrayOf(
            Contract.Debt.ID,
            Contract.Debt.TYPE,
            Contract.Debt.ICON,
            Contract.Debt.DESCRIPTION,
            Contract.Debt.MONEY,
            Contract.Debt.PROGRESS,
            Contract.Debt.WALLET_CURRENCY,
            Contract.Debt.EXPIRATION_DATE,
            Contract.Debt.ARCHIVED,
            Contract.Debt.PLACE_ID,
            Contract.Debt.PLACE_NAME,
            "${DebtHeaderCursor.SQL_IS_SETTLED} AS ${DebtHeaderCursor.COLUMN_IS_SETTLED}"
        )
        var selection: String
        val selectionArgs: Array<String>?
        val currentWallet = PreferenceManager.getCurrentWallet()
        if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) {
            selection = "${Contract.Debt.WALLET_COUNT_IN_TOTAL} = 1"
            selectionArgs = null
        } else {
            selection = "${Contract.Debt.WALLET_ID} = ?"
            selectionArgs = arrayOf(currentWallet.toString())
        }
        selection += " AND ${Contract.Debt.TYPE} = ${debtType.value}"
        // read once and used for both, so an empty list cannot claim there are no debts at all
        // while the query that emptied it was only asking for the finished ones
        val finishedOnly = PreferenceManager.isDebtFinishedOnlyEnabled()
        if (finishedOnly) {
            // the same expression the sort and the grouping use, so the filter cannot decide a
            // debt is finished while the header above it says otherwise
            selection += " AND ${DebtHeaderCursor.SQL_IS_SETTLED} = 1"
        }
        setEmptyText(if (finishedOnly) R.string.message_no_finished_debt_found else R.string.message_no_debt_found)
        // a debt that has been paid off in full is finished whether or not anyone remembered to
        // archive it, so it belongs below the ones that still need attention. The grouping
        // reads the same value back out of the projection, so the two cannot drift apart.
        val sortOrder = "${DebtHeaderCursor.COLUMN_IS_SETTLED} ASC, ${Contract.Debt.DATE} DESC"
        return WrappedCursorLoader(act, uri, projection, selection, selectionArgs, sortOrder, debtType)
    }

    private class WrappedCursorLoader(
        context: Context,
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
        private val mDebtType: Contract.DebtType
    ) : CursorLoader(context, uri, projection, selection, selectionArgs, sortOrder) {

        override fun loadInBackground(): Cursor? {
            val cursor = super.loadInBackground() ?: return null
            return DebtHeaderCursor(cursor, mDebtType)
        }
    }

    override fun onDebtClick(id: Long) {
        val act = activity ?: return
        val intent = Intent(LocalAction.ACTION_ITEM_CLICK).apply {
            putExtra(Message.ITEM_ID, id)
            putExtra(Message.ITEM_TYPE, Message.TYPE_DEBT)
        }
        LocalBroadcastManager.getInstance(act).sendBroadcast(intent)
    }

    override fun onPayClick(id: Long) {
        val intent = Intent(activity, NewEditTransactionActivity::class.java).apply {
            putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_DEBT)
            putExtra(NewEditTransactionActivity.DEBT_ID, id)
            putExtra(NewEditTransactionActivity.DEBT_ACTION, NewEditTransactionActivity.DEBT_PAY)
        }
        startActivity(intent)
    }

    override fun onReceiveClick(id: Long) {
        val intent = Intent(activity, NewEditTransactionActivity::class.java).apply {
            putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_DEBT)
            putExtra(NewEditTransactionActivity.DEBT_ID, id)
            putExtra(NewEditTransactionActivity.DEBT_ACTION, NewEditTransactionActivity.DEBT_RECEIVE)
        }
        startActivity(intent)
    }

    override fun onPayInFullClick(id: Long) {
        val intent = Intent(activity, NewEditTransactionActivity::class.java).apply {
            putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_DEBT)
            putExtra(NewEditTransactionActivity.DEBT_ID, id)
            putExtra(NewEditTransactionActivity.DEBT_ACTION, NewEditTransactionActivity.DEBT_PAY_IN_FULL)
        }
        startActivity(intent)
    }

    override fun onReceiveInFullClick(id: Long) {
        val intent = Intent(activity, NewEditTransactionActivity::class.java).apply {
            putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_DEBT)
            putExtra(NewEditTransactionActivity.DEBT_ID, id)
            putExtra(NewEditTransactionActivity.DEBT_ACTION, NewEditTransactionActivity.DEBT_RECEIVE_IN_FULL)
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
        private const val ARG_DEBT_TYPE = "DebtListFragment::Arguments::DebtType"

        @JvmStatic
        fun newInstance(debtType: Contract.DebtType): DebtListFragment {
            val arguments = Bundle().apply {
                putSerializable(ARG_DEBT_TYPE, debtType)
            }
            val fragment = DebtListFragment()
            fragment.arguments = arguments
            return fragment
        }
    }
}
