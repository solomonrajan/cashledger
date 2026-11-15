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
import android.content.ContentValues
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
import android.widget.ImageView
import android.widget.TextView
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.activity.NewEditDebtActivity
import com.oriondev.moneywallet.ui.activity.NewEditItemActivity
import com.oriondev.moneywallet.ui.activity.TransactionListActivity
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.MoneyFormatter
import java.util.Date

/**
 * Created by andrea on 03/04/18.
 */
class DebtItemFragment : SecondaryPanelFragment(), LoaderManager.LoaderCallbacks<Cursor> {

    private var mProgressLayout: View? = null
    private var mMainLayout: View? = null

    private var mAvatarImageView: ImageView? = null
    private var mCurrencyTextView: TextView? = null
    private var mMoneyTextView: TextView? = null

    private var mDescriptionTextView: TextView? = null
    private var mDateTextView: TextView? = null
    private var mExpirationDateTextView: TextView? = null
    private var mWalletTextView: TextView? = null
    private var mPeopleTextView: TextView? = null
    private var mPlaceTextView: TextView? = null
    private var mNoteTextView: TextView? = null

    private val mMoneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()

    override fun onCreateHeaderView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_header_show_icon_money_item, parent, true)
        mAvatarImageView = view.findViewById(R.id.avatar_image_view)
        mCurrencyTextView = view.findViewById(R.id.currency_text_view)
        mMoneyTextView = view.findViewById(R.id.money_text_view)
    }

    override fun onCreateBodyView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_panel_show_debt_item, parent, true)
        mProgressLayout = view.findViewById(R.id.secondary_panel_progress_wheel)
        mMainLayout = view.findViewById(R.id.secondary_panel_layout)
        mDescriptionTextView = view.findViewById(R.id.description_text_view)
        mDateTextView = view.findViewById(R.id.date_text_view)
        mExpirationDateTextView = view.findViewById(R.id.expiration_date_text_view)
        mWalletTextView = view.findViewById(R.id.wallet_text_view)
        mPeopleTextView = view.findViewById(R.id.people_text_view)
        mPlaceTextView = view.findViewById(R.id.place_text_view)
        mNoteTextView = view.findViewById(R.id.note_text_view)
    }

    override fun getTitle(): String {
        return getString(R.string.title_fragment_item_debt)
    }

    override fun onInflateMenu(): Int {
        return R.menu.menu_list_archive_edit_delete_item
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == R.id.action_show_transaction_list) {
            val intent = Intent(activity, TransactionListActivity::class.java).apply {
                putExtra(TransactionListActivity.DEBT_ID, getItemId())
            }
            startActivity(intent)
        } else if (itemId == R.id.action_archive_item) {
            activity?.let { showArchiveDialog(it) }
        } else if (itemId == R.id.action_unarchive_item) {
            activity?.let { showUnarchiveDialog(it) }
        } else if (itemId == R.id.action_edit_item) {
            val intent = Intent(activity, NewEditDebtActivity::class.java).apply {
                putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.EDIT_ITEM)
                putExtra(NewEditItemActivity.ID, getItemId())
            }
            startActivity(intent)
        } else if (itemId == R.id.action_delete_item) {
            activity?.let { showDeleteDialog(it) }
        }
        return false
    }

    private fun showArchiveDialog(context: Context) {
        ThemedDialog.buildMaterialDialog(context)
            .setTitle(R.string.title_warning)
            .setMessage(R.string.message_action_archive_debt)
            .setPositiveButton(android.R.string.yes) { _, _ ->
                val act = activity
                if (act != null) {
                    val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_DEBTS, getItemId())
                    val contentValues = ContentValues().apply {
                        put(Contract.Debt.ARCHIVED, true)
                    }
                    val rows = act.contentResolver.update(uri, contentValues, null, null)
                    if (rows > 0) {
                        setMenuItemVisibility(R.id.action_archive_item, false)
                        setMenuItemVisibility(R.id.action_unarchive_item, true)
                    }
                }
            }
            .setNegativeButton(android.R.string.no, null)
            .show()
    }

    private fun showUnarchiveDialog(context: Context) {
        ThemedDialog.buildMaterialDialog(context)
            .setTitle(R.string.title_warning)
            .setMessage(R.string.message_action_unarchive_debt)
            .setPositiveButton(android.R.string.yes) { _, _ ->
                val act = activity
                if (act != null) {
                    val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_DEBTS, getItemId())
                    val contentValues = ContentValues().apply {
                        put(Contract.Debt.ARCHIVED, false)
                    }
                    val rows = act.contentResolver.update(uri, contentValues, null, null)
                    if (rows > 0) {
                        setMenuItemVisibility(R.id.action_archive_item, true)
                        setMenuItemVisibility(R.id.action_unarchive_item, false)
                    }
                }
            }
            .setNegativeButton(android.R.string.no, null)
            .show()
    }

    private fun showDeleteDialog(context: Context) {
        ThemedDialog.buildMaterialDialog(context)
            .setTitle(R.string.title_warning)
            .setMessage(R.string.message_delete_debt)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val act = activity
                if (act != null) {
                    val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_DEBTS, getItemId())
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
        LoaderManager.getInstance(this).restartLoader(DEBT_LOADER_ID, null, this)
        LoaderManager.getInstance(this).restartLoader(PEOPLE_LOADER_ID, null, this)
    }

    private fun setLoadingScreen(loading: Boolean) {
        if (loading) {
            mAvatarImageView?.setImageDrawable(null)
            mCurrencyTextView?.text = null
            mMoneyTextView?.text = null
            mDescriptionTextView?.text = null
            mDateTextView?.text = null
            mExpirationDateTextView?.text = null
            mWalletTextView?.text = null
            mPeopleTextView?.text = null
            mPlaceTextView?.text = null
            mNoteTextView?.text = null
            mProgressLayout?.visibility = View.VISIBLE
            mMainLayout?.visibility = View.GONE
            setMenuItemVisibility(R.id.action_archive_item, false)
            setMenuItemVisibility(R.id.action_unarchive_item, false)
        } else {
            mProgressLayout?.visibility = View.GONE
            mMainLayout?.visibility = View.VISIBLE
        }
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor>? {
        val act = activity ?: return null
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_DEBTS, getItemId())
        return if (id == DEBT_LOADER_ID) {
            val projection = arrayOf(
                Contract.Debt.ID,
                Contract.Debt.ICON,
                Contract.Debt.WALLET_CURRENCY,
                Contract.Debt.MONEY,
                Contract.Debt.DESCRIPTION,
                Contract.Debt.DATE,
                Contract.Debt.EXPIRATION_DATE,
                Contract.Debt.WALLET_NAME,
                Contract.Debt.PLACE_NAME,
                Contract.Debt.NOTE,
                Contract.Debt.ARCHIVED
            )
            CursorLoader(act, uri, projection, null, null, null)
        } else if (id == PEOPLE_LOADER_ID) {
            val projection = arrayOf("*")
            CursorLoader(act, Uri.withAppendedPath(uri, "people"), projection, null, null, null)
        } else {
            null
        }
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        if (loader.id == DEBT_LOADER_ID) {
            if (cursor != null && cursor.moveToFirst()) {
                val icon: Icon? = IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.ICON)))
                mAvatarImageView?.let { IconLoader.loadInto(icon, it) }
                val iso = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.WALLET_CURRENCY))
                val currency = CurrencyManager.getCurrency(iso)
                if (currency != null) {
                    mCurrencyTextView?.text = currency.symbol
                } else {
                    mCurrencyTextView?.text = "?"
                }
                val money = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Debt.MONEY))
                mMoneyTextView?.text = mMoneyFormatter.getNotTintedString(currency, money, MoneyFormatter.CurrencyMode.ALWAYS_HIDDEN)
                val description = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.DESCRIPTION))
                if (!TextUtils.isEmpty(description)) {
                    mDescriptionTextView?.text = description
                    mDescriptionTextView?.visibility = View.VISIBLE
                } else {
                    mDescriptionTextView?.visibility = View.GONE
                }
                val date: Date? = DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.DATE)))
                date?.let { d -> mDateTextView?.let { tv -> DateFormatter.applyDate(tv, d) } }
                val expirationDate = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.EXPIRATION_DATE))
                if (!TextUtils.isEmpty(expirationDate)) {
                    val expDate = DateUtils.getDateFromSQLDateString(expirationDate)
                    if (expDate != null) {
                        mExpirationDateTextView?.let { tv ->
                            DateFormatter.applyDate(tv, expDate)
                        }
                    }
                    mExpirationDateTextView?.visibility = View.VISIBLE
                } else {
                    mExpirationDateTextView?.visibility = View.GONE
                }
                val wallet = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.WALLET_NAME))
                mWalletTextView?.text = wallet
                val place = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.PLACE_NAME))
                if (!TextUtils.isEmpty(place)) {
                    mPlaceTextView?.text = place
                    mPlaceTextView?.visibility = View.VISIBLE
                } else {
                    mPlaceTextView?.visibility = View.GONE
                }
                val note = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.NOTE))
                if (!TextUtils.isEmpty(note)) {
                    mNoteTextView?.text = note
                    mNoteTextView?.visibility = View.VISIBLE
                } else {
                    mNoteTextView?.visibility = View.GONE
                }
                if (cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Debt.ARCHIVED)) == 1) {
                    setMenuItemVisibility(R.id.action_archive_item, false)
                    setMenuItemVisibility(R.id.action_unarchive_item, true)
                } else {
                    setMenuItemVisibility(R.id.action_archive_item, true)
                    setMenuItemVisibility(R.id.action_unarchive_item, false)
                }
            } else {
                showItemId(0L)
            }
            setLoadingScreen(false)
        } else if (loader.id == PEOPLE_LOADER_ID) {
            if (cursor != null && cursor.moveToFirst()) {
                val builder = StringBuilder()
                var i = 0
                while (cursor.moveToPosition(i) && i < cursor.count) {
                    if (i != 0) {
                        builder.append(", ")
                    }
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Person.NAME))
                    builder.append(name)
                    i++
                }
                mPeopleTextView?.text = builder
                mPeopleTextView?.visibility = View.VISIBLE
            } else {
                mPeopleTextView?.visibility = View.GONE
            }
        }
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        // nothing to release
    }

    companion object {
        private const val DEBT_LOADER_ID = 53321
        private const val PEOPLE_LOADER_ID = 53322
    }
}
