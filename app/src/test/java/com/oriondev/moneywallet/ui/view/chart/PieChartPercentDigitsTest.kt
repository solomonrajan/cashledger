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

package com.oriondev.moneywallet.ui.view.chart

import android.content.Context
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The percentage the period report pie chart writes on each of its wedges.
 */
@RunWith(RobolectricTestRunner::class)
class PieChartPercentDigitsTest {

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

    private fun wedgeLabel(sweepAngle: Float): String {
        return PieChart(themed()).percentLabel(sweepAngle)
    }

    @Test
    @Config(qualifiers = "fa-rIR")
    fun theWedgePercentageIsWrittenInPersianDigits() {
        requirePersianLocale()
        val label = wedgeLabel(EIGHTY_FIVE_PERCENT_SWEEP)
        assertTrue(
            "the pie wedge percentage is empty, so it carries no digits to check",
            label.isNotEmpty()
        )
        assertTrue(
            "the pie wedge percentage drew $label, which carries no digit from the Persian set",
            carries(label, PERSIAN_ZERO, PERSIAN_NINE)
        )
        assertTrue(
            "the pie wedge percentage drew $label, which carries a Latin digit",
            !carries(label, '0'.code, '9'.code)
        )
        assertTrue(
            "the pie wedge percentage drew $label, which carries the ASCII percent sign",
            label.indexOf('%') < 0
        )
    }

    @Test
    @Config(qualifiers = "fa-rIR")
    fun aHalfPercentRoundsUp() {
        requirePersianLocale()
        val label = wedgeLabel(SEVENTEEN_AND_A_HALF_PERCENT_SWEEP)
        assertTrue(
            "the pie wedge percentage is empty, so it carries no digits to check",
            label.isNotEmpty()
        )
        assertTrue(
            "the pie wedge percentage drew $label, which carries no digit from the Persian set",
            carries(label, PERSIAN_ZERO, PERSIAN_NINE)
        )
    }

    @Test
    @Config(qualifiers = "fa-rIR")
    fun anEmptyPeriodReadsZeroPercent() {
        requirePersianLocale()
        val label = wedgeLabel(Float.NaN)
        assertTrue(
            "the pie wedge percentage is empty, so it carries no digits to check",
            label.isNotEmpty()
        )
        assertTrue(
            "the pie wedge percentage drew $label, which carries no digit from the Persian set",
            carries(label, PERSIAN_ZERO, PERSIAN_NINE)
        )
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun theWedgePercentageStaysLatinInEnglish() {
        val label = wedgeLabel(EIGHTY_FIVE_PERCENT_SWEEP)
        assertEquals("the pie wedge percentage is not 85% in English", "85%", label)
    }

    companion object {
        private const val PERSIAN_ZERO = 0x06F0
        private const val PERSIAN_NINE = 0x06F9

        private const val EIGHTY_FIVE_PERCENT_SWEEP = 306f
        private const val SEVENTEEN_AND_A_HALF_PERCENT_SWEEP = 63f

        private fun carries(text: String, from: Int, to: Int): Boolean {
            for (at in 0 until text.length) {
                val character = text[at].code
                if (character in from..to) {
                    return true
                }
            }
            return false
        }
    }
}
