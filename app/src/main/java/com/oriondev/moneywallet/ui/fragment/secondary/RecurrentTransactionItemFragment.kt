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
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.broadcast.RecurrenceBroadcastReceiver
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.RecurrenceSetting
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.activity.NewEditItemActivity
import com.oriondev.moneywallet.ui.activity.NewEditRecurrentTransactionActivity
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.MoneyFormatter
import java.util.Date
import org.dmfs.rfc5545.recur.InvalidRecurrenceRuleException

/**
 * Created by andrea on 04/11/18.
 */
class RecurrentTransactionItemFragment : SecondaryPanelFragment(), LoaderManager.LoaderCallbacks<Cursor> {

    private var mProgressLayout: View? = null
    private var mMainLayout: View? = null

    private var mCurrencyTextView: TextView? = null
    private var mMoneyTextView: TextView? = null

    private var mDescriptionTextView: TextView? = null
    private var mCategoryTextView: TextView? = null
    private var mWalletTextView: TextView? = null
    private var mRecurrenceTextView: TextView? = null
    private var mEventTextView: TextView? = null
    private var mPlaceTextView: TextView? = null
    private var mNoteTextView: TextView? = null
    private var mConfirmedCheckBox: CheckBox? = null
    private var mCountInTotalCheckBox: CheckBox? = null

    private val mMoneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()

