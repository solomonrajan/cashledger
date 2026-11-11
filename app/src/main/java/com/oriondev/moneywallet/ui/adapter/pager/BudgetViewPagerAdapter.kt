/*
 * Copyright (c) 2018.
 * Copyright (c) 2026 solomonrajan.
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

package com.oriondev.moneywallet.ui.adapter.pager

import android.content.Context
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.fragment.primary.BudgetListFragment

/**
 * Created by andrea on 02/03/18.
 */
@Suppress("DEPRECATION")
abstract class BaseTabPagerAdapter(
    fm: FragmentManager,
    private val context: Context,
    @StringRes private vararg val tabTitles: Int
) : FragmentPagerAdapter(fm) {

    override fun getCount(): Int = tabTitles.size

    override fun getPageTitle(position: Int): CharSequence? {
        return if (position in tabTitles.indices) {
            context.getString(tabTitles[position])
        } else {
            null
        }
    }
}

class BudgetViewPagerAdapter(
    fm: FragmentManager,
    context: Context
) : BaseTabPagerAdapter(
    fm,
    context,
    R.string.menu_budget_tab_running,
    R.string.menu_budget_tab_expired
) {

    override fun getItem(position: Int): Fragment {
        return BudgetListFragment.newInstance(position == 1)
    }
}
