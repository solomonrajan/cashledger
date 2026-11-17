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
import com.oriondev.moneywallet.ui.adapter.recycler.BudgetCursorAdapter
import com.oriondev.moneywallet.ui.fragment.base.CursorListFragment
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView

/**
 * Created by andrea on 03/03/18.
 */
class BudgetListFragment : CursorListFragment(), BudgetCursorAdapter.ActionListener {

    override fun onPrepareRecyclerView(recyclerView: AdvancedRecyclerView) {
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.setEmptyText(R.string.message_no_budget_found)
    }

    override fun onCreateAdapter(): AbstractCursorAdapter<*> {
        return BudgetCursorAdapter(this)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor>? {
        val act = activity ?: return null
        val arguments = arguments ?: return null

        // unpack the argument bundle
        val expired = arguments.getBoolean(ARG_BUDGET_EXPIRED, true)
        // query the content provider
        val uri: Uri = DataContentProvider.CONTENT_BUDGETS
        val projection = arrayOf(
            Contract.Budget.ID,
            Contract.Budget.TYPE,
            Contract.Budget.MONEY,
            Contract.Budget.END_DATE,
            Contract.Budget.CATEGORY_ICON,
            Contract.Budget.CATEGORY_NAME,
            Contract.Budget.CURRENCY,
            Contract.Budget.PROGRESS
        )
        var selection: String
        val selectionArgs: Array<String>?
        val currentWallet = PreferenceManager.getCurrentWallet()
        if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) {
            selection = "${Contract.Budget.HAS_WALLET_IN_TOTAL} = 1 AND "
            selectionArgs = null
        } else {
            selection = "${Contract.Budget.WALLET_IDS} LIKE '%<'||?||'>%' AND "
            selectionArgs = arrayOf(currentWallet.toString())
        }
        var sortOrder = Contract.Budget.END_DATE
        if (expired) {
            selection += "${Contract.Budget.END_DATE} < DATE('now', 'localtime')"
            sortOrder += " DESC"
        } else {
            selection += "${Contract.Budget.END_DATE} >= DATE('now', 'localtime')"
            sortOrder += " ASC"
        }
        return CursorLoader(act, uri, projection, selection, selectionArgs, sortOrder)
    }

    override fun onBudgetClick(id: Long) {
        val act = activity ?: return
        val intent = Intent(LocalAction.ACTION_ITEM_CLICK).apply {
            putExtra(Message.ITEM_ID, id)
            putExtra(Message.ITEM_TYPE, Message.TYPE_BUDGET)
        }
        LocalBroadcastManager.getInstance(act).sendBroadcast(intent)
    }

    override fun shouldRefreshOnCurrentWalletChange(): Boolean {
        // this fragment content is dependant on the current
        // wallet when the loader is created, so the query
        // operation must be recreated from the beginning.
        return true
    }

    companion object {
        private const val ARG_BUDGET_EXPIRED = "BudgetListFragment::Arguments::BudgetExpired"

        @JvmStatic
        fun newInstance(expired: Boolean): BudgetListFragment {
            val arguments = Bundle().apply {
                putBoolean(ARG_BUDGET_EXPIRED, expired)
            }
            val fragment = BudgetListFragment()
            fragment.arguments = arguments
            return fragment
        }
    }
}
