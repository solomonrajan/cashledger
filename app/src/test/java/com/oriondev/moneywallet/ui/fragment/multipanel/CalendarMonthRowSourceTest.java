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

package com.oriondev.moneywallet.ui.fragment.multipanel;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The month row above the calendar day strip names the month the strip is on. What holds that up
 * is spread over three files: the strip reports the month it scrolls through and the month it is
 * placed on, the fragment listens and moves the row, the row is left alone while the strip stays
 * inside the month it already marks, and a tap on the marked month reaches the row's listener.
 *
 * None of the three views can be built here, so this reads the source, and what that buys is
 * narrow. It pins spellings. An edit that keeps the behavior and changes a pinned spelling fails
 * this with nothing wrong, and an edit that changes the behavior without touching a pinned
 * spelling passes it. Whole method bodies are pinned instead of single statements, which catches
 * a line added above or deleted below and drags pre existing lines into the pins as its cost. The
 * screen itself is what proves any of this, and a failure here means read the file and decide.
 *
 * Comments are stripped before anything is matched, which cuts both ways: a comment cannot stand
 * in for a statement, and a comment can hide one. Whitespace is collapsed and the space a line
 * break leaves against a bracket is dropped, so a call may be broken there, and not at a dot or
 * in front of a comma.
 */
public class CalendarMonthRowSourceTest {

    private static final Pattern COMMENTS = Pattern.compile("//.*?$|/[*].*?[*]/", Pattern.DOTALL | Pattern.MULTILINE);

    private static final String STRIP_BASE =
            "src/main/java/com/oriondev/moneywallet/ui/view/calendar/TimelineView";

    private static final String MONTH_ROW_BASE =
            "src/main/java/com/oriondev/moneywallet/ui/view/calendar/MonthView";

    private static final String CALENDAR_BASE =
            "src/main/java/com/oriondev/moneywallet/ui/fragment/multipanel/CalendarMultiPanelFragment";

    /**
     * The whole listener, because a report added above the dx test is what puts the row on
     * January 1900 on every open, and pinning the two statements apart would not see it.
     */
    private static final String WHILE_IT_SCROLLS_JAVA =
            "addOnScrollListener(new OnScrollListener() { @Override public void onScrolled("
            + "@NonNull RecyclerView recyclerView, int dx, int dy) { if (dx != 0) { "
            + "reportMonthAt(centerPosition()); } } });";
    private static final String WHILE_IT_SCROLLS_KOTLIN =
            "addOnScrollListener(object : OnScrollListener() { override fun onScrolled("
            + "recyclerView: RecyclerView, dx: Int, dy: Int) { if (dx != 0) { "
            + "reportMonthAt(centerPosition()) } } })";

    /** The whole method, because emptying any line of it kills the report on its own. */
    private static final String THE_REPORT_JAVA =
            "private void reportMonthAt(int position) { if (onMonthScrolledListener == null "
            + "|| position == NO_POSITION) { return; } resetCalendar(); "
            + "calendar.add(Calendar.DAY_OF_YEAR, position); "
            + "onMonthScrolledListener.onMonthScrolled(calendar.get(Calendar.YEAR), "
            + "calendar.get(Calendar.MONTH)); }";
    private static final String THE_REPORT_KOTLIN =
            "private fun reportMonthAt(position: Int) { val listener = onMonthScrolledListener "
            + "if (listener == null || position == NO_POSITION) { return } resetCalendar() "
            + "calendar.add(Calendar.DAY_OF_YEAR, position) "
            + "listener.onMonthScrolled(calendar.get(Calendar.YEAR), "
            + "calendar.get(Calendar.MONTH)) }";

    /** Which cell is in the middle, since the listener above only names the call. */
    private static final String WHICH_CELL_JAVA =
            "private int centerPosition() { View center = findChildViewUnder(getWidth() / 2f, "
            + "getHeight() / 2f); return center == null ? NO_POSITION : "
            + "getChildAdapterPosition(center); }";
    private static final String WHICH_CELL_KOTLIN =
            "private fun centerPosition(): Int { val center = findChildViewUnder(width / 2f, "
            + "height / 2f) return if (center == null) NO_POSITION else "
            + "getChildAdapterPosition(center) }";

