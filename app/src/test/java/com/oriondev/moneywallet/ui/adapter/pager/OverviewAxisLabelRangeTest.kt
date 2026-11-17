/*
 * Copyright (c) 2026.
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
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.RadarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.RadarData
import com.github.mikephil.charting.data.RadarDataSet
import com.github.mikephil.charting.data.RadarEntry
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.OverviewData
import com.oriondev.moneywallet.model.PeriodMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.ArrayList
import java.util.Date

/**
 * The period numbers the overview bar and line pages write along their x axis.
 */
@RunWith(RobolectricTestRunner::class)
class OverviewAxisLabelRangeTest {

    private fun themed(): Context {
        return ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.MoneyWalletAppTheme
        )
    }

    private fun overviewPage(position: Int, count: Int): View {
        val adapter = OverviewChartViewPagerAdapter()
        adapter.setData(
            OverviewData(
                barData(count),
                lineData(count),
                radarData(count),
                periods(count)
            )
        )
        return adapter.instantiateItem(FrameLayout(themed()), position) as View
    }

    private fun assertBarLabelsWithin(count: Int) {
        val chart = overviewPage(0, count).findViewById<BarChart>(R.id.bar_chart_view)
        assertNotNull("the page carries no bar chart, so there are no axis labels to read", chart)
        chart?.notifyDataSetChanged()
        val nonNullChart = chart ?: return
        val where = "the overview bar chart over $count periods"
        assertLabelsWithin(where, drawnLabels(nonNullChart.xAxis), count)
        assertHalfPeriodMargins(where, nonNullChart.xAxis, count)
    }

    private fun assertLineLabelsWithin(count: Int) {
        val chart = overviewPage(1, count).findViewById<LineChart>(R.id.line_chart_view)
        assertNotNull("the page carries no line chart, so there are no axis labels to read", chart)
        chart?.notifyDataSetChanged()
        val nonNullChart = chart ?: return
        val where = "the overview line chart over $count periods"
        assertLabelsWithin(where, drawnLabels(nonNullChart.xAxis), count)
        assertHalfPeriodMargins(where, nonNullChart.xAxis, count)
    }

    @Test
    fun theBarAxisStaysWithinTwelvePeriods() {
        assertBarLabelsWithin(12)
    }

    @Test
    fun theBarAxisStaysWithinFivePeriods() {
        assertBarLabelsWithin(5)
    }

    @Test
    fun theLineAxisStaysWithinTwelvePeriods() {
        assertLineLabelsWithin(12)
    }

    @Test
    fun theLineAxisStaysWithinFivePeriods() {
        assertLineLabelsWithin(5)
    }

    @Test
    fun theRadarAxisCountsFromTheFirstPeriod() {
        val chart = overviewPage(2, 12).findViewById<RadarChart>(R.id.radar_chart_view)
        assertNotNull("the page carries no radar chart, so there is no axis label to read", chart)
        assertEquals(
            "the radar chart names the first period by its index and not by its number",
            "1",
            chart?.xAxis?.valueFormatter?.getFormattedValue(0f)
        )
    }

    companion object {
        private fun barData(count: Int): BarData {
            val entries = ArrayList<BarEntry>()
            for (at in 0 until count) {
                entries.add(BarEntry(at.toFloat(), 1f))
            }
            return BarData(BarDataSet(entries, "bar"))
        }

        private fun lineData(count: Int): LineData {
            val entries = ArrayList<Entry>()
            for (at in 0 until count) {
                entries.add(Entry(at.toFloat(), 1f))
            }
            return LineData(LineDataSet(entries, "line"))
        }

        private fun radarData(count: Int): RadarData {
            val entries = ArrayList<RadarEntry>()
            for (at in 0 until count) {
                entries.add(RadarEntry(1f))
            }
            return RadarData(RadarDataSet(entries, "radar"))
        }

        private fun periods(count: Int): List<PeriodMoney> {
            val list = ArrayList<PeriodMoney>()
            for (at in 0 until count) {
                list.add(PeriodMoney(Date(), Date()))
            }
            return list
        }

        private fun drawnLabels(axis: XAxis): List<String> {
            val labels = ArrayList<String>()
            for (at in 0 until axis.mEntryCount) {
                labels.add(axis.valueFormatter.getFormattedValue(axis.mEntries[at]))
            }
            return labels
        }

        private fun assertLabelsWithin(where: String, labels: List<String>, count: Int) {
            assertFalse("$where drew no labels at all, so there is nothing to check", labels.isEmpty())
            for (label in labels) {
                assertTrue(
                    "$where drew $labels, and $label is not a period number within 1 and $count",
                    label.matches(Regex("\\d+")) && label.toInt() in 1..count
                )
            }
        }

        private fun assertHalfPeriodMargins(where: String, axis: XAxis, count: Int) {
            assertEquals(
                "$where starts its axis somewhere other than half a period before the first",
                -0.5f,
                axis.axisMinimum,
                0f
            )
            assertEquals(
                "$where ends its axis somewhere other than half a period after the last",
                count - 0.5f,
                axis.axisMaximum,
                0f
            )
        }
    }
}
