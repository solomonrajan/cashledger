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

package com.oriondev.moneywallet.ui.fragment.multipanel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.regex.Pattern

/**
 * The month row above the calendar day strip names the month the strip is on. What holds that up
 * is spread over three files: the strip reports the month it scrolls through and the month it is
 * placed on, the fragment listens and moves the row, the row is left alone while the strip stays
 * inside the month it already marks, and a tap on the marked month reaches the row's listener.
 */
class CalendarMonthRowSourceTest {

    companion object {
        private val COMMENTS: Pattern = Pattern.compile("//.*?$|/[*].*?[*]/", Pattern.DOTALL or Pattern.MULTILINE)

        private const val STRIP_BASE =
            "src/main/java/com/oriondev/moneywallet/ui/view/calendar/TimelineView"

        private const val MONTH_ROW_BASE =
            "src/main/java/com/oriondev/moneywallet/ui/view/calendar/MonthView"

        private const val CALENDAR_BASE =
            "src/main/java/com/oriondev/moneywallet/ui/fragment/multipanel/CalendarMultiPanelFragment"

        private const val WHILE_IT_SCROLLS_JAVA =
            "addOnScrollListener(new OnScrollListener() { @Override public void onScrolled(" +
                "@NonNull RecyclerView recyclerView, int dx, int dy) { if (dx != 0) { " +
                "reportMonthAt(centerPosition()); } } });"
        private const val WHILE_IT_SCROLLS_KOTLIN =
            "addOnScrollListener(object : OnScrollListener() { override fun onScrolled(" +
                "recyclerView: RecyclerView, dx: Int, dy: Int) { if (dx != 0) { " +
                "reportMonthAt(centerPosition()) } } })"

        private const val THE_REPORT_JAVA =
            "private void reportMonthAt(int position) { if (onMonthScrolledListener == null " +
                "|| position == NO_POSITION) { return; } resetCalendar(); " +
                "calendar.add(Calendar.DAY_OF_YEAR, position); " +
                "onMonthScrolledListener.onMonthScrolled(calendar.get(Calendar.YEAR), " +
                "calendar.get(Calendar.MONTH)); }"
        private const val THE_REPORT_KOTLIN =
            "private fun reportMonthAt(position: Int) { val listener = onMonthScrolledListener " +
                "if (listener == null || position == NO_POSITION) { return } resetCalendar() " +
                "calendar.add(Calendar.DAY_OF_YEAR, position) " +
                "listener.onMonthScrolled(calendar.get(Calendar.YEAR), " +
                "calendar.get(Calendar.MONTH)) }"

        private const val WHICH_CELL_JAVA =
            "private int centerPosition() { View center = findChildViewUnder(getWidth() / 2f, " +
                "getHeight() / 2f); return center == null ? NO_POSITION : " +
                "getChildAdapterPosition(center); }"
        private const val WHICH_CELL_KOTLIN =
            "private fun centerPosition(): Int { val center = findChildViewUnder(width / 2f, " +
                "height / 2f) return if (center == null) NO_POSITION else " +
                "getChildAdapterPosition(center) }"

        private const val WHEN_IT_IS_PLACED_JAVA =
            "public void centerOnPosition(int position) { if (getChildCount() == 0 " +
                "|| !isLaidOut()) { return; } int offset = getMeasuredWidth() / 2 - " +
                "getChildAt(0).getMeasuredWidth() / 2; " +
                "layoutManager.scrollToPositionWithOffset(position, offset); " +
                "reportMonthAt(position); }"
        private const val WHEN_IT_IS_PLACED_KOTLIN =
            "fun centerOnPosition(position: Int) { if (childCount == 0 " +
                "|| !isLaidOut) { return } val offset = measuredWidth / 2 - " +
                "getChildAt(0).measuredWidth / 2 " +
                "layoutManager?.scrollToPositionWithOffset(position, offset) " +
                "reportMonthAt(position) }"

        private const val WIRING_JAVA =
            "mTimelineView.setSelectedDate(year, month, day); " +
                "mTimelineView.setOnDateSelectedListener(this); " +
                "mTimelineView.setOnMonthScrolledListener(this); " +
                "onDateSelected(year, month, day, mTimelineView.getSelectedPosition());"
        private const val WIRING_KOTLIN =
            "tv.setSelectedDate(year, month, day) " +
                "tv.setOnDateSelectedListener(this) " +
                "tv.setOnMonthScrolledListener(this) " +
                "onDateSelected(year, month, day, tv.getSelectedPosition())"

        private const val THE_STRIP_KEEPS_IT_JAVA =
            "public void setOnMonthScrolledListener(OnMonthScrolledListener onMonthScrolledListener) " +
                "{ this.onMonthScrolledListener = onMonthScrolledListener; }"
        private const val THE_STRIP_KEEPS_IT_KOTLIN =
            "fun setOnMonthScrolledListener(onMonthScrolledListener: OnMonthScrolledListener?) " +
                "{ this.onMonthScrolledListener = onMonthScrolledListener }"

        private const val THE_ROW_FOLLOWS_JAVA =
            "public void onMonthScrolled(int year, int month) { if (year == " +
                "mMonthView.getSelectedYear() && month == mMonthView.getSelectedMonth()) { return; } " +
                "mMonthView.setSelectedMonth(year, month, false, true); }"
        private const val THE_ROW_FOLLOWS_KOTLIN =
            "override fun onMonthScrolled(year: Int, month: Int) { val mv = monthView ?: return " +
                "if (year == mv.getSelectedYear() && month == mv.getSelectedMonth()) { return } " +
                "mv.setSelectedMonth(year, month, false, true) }"

        private const val ANY_MOVE_JAVA = "mMonthView.setSelectedMonth("
        private const val ANY_MOVE_KOTLIN = "setSelectedMonth("

        private const val MOVE_THE_ROW_JAVA =
            "mMonthView.setSelectedMonth(year, month, false, true);"
        private const val MOVE_THE_ROW_KOTLIN =
            "setSelectedMonth(year, month, false, true)"

        private const val TELL_ON_A_TAP_OF_THE_MARKED_MONTH_JAVA =
            "if (selectedPosition == oldPosition) { if (centerOnPosition) { " +
                "centerOnPosition(selectedPosition); } if (callListener " +
                "&& onMonthSelectedListener != null) { " +
                "onMonthSelectedListener.onMonthSelected(year, month, selectedPosition); } return; }"
        private const val TELL_ON_A_TAP_OF_THE_MARKED_MONTH_KOTLIN =
            "if (selectedPosition == oldPosition) { if (centerOnPosition) { " +
                "centerOnPosition(selectedPosition) } if (callListener) { " +
                "onMonthSelectedListener?.onMonthSelected(year, month, selectedPosition) } return }"

        private const val A_CELL_IS_TAPPED_JAVA =
            "root.setOnClickListener(new OnClickListener() { @Override public void onClick(" +
                "View view) { onMonthSelected(year, month, true, true); } });"
        private const val A_CELL_IS_TAPPED_KOTLIN =
            "root.setOnClickListener { onMonthSelected(year, month, true, true) }"

        private fun count(source: String, statement: String): Int {
            var found = 0
            var at = source.indexOf(statement)
            while (at >= 0) {
                found++
                at = source.indexOf(statement, at + 1)
            }
            return found
        }

        private fun findSourceFile(relativePath: String): File {
            var file = File(relativePath)
            if (!file.exists()) {
                file = File("app/$relativePath")
            }
            return file
        }

        private fun isKotlin(basePath: String): Boolean {
            return findSourceFile("$basePath.kt").exists()
        }
    }

