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

package com.oriondev.moneywallet.ui.fragment.secondary

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.RecurrenceSetting
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.activity.NewEditBudgetActivity
import com.oriondev.moneywallet.ui.activity.NewEditItemActivity
import com.oriondev.moneywallet.ui.activity.TransactionListActivity
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.MoneyFormatter

/**
 * Created by andrea on 03/04/18.
 */
class BudgetItemFragment : SecondaryPanelFragment(), LoaderManager.LoaderCallbacks<Cursor> {

    private var mProgressLayout: View? = null
    private var mMainLayout: View? = null

    private var mCurrencyTextView: TextView? = null
    private var mMoneyTextView: TextView? = null

    private var mTypeTextView: TextView? = null
    private var mCategoryTextView: TextView? = null
    private var mStartDateTextView: TextView? = null
    private var mEndDateTextView: TextView? = null
    private var mWalletsTextView: TextView? = null

    private var mRecurrenceTextView: TextView? = null

    private val mMoneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()

    override fun onCreateHeaderView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_header_show_money_item, parent, true)
        mCurrencyTextView = view.findViewById(R.id.currency_text_view)
        mMoneyTextView = view.findViewById(R.id.money_text_view)
    }

    override fun onCreateBodyView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_panel_show_budget_item, parent, true)
        mProgressLayout = view.findViewById(R.id.secondary_panel_progress_wheel)
        mMainLayout = view.findViewById(R.id.secondary_panel_layout)
        mTypeTextView = view.findViewById(R.id.type_text_view)
        mCategoryTextView = view.findViewById(R.id.category_text_view)
        mRecurrenceTextView = view.findViewById(R.id.recurrence_text_view)
        mStartDateTextView = view.findViewById(R.id.start_date_text_view)
        mEndDateTextView = view.findViewById(R.id.end_date_text_view)
        mWalletsTextView = view.findViewById(R.id.wallets_text_view)
    }

    override fun getTitle(): String {
        return getString(R.string.title_fragment_item_budget)
    }

    override fun onInflateMenu(): Int {
        return R.menu.menu_list_edit_delete_item
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == R.id.action_show_transaction_list) {
            val intent = Intent(activity, TransactionListActivity::class.java).apply {
                putExtra(TransactionListActivity.BUDGET_ID, getItemId())
            }
            startActivity(intent)
        } else if (itemId == R.id.action_edit_item) {
            val intent = Intent(activity, NewEditBudgetActivity::class.java).apply {
                putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.EDIT_ITEM)
                putExtra(NewEditItemActivity.ID, getItemId())
            }
            startActivity(intent)
        } else if (itemId == R.id.action_delete_item) {
            activity?.let { showDeleteDialog(it) }
        }
        return false
    }

    private fun showDeleteDialog(context: Context) {
        ThemedDialog.buildMaterialDialog(context)
            .setTitle(R.string.title_warning)
            .setMessage(R.string.message_delete_budget)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val act = activity
                if (act != null) {
                    val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, getItemId())
                    val contentResolver: ContentResolver = act.contentResolver
                    contentResolver.delete(uri, null, null)
                    navigateBackSafely()
                    showItemId(0L)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onShowItemId(itemId: Long) {
        setLoadingScreen(true)
        LoaderManager.getInstance(this).restartLoader(BUDGET_LOADER_ID, null, this)
        LoaderManager.getInstance(this).restartLoader(WALLETS_LOADER_ID, null, this)
    }

    private fun setLoadingScreen(loading: Boolean) {
        if (loading) {
            mCurrencyTextView?.text = null
            mMoneyTextView?.text = null
            mTypeTextView?.text = null
            mCategoryTextView?.text = null
            mStartDateTextView?.text = null
            mEndDateTextView?.text = null
            mWalletsTextView?.text = null
            mProgressLayout?.visibility = View.VISIBLE
            mMainLayout?.visibility = View.GONE
        } else {
            mProgressLayout?.visibility = View.GONE
            mMainLayout?.visibility = View.VISIBLE
        }
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor>? {
        val act = activity ?: return null
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, getItemId())
        return if (id == BUDGET_LOADER_ID) {
            val projection = arrayOf(
                Contract.Budget.ID,
                Contract.Budget.CURRENCY,
                Contract.Budget.MONEY,
                Contract.Budget.TYPE,
                Contract.Budget.CATEGORY_NAME,
                Contract.Budget.START_DATE,
                Contract.Budget.END_DATE,
                Contract.Budget.RULE,
                Contract.Budget.RULE_START
            )
            CursorLoader(act, uri, projection, null, null, null)
        } else if (id == WALLETS_LOADER_ID) {
            val projection = arrayOf("*")
            CursorLoader(act, Uri.withAppendedPath(uri, "wallets"), projection, null, null, null)
        } else {
            null
        }
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        if (loader.id == BUDGET_LOADER_ID) {
            if (cursor != null && cursor.moveToFirst()) {
                val iso = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.CURRENCY))
                val currency = CurrencyManager.getCurrency(iso)
                if (currency != null) {
                    mCurrencyTextView?.text = currency.symbol
                } else {
                    mCurrencyTextView?.text = "?"
                }
                val money = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Budget.MONEY))
                mMoneyTextView?.text = mMoneyFormatter.getNotTintedString(currency, money, MoneyFormatter.CurrencyMode.ALWAYS_HIDDEN)
                val budgetType = Contract.BudgetType.fromValue(cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Budget.TYPE)))
                if (budgetType != null) {
                    when (budgetType) {
                        Contract.BudgetType.INCOMES -> {
                            mTypeTextView?.setText(R.string.hint_incomes)
                            mCategoryTextView?.visibility = View.GONE
                        }
                        Contract.BudgetType.EXPENSES -> {
                            mTypeTextView?.setText(R.string.hint_expenses)
                            mCategoryTextView?.visibility = View.GONE
                        }
                        Contract.BudgetType.CATEGORY -> {
                            mTypeTextView?.setText(R.string.hint_category)
                            mCategoryTextView?.text = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.CATEGORY_NAME))
                            mCategoryTextView?.visibility = View.VISIBLE
                        }
                    }
                }
                val startDate = DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
                val endDate = DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
                mStartDateTextView?.let { DateFormatter.applyDate(it, startDate) }
                mEndDateTextView?.let { DateFormatter.applyDate(it, endDate) }
                val rule = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.RULE))
                val ruleStartString = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.RULE_START))
                val anchor = if (ruleStartString != null) DateUtils.getDateFromSQLDateString(ruleStartString) else startDate
                if (rule != null && RecurrenceSetting.periodEnd(rule, anchor, anchor) != null) {
                    mRecurrenceTextView?.text = RecurrenceSetting(anchor, rule).getUserReadableString(activity)
                    mRecurrenceTextView?.visibility = View.VISIBLE
                } else {
                    mRecurrenceTextView?.visibility = View.GONE
                }
            } else {
                showItemId(0L)
            }
            setLoadingScreen(false)
        } else if (loader.id == WALLETS_LOADER_ID) {
            if (cursor != null && cursor.moveToFirst()) {
                val builder = StringBuilder()
                var i = 0
                while (cursor.moveToPosition(i) && i < cursor.count) {
                    if (i != 0) {
                        builder.append(", ")
                    }
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.NAME))
                    builder.append(name)
                    i++
                }
                mWalletsTextView?.text = builder
                mWalletsTextView?.visibility = View.VISIBLE
            } else {
                mWalletsTextView?.visibility = View.GONE
            }
        }
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        // nothing to release
    }

    companion object {
        private const val BUDGET_LOADER_ID = 53320
        private const val WALLETS_LOADER_ID = 53321
    }
}
