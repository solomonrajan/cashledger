package com.oriondev.moneywallet.ui.fragment.dialog

import android.graphics.Color
import android.os.Looper
import android.util.DisplayMetrics
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.view.ColorSwatchView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

/**
 * Drives the color chooser on the JVM under a host fragment that plays the caller: which swatch it
 * opens on, what the hex field reads, and which color OK hands back. The swatches it reads are the
 * grid's own children, so what it sees is what the grid draws.
 */
@RunWith(RobolectricTestRunner::class)
class ColorChooserDialogTest {

    companion object {
        private const val TAG_HOST = "ColorChooserDialogTest::Host"
        private const val TAG_DIALOG = "ColorChooserDialogTest::Dialog"
    }

    /** The caller the dialog resolves its callback from, keeping what it was handed. */
    class HostFragment : Fragment(), ColorChooserDialog.Callback {

        var selected: Int? = null
        var dismissed: Boolean = false

        override fun onColorSelection(dialog: ColorChooserDialog, color: Int) {
            selected = color
        }

        override fun onColorChooserDismissed(dialog: ColorChooserDialog) {
            dismissed = true
        }
    }

    @Test
    fun aPreselectThatThePaletteCarriesOpensOnItsSwatch() {
        run(Color.parseColor("#3F51B5"), false) { host, dialog ->
            assertTrue(swatch(dialog, 4).isSwatchSelected)
            assertFalse(swatch(dialog, 0).isSwatchSelected)
            assertEquals("3F51B5", hex(dialog).text.toString())
            ok(dialog)
            assertEquals(Integer.valueOf(0xFF3F51B5.toInt()), host.selected)
        }
    }

    @Test
    fun aPreselectOutsideThePaletteOpensOnNoSwatchAtAll() {
        run(Color.parseColor("#FF0000"), false) { host, dialog ->
            val grid = grid(dialog)
            assertEquals(grid.adapter.count, grid.childCount)
            for (index in 0 until grid.childCount) {
                assertFalse(swatch(dialog, index).isSwatchSelected)
            }
            assertEquals("FF0000", hex(dialog).text.toString())
            ok(dialog)
            assertEquals(Integer.valueOf(0xFFFF0000.toInt()), host.selected)
        }
    }

    @Test
    fun aColorTypedIntoTheHexFieldSelectsItsSwatch() {
        run(Color.parseColor("#FF0000"), false) { host, dialog ->
            hex(dialog).setText("4CAF50")
            layout(dialog)
            assertEquals("#4CAF50", preview(dialog).contentDescription.toString())
            assertTrue(swatch(dialog, 9).isSwatchSelected)
            ok(dialog)
            assertEquals(Integer.valueOf(0xFF4CAF50.toInt()), host.selected)
        }
    }

    @Test
    fun aColorTypedWithAnExtraDigitIsCutToTheSixTheFieldHolds() {
        run(Color.parseColor("#FF0000"), false) { host, dialog ->
            hex(dialog).setText("4CAF50A")
            layout(dialog)
            assertEquals("4CAF50", hex(dialog).text.toString())
            assertTrue(swatch(dialog, 9).isSwatchSelected)
            ok(dialog)
            assertEquals(Integer.valueOf(0xFF4CAF50.toInt()), host.selected)
        }
    }

    @Test
    fun aTapOnAHueOtherThanTheRingedOneTakesItsColorAndShade() {
        run(Color.parseColor("#FF0000"), false) { host, dialog ->
            swatch(dialog, 4).performClick()
            layout(dialog)
            assertEquals(10, grid(dialog).childCount)
            // Indigo 500 is the hue itself and the sixth of the shades this level is showing.
            assertTrue(swatch(dialog, 5).isSwatchSelected)
            assertEquals("3F51B5", hex(dialog).text.toString())
            ok(dialog)
            assertEquals(Integer.valueOf(0xFF3F51B5.toInt()), host.selected)
        }
    }

    @Test
    fun backFromAShadeLevelRingsTheFamilyOfTheTypedColorAndDrillingBackInKeepsIt() {
        run(Color.parseColor("#3F51B5"), false) { host, dialog ->
            swatch(dialog, 4).performClick()
            layout(dialog)
            assertEquals(dialog.getString(R.string.action_back), negative(dialog).text.toString())
            assertEquals(10, grid(dialog).childCount)
            assertTrue(swatch(dialog, 5).isSwatchSelected)
            // Red 300, a shade of a hue other than the one this level is showing.
            hex(dialog).setText("E57373")
            layout(dialog)
            val shades = grid(dialog)
            assertEquals(shades.adapter.count, shades.childCount)
            for (index in 0 until shades.childCount) {
                assertFalse(swatch(dialog, index).isSwatchSelected)
            }
            back(dialog)
            layout(dialog)
            assertEquals(19, grid(dialog).childCount)
            assertTrue(swatch(dialog, 0).isSwatchSelected)
            assertFalse(swatch(dialog, 4).isSwatchSelected)
            assertEquals("E57373", hex(dialog).text.toString())
            swatch(dialog, 0).performClick()
            layout(dialog)
            assertEquals(dialog.getString(R.string.action_back), negative(dialog).text.toString())
            assertEquals(10, grid(dialog).childCount)
            assertTrue(swatch(dialog, 3).isSwatchSelected)
            assertEquals("E57373", hex(dialog).text.toString())
            ok(dialog)
            assertEquals(Integer.valueOf(0xFFE57373.toInt()), host.selected)
        }
    }

