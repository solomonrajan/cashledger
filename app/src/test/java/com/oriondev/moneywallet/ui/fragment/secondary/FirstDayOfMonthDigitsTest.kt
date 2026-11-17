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

package com.oriondev.moneywallet.ui.fragment.secondary

import android.os.Looper
import android.widget.ListAdapter
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.activity.BackupListActivity
import com.oriondev.moneywallet.ui.preference.ThemedListPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.util.Locale

/**
 * The first day of month setting writes its day numbers in the digits of the app's language,
 * while the values it stores stay ASCII, since they are read back with Integer.parseInt. The
 * preference keeps its entries private, so they are read off the list its dialog shows.
 */
@RunWith(RobolectricTestRunner::class)
class FirstDayOfMonthDigitsTest {

    companion object {
        private const val TAG_FRAGMENT = "FirstDayOfMonthDigitsTest::Fragment"

        private const val PERSIAN_ZERO = 0x06F0
        private const val PERSIAN_NINE = 0x06F9

        private fun onSetting(storedDay: Int, check: (ThemedListPreference, AlertDialog) -> Unit) {
            PreferenceManager.setCurrentFirstDayOfMonth(storedDay)
            ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val fragment = UserInterfaceSettingFragment()
                    activity.supportFragmentManager
                        .beginTransaction()
                        .add(android.R.id.content, fragment, TAG_FRAGMENT)
                        .commitNow()
                    val preference = fragment.findPreference<ThemedListPreference>("first_day_month")
                    assertNotNull("the settings screen has no first day of month row", preference)
                    val pref = preference ?: return@onActivity
                    pref.performClick()
                    shadowOf(Looper.getMainLooper()).idle()
                    val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
                    assertNotNull("tapping the row opened no dialog", dialog)
                    val dlg = dialog ?: return@onActivity
                    check(pref, dlg)
                }
            }
        }

        private fun choose(dialog: AlertDialog, position: Int) {
            dialog.listView.performItemClick(null, position, position.toLong())
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
        }

        private fun assertDigitsIn(where: String, text: CharSequence, zero: Char, nine: Char) {
            assertTrue("$where is empty, so it carries no digits to check", text.isNotEmpty())
            for (at in 0 until text.length) {
                val character = text[at]
                assertTrue(
                    "$where reads $text, which carries the digit $character from outside the expected set",
                    character in zero..nine
                )
            }
        }

        private fun assertEntries(dialog: AlertDialog, zero: Char, nine: Char) {
            val list: ListAdapter = dialog.listView.adapter
            assertEquals("the list does not offer the 28 days", 28, list.count)
            for (position in 0 until list.count) {
                assertDigitsIn(
                    "the entry for day " + (position + 1),
                    list.getItem(position).toString(), zero, nine
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "fa-rIR")
    fun theDaysAreListedAndSummarizedInPersianDigits() {
        assertEquals(
            "the qualifier did not reach the default locale, so nothing below is a check",
            "fa", Locale.getDefault().language
        )
        onSetting(12) { preference, dialog ->
            assertEntries(dialog, PERSIAN_ZERO.toChar(), PERSIAN_NINE.toChar())
            assertEquals("۱۲", preference.summary?.toString().orEmpty())
            assertEquals("12", preference.value)
            choose(dialog, 14)
            assertEquals(15, PreferenceManager.getFirstDayOfMonth())
            assertEquals("15", preference.value)
            assertEquals("۱۵", preference.summary?.toString().orEmpty())
        }
    }

    @Test
    @Config(qualifiers = "en")
    fun theDaysStayAsciiInEnglish() {
        onSetting(12) { preference, dialog ->
            assertEntries(dialog, '0', '9')
            assertEquals("12", preference.summary?.toString().orEmpty())
            assertEquals("12", preference.value)
            choose(dialog, 14)
            assertEquals(15, PreferenceManager.getFirstDayOfMonth())
            assertEquals("15", preference.summary?.toString().orEmpty())
        }
    }
}
