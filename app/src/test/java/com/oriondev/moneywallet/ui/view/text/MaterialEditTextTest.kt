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

package com.oriondev.moneywallet.ui.view.text

import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowCanvas
import org.robolectric.shadows.ShadowLegacyCanvas
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * Calls onDrawBottomLine on a laid out field and reads the rectangle it drew off the canvas
 * shadow.
 */
@RunWith(RobolectricTestRunner::class)
class MaterialEditTextTest {

    @Before
    fun theDensityIsTheOneTheseFiguresAreIn() {
        assertEquals(
            "the figures below are pixel counts taken at 160 dpi, where one dp is one px",
            160,
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .resources.displayMetrics.densityDpi
        )
    }

    private fun field(showCancelButton: Boolean): MaterialEditText {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.MoneyWalletAppTheme
        )
        val attributes = if (showCancelButton) {
            Robolectric.buildAttributeSet()
                .addAttribute(R.attr.met_showCancelButton, "true").build()
        } else {
            Robolectric.buildAttributeSet().build()
        }
        val view = MaterialEditText(context, attributes)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, WIDTH, HEIGHT)
        return view
    }

    private fun bottomLineOf(view: MaterialEditText): ShadowCanvas.RectPaintHistoryEvent {
        val canvas = Canvas()
        view.onDrawBottomLine(canvas)
        val shadow = Shadows.shadowOf(canvas) as ShadowLegacyCanvas
        assertEquals(
            "onDrawBottomLine drew something other than one rectangle",
            1,
            shadow.rectPaintHistoryCount
        )
        return shadow.lastDrawnRect
    }

    private fun assertRunsTheFullWidth(line: ShadowCanvas.RectPaintHistoryEvent) {
        assertEquals("the line does not start at the side padding", SIDE_PADDING.toFloat(), line.left, 0f)
        assertEquals(
            "the line does not end at the side padding",
            (WIDTH - SIDE_PADDING).toFloat(),
            line.right,
            0f
        )
    }

    @Test
    fun leftToRightWithNoButtonTheLineRunsBetweenTheSidePaddings() {
        val view = field(false)
        assertEquals(SIDE_PADDING, view.paddingLeft)
        assertEquals(SIDE_PADDING, view.paddingRight)
        assertRunsTheFullWidth(bottomLineOf(view))
    }

    @Test
    fun leftToRightWithAButtonTheLineKeepsTheWidthItHasWithout() {
        val view = field(true)
        assertEquals(SIDE_PADDING, view.paddingLeft)
        assertEquals(
            "the button slot is not held on the right",
            SIDE_PADDING + CANCEL_SLOT,
            view.paddingRight
        )
        assertRunsTheFullWidth(bottomLineOf(view))
    }

    @Test
    @Config(qualifiers = "ar-rEG-ldrtl")
    fun rightToLeftWithAButtonTheLineKeepsTheWidthItHasWithout() {
        val view = field(true)
        assertTrue(
            "the configuration did not come up right to left",
            Utils.isRtl(view.resources)
        )
        assertEquals(
            "the button slot is not held on the left",
            SIDE_PADDING + CANCEL_SLOT,
            view.paddingLeft
        )
        assertEquals(SIDE_PADDING, view.paddingRight)
        assertRunsTheFullWidth(bottomLineOf(view))
    }

    @Test
    @Config(qualifiers = "ar-rEG-ldrtl")
    fun rightToLeftWithNoButtonTheLineRunsBetweenTheSidePaddings() {
        val view = field(false)
        assertTrue(
            "the configuration did not come up right to left",
            Utils.isRtl(view.resources)
        )
        assertEquals(SIDE_PADDING, view.paddingLeft)
        assertEquals(SIDE_PADDING, view.paddingRight)
        assertRunsTheFullWidth(bottomLineOf(view))
    }

    @Test
    fun bothEdgesOfTheLineCarryTheScrollOffset() {
        val view = field(false)
        val scroll = 50
        view.scrollX = scroll
        assertEquals("the field did not take the scroll offset", scroll, view.scrollX)
        val line = bottomLineOf(view)
        assertEquals(
            "the left edge does not move with the scroll offset",
            (SIDE_PADDING + scroll).toFloat(),
            line.left,
            0f
        )
        assertEquals(
            "the right edge does not move with the scroll offset",
            (WIDTH - SIDE_PADDING + scroll).toFloat(),
            line.right,
            0f
        )
    }

    private fun floatingLabelField(xmlHint: String?): MaterialEditText {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.MoneyWalletAppTheme
        )
        val attributes = Robolectric.buildAttributeSet()
            .addAttribute(R.attr.met_mode, "floatingLabel")
        if (xmlHint != null) {
            attributes.addAttribute(android.R.attr.hint, xmlHint)
        }
        return MaterialEditText(context, attributes.build())
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aLabelSetAfterInflationGetsTheRoomOfOneSetInXml() {
        val withoutLabel = floatingLabelField(null).paddingTop
        val withXmlLabel = floatingLabelField("Food").paddingTop
        assertTrue(
            "a label in the xml makes no room above the text, so this cannot tell",
            withXmlLabel > withoutLabel
        )
        val view = floatingLabelField(null)
        view.floatingLabelText = "Food"
        assertEquals(
            "the label set later does not make the room one from the xml does",
            withXmlLabel,
            view.paddingTop
        )
    }

    @Test
    fun aLabelSetAfterInflationIsDrawnOnceThereIsText() {
        val view = floatingLabelField(null)
        view.floatingLabelText = "Food"
        view.setText("12.00")
        view.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, WIDTH, HEIGHT)
        // the label slides in when the text stops being empty, so run that animation out
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        val canvas = Canvas()
        view.onDrawFloatingLabel(canvas)
        val shadow = Shadows.shadowOf(canvas) as ShadowLegacyCanvas
        assertEquals("the label was not drawn", 1, shadow.textHistoryCount)
        assertEquals("Food", shadow.getDrawnTextEvent(0).text)
    }

    private fun labeledField(label: String): MaterialEditText {
        val view = floatingLabelField(null)
        view.floatingLabelText = label
        return view
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun theRoomForALabelDoesNotDependOnItsLetters() {
        val rent = labeledField("Rent").paddingTop
        assertEquals(
            "a label with descenders gets more room",
            rent,
            labeledField("Electricity").paddingTop
        )
        assertEquals(
            "a label in lower case gets less room",
            rent,
            labeledField("rent").paddingTop
        )
    }

    private class LabelCanvas : Canvas() {
        var text: String? = null
        var paint: Paint? = null

        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            this.text = text
            this.paint = paint
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aLabelWiderThanTheFieldIsDrawnEllipsized() {
        val label = "Remaining under Home supplies, Garden tools, Kitchen appliances and Bathroom fittings"
        val view = labeledField(label)
        view.setText("12.00")
        view.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, WIDTH, HEIGHT)
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        val canvas = LabelCanvas()
        view.onDrawFloatingLabel(canvas)
        assertNotNull("the label was not drawn", canvas.text)
        val drawnText = canvas.text ?: error("canvas text is null")
        val paint = canvas.paint ?: error("canvas paint is null")
        val textWidth = (WIDTH - view.paddingLeft - view.paddingRight).toFloat()
        assertTrue(
            "the label fits the field, so this cannot tell",
            paint.measureText(label) > textWidth
        )
        assertTrue("the label was not ellipsized: $drawnText", drawnText.endsWith("\u2026"))
        assertTrue(
            "the drawn label is wider than the field",
            paint.measureText(drawnText) <= textWidth
        )
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aLabelThatFitsOnlyOverTheButtonSlotIsDrawnInFull() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.MoneyWalletAppTheme
        )
        val view = MaterialEditText(
            context,
            Robolectric.buildAttributeSet()
                .addAttribute(R.attr.met_mode, "floatingLabel")
                .addAttribute(R.attr.met_showCancelButton, "true").build()
        )
        view.floatingLabelText = "Food"
        view.setText("12.00")
        view.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, WIDTH, HEIGHT)
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        assertEquals(
            "the button slot is not held on the right",
            SIDE_PADDING + CANCEL_SLOT,
            view.paddingRight
        )
        // the label paint only comes out of a draw, so a short label is drawn first
        var canvas = LabelCanvas()
        view.onDrawFloatingLabel(canvas)
        assertNotNull("the label was not drawn", canvas.text)
        val paint = canvas.paint ?: error("paint is null")
        val textWidth = (WIDTH - view.paddingLeft - view.paddingRight).toFloat()
        val words = "Remaining under Home supplies, Garden tools, Kitchen appliances and Bathroom fittings"
        var end = 1
        while (paint.measureText(words, 0, end) <= textWidth) {
            end++
        }
        val label = words.substring(0, end)
        assertTrue(
            "the label fits beside the button slot, so this cannot tell",
            paint.measureText(label) > textWidth
        )
        assertTrue(
            "the label does not fit over the button slot either, so this cannot tell",
            paint.measureText(label) < textWidth + CANCEL_SLOT
        )
        view.floatingLabelText = label
        canvas = LabelCanvas()
        view.onDrawFloatingLabel(canvas)
        assertEquals(label, canvas.text)
        assertTrue(
            "the label fits over the button slot, so this cannot tell",
            paint.measureText(words) > textWidth + CANCEL_SLOT
        )
        view.floatingLabelText = words
        canvas = LabelCanvas()
        view.onDrawFloatingLabel(canvas)
        val ellipsizedText = canvas.text ?: error("ellipsized text is null")
        assertTrue("the label was not ellipsized: $ellipsizedText", ellipsizedText.endsWith("\u2026"))
        assertTrue(
            "the drawn label is wider than the field and the button slot",
            paint.measureText(ellipsizedText) <= textWidth + CANCEL_SLOT
        )
    }

    @Test
    fun aLabelReachesAccessibilityAsTheHintWithoutBeingTheHint() {
        assertTrue(
            "the node has no hint text below API 26",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        )
        val labeled = labeledField("Food")
        assertNull("the label was set as the hint", labeled.hint)
        assertEquals("Food", labeled.createAccessibilityNodeInfo().hintText?.toString())
        val withXmlHint = floatingLabelField("Rent")
        assertEquals("Rent", withXmlHint.createAccessibilityNodeInfo().hintText?.toString())
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aLongLabelDoesNotMakeTheFieldTaller() {
        val label = "Remaining under Home supplies, Garden tools, Kitchen appliances and Bathroom fittings"
        val withShortLabel = wrappedHeight(labeledField("Rent"))
        val view = labeledField(label)
        assertTrue(
            "the label fits one line of the text, so this cannot tell",
            view.paint.measureText(label) > WIDTH
        )
        assertEquals("the long label makes the field taller", withShortLabel, wrappedHeight(view))
    }

    private fun wrappedHeight(view: MaterialEditText): Int {
        view.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        view.setText("12.00")
        view.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return view.measuredHeight
    }

    companion object {
        private const val WIDTH = 320
        private const val HEIGHT = 120

        private const val SIDE_PADDING = 8
        private const val CANCEL_SLOT = 36
    }
}
