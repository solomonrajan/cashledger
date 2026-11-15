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

package com.oriondev.moneywallet.ui.fragment.multipanel

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.Group
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.picker.CategoryPicker
import com.oriondev.moneywallet.picker.WalletPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.preference.CurrentWalletController
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.storage.wrapper.TransactionHeaderCursor
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter
import com.oriondev.moneywallet.ui.adapter.recycler.TransactionCursorAdapter
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelCursorListItemFragment
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment
import com.oriondev.moneywallet.ui.fragment.base.TransactionSelectionMode
import com.oriondev.moneywallet.ui.fragment.secondary.TransactionItemFragment
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView
import com.oriondev.moneywallet.utils.DateUtils
import java.util.Date

/**
 * Created by andrea on 08/04/18.
 */
class TransactionMultiPanelFragment : MultiPanelCursorListItemFragment(),
    TransactionCursorAdapter.ActionListener,
    CurrentWalletController,
    WalletPicker.SingleWalletController,
    CategoryPicker.Controller {

    enum class FilterType {
        CATEGORY,
        DEBT,
        BUDGET,
        SAVING,
        EVENT,
        PLACE,
        PERSON
    }

    private var broadcastReceiver: BroadcastReceiver? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        broadcastReceiver = PreferenceManager.registerCurrentWalletObserver(context, this)
    }

    override fun onDetach() {
        broadcastReceiver?.let { PreferenceManager.unregisterCurrentWalletObserver(activity, it) }
        super.onDetach()
    }

    override fun onCreateSecondaryPanel(): SecondaryPanelFragment = TransactionItemFragment()

    override fun getSecondaryFragmentTag(): String = SECONDARY_PANEL_TAG

    override fun onPrepareRecyclerView(recyclerView: AdvancedRecyclerView) {
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.setEmptyText(R.string.message_no_transaction_found)
    }

    override fun onCreateAdapter(): AbstractCursorAdapter<*> {
        val adapter = TransactionCursorAdapter(this)
        selectionMode = TransactionSelectionMode(this, this, adapter)
        return adapter
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectionMode?.onRestoreInstanceState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectionMode?.onSaveInstanceState(outState)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor>? {
        val act = activity
        val arguments = arguments
        if (act != null && arguments != null) {
            val uri = buildItemTransactionsUri(arguments)
            val startDate = arguments.getSerializable(FILTER_START_DATE) as? Date
            val endDate = arguments.getSerializable(FILTER_END_DATE) as? Date
            var selection: String
            val selectionArgs: Array<String>?
            val currentWallet = PreferenceManager.getCurrentWallet()
            if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) {
                selection = "${Contract.Transaction.WALLET_COUNT_IN_TOTAL} = 1"
                selectionArgs = null
            } else {
                selection = "${Contract.Transaction.WALLET_ID} = ?"
                selectionArgs = arrayOf(currentWallet.toString())
            }
            selection += " AND DATETIME(${Contract.Transaction.DATE}) <= DATETIME('now', 'localtime')"
            val dateRange = buildDateRangeSelection(startDate, endDate)
            if (dateRange != null) {
                selection += " AND $dateRange"
            }
            val sortOrder = "${Contract.Transaction.DATE} DESC"
            val groupType = PreferenceManager.getCurrentGroupType()
            return WrappedCursorLoader(act, uri, null, selection, selectionArgs, sortOrder, groupType, startDate, endDate)
        }
        return null
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        val empty = cursor == null || cursor.count == 0
        if (empty) {
            setEmptyText(R.string.message_no_transaction_found)
        }
        super.onLoadFinished(loader, cursor)
        if (empty) {
            LoaderManager.getInstance(this).restartLoader(HIDDEN_TRANSACTIONS_LOADER_ID, null, hiddenTransactionsCallbacks)
        } else {
            LoaderManager.getInstance(this).destroyLoader(HIDDEN_TRANSACTIONS_LOADER_ID)
        }
    }

    private val hiddenTransactionsCallbacks = object : LoaderManager.LoaderCallbacks<Cursor> {
        override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
            val arguments = arguments
            val startDate = arguments?.getSerializable(FILTER_START_DATE) as? Date
            val endDate = arguments?.getSerializable(FILTER_END_DATE) as? Date
            return CursorLoader(
                requireActivity(),
                buildItemTransactionsUri(arguments),
                HIDDEN_PROJECTION,
                buildDateRangeSelection(startDate, endDate),
                null,
                null
            )
        }

        override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor?) {
            setEmptyText(describeHiddenTransactions(data))
        }

        override fun onLoaderReset(loader: Loader<Cursor>) {
            // the message holds a copy of the string, not the cursor
        }
    }

    @StringRes
    private fun describeHiddenTransactions(cursor: Cursor?): Int {
        var otherWallets = false
        var future = false
        if (cursor != null) {
            val currentWallet = PreferenceManager.getCurrentWallet()
            val now = DateUtils.getSQLDateTimeString(Date())
            val indexWalletId = cursor.getColumnIndexOrThrow(Contract.Transaction.WALLET_ID)
            val indexCountInTotal = cursor.getColumnIndexOrThrow(Contract.Transaction.WALLET_COUNT_IN_TOTAL)
            val indexDate = cursor.getColumnIndexOrThrow(Contract.Transaction.DATE)
            var i = 0
            while (i < cursor.count && !(otherWallets && future)) {
                cursor.moveToPosition(i)
                val inSelectedWallet = if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) {
                    cursor.getInt(indexCountInTotal) == 1
                } else {
                    cursor.getLong(indexWalletId) == currentWallet
                }
                if (!inSelectedWallet) {
                    otherWallets = true
                } else {
                    val date = cursor.getString(indexDate)
                    future = future or (date != null && date > now)
                }
                i++
            }
        }
        return when {
            otherWallets && future -> R.string.message_no_transaction_found_other_wallets_and_future
            otherWallets -> R.string.message_no_transaction_found_other_wallets
            future -> R.string.message_no_transaction_found_future
            else -> R.string.message_no_transaction_found
        }
    }

    override fun getTitleRes(): Int = R.string.menu_transaction

    override fun showsCurrentWallet(): Boolean = true

    override fun isFloatingActionButtonEnabled(): Boolean = false

    override fun onHeaderClick(startDate: Date, endDate: Date) {}

    override fun onTransactionClick(id: Long) {
        showItemId(id)
        showSecondaryPanel()
    }

    override fun onSelectionChanged(count: Int) {
        selectionMode?.onSelectionChanged(count)
    }

    override fun onWalletChanged(tag: String?, wallet: Wallet?) {
        selectionMode?.onWalletPicked(wallet)
    }

    override fun onCategoryChanged(tag: String?, category: Category?) {
        selectionMode?.onCategoryPicked(category)
    }

    override fun onCurrentWalletChanged(walletId: Long) {
        selectionMode?.finish()
        recreateLoader()
    }

    private class WrappedCursorLoader(
        context: Context,
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
        private val group: Group,
        private val startDate: Date?,
        private val endDate: Date?
    ) : CursorLoader(context, uri, projection, selection, selectionArgs, sortOrder) {

        override fun loadInBackground(): Cursor? {
            val cursor = super.loadInBackground()
            return TransactionHeaderCursor(cursor, group, startDate, endDate)
        }
    }

    companion object {
        private const val FILTER_TYPE = "TransactionMultiPanelFragment::Arguments::FilterType"
        private const val FILTER_ID = "TransactionMultiPanelFragment::Arguments::FilterId"
        private const val FILTER_START_DATE = "TransactionMultiPanelFragment::Arguments::FilterStartDate"
        private const val FILTER_END_DATE = "TransactionMultiPanelFragment::Arguments::FilterEndDate"

        private const val SECONDARY_PANEL_TAG = "TransactionMultiPanelFragment::Tag::SecondaryPanel"

        private const val HIDDEN_TRANSACTIONS_LOADER_ID = 60002

        private val HIDDEN_PROJECTION = arrayOf(
            Contract.Transaction.WALLET_ID,
            Contract.Transaction.WALLET_COUNT_IN_TOTAL,
            Contract.Transaction.DATE
        )

        @JvmStatic
        fun newInstance(type: FilterType, id: Long, startDate: Date?, endDate: Date?): TransactionMultiPanelFragment {
            return TransactionMultiPanelFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(FILTER_TYPE, type)
                    putLong(FILTER_ID, id)
                    putSerializable(FILTER_START_DATE, startDate)
                    putSerializable(FILTER_END_DATE, endDate)
                }
            }
        }

        private fun buildItemTransactionsUri(arguments: Bundle?): Uri {
            val baseUri = DataContentProvider.CONTENT_TRANSACTIONS
            if (arguments == null) {
                return baseUri
            }
            val type = arguments.getSerializable(FILTER_TYPE) as? FilterType ?: return baseUri
            val itemId = arguments.getLong(FILTER_ID)
            val uri = when (type) {
                FilterType.CATEGORY -> ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, itemId)
                FilterType.DEBT -> ContentUris.withAppendedId(DataContentProvider.CONTENT_DEBTS, itemId)
                FilterType.BUDGET -> ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, itemId)
                FilterType.SAVING -> ContentUris.withAppendedId(DataContentProvider.CONTENT_SAVINGS, itemId)
                FilterType.EVENT -> ContentUris.withAppendedId(DataContentProvider.CONTENT_EVENTS, itemId)
                FilterType.PLACE -> ContentUris.withAppendedId(DataContentProvider.CONTENT_PLACES, itemId)
                FilterType.PERSON -> ContentUris.withAppendedId(DataContentProvider.CONTENT_PEOPLE, itemId)
            }
            return Uri.withAppendedPath(uri, "transactions")
        }

        private fun buildDateRangeSelection(startDate: Date?, endDate: Date?): String? {
            val builder = StringBuilder()
            if (startDate != null) {
                builder.append("DATETIME(").append(Contract.Transaction.DATE).append(") >= DATETIME('")
                    .append(DateUtils.getSQLDateTimeString(startDate)).append("')")
            }
            if (endDate != null) {
                if (builder.isNotEmpty()) {
                    builder.append(" AND ")
                }
                builder.append("DATETIME(").append(Contract.Transaction.DATE).append(") <= DATETIME('")
                    .append(DateUtils.getSQLDateTimeString(endDate)).append("')")
            }
            return if (builder.isNotEmpty()) builder.toString() else null
        }
    }
}