    @Test
    fun theStripReportsTheMonthItScrollsThrough() {
        val kotlin = isKotlin(STRIP_BASE)
        val source = readSource(STRIP_BASE)
        assertTrue(
            "a strip that reports nothing while it scrolls leaves the month row naming the " +
                "month the screen opened on, and a report above the dx test names " +
                "January 1900 on the first layout of all",
            source.contains(if (kotlin) WHILE_IT_SCROLLS_KOTLIN else WHILE_IT_SCROLLS_JAVA)
        )
        assertTrue(
            "and the listener only names the call, so a middle cell that is never worked " +
                "out reports nothing on every scroll",
            source.contains(if (kotlin) WHICH_CELL_KOTLIN else WHICH_CELL_JAVA)
        )
    }

    @Test
    fun theStripWorksOutTheMonthAndSendsIt() {
        val kotlin = isKotlin(STRIP_BASE)
        assertTrue(
            "everything else here is plumbing that leads to this, and the strip can be " +
                "left working the month out and telling nobody",
            readSource(STRIP_BASE).contains(if (kotlin) THE_REPORT_KOTLIN else THE_REPORT_JAVA)
        )
    }

    @Test
    fun theStripReportsACellItIsPlacedOn() {
        val kotlin = isKotlin(STRIP_BASE)
        assertTrue(
            "a cell the strip is placed on is scrolled to by a layout, so it reaches the " +
                "listener only from here. Without it, tapping the day already selected " +
                "leaves the row on the month the last drag reached",
            readSource(STRIP_BASE).contains(if (kotlin) WHEN_IT_IS_PLACED_KOTLIN else WHEN_IT_IS_PLACED_JAVA)
        )
    }

