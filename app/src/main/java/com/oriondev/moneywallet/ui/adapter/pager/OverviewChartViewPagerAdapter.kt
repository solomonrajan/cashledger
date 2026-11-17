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
import androidx.viewpager.widget.PagerAdapter
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.RadarChart
import com.github.mikephil.charting.formatter.ValueFormatter
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.OverviewData
import java.util.Locale

/**
 * Created by andrea on 17/08/18.
 */
abstract class BaseChartPagerAdapter : PagerAdapter() {

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        if (obj is View) {
            container.removeView(obj)
        }
    }

    override fun isViewFromObject(view: View, obj: Any): Boolean = view === obj

    override fun getItemPosition(obj: Any): Int = POSITION_NONE
}

internal val PERIOD_NUMBER_FORMATTER: ValueFormatter = object : ValueFormatter() {
    override fun getFormattedValue(value: Float): String {
        return String.format(Locale.getDefault(), "%d", value.toInt() + 1)
    }
}

class OverviewChartViewPagerAdapter : BaseChartPagerAdapter() {

    private var mOverviewData: OverviewData? = null

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val inflater = LayoutInflater.from(container.context)
        val overviewData = mOverviewData
        val view = when (position) {
            POSITION_BAR_CHART -> {
                val barView = inflater.inflate(R.layout.adapter_bar_chart_item, container, false)
                val barChart: BarChart = barView.findViewById(R.id.bar_chart_view)
                barChart.description.isEnabled = false
                barChart.setDrawBarShadow(false)
                barChart.setDrawGridBackground(false)
                barChart.setDrawMarkers(true)
                if (overviewData != null) {
                    val barData = overviewData.barData
                    barChart.data = barData
                    if (barData != null) {
                        barData.isHighlightEnabled = false
                        val xAxis = barChart.xAxis
                        xAxis.granularity = 1f
                        if (barData.dataSetCount > 1) {
                            xAxis.setCenterAxisLabels(true)
                            xAxis.axisMinimum = 0f
                            xAxis.axisMaximum = overviewData.getPeriodCount().toFloat()
                        } else {
                            xAxis.axisMinimum = -0.5f
                            xAxis.axisMaximum = overviewData.getPeriodCount() - 0.5f
                        }
                        xAxis.valueFormatter = PERIOD_NUMBER_FORMATTER
                        barChart.axisLeft.spaceTop = 35f
                        barChart.axisRight.isEnabled = false
                        barData.setValueTextColor(xAxis.textColor)
                    }
                }
                barView
            }
            POSITION_LINE_CHART -> {
                val lineView = inflater.inflate(R.layout.adapter_line_chart_item, container, false)
                val lineChart: LineChart = lineView.findViewById(R.id.line_chart_view)
                lineChart.description.isEnabled = false
                if (overviewData != null) {
                    val lineData = overviewData.lineData
                    lineChart.data = lineData
                    if (lineData != null) {
                        lineData.isHighlightEnabled = false
                        val xAxis = lineChart.xAxis
                        xAxis.granularity = 1f
                        xAxis.axisMinimum = -0.5f
                        xAxis.axisMaximum = overviewData.getPeriodCount() - 0.5f
                        xAxis.valueFormatter = PERIOD_NUMBER_FORMATTER
                        lineChart.axisLeft.spaceTop = 35f
                        lineChart.axisRight.isEnabled = false
                        lineData.setValueTextColor(xAxis.textColor)
                    }
                }
                lineView
            }
            POSITION_RADAR_CHART -> {
                val radarView = inflater.inflate(R.layout.adapter_radar_chart_item, container, false)
                val radarChart: RadarChart = radarView.findViewById(R.id.radar_chart_view)
                radarChart.description.isEnabled = false
                radarChart.isRotationEnabled = false
                if (overviewData != null) {
                    val radarData = overviewData.radarData
                    radarChart.data = radarData
                    if (radarData != null) {
                        val xAxis = radarChart.xAxis
                        xAxis.valueFormatter = PERIOD_NUMBER_FORMATTER
                        radarData.setValueTextColor(xAxis.textColor)
                    }
                }
                radarView
            }
            else -> throw IllegalStateException(
                "OverviewChartViewPagerAdapter has not correctly initialized the view at position: $position"
            )
        }
        container.addView(view)
        return view
    }

    override fun getCount(): Int = CHART_COUNT

    fun setData(data: OverviewData?) {
        mOverviewData = data
        notifyDataSetChanged()
    }

    companion object {
        private const val CHART_COUNT = 3
        private const val POSITION_BAR_CHART = 0
        private const val POSITION_LINE_CHART = 1
        private const val POSITION_RADAR_CHART = 2
    }
}
