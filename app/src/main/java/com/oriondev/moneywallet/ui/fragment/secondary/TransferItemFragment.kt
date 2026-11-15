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
import com.oriondev.moneywallet.model.Attachment
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.activity.NewEditItemActivity
import com.oriondev.moneywallet.ui.activity.NewEditTransferActivity
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment
import com.oriondev.moneywallet.ui.view.AttachmentView
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.MoneyFormatter
import java.util.ArrayList
import java.util.Date

/**
 * Created by andrea on 03/04/18.
 */
class TransferItemFragment :
    SecondaryPanelFragment(),
    AttachmentView.Controller,
    LoaderManager.LoaderCallbacks<Cursor> {

    private var mProgressLayout: View? = null
    private var mMainLayout: View? = null

    private var mCurrencyTextView: TextView? = null
    private var mMoneyTextView: TextView? = null

    private var mDescriptionTextView: TextView? = null
    private var mDateTimeTextView: TextView? = null
    private var mWalletFromTextView: TextView? = null
    private var mWalletToTextView: TextView? = null
    private var mTaxTextView: TextView? = null
    private var mEventTextView: TextView? = null
    private var mPeopleTextView: TextView? = null
    private var mPlaceTextView: TextView? = null
    private var mNoteTextView: TextView? = null
    private var mConfirmedCheckBox: CheckBox? = null
    private var mCountInTotalCheckBox: CheckBox? = null
    private var mAttachmentView: AttachmentView? = null

    private val mMoneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()

    override fun onCreateHeaderView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_header_show_money_item, parent, true)
        mCurrencyTextView = view.findViewById(R.id.currency_text_view)
        mMoneyTextView = view.findViewById(R.id.money_text_view)
    }

    override fun onCreateBodyView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_panel_show_transfer_item, parent, true)
        mProgressLayout = view.findViewById(R.id.secondary_panel_progress_wheel)
        mMainLayout = view.findViewById(R.id.secondary_panel_layout)
        mDescriptionTextView = view.findViewById(R.id.description_text_view)
        mDateTimeTextView = view.findViewById(R.id.date_time_text_view)
        mWalletFromTextView = view.findViewById(R.id.wallet_from_text_view)
        mWalletToTextView = view.findViewById(R.id.wallet_to_text_view)
        mTaxTextView = view.findViewById(R.id.tax_text_view)
        mEventTextView = view.findViewById(R.id.event_text_view)
        mPeopleTextView = view.findViewById(R.id.people_text_view)
        mPlaceTextView = view.findViewById(R.id.place_text_view)
        mNoteTextView = view.findViewById(R.id.note_text_view)
        mConfirmedCheckBox = view.findViewById(R.id.confirmed_checkbox)
        mCountInTotalCheckBox = view.findViewById(R.id.count_in_total_checkbox)
        val attachmentView: AttachmentView = view.findViewById(R.id.attachment_view)
        mAttachmentView = attachmentView
        attachmentView.setAllowRemove(false)
        attachmentView.setController(this)
    }

    override fun getTitle(): String {
        return getString(R.string.title_fragment_item_transfer)
    }

    override fun onInflateMenu(): Int {
        return R.menu.menu_edit_delete_item
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == R.id.action_edit_item) {
            val intent = Intent(activity, NewEditTransferActivity::class.java).apply {
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
            .setMessage(R.string.message_delete_transfer)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSFERS, getItemId())
                deleteItemInBackground(uri, null)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onShowItemId(itemId: Long) {
        setLoadingScreen(true)
        LoaderManager.getInstance(this).restartLoader(TRANSFER_LOADER_ID, null, this)
        LoaderManager.getInstance(this).restartLoader(PEOPLE_LOADER_ID, null, this)
        LoaderManager.getInstance(this).restartLoader(ATTACHMENTS_LOADER_ID, null, this)
    }

    private fun setLoadingScreen(loading: Boolean) {
        if (loading) {
            mMoneyTextView?.text = null
            mCurrencyTextView?.text = null
            mDescriptionTextView?.text = null
            mDateTimeTextView?.text = null
            mWalletFromTextView?.text = null
            mWalletToTextView?.text = null
            mTaxTextView?.text = null
            mEventTextView?.text = null
            mPeopleTextView?.text = null
            mPlaceTextView?.text = null
            mNoteTextView?.text = null
            mConfirmedCheckBox?.text = null
            mCountInTotalCheckBox?.text = null
            mAttachmentView?.setAttachments(null)
            mProgressLayout?.visibility = View.VISIBLE
            mMainLayout?.visibility = View.GONE
        } else {
            mProgressLayout?.visibility = View.GONE
            mMainLayout?.visibility = View.VISIBLE
        }
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor>? {
        val act = activity ?: return null
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSFERS, getItemId())
        return if (id == TRANSFER_LOADER_ID) {
            val projection = arrayOf("*")
            CursorLoader(act, uri, projection, null, null, null)
        } else if (id == PEOPLE_LOADER_ID) {
            val projection = arrayOf("*")
            CursorLoader(act, Uri.withAppendedPath(uri, "people"), projection, null, null, null)
        } else if (id == ATTACHMENTS_LOADER_ID) {
            val projection = arrayOf(
                Contract.Attachment.ID,
                Contract.Attachment.FILE,
                Contract.Attachment.NAME,
                Contract.Attachment.TYPE,
                Contract.Attachment.SIZE
            )
            CursorLoader(act, Uri.withAppendedPath(uri, "attachments"), projection, null, null, null)
        } else {
            null
        }
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        if (loader.id == TRANSFER_LOADER_ID) {
            if (cursor != null && cursor.moveToFirst()) {
                val iso = cursor.getString(cursor.getColumnIndex(Contract.Transfer.TRANSACTION_FROM_WALLET_CURRENCY))
                val currency = CurrencyManager.getCurrency(iso)
                if (currency != null) {
                    mCurrencyTextView?.text = currency.symbol
                } else {
                    mCurrencyTextView?.text = "?"
                }
                val money = cursor.getLong(cursor.getColumnIndex(Contract.Transfer.TRANSACTION_FROM_MONEY))
                mMoneyTextView?.text = mMoneyFormatter.getNotTintedString(currency, money, MoneyFormatter.CurrencyMode.ALWAYS_HIDDEN)
                val description = cursor.getString(cursor.getColumnIndex(Contract.Transfer.DESCRIPTION))
                if (!TextUtils.isEmpty(description)) {
                    mDescriptionTextView?.text = description
                    mDescriptionTextView?.visibility = View.VISIBLE
                } else {
                    mDescriptionTextView?.visibility = View.GONE
                }
                val datetime: Date? = DateUtils.getDateFromSQLDateTimeString(cursor.getString(cursor.getColumnIndex(Contract.Transfer.DATE)))
                mDateTimeTextView?.let { DateFormatter.applyDateTime(it, datetime) }
                val walletFrom = cursor.getString(cursor.getColumnIndex(Contract.Transfer.TRANSACTION_FROM_WALLET_NAME))
                val walletTo = cursor.getString(cursor.getColumnIndex(Contract.Transfer.TRANSACTION_TO_WALLET_NAME))
                mWalletFromTextView?.text = walletFrom
                mWalletToTextView?.text = walletTo
                if (!cursor.isNull(cursor.getColumnIndex(Contract.Transfer.TRANSACTION_TAX_ID))) {
                    val tax = cursor.getLong(cursor.getColumnIndex(Contract.Transfer.TRANSACTION_TAX_MONEY))
                    mTaxTextView?.text = mMoneyFormatter.getNotTintedString(currency, tax)
                    mTaxTextView?.visibility = View.VISIBLE
                } else {
                    mTaxTextView?.visibility = View.GONE
                }
                val event = cursor.getString(cursor.getColumnIndex(Contract.Transfer.EVENT_NAME))
                if (!TextUtils.isEmpty(event)) {
                    mEventTextView?.text = event
                    mEventTextView?.visibility = View.VISIBLE
                } else {
                    mEventTextView?.visibility = View.GONE
                }
                val place = cursor.getString(cursor.getColumnIndex(Contract.Transfer.PLACE_NAME))
                if (!TextUtils.isEmpty(place)) {
                    mPlaceTextView?.text = place
                    mPlaceTextView?.visibility = View.VISIBLE
                } else {
                    mPlaceTextView?.visibility = View.GONE
                }
                val note = cursor.getString(cursor.getColumnIndex(Contract.Transfer.NOTE))
                if (!TextUtils.isEmpty(note)) {
                    mNoteTextView?.text = note
                    mNoteTextView?.visibility = View.VISIBLE
                } else {
                    mNoteTextView?.visibility = View.GONE
                }
                mConfirmedCheckBox?.isChecked = cursor.getInt(cursor.getColumnIndex(Contract.Transfer.CONFIRMED)) == 1
                mCountInTotalCheckBox?.isChecked = cursor.getInt(cursor.getColumnIndex(Contract.Transfer.COUNT_IN_TOTAL)) == 1
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
                    val name = cursor.getString(cursor.getColumnIndex(Contract.Person.NAME))
                    builder.append(name)
                    i++
                }
                mPeopleTextView?.text = builder
                mPeopleTextView?.visibility = View.VISIBLE
            } else {
                mPeopleTextView?.visibility = View.GONE
            }
        } else if (loader.id == ATTACHMENTS_LOADER_ID) {
            if (cursor != null && cursor.moveToFirst()) {
                val attachments: MutableList<Attachment> = ArrayList()
                var i = 0
                while (cursor.moveToPosition(i) && i < cursor.count) {
                    attachments.add(
                        Attachment(
                            cursor.getLong(cursor.getColumnIndex(Contract.Attachment.ID)),
                            cursor.getString(cursor.getColumnIndex(Contract.Attachment.FILE)),
                            cursor.getString(cursor.getColumnIndex(Contract.Attachment.NAME)),
                            cursor.getString(cursor.getColumnIndex(Contract.Attachment.TYPE)),
                            cursor.getLong(cursor.getColumnIndex(Contract.Attachment.SIZE))
                        )
                    )
                    i++
                }
                mAttachmentView?.setAttachments(attachments)
                mAttachmentView?.visibility = View.VISIBLE
            } else {
                mAttachmentView?.visibility = View.GONE
            }
        }
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        // nothing to release
    }

    override fun onAttachmentClick(attachment: Attachment) {
        Attachment.openAttachment(activity, attachment)
    }

    override fun onAttachmentDelete(attachment: Attachment) {
        // attachment removal is not allowed here
    }

    companion object {
        private const val TRANSFER_LOADER_ID = 557639
        private const val PEOPLE_LOADER_ID = 557640
        private const val ATTACHMENTS_LOADER_ID = 557641
    }
}
