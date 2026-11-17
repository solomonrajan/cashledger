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

package com.oriondev.moneywallet.ui.fragment.dialog

import android.os.Looper
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.RecurrenceSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.util.Date
import java.util.Locale

/**
 * The recurrence picker writes its every and times counts in the digits of the app's language,
 * and reads them back into the same numbers. A count it failed to parse would silently come back
 * as 1, so the counts used here are above 1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fa-rIR")
class RecurrencePickerDigitsTest {

    companion object {
        private const val TAG_HOST = "RecurrencePickerDigitsTest::Host"
        private const val TAG_DIALOG = "RecurrencePickerDigitsTest::Dialog"

        private const val EVERY = 2
        private const val TIMES = 3

        private fun everyTwoDaysThreeTimes(): RecurrenceSetting {
            val builder = RecurrenceSetting.Builder(Date(), RecurrenceSetting.TYPE_DAILY)
            builder.setOffset(EVERY)
            builder.setEndFor(TIMES)
            return builder.build()
        }

        private fun every(dialog: RecurrencePickerDialog): EditText {
            val d = dialog.dialog ?: throw AssertionError("dialog is null")
            return d.findViewById(R.id.every_number_edit_text)
        }

        private fun times(dialog: RecurrencePickerDialog): EditText {
            val d = dialog.dialog ?: throw AssertionError("dialog is null")
            return d.findViewById(R.id.end_type_for_edit_text)
        }

        private fun assertCounts(dialog: RecurrencePickerDialog) {
            assertEquals("fa", Locale.getDefault().language)
            assertEquals("the every count", "۲", every(dialog).text.toString())
            assertEquals("the times count", "۳", times(dialog).text.toString())
            val read = dialog.getCurrentRecurrenceSetting()
            assertEquals(RecurrenceSetting.END_FOR, read.getEndType())
            assertEquals("the every count read back", EVERY, read.getOffsetValue())
            assertEquals("the times count read back", TIMES, read.getOccurrenceValue())
        }

        private fun run(body: (controller: ActivityController<AppCompatActivity>, dialog: RecurrencePickerDialog) -> Unit) {
            val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
            try {
                val host = HostFragment()
                controller.get().supportFragmentManager.beginTransaction()
                    .add(host, TAG_HOST).commitNow()
                val dialog = RecurrencePickerDialog.newInstance()
                dialog.showPicker(host.childFragmentManager, TAG_DIALOG,
                    everyTwoDaysThreeTimes(), true, false)
                host.childFragmentManager.executePendingTransactions()
                body(controller, dialog)
            } finally {
                controller.close()
            }
        }
    }

    class HostFragment : Fragment()

    @Test
    fun theCountsAreWrittenInPersianDigitsAndReadBack() {
        run { _, dialog -> assertCounts(dialog) }
    }

    @Test
    fun theCountsSurviveARecreate() {
        run { controller, _ ->
            controller.recreate()
            shadowOf(Looper.getMainLooper()).idle()
            val host = controller.get().supportFragmentManager
                .findFragmentByTag(TAG_HOST)
            assertNotNull(host)
            val restored = host?.childFragmentManager?.findFragmentByTag(TAG_DIALOG) as? RecurrencePickerDialog
            assertNotNull(restored)
            restored?.let { assertCounts(it) }
        }
    }
}