    @Test
    fun aPreselectCarryingAnAlphaChannelIsTakenAsItsOpaqueColor() {
        run(0x803F51B5.toInt(), false) { host, dialog ->
            assertTrue(swatch(dialog, 4).isSwatchSelected)
            assertEquals("3F51B5", hex(dialog).text.toString())
            ok(dialog)
            assertEquals(Integer.valueOf(0xFF3F51B5.toInt()), host.selected)
        }
    }

    @Test
    fun rotatingOnTheShadeLevelKeepsTheLevelTheRingAndTheField() {
        run(Color.parseColor("#3F51B5"), false) { _, dialog ->
            swatch(dialog, 4).performClick()
            layout(dialog)
            // Indigo 50, the first of the shades this level is showing.
            hex(dialog).setText("E8EAF6")
            layout(dialog)
            assertTrue(swatch(dialog, 0).isSwatchSelected)

            val controller = mController ?: return@run
            controller.recreate()
            shadowOf(Looper.getMainLooper()).idle()
            val restoredHost = controller.get()
                .supportFragmentManager.findFragmentByTag(TAG_HOST) as? HostFragment
            assertNotNull(restoredHost)
            val host = restoredHost ?: return@run
            val restored = host.childFragmentManager.findFragmentByTag(TAG_DIALOG) as? ColorChooserDialog
            assertNotNull(restored)
            val restoredDialog = restored ?: return@run
            layout(restoredDialog)
            assertEquals(10, grid(restoredDialog).childCount)
            assertTrue(swatch(restoredDialog, 0).isSwatchSelected)
            assertEquals("E8EAF6", hex(restoredDialog).text.toString())
            assertEquals(restoredDialog.getString(R.string.action_back), negative(restoredDialog).text.toString())

            back(restoredDialog)
            layout(restoredDialog)
            assertEquals(19, grid(restoredDialog).childCount)
            assertTrue(swatch(restoredDialog, 4).isSwatchSelected)
            ok(restoredDialog)
            assertEquals(Integer.valueOf(0xFFE8EAF6.toInt()), host.selected)
        }
    }

    private fun interface Case {
        fun run(host: HostFragment, dialog: ColorChooserDialog)
    }

    private var mController: ActivityController<AppCompatActivity>? = null

    private fun run(preselect: Int, accentPalette: Boolean, body: Case) {
        // The host is a bare AppCompatActivity, which the manifest does not declare, so it is
        // driven through a controller instead of through ActivityScenario. The controller is a
        // field so that a case which recreates the activity can reach it.
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        mController = controller
        try {
            val activity = controller.get()
            val host = HostFragment()
            activity.supportFragmentManager.beginTransaction().add(host, TAG_HOST).commitNow()
            val dialog = ColorChooserDialog.newInstance(
                R.string.dialog_color_picker_title, accentPalette, preselect
            )
            dialog.show(host.childFragmentManager, TAG_DIALOG)
            host.childFragmentManager.executePendingTransactions()
            assertNull(host.selected)
            layout(dialog)
            body.run(host, dialog)
        } finally {
            controller.close()
        }
    }

    /**
     * Measures and lays out the dialog's decor, which is what attaches the grid's children. The
     * adapter rebuilds them on the next layout, so this runs again after anything that changes the
     * grid.
     */
    private fun layout(dialog: ColorChooserDialog) {
        val decor = dialog.dialog?.window?.decorView ?: return
        val metrics = decor.resources.displayMetrics
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.AT_MOST)
        )
        decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
    }

    private fun grid(dialog: ColorChooserDialog): GridView {
        val d = dialog.dialog ?: throw AssertionError("Dialog is null")
        return d.findViewById(R.id.color_chooser_grid_view)
    }

    private fun swatch(dialog: ColorChooserDialog, index: Int): ColorSwatchView {
        return grid(dialog).getChildAt(index) as ColorSwatchView
    }

    private fun hex(dialog: ColorChooserDialog): EditText {
        val d = dialog.dialog ?: throw AssertionError("Dialog is null")
        return d.findViewById(R.id.color_chooser_hex_edit_text)
    }

    private fun preview(dialog: ColorChooserDialog): ColorSwatchView {
        val d = dialog.dialog ?: throw AssertionError("Dialog is null")
        return d.findViewById(R.id.color_chooser_preview_view)
    }

    private fun negative(dialog: ColorChooserDialog): Button {
        val d = dialog.dialog as? AlertDialog ?: throw AssertionError("Dialog is not AlertDialog")
        return d.getButton(AlertDialog.BUTTON_NEGATIVE)
    }

    private fun back(dialog: ColorChooserDialog) {
        negative(dialog).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun ok(dialog: ColorChooserDialog) {
        val d = dialog.dialog as? AlertDialog ?: throw AssertionError("Dialog is not AlertDialog")
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        // AlertController hands the button click to a Handler, and the looper is paused here.
        shadowOf(Looper.getMainLooper()).idle()
    }
}
