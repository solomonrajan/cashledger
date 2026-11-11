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

import android.view.LayoutInflater
import android.view.ViewGroup
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.PeriodDetailFlowData
import com.oriondev.moneywallet.ui.view.chart.PieChart

/**
 * Created by andrea on 14/08/18.
 */
class PieChartViewPagerAdapter : BaseChartPagerAdapter() {

    private var mData: PeriodDetailFlowData? = null

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val view = LayoutInflater.from(container.context)
            .inflate(R.layout.adapter_pie_chart_item, container, false)
        val pieChart: PieChart = view.findViewById(R.id.pie_chart_view)
        val data = mData
        if (data != null && data.getChartCount() > 0) {
            pieChart.setChartData(data.getChartData(position))
        } else {
            pieChart.setChartData(null)
        }
        container.addView(view)
        return view
    }

    override fun getCount(): Int {
        val data = mData
        return data?.getChartCount() ?: 1
    }

    fun setData(data: PeriodDetailFlowData?) {
        mData = data
        notifyDataSetChanged()
    }
}
