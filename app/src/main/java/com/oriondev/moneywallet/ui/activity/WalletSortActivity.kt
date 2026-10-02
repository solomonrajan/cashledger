/*
 * Copyright (c) 2018. MoneyWallet
 * Copyright (c) 2026. solomonrajan/CashLedger
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
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.annotation.MenuRes
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.activity.base.SinglePanelSimpleListActivity
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter
import com.oriondev.moneywallet.ui.adapter.recycler.WalletSortCursorAdapter
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView

class WalletSortActivity : SinglePanelSimpleListActivity(), WalletSortCursorAdapter.WalletSortListener {

    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun getActivityTitleRes(): Int = R.string.title_activity_wallet_sort

    @MenuRes
    override fun onInflateMenu(): Int = R.menu.menu_save_changes

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_save_changes) {
            saveChanges()
        }
        return false
    }

    override fun isFloatingActionButtonEnabled(): Boolean = false

    override fun onPrepareRecyclerView(recyclerView: AdvancedRecyclerView) {
        recyclerView.setLayoutManager(LinearLayoutManager(this))
        recyclerView.setEmptyText(R.string.message_no_category_found)
        recyclerView.isEnabled = false

        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                return makeMovementFlags(dragFlags, 0)
            }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val adapter = recyclerView.adapter
                if (adapter is WalletSortCursorAdapter) {
                    return adapter.moveItem(viewHolder.adapterPosition, target.adapterPosition)
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean = false
        })
        itemTouchHelper.attachToRecyclerView(recyclerView.recyclerView)
    }

    override fun onCreateAdapter(): AbstractCursorAdapter<*> = WalletSortCursorAdapter(this)

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        val uri: Uri = DataContentProvider.CONTENT_WALLETS
        val projection = arrayOf(
            Contract.Wallet.ID,
            Contract.Wallet.NAME,
            Contract.Wallet.ICON
        )
        val sortOrder = "${Contract.Wallet.INDEX} ASC, ${Contract.Wallet.NAME} ASC"
        return CursorLoader(this, uri, projection, null, null, sortOrder)
    }

    override fun onWalletDragStarted(viewHolder: RecyclerView.ViewHolder) {
        itemTouchHelper.startDrag(viewHolder)
    }

    private fun saveChanges() {
        val baseUri: Uri = DataContentProvider.CONTENT_WALLETS
        val contentResolver: ContentResolver = contentResolver
        val adapter = advancedRecyclerView.recyclerView.adapter
        if (adapter is WalletSortCursorAdapter) {
            val walletIdsList = adapter.sortedCategoryIds
            for (i in walletIdsList.indices) {
                val walletId = walletIdsList[i]
                val uri = ContentUris.withAppendedId(baseUri, walletId)
                val contentValues = ContentValues().apply {
                    put(Contract.Wallet.INDEX, i + 1)
                }
                contentResolver.update(uri, contentValues, null, null)
            }
        }
        setResult(Activity.RESULT_OK)
        finish()
    }
}
