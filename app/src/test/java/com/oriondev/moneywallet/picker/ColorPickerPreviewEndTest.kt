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

package com.oriondev.moneywallet.picker

import androidx.appcompat.app.AppCompatActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController

/**
 * Ending a preview is not per picker, it drops whatever is being shown, so a picker whose chooser
 * showed nothing has to stay out of it.
 */
@RunWith(RobolectricTestRunner::class)
class ColorPickerPreviewEndTest {

    @Test
    fun aChooserThatShowedNothingEndsNothing() {
        val controller: ActivityController<AppCompatActivity> =
            Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val recorder = RecordingController()
            val theme = ColorPicker.createPicker(
                controller.get().supportFragmentManager,
                THEME_TAG,
                0xFF3F51B5.toInt(),
                false,
                recorder
            )
            val plain = ColorPicker.createPicker(
                controller.get().supportFragmentManager,
                PLAIN_TAG,
                0xFF2196F3.toInt(),
                false,
                recorder
            )

            assertTrue(theme.onColorPreview(null, 0xFF009688.toInt()))
            plain.onColorPreview(null, 0xFF4CAF50.toInt())
            plain.onColorChooserDismissed(null)
            assertEquals("the chooser that showed nothing ended the one that did", 0, recorder.ended)

            theme.onColorChooserDismissed(null)
            assertEquals(1, recorder.ended)
        } finally {
            controller.close()
        }
    }

    private class RecordingController : ColorPicker.Controller {
        var ended: Int = 0
            private set

        override fun onColorChanged(tag: String?, color: Int, autoFired: Boolean) {}

        override fun onColorPreview(tag: String?, color: Int): Boolean {
            return THEME_TAG == tag
        }

        override fun onColorPreviewEnded() {
            ended++
        }
    }

    companion object {
        private const val THEME_TAG = "theme"
        private const val PLAIN_TAG = "plain"
    }
}
