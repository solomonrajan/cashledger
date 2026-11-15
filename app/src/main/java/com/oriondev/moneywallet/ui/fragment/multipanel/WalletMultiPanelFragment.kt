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
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Money
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.activity.NewEditWalletActivity
import com.oriondev.moneywallet.ui.activity.WalletSortActivity
import com.oriondev.moneywallet.ui.adapter.recycler.WalletCursorAdapter
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelAppBarItemFragment
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment
import com.oriondev.moneywallet.ui.fragment.secondary.WalletItemFragment
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView
import com.oriondev.moneywallet.utils.MoneyFormatter

/**
 * Created by andrea on 24/01/18.
 */
class WalletMultiPanelFragment : MultiPanelAppBarItemFragment(),
    LoaderManager.LoaderCallbacks<Cursor>,
    SwipeRefreshLayout.OnRefreshListener,
    WalletCursorAdapter.WalletActionListener {

    private var totalMoneyTextView: TextView? = null
    private var advancedRecyclerView: AdvancedRecyclerView? = null
    private var adapter: WalletCursorAdapter? = null

    private val moneyFormatter = MoneyFormatter.getInstance()

    override fun onCreatePrimaryAppBar(inflater: LayoutInflater, primaryAppBarContainer: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.view_wallet_list_app_bar, primaryAppBarContainer, true)
        totalMoneyTextView = view.findViewById(R.id.total_money_text_view)
    }

    override fun onCreatePrimaryPanel(inflater: LayoutInflater, primaryPanel: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.view_wallet_list, primaryPanel, true)
        val rv: AdvancedRecyclerView = view.findViewById(R.id.advanced_recycler_view)
        advancedRecyclerView = rv
        val walletAdapter = WalletCursorAdapter(this)
        adapter = walletAdapter
        rv.adapter = walletAdapter
        rv.layoutManager = LinearLayoutManager(activity)
        rv.setEmptyText(R.string.message_no_wallet_found)
        rv.setOnRefreshListener(this)

        LoaderManager.getInstance(this).initLoader(LOADER_ID, null, this)
        rv.setState(AdvancedRecyclerView.State.LOADING)
    }

    override fun onCreateSecondaryPanel(): SecondaryPanelFragment = WalletItemFragment()

    override fun getSecondaryFragmentTag(): String = SECONDARY_FRAGMENT_TAG

    @StringRes
    override fun getTitleRes(): Int = R.string.action_manage_wallets

    @MenuRes
    override fun onInflateMenu(): Int = R.menu.menu_wallet_multipanel_fragment

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_sort_items) {
            val intent = Intent(activity, WalletSortActivity::class.java)
            startActivity(intent)
        }
        return false
    }

    override fun onFloatingActionButtonClick() {
        val intent = Intent(activity, NewEditWalletActivity::class.java)
        startActivity(intent)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor>? {
        val context: Context? = activity
        if (context != null) {
            val uri: Uri = DataContentProvider.CONTENT_WALLETS
            val projection = arrayOf(
                Contract.Wallet.ID,
                Contract.Wallet.NAME,
                Contract.Wallet.ICON,
                Contract.Wallet.CURRENCY,
                Contract.Wallet.START_MONEY,
                Contract.Wallet.COUNT_IN_TOTAL,
                Contract.Wallet.TOTAL_MONEY
            )
            val sortOrder = "${Contract.Wallet.INDEX} ASC, ${Contract.Wallet.NAME} ASC"
            return CursorLoader(context, uri, projection, null, null, sortOrder)
        }
        return null
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        if (cursor != null) {
            val money = Money()
            val indexCurrency = cursor.getColumnIndex(Contract.Wallet.CURRENCY)
            val indexInTotal = cursor.getColumnIndex(Contract.Wallet.COUNT_IN_TOTAL)
            val indexWalletInitial = cursor.getColumnIndex(Contract.Wallet.START_MONEY)
            val indexWalletTotal = cursor.getColumnIndex(Contract.Wallet.TOTAL_MONEY)
            for (i in 0 until cursor.count) {
                cursor.moveToPosition(i)
                if (cursor.getInt(indexInTotal) == 1) {
                    val currency = cursor.getString(indexCurrency)
                    val amount = cursor.getLong(indexWalletInitial) + cursor.getLong(indexWalletTotal)
                    money.addMoney(currency, amount)
                }
            }
            totalMoneyTextView?.let { moneyFormatter.applyNotTinted(it, money) }
        }
        adapter?.changeCursor(cursor)
        if (cursor != null && cursor.count > 0) {
            advancedRecyclerView?.setState(AdvancedRecyclerView.State.READY)
        } else {
            advancedRecyclerView?.setState(AdvancedRecyclerView.State.EMPTY)
        }
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        adapter?.changeCursor(null)
    }

    override fun onRefresh() {
        LoaderManager.getInstance(this).restartLoader(LOADER_ID, null, this)
        advancedRecyclerView?.setState(AdvancedRecyclerView.State.REFRESHING)
    }

    override fun onWalletClick(id: Long) {
        showItemId(id)
        showSecondaryPanel()
    }

    companion object {
        private const val SECONDARY_FRAGMENT_TAG = "WalletMultiPanelFragment::Tag::SecondaryPanelFragment"
        private const val LOADER_ID = 0
    }
}
