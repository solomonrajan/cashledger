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
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.FragmentManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.viewpager.widget.PagerAdapter
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.broadcast.Message
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.activity.NewEditDebtActivity
import com.oriondev.moneywallet.ui.activity.NewEditItemActivity
import com.oriondev.moneywallet.ui.adapter.pager.DebtViewPagerAdapter
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelViewPagerItemFragment
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment
import com.oriondev.moneywallet.ui.fragment.primary.DebtListFragment
import com.oriondev.moneywallet.ui.fragment.secondary.DebtItemFragment

/**
 * Created by andrea on 02/03/18.
 */
class DebtMultiPanelViewPagerFragment : MultiPanelViewPagerItemFragment() {

    private val itemClickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null && intent.getIntExtra(Message.ITEM_TYPE, 0) == Message.TYPE_DEBT) {
                showItemId(intent.getLongExtra(Message.ITEM_ID, 0L))
                showSecondaryPanel()
            }
        }
    }

    override fun onCreatePagerAdapter(fragmentManager: FragmentManager): PagerAdapter {
        return DebtViewPagerAdapter(fragmentManager, activity)
    }

    override fun getTitleRes(): Int = R.string.menu_debt

    override fun showsCurrentWallet(): Boolean = true

    @MenuRes
    override fun onInflateMenu(): Int = R.menu.menu_debt_multipanel_fragment

    override fun setupPrimaryToolbar(toolbar: Toolbar?) {
        super.setupPrimaryToolbar(toolbar)
        val item = toolbar?.menu?.findItem(R.id.action_show_finished_debts_only)
        item?.isChecked = PreferenceManager.isDebtFinishedOnlyEnabled()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_show_finished_debts_only) {
            val enabled = !item.isChecked
            item.isChecked = enabled
            PreferenceManager.setDebtFinishedOnlyEnabled(enabled)
            reloadDebtLists()
        }
        return false
    }

    private fun reloadDebtLists() {
        for (fragment in childFragmentManager.fragments) {
            if (fragment is DebtListFragment) {
                fragment.reloadList()
            }
        }
    }

    override fun onFloatingActionButtonClick() {
        val intent = Intent(activity, NewEditDebtActivity::class.java).apply {
            putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.NEW_ITEM)
            putExtra(NewEditDebtActivity.TYPE, getCurrentDebtType())
        }
        startActivity(intent)
    }

    private fun getCurrentDebtType(): Contract.DebtType? {
        return when (getViewPagerPosition()) {
            0 -> Contract.DebtType.DEBT
            1 -> Contract.DebtType.CREDIT
            else -> null
        }
    }

    override fun onCreateSecondaryPanel(): SecondaryPanelFragment = DebtItemFragment()

    override fun getSecondaryFragmentTag(): String = SECONDARY_FRAGMENT_TAG

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
        private const val SECONDARY_FRAGMENT_TAG = "DebtMultiPanelViewPagerFragment::Tag::SecondaryPanelFragment"
    }
}
