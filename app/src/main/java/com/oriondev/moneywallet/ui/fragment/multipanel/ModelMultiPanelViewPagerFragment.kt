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
import androidx.fragment.app.FragmentManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.viewpager.widget.PagerAdapter
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.broadcast.Message
import com.oriondev.moneywallet.ui.activity.NewEditItemActivity
import com.oriondev.moneywallet.ui.activity.NewEditTransactionModelActivity
import com.oriondev.moneywallet.ui.activity.NewEditTransferModelActivity
import com.oriondev.moneywallet.ui.adapter.pager.ModelViewPagerAdapter
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelViewPagerMultiItemFragment
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment
import com.oriondev.moneywallet.ui.fragment.secondary.TransactionModelItemFragment
import com.oriondev.moneywallet.ui.fragment.secondary.TransferModelItemFragment

/**
 * Created by andrea on 04/11/18.
 */
class ModelMultiPanelViewPagerFragment : MultiPanelViewPagerMultiItemFragment() {

    private val itemClickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                val id = intent.getLongExtra(Message.ITEM_ID, 0L)
                when (intent.getIntExtra(Message.ITEM_TYPE, 0)) {
                    Message.TYPE_MODEL_TRANSACTION -> showItemId(TYPE_TRANSACTION, id)
                    Message.TYPE_MODEL_TRANSFER -> showItemId(TYPE_TRANSFER, id)
                    else -> return
                }
                showSecondaryPanel()
            }
        }
    }

    override fun onCreateSecondaryPanel(index: Int): SecondaryPanelFragment {
        return when (index) {
            TYPE_TRANSACTION -> TransactionModelItemFragment()
            TYPE_TRANSFER -> TransferModelItemFragment()
            else -> throw IllegalArgumentException("Unknown item type: $index")
        }
    }

    override fun getSecondaryFragmentTag(index: Int): String {
        return "$SECONDARY_FRAGMENT_TAG::Type$index"
    }

    override fun onFloatingActionButtonClick() {
        val intent = when (viewPagerPosition) {
            0 -> Intent(activity, NewEditTransactionModelActivity::class.java)
            1 -> Intent(activity, NewEditTransferModelActivity::class.java)
            else -> return
        }
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.NEW_ITEM)
        startActivity(intent)
    }

    override fun onCreatePagerAdapter(fragmentManager: FragmentManager): PagerAdapter {
        return ModelViewPagerAdapter(fragmentManager, activity)
    }

    override fun getTitleRes(): Int = R.string.menu_models

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
        private const val SECONDARY_FRAGMENT_TAG = "ModelMultiPanelViewPagerFragment::Tag::SecondaryPanelFragment"

        private const val TYPE_TRANSACTION = 0
        private const val TYPE_TRANSFER = 1
    }
}
