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
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.MenuItem
import androidx.annotation.MenuRes
import androidx.fragment.app.FragmentManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.viewpager.widget.PagerAdapter
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.broadcast.Message
import com.oriondev.moneywallet.ui.activity.CalendarActivity
import com.oriondev.moneywallet.ui.activity.NewEditItemActivity
import com.oriondev.moneywallet.ui.activity.NewEditTransactionActivity
import com.oriondev.moneywallet.ui.activity.NewEditTransferActivity
import com.oriondev.moneywallet.ui.activity.SearchActivity
import com.oriondev.moneywallet.ui.adapter.pager.TransactionViewPagerAdapter
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelViewPagerMultiItemFragment
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment
import com.oriondev.moneywallet.ui.fragment.secondary.TransactionItemFragment
import com.oriondev.moneywallet.ui.fragment.secondary.TransferItemFragment

/**
 * Created by andrea on 02/03/18.
 */
class TransactionMultiPanelViewPagerFragment : MultiPanelViewPagerMultiItemFragment() {

    private val itemClickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                val id = intent.getLongExtra(Message.ITEM_ID, 0L)
                when (intent.getIntExtra(Message.ITEM_TYPE, 0)) {
                    Message.TYPE_TRANSACTION -> showItemId(TYPE_TRANSACTION, id)
                    Message.TYPE_TRANSFER -> showItemId(TYPE_TRANSFER, id)
                    else -> return
                }
                showSecondaryPanel()
            }
        }
    }

    override fun onCreatePagerAdapter(fragmentManager: FragmentManager): PagerAdapter {
        return TransactionViewPagerAdapter(fragmentManager, activity)
    }

    override fun getTitleRes(): Int = R.string.menu_transaction

    override fun showsCurrentWallet(): Boolean = true

    @MenuRes
    override fun onInflateMenu(): Int = R.menu.menu_transaction_multipanel_fragment

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search_item -> startActivity(Intent(activity, SearchActivity::class.java))
            R.id.action_open_calendar -> startActivity(Intent(activity, CalendarActivity::class.java))
        }
        return false
    }

    override fun onPageSelected(position: Int) {
        // the transactions page draws its selection on this toolbar, which the transfers page shares
        finishSelectionMode()
    }

    override fun onCreateSecondaryPanel(type: Int): SecondaryPanelFragment {
        return when (type) {
            TYPE_TRANSACTION -> TransactionItemFragment()
            TYPE_TRANSFER -> TransferItemFragment()
            else -> throw IllegalArgumentException("Unknown item type: $type")
        }
    }

    override fun getSecondaryFragmentTag(type: Int): String {
        return "$SECONDARY_FRAGMENT_TAG::Type$type"
    }

    override fun onFloatingActionButtonClick() {
        val intent = when (viewPagerPosition) {
            0 -> Intent(activity, NewEditTransactionActivity::class.java).apply {
                putExtra(NewEditTransactionActivity.AUTO_OPEN_CALCULATOR, true)
            }
            1 -> Intent(activity, NewEditTransferActivity::class.java)
            else -> return
        }
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.NEW_ITEM)
        startActivity(intent)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val filter = IntentFilter(LocalAction.ACTION_ITEM_CLICK)
        LocalBroadcastManager.getInstance(context).registerReceiver(itemClickReceiver, filter)
    }

    override fun onDetach() {
        super.onDetach()
        activity?.let {
            LocalBroadcastManager.getInstance(it).unregisterReceiver(itemClickReceiver)
        }
    }

    companion object {
        private const val SECONDARY_FRAGMENT_TAG = "TransactionMultiPanelViewPagerFragment::Tag::SecondaryPanelFragment"

        private const val TYPE_TRANSACTION = 0
        private const val TYPE_TRANSFER = 1
    }
}
