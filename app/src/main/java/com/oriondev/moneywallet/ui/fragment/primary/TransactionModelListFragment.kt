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
import com.oriondev.moneywallet.ui.activity.NewEditItemActivity
import com.oriondev.moneywallet.ui.activity.NewEditTransactionActivity
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter
import com.oriondev.moneywallet.ui.adapter.recycler.TransactionModelCursorAdapter
import com.oriondev.moneywallet.ui.fragment.base.CursorListFragment
import com.oriondev.moneywallet.ui.fragment.multipanel.ModelMultiPanelViewPagerFragment
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView

/**
 * Created by andrea on 14/03/18.
 */
class TransactionModelListFragment : CursorListFragment(), TransactionModelCursorAdapter.ActionListener {

    override fun onPrepareRecyclerView(recyclerView: AdvancedRecyclerView) {
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.setEmptyText(R.string.message_no_model_found)
    }

    override fun onCreateAdapter(): AbstractCursorAdapter {
        return TransactionModelCursorAdapter(this)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor>? {
        val act = activity ?: return null
        val uri: Uri = DataContentProvider.CONTENT_TRANSACTION_MODELS
        val projection = arrayOf(
            Contract.TransactionModel.ID,
            Contract.TransactionModel.CATEGORY_NAME,
            Contract.TransactionModel.CATEGORY_ICON,
            Contract.TransactionModel.WALLET_CURRENCY,
            Contract.TransactionModel.DIRECTION,
            Contract.TransactionModel.MONEY,
            Contract.TransactionModel.DESCRIPTION
        )
        val selection: String
        val selectionArgs: Array<String>?
        val currentWallet = PreferenceManager.getCurrentWallet()
        if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) {
            selection = "${Contract.TransactionModel.WALLET_COUNT_IN_TOTAL} = 1"
            selectionArgs = null
        } else {
            selection = "${Contract.TransactionModel.WALLET_ID} = ?"
            selectionArgs = arrayOf(currentWallet.toString())
        }
        return CursorLoader(act, uri, projection, selection, selectionArgs, null)
    }

    override fun onModelClick(modelId: Long) {
        val act = activity ?: return
        val intent = Intent(LocalAction.ACTION_ITEM_CLICK).apply {
            putExtra(Message.ITEM_ID, modelId)
            putExtra(Message.ITEM_TYPE, Message.TYPE_TRANSACTION_MODEL)
        }
        LocalBroadcastManager.getInstance(act).sendBroadcast(intent)
    }

    override fun onModelAddClick(modelId: Long) {
        val uri = NewEditTransactionActivity.insertTransactionFromModel(activity, modelId)
        if (uri != null) {
            val parentFragment = parentFragment
            if (parentFragment is ModelMultiPanelViewPagerFragment) {
                parentFragment.onTransactionAdded(uri)
            }
        } else {
            // TODO: alert user of the failure.
        }
    }

    override fun onModelEditClick(modelId: Long) {
        val intent = Intent(activity, NewEditTransactionActivity::class.java).apply {
            putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.NEW_ITEM)
            putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_MODEL)
            putExtra(NewEditTransactionActivity.MODEL_ID, modelId)
        }
        startActivity(intent)
    }

    override fun shouldRefreshOnCurrentWalletChange(): Boolean {
        // this fragment content is dependant on the current
        // wallet when the loader is created, so the query
        // operation must be recreated from the beginning.
        return true
    }
}
