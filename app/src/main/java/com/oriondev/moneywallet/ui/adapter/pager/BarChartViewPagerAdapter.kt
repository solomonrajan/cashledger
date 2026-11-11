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
import android.view.View
import android.view.ViewGroup
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.formatter.ValueFormatter
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.PeriodDetailSummaryData
import com.oriondev.moneywallet.utils.MoneyFormatter

/**
 * Created by andrea on 16/08/18.
 */
class BarChartViewPagerAdapter : BaseChartPagerAdapter() {

    private var mData: PeriodDetailSummaryData? = null

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val view = LayoutInflater.from(container.context)
            .inflate(R.layout.adapter_bar_chart_item, container, false)
        val barChart: BarChart = view.findViewById(R.id.bar_chart_view)
        barChart.description.isEnabled = false
        barChart.setDrawBarShadow(false)
        barChart.setDrawGridBackground(false)
        barChart.setDrawMarkers(true)
        val data = mData
        if (data != null && data.getChartCount() > 0) {
            val barData = data.getChartData(position)
            val currencyUnit = data.getChartCurrency(position)
            barChart.data = barData
            val xAxis = barChart.xAxis
            xAxis.granularity = 1f
            xAxis.setCenterAxisLabels(true)
            xAxis.axisMinimum = 0f
            xAxis.axisMaximum = data.getPeriodCount().toFloat()
            xAxis.valueFormatter = PERIOD_NUMBER_FORMATTER
            val leftAxis = barChart.axisLeft
            leftAxis.valueFormatter = IMoneyFormatter(currencyUnit)
            leftAxis.setDrawGridLines(false)
            leftAxis.spaceTop = 35f
            leftAxis.axisMinimum = 0f
            barChart.axisRight.isEnabled = false
            barData.barWidth = 0.4f
            barData.isHighlightEnabled = false
            barData.setValueTextColor(xAxis.textColor)
            barData.setValueFormatter(IMoneyFormatter(currencyUnit))
            barChart.groupBars(0f, 0.08f, 0.06f)
        } else {
            barChart.data = null
        }
        container.addView(view)
        return view
    }

    override fun getCount(): Int {
        val data = mData
        return data?.getChartCount() ?: 1
    }

    fun setData(data: PeriodDetailSummaryData?) {
        mData = data
        notifyDataSetChanged()
    }

    private class IMoneyFormatter(
        private val mCurrencyUnit: CurrencyUnit?
    ) : ValueFormatter() {

        private val mMoneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()

        override fun getFormattedValue(value: Float): String {
            return mMoneyFormatter.getNotTintedString(mCurrencyUnit, value.toLong())
        }
    }
}
