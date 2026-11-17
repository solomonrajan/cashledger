package com.oriondev.moneywallet.ui.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the elbow onto a real bitmap under Robolectric's native graphics and reads the pixels
 * back.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoryChildIndicatorTest {

    private fun indicator(layoutDirection: Int, last: Boolean): CategoryChildIndicator {
        val view = CategoryChildIndicator(ApplicationProvider.getApplicationContext()).apply {
            setLineColor(LINE)
            setLast(last)
            setLayoutDirection(layoutDirection)
            measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, WIDTH, HEIGHT)
        }
        assertEquals(
            "the view did not resolve the layout direction it was given, so the case below would run against the wrong one",
            layoutDirection,
            view.layoutDirection
        )
        assertNotEquals(0, view.measuredWidth)
        assertNotEquals(0, view.measuredHeight)
        return view
    }

    private fun drawn(view: CategoryChildIndicator): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    /** The columns of one row that carry the line color, as a string of 41 dots and hashes. */
    private fun paintedColumns(bitmap: Bitmap, row: Int): String {
        val columns = StringBuilder()
        for (x in 0 until WIDTH) {
            columns.append(if (bitmap.getPixel(x, row) == LINE) '#' else '.')
        }
        return columns.toString()
    }

    @Test
    fun inALeftToRightLayoutTheArmRunsToTheRightEdge() {
        val bitmap = drawn(indicator(View.LAYOUT_DIRECTION_LTR, true))
        assertEquals(
            "the arm does not reach the right edge",
            LINE,
            bitmap.getPixel(WIDTH - 1, ROW_INSIDE_THE_ARM)
        )
        assertEquals("the arm reaches the left edge", 0, bitmap.getPixel(0, ROW_INSIDE_THE_ARM))
        assertEquals(
            "the vertical stroke is not on the center column",
            LINE,
            bitmap.getPixel(CENTER_COLUMN, ROW_ABOVE_THE_ARM)
        )
        assertEquals(0, bitmap.getPixel(0, ROW_ABOVE_THE_ARM))
        assertEquals(0, bitmap.getPixel(WIDTH - 1, ROW_ABOVE_THE_ARM))
    }

    @Test
    fun inARightToLeftLayoutTheArmRunsToTheLeftEdge() {
        val bitmap = drawn(indicator(View.LAYOUT_DIRECTION_RTL, true))
        assertEquals(
            "the arm does not reach the left edge, so it runs away from the child icon",
            LINE,
            bitmap.getPixel(0, ROW_INSIDE_THE_ARM)
        )
        assertEquals(
            "the arm still reaches the right edge",
            0,
            bitmap.getPixel(WIDTH - 1, ROW_INSIDE_THE_ARM)
        )
        assertEquals(
            "the vertical stroke is not on the center column",
            LINE,
            bitmap.getPixel(CENTER_COLUMN, ROW_ABOVE_THE_ARM)
        )
        assertEquals(0, bitmap.getPixel(0, ROW_ABOVE_THE_ARM))
        assertEquals(0, bitmap.getPixel(WIDTH - 1, ROW_ABOVE_THE_ARM))
    }

    @Test
    fun theVerticalStrokeSitsOnTheSameColumnsInBothDirections() {
        val expected = StringBuilder()
        for (x in 0 until WIDTH) {
            expected.append(if (x in STROKE_FIRST_COLUMN..STROKE_LAST_COLUMN) '#' else '.')
        }
        val leftToRight = paintedColumns(
            drawn(indicator(View.LAYOUT_DIRECTION_LTR, true)),
            ROW_ABOVE_THE_ARM
        )
        val rightToLeft = paintedColumns(
            drawn(indicator(View.LAYOUT_DIRECTION_RTL, true)),
            ROW_ABOVE_THE_ARM
        )
        assertEquals(expected.toString(), leftToRight)
        assertEquals(
            "the mirror moved the vertical stroke instead of mapping it onto itself, which is what a pivot other than the integer width / 2 does at an odd width",
            leftToRight,
            rightToLeft
        )
    }

    @Test
    fun theStrokeStopsAtTheArmForALastChildAndRunsThroughForAMiddleOne() {
        assertEquals(
            "a last child still draws below the arm",
            0,
            drawn(indicator(View.LAYOUT_DIRECTION_LTR, true))
                .getPixel(CENTER_COLUMN, ROW_BELOW_THE_ARM)
        )
        assertEquals(
            "a middle child stops at the arm, so the next child below is not linked",
            LINE,
            drawn(indicator(View.LAYOUT_DIRECTION_LTR, false))
                .getPixel(CENTER_COLUMN, ROW_BELOW_THE_ARM)
        )
    }

    @Test
    fun theCanvasIsLeftUnmirroredForWhateverDrawsAfterIt() {
        val view = indicator(View.LAYOUT_DIRECTION_RTL, true)
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val before = canvas.saveCount
        view.draw(canvas)
        assertEquals(
            "onDraw left a save on the canvas, so onDrawForeground and anything else drawing after it stays mirrored",
            before,
            canvas.saveCount
        )
        val marker = Paint().apply {
            color = Color.GREEN
        }
        canvas.drawRect(0f, (HEIGHT - 2).toFloat(), 4f, (HEIGHT - 1).toFloat(), marker)
        assertEquals(
            "the canvas is still mirrored for whatever draws after onDraw",
            Color.GREEN,
            bitmap.getPixel(0, HEIGHT - 2)
        )
        assertEquals(0, bitmap.getPixel(WIDTH - 1, HEIGHT - 2))
    }

    companion object {
        private const val WIDTH = 41
        private const val HEIGHT = 40

        private const val STROKE_FIRST_COLUMN = 16
        private const val STROKE_LAST_COLUMN = 23
        private const val ROW_ABOVE_THE_ARM = 10
        private const val ROW_INSIDE_THE_ARM = 27
        private const val ROW_BELOW_THE_ARM = 35
        private const val CENTER_COLUMN = 20

        private const val LINE = Color.RED
    }
}
