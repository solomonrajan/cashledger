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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.ui.fragment.primary.CategoryListFragment

/**
 * Created by andrea on 10/02/18.
 */
class CategoryViewPagerAdapter(
    fm: FragmentManager,
    context: Context,
    private val mShowSubCategories: Boolean,
    showSystemCategories: Boolean
) : BaseTabPagerAdapter(fm, context, *tabTitlesFor(showSystemCategories)) {

    override fun getItem(position: Int): Fragment {
        return when (position) {
            0 -> CategoryListFragment.newInstance(Contract.CategoryType.INCOME, mShowSubCategories)
            1 -> CategoryListFragment.newInstance(Contract.CategoryType.EXPENSE, mShowSubCategories)
            2 -> CategoryListFragment.newInstance(Contract.CategoryType.SYSTEM, mShowSubCategories)
            else -> throw IllegalArgumentException("Invalid adapter position: $position")
        }
    }

    companion object {
        private fun tabTitlesFor(showSystemCategories: Boolean): IntArray {
            return if (showSystemCategories) {
                intArrayOf(
                    R.string.menu_category_tab_income,
                    R.string.menu_category_tab_expense,
                    R.string.menu_category_tab_system
                )
            } else {
                intArrayOf(
                    R.string.menu_category_tab_income,
                    R.string.menu_category_tab_expense
                )
            }
        }
    }
}
