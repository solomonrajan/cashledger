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

package com.oriondev.moneywallet.ui.fragment.dialog

import android.app.Dialog
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.collection.LongSparseArray
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.activity.NewEditWalletActivity
import com.oriondev.moneywallet.ui.adapter.recycler.WalletSelectorCursorAdapter
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog

/**
 * Created by andre on 18/03/2018.
 */
class WalletPickerDialog : DialogFragment(), LoaderManager.LoaderCallbacks<Cursor>, WalletSelectorCursorAdapter.Controller {

    private var mCallback: Callback? = null

    private var mSinglePicker: Boolean = false
    private var mSelectedWallets = LongSparseArray<Wallet>()

    private var mRecyclerView: RecyclerView? = null
    private var mMessageTextView: TextView? = null

    private var mCursorAdapter: WalletSelectorCursorAdapter? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val currentActivity = activity ?: return super.onCreateDialog(savedInstanceState)
        if (savedInstanceState != null) {
            mSelectedWallets = LongSparseArray()
            mSinglePicker = savedInstanceState.getBoolean(SS_SINGLE_PICKER)
            val parcelables = savedInstanceState.getParcelableArray(SS_SELECTED_WALLETS)
            if (parcelables != null) {
                for (parcelable in parcelables) {
                    if (parcelable is Wallet) {
                        mSelectedWallets.append(parcelable.id, parcelable)
                    }
                }
            }
        }
        val view = layoutInflater.inflate(R.layout.dialog_advanced_list, null)
        val builder = ThemedDialog.buildMaterialDialog(currentActivity)
            .setTitle(R.string.dialog_wallet_picker_title)
            .setView(view)

        if (mSinglePicker) {
            builder.setPositiveButton(R.string.action_new) { _, _ ->
                startActivity(Intent(activity, NewEditWalletActivity::class.java))
            }
            .setNegativeButton(android.R.string.cancel, null)
        } else {
            builder.setPositiveButton(android.R.string.ok) { _, _ ->
                mCallback?.onWalletsSelected(selectedWallets())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.action_new) { _, _ ->
                startActivity(Intent(activity, NewEditWalletActivity::class.java))
            }
        }
        val dialog = builder.create()
        val adapter = WalletSelectorCursorAdapter(this)
        mCursorAdapter = adapter
        mRecyclerView = view.findViewById(R.id.recycler_view)
        mMessageTextView = view.findViewById(R.id.message_text_view)
        mRecyclerView?.layoutManager = LinearLayoutManager(currentActivity)
        mRecyclerView?.adapter = adapter
        mMessageTextView?.setText(R.string.message_no_wallet_found)
        mRecyclerView?.visibility = View.GONE
        mMessageTextView?.visibility = View.GONE
        LoaderManager.getInstance(this).restartLoader(DEFAULT_LOADER_ID, null, this)
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SS_SINGLE_PICKER, mSinglePicker)
        outState.putParcelableArray(SS_SELECTED_WALLETS, selectedWallets())
    }

    private fun selectedWallets(): Array<Wallet> {
        return Array(mSelectedWallets.size()) { i ->
            mSelectedWallets.valueAt(i)
        }
    }

    fun setCallback(callback: Callback?) {
        mCallback = callback
    }

    fun showSinglePicker(fragmentManager: FragmentManager, tag: String?, wallet: Wallet?) {
        mSinglePicker = true
        mSelectedWallets = LongSparseArray()
        if (wallet != null) {
            mSelectedWallets.append(wallet.id, wallet)
        }
        show(fragmentManager, tag)
    }

    fun showMultiPicker(fragmentManager: FragmentManager, tag: String?, wallets: Array<Wallet>?) {
        mSinglePicker = false
        mSelectedWallets = LongSparseArray()
        if (wallets != null && wallets.isNotEmpty()) {
            for (wallet in wallets) {
                mSelectedWallets.append(wallet.id, wallet)
            }
        }
        show(fragmentManager, tag)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        val uri: Uri = DataContentProvider.CONTENT_WALLETS
        val projection = arrayOf(
            Contract.Wallet.ID,
            Contract.Wallet.NAME,
            Contract.Wallet.ICON,
            Contract.Wallet.CURRENCY,
            Contract.Wallet.COUNT_IN_TOTAL,
            Contract.Wallet.START_MONEY,
            Contract.Wallet.TOTAL_MONEY
        )
        val selection = Contract.Wallet.ARCHIVED + " = 0"
        val sortOrder = Contract.Wallet.INDEX + " ASC, " + Contract.Wallet.NAME + " ASC"
        return CursorLoader(requireContext(), uri, projection, selection, null, sortOrder)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        mCursorAdapter?.swapCursor(cursor)
        if (cursor != null && cursor.count > 0) {
            mRecyclerView?.visibility = View.VISIBLE
            mMessageTextView?.visibility = View.GONE
        } else {
            mRecyclerView?.visibility = View.GONE
            mMessageTextView?.visibility = View.VISIBLE
        }
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        mCursorAdapter?.swapCursor(null)
    }

    override fun onWalletClick(wallet: Wallet) {
        if (mSinglePicker) {
            mCallback?.onWalletSelected(wallet)
            dismiss()
        } else {
            if (mSelectedWallets.indexOfKey(wallet.id) >= 0) {
                mSelectedWallets.remove(wallet.id)
            } else {
                mSelectedWallets.append(wallet.id, wallet)
            }
            mCursorAdapter?.notifyDataSetChanged()
        }
    }

    override fun isWalletSelected(id: Long): Boolean {
        return mSelectedWallets.indexOfKey(id) >= 0
    }

    interface Callback {
        fun onWalletSelected(wallet: Wallet?)

        fun onWalletsSelected(wallets: Array<Wallet>?)
    }

    companion object {
        private const val SS_SINGLE_PICKER = "WalletPickerDialog::SavedState::SinglePicker"
        private const val SS_SELECTED_WALLETS = "WalletPickerDialog::SavedState::SelectedWallets"

        private const val DEFAULT_LOADER_ID = 1

        @JvmStatic
        fun newInstance(): WalletPickerDialog {
            return WalletPickerDialog()
        }
    }
}
