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
import com.oriondev.moneywallet.ui.fragment.single.PeriodDetailFlowFragment
import com.oriondev.moneywallet.ui.fragment.single.PeriodDetailSummaryFragment
import java.util.Date

/**
 * Created by andrea on 09/04/18.
 */
class PeriodViewPagerAdapter(
    fm: FragmentManager,
    context: Context,
    private val mStartDate: Date?,
    private val mEndDate: Date?
) : BaseTabPagerAdapter(
    fm,
    context,
    R.string.hint_summary,
    R.string.hint_incomes,
    R.string.hint_expenses
) {

    override fun getItem(position: Int): Fragment {
        return when (position) {
            0 -> PeriodDetailSummaryFragment.newInstance(mStartDate, mEndDate)
            1 -> PeriodDetailFlowFragment.newInstance(mStartDate, mEndDate, true)
            2 -> PeriodDetailFlowFragment.newInstance(mStartDate, mEndDate, false)
            else -> throw IllegalArgumentException("Invalid position")
        }
    }
}
