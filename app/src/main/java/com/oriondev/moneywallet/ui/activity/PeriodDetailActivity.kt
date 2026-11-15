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

package com.oriondev.moneywallet.ui.activity

import androidx.fragment.app.FragmentManager
import androidx.viewpager.widget.PagerAdapter
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.activity.base.SinglePanelViewPagerActivity
import com.oriondev.moneywallet.ui.adapter.pager.PeriodViewPagerAdapter
import com.oriondev.moneywallet.utils.DateFormatter
import java.util.Date

/**
 * Created by andrea on 04/04/18.
 */
class PeriodDetailActivity : SinglePanelViewPagerActivity() {

    override fun onCreatePagerAdapter(fragmentManager: FragmentManager): PagerAdapter {
        val intent = intent
        if (intent != null) {
            val startDate = intent.getSerializableExtra(START_DATE) as? Date
            val endDate = intent.getSerializableExtra(END_DATE) as? Date
            if (startDate != null && endDate != null) {
                setToolbarSubtitle(DateFormatter.getDateRange(this, startDate, endDate))
            }
            return PeriodViewPagerAdapter(fragmentManager, this, startDate, endDate)
        } else {
            return PeriodViewPagerAdapter(fragmentManager, this, null, null)
        }
    }

    override fun getActivityTitleRes(): Int = R.string.menu_overview

    override fun isFloatingActionButtonEnabled(): Boolean = false

    companion object {
        const val START_DATE = "PeriodDetailActivity::Arguments::StartDate"
        const val END_DATE = "PeriodDetailActivity::Arguments::EndDate"
    }
}
