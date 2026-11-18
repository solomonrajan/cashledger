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
class CategoryViewPagerAdapter @JvmOverloads constructor(
    fm: FragmentManager,
    context: Context,
    private val mShowSubCategories: Boolean,
    showSystemCategories: Boolean,
    private val mOnlyType: Contract.CategoryType? = null
) : BaseTabPagerAdapter(fm, context, *tabTitlesFor(showSystemCategories, mOnlyType)) {

    /**
     * The page a type is shown on when every type is.
     */
    private fun pageOf(position: Int): Int {
        val onlyType = mOnlyType ?: return position
        return when (onlyType) {
            Contract.CategoryType.INCOME -> 0
            Contract.CategoryType.EXPENSE -> 1
            else -> 2
        }
    }

    override fun getItem(position: Int): Fragment {
        return when (pageOf(position)) {
            0 -> CategoryListFragment.newInstance(Contract.CategoryType.INCOME, mShowSubCategories)
            1 -> CategoryListFragment.newInstance(Contract.CategoryType.EXPENSE, mShowSubCategories)
            2 -> CategoryListFragment.newInstance(Contract.CategoryType.SYSTEM, mShowSubCategories)
            else -> throw IllegalArgumentException("Invalid adapter position: $position")
        }
    }

    companion object {
        private fun tabTitlesFor(showSystemCategories: Boolean, onlyType: Contract.CategoryType?): IntArray {
            if (onlyType != null) {
                return when (onlyType) {
                    Contract.CategoryType.INCOME -> intArrayOf(R.string.menu_category_tab_income)
                    Contract.CategoryType.EXPENSE -> intArrayOf(R.string.menu_category_tab_expense)
                    else -> intArrayOf(R.string.menu_category_tab_system)
                }
            }
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
