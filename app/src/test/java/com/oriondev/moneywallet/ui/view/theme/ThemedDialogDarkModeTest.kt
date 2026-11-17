package com.oriondev.moneywallet.ui.view.theme

import android.graphics.Color
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.fragment.dialog.GenericProgressDialog
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * Holds the two dialog layouts that are inflated outside a dialog's own themed context to the dark
 * surface they land on.
 */
@RunWith(RobolectricTestRunner::class)
class ThemedDialogDarkModeTest {

    @After
    fun restoreLightMode() {
        ThemeEngine.setMode(ThemeEngine.Mode.LIGHT)
    }

    @Test
    fun aTintedInputTakesItsTextAndHintFromTheDialogSurface() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val activity = controller.get()

            val lightCard = switchTo(ThemeEngine.Mode.LIGHT)
            val onLight = inflateInput(activity)
            ThemedDialog.tintInput(onLight)
            assertDarker("light mode text", onLight.currentTextColor, lightCard)
            assertDarker("light mode hint", onLight.currentHintTextColor, lightCard)
            assertFadedHint("light mode", onLight)

            val darkCard = switchTo(ThemeEngine.Mode.DARK)
            assertNotEquals("the card background did not change with the mode", lightCard, darkCard)
            val onDark = inflateInput(activity)
            ThemedDialog.tintInput(onDark)
            assertLighter("dark mode text", onDark.currentTextColor, darkCard)
            assertLighter("dark mode hint", onDark.currentHintTextColor, darkCard)
            assertFadedHint("dark mode", onDark)
        } finally {
            controller.close()
        }
    }

    @Test
    fun aProgressDialogInDarkModeDrawsItsMessageOnTheDarkSurface() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val activity = controller.get()
            val darkCard = switchTo(ThemeEngine.Mode.DARK)
            val dialog = GenericProgressDialog.newInstance(
                R.string.title_backup_creation,
                R.string.message_async_init,
                false
            )
            dialog.show(activity.supportFragmentManager, TAG_DIALOG)
            activity.supportFragmentManager.executePendingTransactions()
            assertNotNull(dialog.dialog)
            val message = dialog.dialog?.findViewById<TextView>(R.id.dialog_progress_message)
            assertNotNull(message)
            val nonNullMessage = message ?: return
            assertLighter("progress message", nonNullMessage.currentTextColor, darkCard)
        } finally {
            controller.close()
        }
    }

    @Test
    fun aProgressDialogInLightModeDrawsItsMessageOnTheLightSurface() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val activity = controller.get()
            val lightCard = switchTo(ThemeEngine.Mode.LIGHT)
            val dialog = GenericProgressDialog.newInstance(
                R.string.title_backup_creation,
                R.string.message_async_init,
                false
            )
            dialog.show(activity.supportFragmentManager, TAG_DIALOG)
            activity.supportFragmentManager.executePendingTransactions()
            assertNotNull(dialog.dialog)
            val message = dialog.dialog?.findViewById<TextView>(R.id.dialog_progress_message)
            assertNotNull(message)
            val nonNullMessage = message ?: return
            assertDarker("progress message", nonNullMessage.currentTextColor, lightCard)
        } finally {
            controller.close()
        }
    }

    @Test
    fun theWebdavFormTakesItsColorsFromTheDialogSurface() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val activity = controller.get()

            val darkCard = switchTo(ThemeEngine.Mode.DARK)
            val onDark = ThemedDialog.inflateScrollableView(activity, R.layout.dialog_webdav_setup)
            val darkUrl = onDark.findViewById<EditText>(R.id.webdav_url_edit_text)
            val darkNote = onDark.findViewById<TextView>(R.id.webdav_hint_text_view)
            assertNotNull(darkUrl)
            assertNotNull(darkNote)
            val nonNullDarkUrl = darkUrl ?: return
            val nonNullDarkNote = darkNote ?: return
            assertLighter("dark mode url text", nonNullDarkUrl.currentTextColor, darkCard)
            assertLighter("dark mode url hint", nonNullDarkUrl.currentHintTextColor, darkCard)
            assertLighter("dark mode note", nonNullDarkNote.currentTextColor, darkCard)

            val lightCard = switchTo(ThemeEngine.Mode.LIGHT)
            val onLight = ThemedDialog.inflateScrollableView(activity, R.layout.dialog_webdav_setup)
            val lightUrl = onLight.findViewById<EditText>(R.id.webdav_url_edit_text)
            val lightNote = onLight.findViewById<TextView>(R.id.webdav_hint_text_view)
            assertNotNull(lightUrl)
            assertNotNull(lightNote)
            val nonNullLightUrl = lightUrl ?: return
            val nonNullLightNote = lightNote ?: return
            assertDarker("light mode url text", nonNullLightUrl.currentTextColor, lightCard)
            assertDarker("light mode url hint", nonNullLightUrl.currentHintTextColor, lightCard)
            assertDarker("light mode note", nonNullLightNote.currentTextColor, lightCard)
        } finally {
            controller.close()
        }
    }

    @Test
    fun theFocusableDialogRootsSuppressTheDefaultFocusHighlight() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val activity = controller.get()

            val chooser = activity.layoutInflater.inflate(R.layout.dialog_color_chooser, null)
            assertFalse(
                "the color chooser root still takes the highlight",
                chooser.defaultFocusHighlightEnabled
            )

            val scroller = ThemedDialog.inflateScrollableView(activity, R.layout.dialog_webdav_setup)
            assertFalse(
                "the wrapping scroller still takes the highlight",
                scroller.defaultFocusHighlightEnabled
            )

            val recycler = activity.layoutInflater.inflate(R.layout.dialog_recycler_view, null)
            assertFalse(
                "the recycler root still takes the highlight",
                recycler.defaultFocusHighlightEnabled
            )
        } finally {
            controller.close()
        }
    }

    private fun inflateInput(activity: AppCompatActivity): EditText {
        val view = activity.layoutInflater
            .inflate(R.layout.dialog_input, null)
            .findViewById<EditText>(R.id.dialog_input_edit_text)
        assertNotNull(view)
        return view ?: EditText(activity)
    }

    private fun switchTo(mode: ThemeEngine.Mode): Int {
        ThemeEngine.setMode(mode)
        return ThemeEngine.getTheme().colorCardBackground
    }

    private fun assertLighter(what: String, color: Int, background: Int) {
        assertFalse("$what: the background is not dark", Util.isColorLight(background))
        assertTrue("$what: the color is not light on it", Util.isColorLight(color))
    }

    private fun assertDarker(what: String, color: Int, background: Int) {
        assertTrue("$what: the background is not light", Util.isColorLight(background))
        assertFalse("$what: the color is not dark on it", Util.isColorLight(color))
    }

    private fun assertFadedHint(what: String, editText: EditText) {
        assertTrue(
            "$what: the hint is faded past reading",
            Color.alpha(editText.currentHintTextColor) >= 64
        )
        assertTrue(
            "$what: the hint is not faded below the text",
            Color.alpha(editText.currentHintTextColor) < Color.alpha(editText.currentTextColor)
        )
    }

    companion object {
        private const val TAG_DIALOG = "ThemedDialogDarkModeTest::Dialog"
    }
}
