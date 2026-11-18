/*
 * Copyright (c) 2026.
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

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.res.Resources
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.picker.CategoryPicker
import com.oriondev.moneywallet.picker.WalletPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.SQLiteDataException
import com.oriondev.moneywallet.storage.database.SyncContentProvider
import com.oriondev.moneywallet.ui.activity.ToolbarController
import com.oriondev.moneywallet.ui.adapter.recycler.TransactionCursorAdapter
import com.oriondev.moneywallet.ui.fragment.secondary.TransactionItemFragment
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.MoneyFormatter
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * The selection of a transaction list, drawn on the primary toolbar of the multi panel fragment
 * the list sits in, which is not always the list's own fragment.
 */
class TransactionSelectionMode(
    private val mListFragment: Fragment,
    private val mOwner: MultiPanelFragment,
    private val mAdapter: TransactionCursorAdapter
) : Toolbar.OnMenuItemClickListener {

    class SkippedRows(
        val refused: Int,
        val split: Int
    )

    companion object {
        private const val SS_SELECTED_IDS = "TransactionSelectionMode::SavedState::SelectedIds"
        private const val TAG_WALLET_PICKER = "TransactionSelectionMode::Tag::WalletPicker"
        private const val TAG_CATEGORY_PICKER = "TransactionSelectionMode::Tag::CategoryPicker"

        private val sDeleteExecutor: Executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "TransactionSelectionDelete")
        }
        private val sMainHandler = Handler(Looper.getMainLooper())

        @JvmStatic
        fun deleteTransactions(resolver: ContentResolver, ids: LongArray): Set<Long> {
            val refused = HashSet<Long>()
            for (id in ids) {
                try {
                    resolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, id), null, null)
                } catch (e: SQLiteDataException) {
                    refused.add(id)
                }
            }
            return refused
        }

        @JvmStatic
        fun isSameSelection(selected: LongArray, snapshot: LongArray): Boolean {
            if (snapshot.isEmpty() || selected.size != snapshot.size) {
                return false
            }
            val members = HashSet<Long>()
            for (id in selected) {
                members.add(id)
            }
            for (id in snapshot) {
                if (!members.contains(id)) {
                    return false
                }
            }
            return true
        }

        @JvmStatic
        fun buildMoveMessage(
            resources: Resources,
            resolver: ContentResolver,
            ids: LongArray,
            walletId: Long,
            walletName: String?,
            walletIso: String?
        ): String {
            val to = if (walletIso != null) CurrencyManager.getCurrency(walletIso) else null
            var count = 0
            var otherCurrency = 0
            val projection = arrayOf(
                Contract.Transaction.TYPE,
                Contract.Transaction.WALLET_ID,
                Contract.Transaction.WALLET_CURRENCY
            )
            queryStored(resolver, ids, *projection)?.use { rows ->
                val typeIndex = rows.getColumnIndexOrThrow(Contract.Transaction.TYPE)
                val walletIdIndex = rows.getColumnIndexOrThrow(Contract.Transaction.WALLET_ID)
                val currencyIndex = rows.getColumnIndexOrThrow(Contract.Transaction.WALLET_CURRENCY)
                while (rows.moveToNext()) {
                    if (rows.getLong(walletIdIndex) == walletId) {
                        continue
                    }
                    val from = movableFrom(rows, to, typeIndex, currencyIndex)
                    if (from != null) {
                        count++
                        if (from.iso != to?.iso) {
                            otherCurrency++
                        }
                    }
                }
            }
            var message = resources.getQuantityString(
                R.plurals.message_move_selected_transactions,
                count, count, walletName ?: ""
            )
            if (otherCurrency > 0) {
                message += " " + resources.getQuantityString(
                    R.plurals.message_move_selected_transactions_other_currency,
                    otherCurrency, otherCurrency
                )
            }
            return message
        }

        @JvmStatic
        fun buildCategoryMessage(
            resources: Resources,
            resolver: ContentResolver,
            ids: LongArray,
            category: Category
        ): String {
            var count = 0
            val split = querySplit(resolver, ids)
            val projection = arrayOf(
                Contract.Transaction.ID,
                Contract.Transaction.TYPE,
                Contract.Transaction.CATEGORY_ID,
                Contract.Transaction.DIRECTION
            )
            queryStored(resolver, ids, *projection)?.use { rows ->
                val catIdIndex = rows.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_ID)
                val typeIndex = rows.getColumnIndexOrThrow(Contract.Transaction.TYPE)
                while (rows.moveToNext()) {
                    if (rows.getLong(catIdIndex) != category.id && isStandard(rows, typeIndex) && !keepsItsParts(rows, split, category)) {
                        count++
                    }
                }
            }
            return resources.getQuantityString(
                R.plurals.message_change_category_selected_transactions,
                count, count, category.name ?: ""
            )
        }

        @JvmStatic
        fun moveTransactions(
            resolver: ContentResolver,
            ids: LongArray,
            walletId: Long,
            walletIso: String?
        ): Int {
            val to = if (walletIso != null) CurrencyManager.getCurrency(walletIso) else null
            val projection = arrayOf(
                Contract.Transaction.TYPE,
                Contract.Transaction.WALLET_ID,
                Contract.Transaction.WALLET_CURRENCY,
                Contract.Transaction.MONEY
            )
            var skipped = 0
            for (id in ids) {
                val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, id)
                try {
                    resolver.query(uri, projection, null, null, null)?.use { row ->
                        if (!row.moveToFirst() ||
                            row.getLong(row.getColumnIndexOrThrow(Contract.Transaction.WALLET_ID)) == walletId
                        ) {
                            return@use
                        }
                        val from = movableFrom(
                            row, to,
                            row.getColumnIndexOrThrow(Contract.Transaction.TYPE),
                            row.getColumnIndexOrThrow(Contract.Transaction.WALLET_CURRENCY)
                        )
                        if (from == null) {
                            skipped++
                            return@use
                        }
                        val values = ContentValues()
                        values.put(Contract.Transaction.WALLET_ID, walletId)
                        if (to != null && from.decimals != to.decimals) {
                            val money = row.getLong(row.getColumnIndexOrThrow(Contract.Transaction.MONEY))
                            values.put(
                                Contract.Transaction.MONEY,
                                MoneyFormatter.normalize(money, from.decimals, to.decimals)
                            )
                            values.put(Contract.Transaction.FIX_SPLIT_DECIMALS, to.decimals - from.decimals)
                        }
                        resolver.update(uri, values, null, null)
                    }
                } catch (e: SQLiteDataException) {
                    skipped++
                }
            }
            return skipped
        }

        @JvmStatic
        fun changeCategory(
            resolver: ContentResolver,
            ids: LongArray,
            category: Category
        ): SkippedRows {
            val projection = arrayOf(
                Contract.Transaction.ID,
                Contract.Transaction.TYPE,
                Contract.Transaction.CATEGORY_ID,
                Contract.Transaction.DIRECTION
            )
            val split = querySplit(resolver, ids)
            var skipped = 0
            var splitSkipped = 0
            for (id in ids) {
                val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, id)
                try {
                    resolver.query(uri, projection, null, null, null)?.use { row ->
                        if (!row.moveToFirst() ||
                            row.getLong(row.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_ID)) == category.id
                        ) {
                            return@use
                        }
                        if (!isStandard(row, row.getColumnIndexOrThrow(Contract.Transaction.TYPE))) {
                            skipped++
                            return@use
                        }
                        if (keepsItsParts(row, split, category)) {
                            splitSkipped++
                            return@use
                        }
                        val values = ContentValues()
                        values.put(Contract.Transaction.CATEGORY_ID, category.id)
                        values.put(Contract.Transaction.DIRECTION, category.direction)
                        resolver.update(uri, values, null, null)
                    }
                } catch (e: SQLiteDataException) {
                    skipped++
                }
            }
            return SkippedRows(skipped, splitSkipped)
        }

        @JvmStatic
        fun buildSkippedMessage(resources: Resources, skipped: SkippedRows): String? {
            val message = StringBuilder()
            if (skipped.refused > 0) {
                message.append(
                    resources.getQuantityString(
                        R.plurals.message_error_change_selected_transactions,
                        skipped.refused,
                        skipped.refused
                    )
                )
            }
            if (skipped.split > 0) {
                if (message.isNotEmpty()) {
                    message.append("\n\n")
                }
                message.append(
                    resources.getQuantityString(
                        R.plurals.message_error_change_split_transactions,
                        skipped.split,
                        skipped.split
                    )
                )
            }
            return if (message.isEmpty()) null else message.toString()
        }

        private fun idsIn(column: String, ids: LongArray): String {
            val joined = StringBuilder()
            for (id in ids) {
                if (joined.isNotEmpty()) joined.append(",")
                joined.append(id)
            }
            return "$column IN ($joined)"
        }

        private fun querySplit(resolver: ContentResolver, ids: LongArray): Set<Long> {
            val split = HashSet<Long>()
            resolver.query(
                SyncContentProvider.CONTENT_TRANSACTION_SPLIT,
                arrayOf(Contract.TransactionSplit.TRANSACTION_ID),
                "${idsIn(Contract.TransactionSplit.TRANSACTION_ID, ids)} AND ${Contract.TransactionSplit.DELETED} = 0",
                null, null
            )?.use { parts ->
                while (parts.moveToNext()) {
                    split.add(parts.getLong(0))
                }
            }
            return split
        }

        private fun keepsItsParts(row: Cursor, split: Set<Long>, category: Category): Boolean {
            return split.contains(row.getLong(row.getColumnIndexOrThrow(Contract.Transaction.ID))) &&
                    row.getInt(row.getColumnIndexOrThrow(Contract.Transaction.DIRECTION)) != category.direction
        }

        private fun queryStored(resolver: ContentResolver, ids: LongArray, vararg projection: String): Cursor? {
            val joined = StringBuilder()
            for (id in ids) {
                if (joined.isNotEmpty()) joined.append(",")
                joined.append(id)
            }
            return resolver.query(
                DataContentProvider.CONTENT_TRANSACTIONS,
                projection,
                "${Contract.Transaction.ID} IN ($joined)",
                null,
                null
            )
        }

        private fun isStandard(row: Cursor, typeIndex: Int): Boolean {
            return row.getInt(typeIndex) == Contract.TransactionType.STANDARD
        }

        private fun movableFrom(row: Cursor, to: CurrencyUnit?, typeIndex: Int, currencyIndex: Int): CurrencyUnit? {
            val from = CurrencyManager.getCurrency(row.getString(currencyIndex))
            return if (isStandard(row, typeIndex) && to != null) from else null
        }
    }

    private val mHiddenItems = ArrayList<MenuItem>()
    private var mTitle: CharSequence? = null
    private var mNavigationDescription: CharSequence? = null
    private var mShown = false

    fun onSelectionChanged(count: Int) {
        val toolbar = mOwner.getPrimaryToolbar() ?: return
        if (count > 0) {
            if (!mShown) {
                show(toolbar)
            }
            toolbar.title = mOwner.getString(R.string.title_transactions_selected, count)
        } else if (mShown) {
            hide(toolbar)
        }
    }

    private fun show(toolbar: Toolbar) {
        mShown = true
        mTitle = toolbar.title
        mNavigationDescription = toolbar.navigationContentDescription
        val menu = toolbar.menu
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            if (item.isVisible) {
                item.isVisible = false
                mHiddenItems.add(item)
            }
        }
        toolbar.inflateMenu(R.menu.menu_transaction_selection)
        toolbar.setOnMenuItemClickListener(this)
        toolbar.setNavigationIcon(R.drawable.ic_clear_black_24dp)
        toolbar.setNavigationContentDescription(R.string.description_close_selection)
        toolbar.setNavigationOnClickListener { finish() }
        mOwner.selectionMode = this
    }

    private fun hide(toolbar: Toolbar) {
        mShown = false
        val menu = toolbar.menu
        menu.removeItem(R.id.action_delete_selected_items)
        menu.removeItem(R.id.action_move_selected_items)
        menu.removeItem(R.id.action_change_category_selected_items)
        menu.removeItem(R.id.action_select_all_items)
        for (item in mHiddenItems) {
            item.isVisible = true
        }
        mHiddenItems.clear()
        toolbar.setOnMenuItemClickListener(mOwner)
        toolbar.title = mTitle
        toolbar.navigationContentDescription = mNavigationDescription
        val activity = mOwner.activity
        if (activity is ToolbarController) {
            activity.setToolbar(toolbar)
        }
        mOwner.selectionMode = null
    }

    fun finish() {
        mAdapter.clearSelection()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == R.id.action_delete_selected_items) {
            showDeleteDialog(mListFragment.requireActivity())
            return true
        } else if (itemId == R.id.action_move_selected_items) {
            val fragmentManager = mListFragment.childFragmentManager
            val picker = WalletPicker.createPicker(fragmentManager, TAG_WALLET_PICKER, null as Wallet?)
            fragmentManager.executePendingTransactions()
            picker.showSingleWalletPicker()
            return true
        } else if (itemId == R.id.action_change_category_selected_items) {
            val fragmentManager = mListFragment.childFragmentManager
            val picker = CategoryPicker.createPicker(fragmentManager, TAG_CATEGORY_PICKER, null as Category?)
            fragmentManager.executePendingTransactions()
            picker.showPicker()
            return true
        } else if (itemId == R.id.action_select_all_items) {
            mAdapter.selectAll()
            return true
        }
        return false
    }

    fun onWalletPicked(wallet: Wallet?) {
        if (wallet == null || !removePicker(TAG_WALLET_PICKER)) {
            return
        }
        val ids = mAdapter.getSelectedIds()
        val iso = wallet.currency?.iso
        val context = mListFragment.requireActivity()
        val resources = context.resources
        val resolver = context.applicationContext.contentResolver
        sDeleteExecutor.execute {
            val message = buildMoveMessage(resources, resolver, ids, wallet.id, wallet.name, iso)
            sMainHandler.post {
                if (mListFragment.view == null) {
                    return@post
                }
                if (!isSameSelection(mAdapter.getSelectedIds(), ids)) {
                    showSelectionChangedToast()
                    return@post
                }
                showChangeDialog(mListFragment.requireActivity(), ids, message) {
                    SkippedRows(moveTransactions(resolver, ids, wallet.id, iso), 0)
                }
            }
        }
    }

    fun onCategoryPicked(category: Category?) {
        if (category == null || !removePicker(TAG_CATEGORY_PICKER)) {
            return
        }
        val ids = mAdapter.getSelectedIds()
        val context = mListFragment.requireActivity()
        val resources = context.resources
        val resolver = context.applicationContext.contentResolver
        sDeleteExecutor.execute {
            val message = buildCategoryMessage(resources, resolver, ids, category)
            sMainHandler.post {
                if (mListFragment.view == null) {
                    return@post
                }
                if (!isSameSelection(mAdapter.getSelectedIds(), ids)) {
                    showSelectionChangedToast()
                    return@post
                }
                showChangeDialog(mListFragment.requireActivity(), ids, message) {
                    changeCategory(resolver, ids, category)
                }
            }
        }
    }

    private fun removePicker(tag: String): Boolean {
        if (!mListFragment.isAdded) {
            return false
        }
        val fragmentManager = mListFragment.childFragmentManager
        val picker = fragmentManager.findFragmentByTag(tag) ?: return false
        sMainHandler.post {
            if (!fragmentManager.isDestroyed) {
                fragmentManager.beginTransaction().remove(picker).commitAllowingStateLoss()
            }
        }
        return true
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putLongArray(SS_SELECTED_IDS, mAdapter.getSelectedIds())
    }

    fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        val ids = savedInstanceState?.getLongArray(SS_SELECTED_IDS)
        if (ids != null && ids.isNotEmpty()) {
            mAdapter.setSelectedIds(ids)
        }
    }

    private fun showDeleteDialog(context: Context) {
        val ids = mAdapter.getSelectedIds()
        ThemedDialog.buildMaterialDialog(context)
            .setTitle(R.string.title_warning)
            .setMessage(
                context.resources.getQuantityString(
                    R.plurals.message_delete_selected_transactions, ids.size, ids.size
                )
            )
            .setPositiveButton(android.R.string.ok) { _, _ -> delete(context, ids) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun delete(context: Context, ids: LongArray) {
        val resolver = context.applicationContext.contentResolver
        finish()
        sDeleteExecutor.execute {
            val refused = deleteTransactions(resolver, ids)
            sMainHandler.post {
                if (mListFragment.view == null) {
                    return@post
                }
                closePanelShowingAnyOf(ids, refused)
                if (refused.isNotEmpty()) {
                    ThemedDialog.buildMaterialDialog(mListFragment.requireActivity())
                        .setTitle(R.string.title_error)
                        .setMessage(R.string.message_error_delete_transaction_of_transfer)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    private fun showChangeDialog(context: Context, ids: LongArray, message: String, write: () -> SkippedRows) {
        ThemedDialog.buildMaterialDialog(context)
            .setTitle(R.string.title_warning)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> change(ids, write) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun change(ids: LongArray, write: () -> SkippedRows) {
        if (!isSameSelection(mAdapter.getSelectedIds(), ids)) {
            showSelectionChangedToast()
            return
        }
        finish()
        sDeleteExecutor.execute {
            val skipped = write()
            sMainHandler.post {
                if (mListFragment.view == null) {
                    return@post
                }
                val message = buildSkippedMessage(mListFragment.resources, skipped) ?: return@post
                ThemedDialog.buildMaterialDialog(mListFragment.requireActivity())
                    .setTitle(R.string.title_error)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    private fun showSelectionChangedToast() {
        Toast.makeText(mListFragment.context, R.string.message_selection_changed_nothing_changed, Toast.LENGTH_SHORT).show()
    }

    private fun closePanelShowingAnyOf(ids: LongArray, refused: Set<Long>) {
        for (fragment in mOwner.childFragmentManager.fragments) {
            if (fragment !is TransactionItemFragment) {
                continue
            }
            val panel = fragment as SecondaryPanelFragment
            val shownId = panel.getItemId()
            for (id in ids) {
                if (id == shownId && !refused.contains(id)) {
                    panel.navigateBackSafely()
                    panel.showItemId(0L)
                }
            }
        }
    }
}
