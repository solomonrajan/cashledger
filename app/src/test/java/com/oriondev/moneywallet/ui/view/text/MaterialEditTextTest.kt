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
import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLegacyCanvas

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

    private fun bottomLineOf(view: MaterialEditText): ShadowLegacyCanvas.RectPaintHistoryEvent {
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

    private fun assertRunsTheFullWidth(line: ShadowLegacyCanvas.RectPaintHistoryEvent) {
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

    companion object {
        private const val WIDTH = 320
        private const val HEIGHT = 120

        private const val SIDE_PADDING = 8
        private const val CANCEL_SLOT = 36
    }
}