    /** A cell the strip is placed on, which no scroll reports because nothing scrolled. */
    private static final String WHEN_IT_IS_PLACED_JAVA =
            "public void centerOnPosition(int position) { if (getChildCount() == 0 "
            + "|| !isLaidOut()) { return; } int offset = getMeasuredWidth() / 2 - "
            + "getChildAt(0).getMeasuredWidth() / 2; "
            + "layoutManager.scrollToPositionWithOffset(position, offset); "
            + "reportMonthAt(position); }";
    private static final String WHEN_IT_IS_PLACED_KOTLIN =
            "fun centerOnPosition(position: Int) { if (childCount == 0 "
            + "|| !isLaidOut) { return } val offset = measuredWidth / 2 - "
            + "getChildAt(0).measuredWidth / 2 "
            + "layoutManager?.scrollToPositionWithOffset(position, offset) "
            + "reportMonthAt(position) }";

    /**
     * What tells the strip where to report, with the two lines around it, because the statement
     * on its own reads the same sitting in a method nothing calls.
     */
    private static final String WIRING =
            "mTimelineView.setSelectedDate(year, month, day); "
            + "mTimelineView.setOnDateSelectedListener(this); "
            + "mTimelineView.setOnMonthScrolledListener(this); "
            + "onDateSelected(year, month, day, mTimelineView.getSelectedPosition());";

    /** And what the strip does with it. */
    private static final String THE_STRIP_KEEPS_IT_JAVA =
            "public void setOnMonthScrolledListener(OnMonthScrolledListener onMonthScrolledListener) "
            + "{ this.onMonthScrolledListener = onMonthScrolledListener; }";
    private static final String THE_STRIP_KEEPS_IT_KOTLIN =
            "fun setOnMonthScrolledListener(onMonthScrolledListener: OnMonthScrolledListener?) "
            + "{ this.onMonthScrolledListener = onMonthScrolledListener }";

    /**
     * The whole method, because the guard's condition without its return re centers the row on
     * every scrolled pixel, which is the slide under the finger the guard is there to stop.
     */
    private static final String THE_ROW_FOLLOWS =
            "public void onMonthScrolled(int year, int month) { if (year == "
            + "mMonthView.getSelectedYear() && month == mMonthView.getSelectedMonth()) { return; } "
            + "mMonthView.setSelectedMonth(year, month, false, true); }";

    /** Every call that moves the row, whatever arguments it carries. */
    private static final String ANY_MOVE = "mMonthView.setSelectedMonth(";

    /** Moving the row with the listening form turned off. */
    private static final String MOVE_THE_ROW =
            "mMonthView.setSelectedMonth(year, month, false, true);";

    /** The month tapped is the month already marked, and the fragment is told anyway. */
    private static final String TELL_ON_A_TAP_OF_THE_MARKED_MONTH_JAVA =
            "if (selectedPosition == oldPosition) { if (centerOnPosition) { "
            + "centerOnPosition(selectedPosition); } if (callListener "
            + "&& onMonthSelectedListener != null) { "
            + "onMonthSelectedListener.onMonthSelected(year, month, selectedPosition); } return; }";
    private static final String TELL_ON_A_TAP_OF_THE_MARKED_MONTH_KOTLIN =
            "if (selectedPosition == oldPosition) { if (centerOnPosition) { "
            + "centerOnPosition(selectedPosition) } if (callListener) { "
            + "onMonthSelectedListener?.onMonthSelected(year, month, selectedPosition) } return }";

    /** The only caller that asks for the listener, so the only way a tap reaches the fragment. */
    private static final String A_CELL_IS_TAPPED_JAVA =
            "root.setOnClickListener(new OnClickListener() { @Override public void onClick("
            + "View view) { onMonthSelected(year, month, true, true); } });";
    private static final String A_CELL_IS_TAPPED_KOTLIN =
            "root.setOnClickListener { onMonthSelected(year, month, true, true) }";

    @Test
    public void theStripReportsTheMonthItScrollsThrough() {
        boolean kotlin = isKotlin(STRIP_BASE);
        String source = readSource(STRIP_BASE);
        assertTrue("a strip that reports nothing while it scrolls leaves the month row naming the "
                        + "month the screen opened on, and a report above the dx test names "
                        + "January 1900 on the first layout of all",
                source.contains(kotlin ? WHILE_IT_SCROLLS_KOTLIN : WHILE_IT_SCROLLS_JAVA));
        assertTrue("and the listener only names the call, so a middle cell that is never worked "
                        + "out reports nothing on every scroll",
                source.contains(kotlin ? WHICH_CELL_KOTLIN : WHICH_CELL_JAVA));
    }