    @Test
    fun theStripIsToldWhereToReportTo() {
        val calendarKotlin = isKotlin(CALENDAR_BASE)
        assertTrue(
            "without this line the strip reports to nobody and the row never follows",
            readSource(CALENDAR_BASE).contains(if (calendarKotlin) WIRING_KOTLIN else WIRING_JAVA)
        )
        val kotlin = isKotlin(STRIP_BASE)
        assertTrue(
            "and being told is no use if the strip does not keep it",
            readSource(STRIP_BASE).contains(if (kotlin) THE_STRIP_KEEPS_IT_KOTLIN else THE_STRIP_KEEPS_IT_JAVA)
        )
    }

    @Test
    fun theRowFollowsTheStripAndIsLeftAloneInsideOneMonth() {
        val calendarKotlin = isKotlin(CALENDAR_BASE)
        assertTrue(
            "the row has to move to the month reported, and has to be left alone while " +
                "the strip is inside the month it already marks, or it is re centered " +
                "on every scrolled pixel and slides under the finger",
            readSource(CALENDAR_BASE).contains(if (calendarKotlin) THE_ROW_FOLLOWS_KOTLIN else THE_ROW_FOLLOWS_JAVA)
        )
    }

    @Test
    fun nothingElseMovesTheMonthRow() {
        val calendarKotlin = isKotlin(CALENDAR_BASE)
        val source = readSource(CALENDAR_BASE)
        assertEquals(
            "the day tapped and the month scrolled onto are the only two things that move the row",
            2,
            count(source, if (calendarKotlin) ANY_MOVE_KOTLIN else ANY_MOVE_JAVA)
        )
        assertEquals(
            "both have to pass callListener false, and a two argument call passes it true.",
            2,
            count(source, if (calendarKotlin) MOVE_THE_ROW_KOTLIN else MOVE_THE_ROW_JAVA)
        )
    }

    @Test
    fun tappingTheMonthTheRowMarksReachesTheFragment() {
        val kotlin = isKotlin(MONTH_ROW_BASE)
        val source = readSource(MONTH_ROW_BASE)
        assertTrue(
            "the row returns early when the month tapped is the one it already marks",
            source.contains(if (kotlin) TELL_ON_A_TAP_OF_THE_MARKED_MONTH_KOTLIN else TELL_ON_A_TAP_OF_THE_MARKED_MONTH_JAVA)
        )
        assertTrue(
            "and the cell has to ask for the listener, which is the only caller that does",
            source.contains(if (kotlin) A_CELL_IS_TAPPED_KOTLIN else A_CELL_IS_TAPPED_JAVA)
        )
    }

    private fun readSource(basePath: String): String {
        var file = findSourceFile("$basePath.kt")
        if (!file.exists()) {
            file = findSourceFile("$basePath.java")
        }
        if (!file.exists()) {
            fail("Cannot find $basePath at ${file.absolutePath}")
        }
        try {
            val source = String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
            val collapsed = COMMENTS.matcher(source).replaceAll(" ").replace("\\s+".toRegex(), " ")
            return collapsed.replace("( ", "(").replace(" )", ")")
        } catch (e: IOException) {
            fail("Cannot read $basePath: ${e.message}")
            return ""
        }
    }
}
