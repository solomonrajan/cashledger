/*
 * Copyright (c) 2018.
 * Copyright (c) 2026 solomonrajan.
 *
 * Portions derived from datepicker-timeline, Copyright (c) 2015 Yann Badoual,
 * MIT License.
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

package com.oriondev.moneywallet.ui.view.calendar

import android.annotation.SuppressLint
import android.content.Context
import android.icu.text.DateFormatSymbols
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.oriondev.moneywallet.R
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

open class MonthView : RecyclerView {

    // per instance so the names follow an app language change, which rebuilds this view
    private val months: Array<String> = abbreviatedMonths()

    private var labelSize = 0f

    private var adapter: MonthAdapter? = null
    private var layoutManager: LinearLayoutManager? = null
    private var onMonthSelectedListener: OnMonthSelectedListener? = null

    private var defaultColor = 0
    private var colorSelected = 0
    private var colorBeforeSelection = 0

    private var startYear = 1970
    private var startMonth = 0
    private var yearDigitCount = 2

    private var selectedYear = 0
    private var selectedMonth = 0
    private var selectedPosition = -1
    private var monthCount = Int.MAX_VALUE

    constructor(context: Context) : super(context) {
        initStrip()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initStrip()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        initStrip()
    }

    private fun monthName(month: Int): String = months[month].uppercase(Locale.getDefault())

    /**
     * Every label this strip can render, each carrying a stand in for its year.
     *
     * The year differs per cell, so the stand in is the widest digit repeated rather than one
     * chosen year, which keeps the derived size from depending on which months and years
     * happened to be on screen when it was derived.
     */
    private fun widestLabels(paint: TextPaint): Array<String> {
        val year = widestYear(paint)
        return Array(months.size) { month -> monthName(month) + year }
    }

    private fun widestYear(paint: TextPaint): String {
        if (yearDigitCount <= 0) {
            return ""
        }
        // the digits as the locale writes them, which is not the Latin set in every language
        var widest = String.format(Locale.getDefault(), "%d", 0)
        var widestWidth = paint.measureText(widest)
        for (value in 1..9) {
            val rendered = String.format(Locale.getDefault(), "%d", value)
            val width = paint.measureText(rendered)
            if (width > widestWidth) {
                widestWidth = width
                widest = rendered
            }
        }
        return " " + widest.repeat(yearDigitCount)
    }

    /**
     * One text size for every month label, small enough that each of them measures within a
     * cell.
     *
     * Sized rather than cut, because cutting to a character count is the defect this change
     * removes, and sized rather than left to wrap: read off the running strip in Malayalam,
     * master renders month labels at two different heights in one row.
     */
    private fun labelSize(sample: TextView, cell: View): Float {
        if (labelSize > 0f) {
            return labelSize
        }
        val available = cell.layoutParams.width - cell.paddingLeft - cell.paddingRight
        labelSize = LabelFit.sizeToFit(sample.paint, widestLabels(sample.paint), available)
        return labelSize
    }

    private fun initStrip() {
        val calendar = Calendar.getInstance()
        setSelectedMonth(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), false)

        setHasFixedSize(true)
        layoutManager = LinearLayoutManager(context, HORIZONTAL, false)
        adapter = MonthAdapter()
        setLayoutManager(layoutManager)
        setAdapter(adapter)
    }

    @JvmOverloads
    fun setSelectedMonth(
        year: Int,
        month: Int,
        callListener: Boolean = true,
        centerOnPosition: Boolean = true
    ) {
        onMonthSelected(year, month, callListener, centerOnPosition)
    }

    fun getSelectedYear(): Int = selectedYear

    fun getSelectedMonth(): Int = selectedMonth

    private fun onMonthSelected(
        year: Int,
        month: Int,
        callListener: Boolean,
        centerOnPosition: Boolean
    ) {
        val oldPosition = selectedPosition
        selectedPosition = getPositionForDate(year, month)
        selectedYear = year
        selectedMonth = month

        if (selectedPosition == oldPosition) {
            if (centerOnPosition) {
                centerOnPosition(selectedPosition)
            }
            // the month tapped is the one already marked. That used to be a request for the month
            // the selected day was already in, and the listener was skipped; the mark now follows
            // a strip that scrolls, so this is somebody asking to go to the month they can see
            if (callListener) {
                onMonthSelectedListener?.onMonthSelected(year, month, selectedPosition)
            }
            return
        }

        val currentAdapter = adapter
        if (currentAdapter != null && layoutManager != null) {
            val rangeStart = min(oldPosition, selectedPosition)
            val rangeEnd = max(oldPosition, selectedPosition)
            currentAdapter.notifyItemRangeChanged(rangeStart, rangeEnd - rangeStart + 1)

            // Animate scroll
            if (centerOnPosition) {
                centerOnPosition(selectedPosition)
            }

            if (callListener) {
                onMonthSelectedListener?.onMonthSelected(year, month, selectedPosition)
            }
        } else if (centerOnPosition) {
            post { centerOnPosition(selectedPosition) }
        }
    }

    fun centerOnPosition(position: Int) {
        if (childCount == 0 || !isLaidOut) {
            return
        }
        // Animate scroll
        val offset = measuredWidth / 2 - getItemWidth() / 2
        layoutManager?.scrollToPositionWithOffset(position, offset)
    }

    fun centerOnDate(year: Int, month: Int) {
        centerOnPosition(getPositionForDate(year, month))
    }

    fun centerOnSelection() {
        centerOnPosition(selectedPosition)
    }

    internal fun scrollToYearPosition(year: Int, offsetYear: Int) {
        if (childCount == 0 || !isLaidOut) {
            return
        }
        // Animate scroll
        layoutManager?.scrollToPositionWithOffset(
            getPositionForDate(year + 1, 0),
            offsetYear + measuredWidth / 2 - getItemWidth() / 2
        )
    }

    internal fun getItemWidth(): Int = getChildAt(0).measuredWidth

    internal fun getYearWidth(): Int = getItemWidth() * 12

    fun getSelectedPosition(): Int = selectedPosition

    private fun getYearForPosition(position: Int): Int = (position + startMonth) / 12 + startYear

    private fun getMonthForPosition(position: Int): Int = (startMonth + position) % 12

    private fun getPositionForDate(year: Int, month: Int): Int = (12 * (year - startYear) + month) - startMonth

    fun setOnMonthSelectedListener(onMonthSelectedListener: OnMonthSelectedListener?) {
        this.onMonthSelectedListener = onMonthSelectedListener
    }

    fun getOnMonthSelectedListener(): OnMonthSelectedListener? = onMonthSelectedListener

    fun getMonthCount(): Int = monthCount

    /**
     * Default indicator and text color
     */
    fun setDefaultColor(defaultColor: Int) {
        this.defaultColor = defaultColor
    }

    /**
     * Color when month is selected
     */
    fun setColorSelected(colorSelected: Int) {
        this.colorSelected = colorSelected
    }

    /**
     * Color when month is before the current selected month
     */
    fun setColorBeforeSelection(colorBeforeSelection: Int) {
        this.colorBeforeSelection = colorBeforeSelection
    }

    fun getDefaultColor(): Int = defaultColor

    fun getColorBeforeSelection(): Int = colorBeforeSelection

    fun getColorSelected(): Int = colorSelected

    fun setYearDigitCount(yearDigitCount: Int) {
        require(yearDigitCount in 0..4) {
            "yearDigitCount cannot be smaller than 0 or greater than 4"
        }
        this.yearDigitCount = yearDigitCount
    }

    fun getYearDigitCount(): Int = yearDigitCount

    @SuppressLint("NotifyDataSetChanged")
    fun setFirstDate(startYear: Int, startMonth: Int) {
        this.startYear = startYear
        this.startMonth = startMonth
        selectedYear = startYear
        selectedMonth = startMonth
        selectedPosition = 0
        adapter?.notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    internal fun setMonthCount(monthCount: Int) {
        if (this.monthCount == monthCount) {
            return
        }
        this.monthCount = monthCount
        adapter?.notifyDataSetChanged()
    }

    fun setLastDate(endYear: Int, endMonth: Int) {
        require(endYear >= startYear && (endYear != startYear || endMonth >= startMonth)) {
            "Last visible date cannot be before first visible date"
        }

        val firstDate = Calendar.getInstance()
        firstDate.set(startYear, startMonth, 1)
        val lastDate = Calendar.getInstance()
        lastDate.set(endYear, endMonth, 1)
        val diffYear = lastDate.get(Calendar.YEAR) - firstDate.get(Calendar.YEAR)
        val diffMonth = diffYear * 12 + lastDate.get(Calendar.MONTH) - firstDate.get(Calendar.MONTH)

        setMonthCount(diffMonth + 1)
    }

    private inner class MonthAdapter : Adapter<MonthViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val view = inflater.inflate(R.layout.view_mti_item_month, parent, false)
            return MonthViewHolder(view)
        }

        override fun onBindViewHolder(holder: MonthViewHolder, position: Int) {
            val year = getYearForPosition(position)
            val month = getMonthForPosition(position)
            holder.bind(year, month, position == selectedPosition, position < selectedPosition)
        }

        override fun getItemCount(): Int = monthCount
    }

    private inner class MonthViewHolder(root: View) : ViewHolder(root) {

        private val lbl: TextView = root.findViewById(R.id.mti_month_lbl)
        private val indicator: DotView = root.findViewById(R.id.mti_view_indicator)

        private var year = 0
        private var month = 0

        init {
            lbl.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSize(lbl, root))
            root.setOnClickListener { onMonthSelected(year, month, true, true) }
        }

        fun bind(year: Int, month: Int, selected: Boolean, beforeSelection: Boolean) {
            this.year = year
            this.month = month

            var text = monthName(month)
            if (yearDigitCount > 0) {
                var yearMod = 1
                repeat(yearDigitCount) { yearMod *= 10 }
                // padded, because a year ending in 00 through 09 leaves a remainder of one
                // digit, and 2009 unpadded reads as the year 9
                text += " " + String.format(Locale.getDefault(), "%0" + yearDigitCount + "d", year % yearMod)
            }
            lbl.text = text
            val color = when {
                selected -> colorSelected
                beforeSelection -> colorBeforeSelection
                else -> defaultColor
            }
            lbl.setTextColor(color)
            indicator.setColor(color)
            indicator.setCircleSizeDp(if (selected) 12 else 5)
        }
    }

    interface OnMonthSelectedListener {
        fun onMonthSelected(year: Int, month: Int, index: Int)
    }

    interface DateLabelAdapter {
        fun getLabel(calendar: Calendar, index: Int): CharSequence
    }

    companion object {
        /**
         * The abbreviated month names, indexed by the [Calendar.JANUARY] to
         * [Calendar.DECEMBER] values.
         *
         * Standalone rather than the form a full date uses, because this strip names a month on its
         * own rather than inside a date. Catalan is the case that showed it here: its formatting
         * abbreviations are de gen., de febr. and so on, so cutting them to three characters left
         * five of the seven visible cells reading DE.
         */
        private fun abbreviatedMonths(): Array<String> = DateFormatSymbols(Locale.getDefault())
            .getMonths(DateFormatSymbols.STANDALONE, DateFormatSymbols.ABBREVIATED)
    }
}