    @Test
    public void theStripWorksOutTheMonthAndSendsIt() {
        boolean kotlin = isKotlin(STRIP_BASE);
        assertTrue("everything else here is plumbing that leads to this, and the strip can be "
                        + "left working the month out and telling nobody",
                readSource(STRIP_BASE).contains(kotlin ? THE_REPORT_KOTLIN : THE_REPORT_JAVA));
    }

    @Test
    public void theStripReportsACellItIsPlacedOn() {
        boolean kotlin = isKotlin(STRIP_BASE);
        assertTrue("a cell the strip is placed on is scrolled to by a layout, so it reaches the "
                        + "listener only from here. Without it, tapping the day already selected "
                        + "leaves the row on the month the last drag reached",
                readSource(STRIP_BASE).contains(kotlin ? WHEN_IT_IS_PLACED_KOTLIN : WHEN_IT_IS_PLACED_JAVA));
    }

    @Test
    public void theStripIsToldWhereToReportTo() {
        assertTrue("without this line the strip reports to nobody and the row never follows",
                readSource(CALENDAR_BASE).contains(WIRING));
        boolean kotlin = isKotlin(STRIP_BASE);
        assertTrue("and being told is no use if the strip does not keep it",
                readSource(STRIP_BASE).contains(kotlin ? THE_STRIP_KEEPS_IT_KOTLIN : THE_STRIP_KEEPS_IT_JAVA));
    }

    @Test
    public void theRowFollowsTheStripAndIsLeftAloneInsideOneMonth() {
        assertTrue("the row has to move to the month reported, and has to be left alone while "
                        + "the strip is inside the month it already marks, or it is re centered "
                        + "on every scrolled pixel and slides under the finger",
                readSource(CALENDAR_BASE).contains(THE_ROW_FOLLOWS));
    }

    @Test
    public void nothingElseMovesTheMonthRow() {
        String source = readSource(CALENDAR_BASE);
        assertEquals("the day tapped and the month scrolled onto are the only two things that "
                        + "move the row", 2, count(source, ANY_MOVE));
        assertEquals("both have to pass callListener false, and a two argument call passes it "
                        + "true. The listening form calls onMonthSelected, which sends the strip "
                        + "to the first of the month and takes it out from under the drag",
                2, count(source, MOVE_THE_ROW));
    }

    @Test
    public void tappingTheMonthTheRowMarksReachesTheFragment() {
        boolean kotlin = isKotlin(MONTH_ROW_BASE);
        String source = readSource(MONTH_ROW_BASE);
        assertTrue("the row returns early when the month tapped is the one it already marks, and "
                        + "that mark now follows a strip that scrolls, so the cell a person has "
                        + "the most reason to tap would be the only one in the row that neither "
                        + "moves the strip nor loads a day",
                source.contains(kotlin ? TELL_ON_A_TAP_OF_THE_MARKED_MONTH_KOTLIN : TELL_ON_A_TAP_OF_THE_MARKED_MONTH_JAVA));
        assertTrue("and the cell has to ask for the listener, which is the only caller that does",
                source.contains(kotlin ? A_CELL_IS_TAPPED_KOTLIN : A_CELL_IS_TAPPED_JAVA));
    }

    private static int count(String source, String statement) {
        int found = 0;
        for (int at = source.indexOf(statement); at >= 0; at = source.indexOf(statement, at + 1)) {
            found++;
        }
        return found;
    }

    private static File findSourceFile(String relativePath) {
        File file = new File(relativePath);
        if (!file.exists()) {
            // a runner rooted at the repo root instead of the module, which the precedent
            // handles the same way
            file = new File("app/" + relativePath);
        }
        return file;
    }

    private static boolean isKotlin(String basePath) {
        return findSourceFile(basePath + ".kt").exists();
    }

    private String readSource(String basePath) {
        File file = findSourceFile(basePath + ".kt");
        if (!file.exists()) {
            file = findSourceFile(basePath + ".java");
        }
        if (!file.exists()) {
            fail("Cannot find " + basePath + " at " + file.getAbsolutePath());
        }
        try {
            String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            String collapsed = COMMENTS.matcher(source).replaceAll(" ").replaceAll("\\s+", " ");
            // a line broken after an open bracket leaves a space the pinned spelling has not got
            return collapsed.replace("( ", "(").replace(" )", ")");
        } catch (IOException e) {
            fail("Cannot read " + basePath + ": " + e.getMessage());
            return null;
        }
    }

}
