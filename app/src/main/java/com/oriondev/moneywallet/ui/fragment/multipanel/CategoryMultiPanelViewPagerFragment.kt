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
import com.oriondev.moneywallet.ui.activity.NewEditCategoryActivity
import com.oriondev.moneywallet.ui.activity.NewEditItemActivity
import com.oriondev.moneywallet.ui.adapter.pager.CategoryViewPagerAdapter
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelViewPagerItemFragment
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment
import com.oriondev.moneywallet.ui.fragment.secondary.CategoryItemFragment

/**
 * Created by andrea on 02/03/18.
 */
class CategoryMultiPanelViewPagerFragment : MultiPanelViewPagerItemFragment() {

    private val itemClickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null && intent.getIntExtra(Message.ITEM_TYPE, 0) == Message.TYPE_CATEGORY) {
                showItemId(intent.getLongExtra(Message.ITEM_ID, 0L))
                showSecondaryPanel()
            }
        }
    }

    override fun onCreatePagerAdapter(fragmentManager: FragmentManager): PagerAdapter {
        return CategoryViewPagerAdapter(fragmentManager, activity)
    }

    override fun getTitleRes(): Int = R.string.menu_category

    override fun onFloatingActionButtonClick() {
        val intent = Intent(activity, NewEditCategoryActivity::class.java).apply {
            putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.NEW_ITEM)
        }
        startActivity(intent)
    }

    override fun onCreateSecondaryPanel(): SecondaryPanelFragment = CategoryItemFragment()

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
        private const val SECONDARY_FRAGMENT_TAG = "CategoryMultiPanelViewPagerFragment::Tag::SecondaryPanelFragment"
    }
}