    override fun onCreateHeaderView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_header_show_money_item, parent, true)
        mCurrencyTextView = view.findViewById(R.id.currency_text_view)
        mMoneyTextView = view.findViewById(R.id.money_text_view)
    }

    override fun onCreateBodyView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_panel_show_recurrent_transaction_item, parent, true)
        mProgressLayout = view.findViewById(R.id.secondary_panel_progress_wheel)
        mMainLayout = view.findViewById(R.id.secondary_panel_layout)
        mDescriptionTextView = view.findViewById(R.id.description_text_view)
        mCategoryTextView = view.findViewById(R.id.category_text_view)
        mWalletTextView = view.findViewById(R.id.wallet_text_view)
        mRecurrenceTextView = view.findViewById(R.id.recurrence_text_view)
        mEventTextView = view.findViewById(R.id.event_text_view)
        mPlaceTextView = view.findViewById(R.id.place_text_view)
        mNoteTextView = view.findViewById(R.id.note_text_view)
        mConfirmedCheckBox = view.findViewById(R.id.confirmed_checkbox)
        mCountInTotalCheckBox = view.findViewById(R.id.count_in_total_checkbox)
    }

    override fun getTitle(): String {
        return getString(R.string.title_fragment_item_recurrence)
    }

    override fun onInflateMenu(): Int {
        return R.menu.menu_edit_delete_item
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == R.id.action_edit_item) {
            val intent = Intent(activity, NewEditRecurrentTransactionActivity::class.java).apply {
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
            .setMessage(R.string.message_delete_transaction_model)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val act = activity
                if (act != null) {
                    val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS, getItemId())
                    val contentResolver: ContentResolver = act.contentResolver
                    contentResolver.delete(uri, null, null)
                    RecurrenceBroadcastReceiver.scheduleRecurrenceTask(act)
                    navigateBackSafely()
                    showItemId(0L)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onShowItemId(itemId: Long) {
        setLoadingScreen(true)
        LoaderManager.getInstance(this).restartLoader(RECURRENT_TRANSACTION_LOADER_ID, null, this)
    }

    private fun setLoadingScreen(loading: Boolean) {
        if (loading) {
            mMoneyTextView?.text = null
            mCurrencyTextView?.text = null
            mDescriptionTextView?.text = null
            mCategoryTextView?.text = null
            mWalletTextView?.text = null
            mRecurrenceTextView?.text = null
            mEventTextView?.text = null
            mPlaceTextView?.text = null
            mNoteTextView?.text = null
            mConfirmedCheckBox?.text = null
            mCountInTotalCheckBox?.text = null
            mProgressLayout?.visibility = View.VISIBLE
            mMainLayout?.visibility = View.GONE
        } else {
            mProgressLayout?.visibility = View.GONE
            mMainLayout?.visibility = View.VISIBLE
        }
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        val act = activity ?: throw IllegalStateException("Activity is null")
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS, getItemId())
        val projection = arrayOf("*")
        return CursorLoader(act, uri, projection, null, null, null)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        if (cursor != null && cursor.moveToFirst()) {
            val iso = cursor.getString(cursor.getColumnIndex(Contract.RecurrentTransaction.WALLET_CURRENCY))
            val currency = CurrencyManager.getCurrency(iso)
            if (currency != null) {
                mCurrencyTextView?.text = currency.symbol
            } else {
                mCurrencyTextView?.text = "?"
            }
            val money = cursor.getLong(cursor.getColumnIndex(Contract.RecurrentTransaction.MONEY))
            mMoneyTextView?.text = mMoneyFormatter.getNotTintedString(currency, money, MoneyFormatter.CurrencyMode.ALWAYS_HIDDEN)
            val description = cursor.getString(cursor.getColumnIndex(Contract.RecurrentTransaction.DESCRIPTION))
            if (!TextUtils.isEmpty(description)) {
                mDescriptionTextView?.text = description
                mDescriptionTextView?.visibility = View.VISIBLE
            } else {
                mDescriptionTextView?.visibility = View.GONE
            }
            val category = cursor.getString(cursor.getColumnIndex(Contract.RecurrentTransaction.CATEGORY_NAME))
            mCategoryTextView?.text = category
            val wallet = cursor.getString(cursor.getColumnIndex(Contract.RecurrentTransaction.WALLET_NAME))
            mWalletTextView?.text = wallet
            try {
                val startDate: Date? = DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndex(Contract.RecurrentTransaction.START_DATE)))
                val rule = cursor.getString(cursor.getColumnIndex(Contract.RecurrentTransaction.RULE))
                if (startDate != null && rule != null) {
                    val recurrenceSetting = RecurrenceSetting.build(startDate, rule)
                    mRecurrenceTextView?.text = recurrenceSetting.getUserReadableString(activity)
                } else {
                    mRecurrenceTextView?.text = null
                }
            } catch (e: InvalidRecurrenceRuleException) {
                mRecurrenceTextView?.setText(R.string.hint_recurrence_invalid)
            }
            val event = cursor.getString(cursor.getColumnIndex(Contract.RecurrentTransaction.EVENT_NAME))
            if (!TextUtils.isEmpty(event)) {
                mEventTextView?.text = event
                mEventTextView?.visibility = View.VISIBLE
            } else {
                mEventTextView?.visibility = View.GONE
            }
            val place = cursor.getString(cursor.getColumnIndex(Contract.RecurrentTransaction.PLACE_NAME))
            if (!TextUtils.isEmpty(place)) {
                mPlaceTextView?.text = place
                mPlaceTextView?.visibility = View.VISIBLE
            } else {
                mPlaceTextView?.visibility = View.GONE
            }
            val note = cursor.getString(cursor.getColumnIndex(Contract.RecurrentTransaction.NOTE))
            if (!TextUtils.isEmpty(note)) {
                mNoteTextView?.text = note
                mNoteTextView?.visibility = View.VISIBLE
            } else {
                mNoteTextView?.visibility = View.GONE
            }
            mConfirmedCheckBox?.isChecked = cursor.getInt(cursor.getColumnIndex(Contract.RecurrentTransaction.CONFIRMED)) == 1
            mCountInTotalCheckBox?.isChecked = cursor.getInt(cursor.getColumnIndex(Contract.RecurrentTransaction.COUNT_IN_TOTAL)) == 1
        } else {
            showItemId(0L)
        }
        setLoadingScreen(false)
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        // nothing to release
    }

    companion object {
        private const val RECURRENT_TRANSACTION_LOADER_ID = 57325
    }
}
