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
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Money
import com.oriondev.moneywallet.model.OverviewData
import com.oriondev.moneywallet.model.PeriodDetailSummaryData
import com.oriondev.moneywallet.model.PeriodMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.ArrayList
import java.util.Collections
import java.util.Date
import java.util.Locale

/**
 * The period numbers the two overview charts and the period detail summary chart write along
 * their x axis, in the digits of the language the app is in. All three used to build those
 * numbers without a locale, so a Persian screen read 1 2 3 under amounts written in Persian
 * digits.
 */
@RunWith(RobolectricTestRunner::class)
class ChartAxisDigitsTest {

    private fun themed(): Context {
        return ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.MoneyWalletAppTheme
        )
    }

    private fun requirePersianLocale() {
        assertEquals(
            "the qualifier did not reach the default locale, so nothing below is a check",
            "fa",
            Locale.getDefault().language
        )
    }

    private fun overviewPage(position: Int): View {
        val adapter = OverviewChartViewPagerAdapter()
        adapter.setData(
            OverviewData(
                BarData(oneBar("bar")),
                oneLineData(),
                null,
                onePeriod()
            )
        )
        return adapter.instantiateItem(FrameLayout(themed()), position) as View
    }

    private fun summaryPage(): View {
        val adapter = BarChartViewPagerAdapter()
        adapter.setData(
            PeriodDetailSummaryData(
                Money(),
                listOf(groupedBarData()),
                listOf(CurrencyUnit("USD", "US Dollar", "$", 2)),
                onePeriod()
            )
        )
        return adapter.instantiateItem(FrameLayout(themed()), 0) as View
    }

    @Test
    @Config(qualifiers = "fa-rIR")
    fun theOverviewBarAxisIsWrittenInPersianDigits() {
        requirePersianLocale()
        assertPersianDigits("the overview bar chart period number", barAxisLabel(overviewPage(0)))
    }

    @Test
    @Config(qualifiers = "fa-rIR")
    fun theOverviewLineAxisIsWrittenInPersianDigits() {
        requirePersianLocale()
        assertPersianDigits("the overview line chart period number", lineAxisLabel(overviewPage(1)))
    }

    @Test
    @Config(qualifiers = "fa-rIR")
    fun theSummaryBarAxisIsWrittenInPersianDigits() {
        requirePersianLocale()
        assertPersianDigits("the summary chart period number", barAxisLabel(summaryPage()))
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun theOverviewBarAxisStaysLatinInEnglish() {
        assertEquals(
            "the overview bar chart period number is not the Latin one in English",
            "1",
            barAxisLabel(overviewPage(0))
        )
    }

    companion object {
        private const val PERSIAN_ZERO = 0x06F0
        private const val PERSIAN_NINE = 0x06F9
        private const val PERSIAN_ONE = "۱"

        private fun assertPersianDigits(where: String, text: String) {
            assertTrue("$where is empty, so it carries no digits to check", text.isNotEmpty())
            for (at in 0 until text.length) {
                val character = text[at]
                assertTrue(
                    "$where drew $text, which carries the character $character from outside the Persian set",
                    character.code in PERSIAN_ZERO..PERSIAN_NINE
                )
            }
            assertEquals(
                "$where drew $text, and the formatter was asked for the first period, which is the Persian one",
                PERSIAN_ONE,
                text
            )
        }

        private fun onePeriod(): List<PeriodMoney> {
            return Collections.singletonList(PeriodMoney(Date(), Date()))
        }

        private fun oneBar(label: String): BarDataSet {
            return BarDataSet(ArrayList(Collections.singletonList(BarEntry(0f, 1f))), label)
        }

        private fun groupedBarData(): BarData {
            return BarData(oneBar("first"), oneBar("second"))
        }

        private fun oneLineData(): LineData {
            return LineData(
                LineDataSet(
                    ArrayList(Collections.singletonList(Entry(0f, 1f))),
                    "line"
                )
            )
        }

        private fun barAxisLabel(page: View): String {
            val chart = page.findViewById<BarChart>(R.id.bar_chart_view)
            assertNotNull("the page carries no bar chart, so there is no axis label to read", chart)
            return chart?.xAxis?.valueFormatter?.getFormattedValue(0f) ?: ""
        }

        private fun lineAxisLabel(page: View): String {
            val chart = page.findViewById<LineChart>(R.id.line_chart_view)
            assertNotNull("the page carries no line chart, so there is no axis label to read", chart)
            return chart?.xAxis?.valueFormatter?.getFormattedValue(0f) ?: ""
        }
    }
}
