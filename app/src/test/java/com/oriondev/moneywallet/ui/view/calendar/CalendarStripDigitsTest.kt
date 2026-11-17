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

package com.oriondev.moneywallet.ui.view.calendar

import android.content.Context
import android.text.Layout
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * The numbers the two calendar strips draw, in the digits of the language the app is in.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarStripDigitsTest {

    private fun themed(): Context {
        return ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.MoneyWalletAppTheme
        )
    }

    private fun laidOut(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.AT_MOST)
        )
        view.layout(0, 0, WIDTH, view.measuredHeight)
    }

    private fun strip(): TimelineView {
        val strip = TimelineView(themed())
        strip.setFirstDate(2026, 8, 12)
        strip.setLastDate(2026, 11, 31)
        laidOut(strip)
        return strip
    }

    private fun monthRow(): MonthView {
        val row = MonthView(themed())
        row.setLastDate(2026, 11)
        row.setSelectedMonth(2026, 8, false)
        laidOut(row)
        return row
    }

    private fun monthRowFrom(year: Int): MonthView {
        val row = MonthView(themed())
        row.setFirstDate(year, 0)
        row.setLastDate(year, 11)
        laidOut(row)
        return row
    }

    private fun requirePersianLocale() {
        assertEquals(
            "the qualifier did not reach the default locale, so nothing below is a check",
            "fa",
            Locale.getDefault().language
        )
    }

    @Test
    @Config(qualifiers = "fa-rIR")
    fun theDayNumberIsWrittenInPersianDigits() {
        requirePersianLocale()
        val text = label(strip(), 0, R.id.mti_timeline_lbl_date).text
        assertPersianDigits("the day number", text)
        assertEquals(
            "the day number $text lost a digit, and the label carries a two character limit",
            2,
            digitCount(text)
        )
    }

    @Test
    @Config(qualifiers = "fa-rIR")
    fun theMonthLabelYearIsWrittenInPersianDigits() {
        requirePersianLocale()
        assertPersianDigits(
            "the month label",
            label(monthRow(), 0, R.id.mti_month_lbl).text
        )
    }

    @Test
    @Config(qualifiers = "fa-rIR")
    fun theMonthLabelKeepsBothYearDigits() {
        requirePersianLocale()
        val text = label(monthRowFrom(2009), 0, R.id.mti_month_lbl).text
        assertPersianDigits("the month label", text)
        assertEquals(
            "the label $text carries one year digit, so January 2009 names the year 9",
            2,
            digitCount(text)
        )
    }

    @Test
    @Config(qualifiers = "fa-rIR")
    fun theMonthLabelsKeepTheirDeclaredSizeInPersian() {
        requirePersianLocale()
        val row = monthRow()
        val declared = row.resources.getDimension(R.dimen.view_mti_month_lbl_text)
        assertEquals(
            "the row stepped its text size down in Persian, which is what a size taken from the Latin digits does, and what a wider label or a narrower cell would do as well",
            declared,
            label(row, 0, R.id.mti_month_lbl).textSize,
            0.01f
        )
    }

    @Test
    @Config(qualifiers = "fa-rIR")
    fun everyMonthLabelFitsItsCellInPersian() {
        requirePersianLocale()
        val row = monthRow()
        assertTrue("the row laid out no cells, so there is no label to fit", row.childCount > 0)
        for (cell in 0 until row.childCount) {
            val item = row.getChildAt(cell)
            val lbl = item.findViewById<TextView>(R.id.mti_month_lbl)
            val available = item.width - item.paddingLeft - item.paddingRight
            assertTrue("the cell has no width, so the fit below is not a check", available > 0)
            assertTrue(
                "the label ${lbl.text} asks for more width than its cell has",
                Math.ceil(Layout.getDesiredWidth(lbl.text, lbl.paint).toDouble()) <= available
            )
        }
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun theDayNumberStaysLatinInEnglish() {
        val text = label(strip(), 0, R.id.mti_timeline_lbl_date).text
        assertTrue("the day number is empty, so it carries no digits to check", text.isNotEmpty())
        for (at in 0 until text.length) {
            val character = text[at]
            assertTrue(
                "the day number drew $text in English",
                character in '0'..'9'
            )
        }
    }

    companion object {
        private const val PERSIAN_ZERO = 0x06F0
        private const val PERSIAN_NINE = 0x06F9

        private const val WIDTH = 1080
        private const val HEIGHT = 400

        private fun assertPersianDigits(where: String, text: CharSequence) {
            assertTrue("$where is empty, so it carries no digits to check", text.isNotEmpty())
            var found = false
            for (at in 0 until text.length) {
                val character = text[at]
                if (Character.isDigit(character)) {
                    found = true
                    assertTrue(
                        "$where drew $text, which carries the digit $character from outside the Persian set",
                        character.code in PERSIAN_ZERO..PERSIAN_NINE
                    )
                }
            }
            assertTrue("$where drew $text, which carries no digit at all", found)
        }

        private fun digitCount(text: CharSequence): Int {
            var found = 0
            for (at in 0 until text.length) {
                if (Character.isDigit(text[at])) {
                    found++
                }
            }
            return found
        }

        private fun label(strip: ViewGroup, cell: Int, id: Int): TextView {
            assertTrue(
                "the strip laid out no cells, so there is no label to read",
                strip.childCount > cell
            )
            val textView = strip.getChildAt(cell).findViewById<TextView>(id)
            return textView ?: TextView(strip.context)
        }
    }
}
