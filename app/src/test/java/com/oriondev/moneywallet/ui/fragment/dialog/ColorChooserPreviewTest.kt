/*
 * Copyright (c) 2018.
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

import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.GridView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.oriondev.moneywallet.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController

/**
 * A preview repaints every screen behind the chooser, so it happens once, and only for a color the
 * user picked.
 * <p>
 * The hex field's watcher is what makes a typed color take effect, and the dialog writes that field
 * itself when it opens and on every tap, so both of those reach the watcher too.
 */
@RunWith(RobolectricTestRunner::class)
class ColorChooserPreviewTest {

    companion object {
        private const val PRESELECT = 0xFF3F51B5.toInt()
    }

    @Test
    fun openingTheChooserPreviewsNothingAndOneTapPreviewsOnce() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val host = CountingHost()
            controller.get().supportFragmentManager
                .beginTransaction().add(host, "host").commitNow()
            val chooser = ColorChooserDialog.newInstance(R.string.dialog_color_picker_title, false, PRESELECT)
            chooser.showNow(host.childFragmentManager, "chooser")

            assertEquals(
                "the color the chooser opened on was previewed before anyone picked it",
                0, host.previews
            )

            firstSwatchOf(chooser).performClick()
            assertEquals("one tap, one repaint of every screen", 1, host.previews)
        } finally {
            controller.close()
        }
    }

    /**
     * The field's own watcher is what puts a preview back when the framework restores the field,
     * and it ignores anything that is not six digits of a real color, which is what half typing a
     * custom value leaves behind.
     */
    @Test
    fun aPreviewOutlivesTheScreenBeingRebuiltUnderAHalfTypedHexValue() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val activity = controller.get()
            val host = CountingHost()
            activity.supportFragmentManager
                .beginTransaction().add(host, "host").commitNow()
            val chooser = ColorChooserDialog.newInstance(R.string.dialog_color_picker_title, false, PRESELECT)
            chooser.showNow(host.childFragmentManager, "chooser")
            firstSwatchOf(chooser).performClick()
            hexFieldOf(chooser).setText("F4433")

            controller.recreate()

            val restored = controller.get()
                .supportFragmentManager.findFragmentByTag("host") as? CountingHost
            assertNotNull(restored)
            assertEquals(
                "the rebuilt screen was left showing the stored color while the chooser still held the picked one",
                1, restored?.previews
            )
        } finally {
            controller.close()
        }
    }

    /**
     * The dim is over the surface being previewed, so it goes while one is up. Walking back to
     * the color that is stored ends the preview and has to look like it did on opening.
     */
    @Test
    fun theDimFollowsWhatIsBeingPreviewed() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val host = CountingHost()
            controller.get().supportFragmentManager
                .beginTransaction().add(host, "host").commitNow()
            val chooser = ColorChooserDialog.newInstance(R.string.dialog_color_picker_title, false, PRESELECT)
            chooser.showNow(host.childFragmentManager, "chooser")
            assertTrue("the chooser opened over an undimmed screen", isDimmed(chooser))

            firstSwatchOf(chooser).performClick()
            assertFalse("the dim stayed over the color being previewed", isDimmed(chooser))

            hexFieldOf(chooser).setText("3F51B5")
            assertTrue("nothing is previewed and the dim never came back", isDimmed(chooser))
        } finally {
            controller.close()
        }
    }

    private fun isDimmed(chooser: ColorChooserDialog): Boolean {
        val dialog = chooser.dialog
        assertNotNull(dialog)
        val window = dialog?.window
        assertNotNull(window)
        val flags = window?.attributes?.flags ?: 0
        return (flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0
    }

    private fun hexFieldOf(chooser: ColorChooserDialog): EditText {
        val dialog = chooser.dialog
        assertNotNull(dialog)
        return dialog?.findViewById(R.id.color_chooser_hex_edit_text)
            ?: throw AssertionError("hex edit text not found")
    }

    private fun firstSwatchOf(chooser: ColorChooserDialog): View {
        val dialog = chooser.dialog
        assertNotNull(dialog)
        val grid = dialog?.findViewById<GridView>(R.id.color_chooser_grid_view)
        assertNotNull(grid)
        val g = grid ?: throw AssertionError("grid is null")
        return g.adapter.getView(0, null, g)
    }

    class CountingHost : Fragment(), ColorChooserDialog.Callback {

        var previews: Int = 0
            private set

        override fun onColorSelection(dialog: ColorChooserDialog?, color: Int) {}

        override fun onColorChooserDismissed(dialog: ColorChooserDialog?) {}

        /** As the engine does, which refuses to override the color that is stored. */
        override fun onColorPreview(dialog: ColorChooserDialog?, color: Int): Boolean {
            previews++
            return color != PRESELECT
        }
    }
}
