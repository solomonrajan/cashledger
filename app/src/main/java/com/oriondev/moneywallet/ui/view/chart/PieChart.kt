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

package com.oriondev.moneywallet.ui.view.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.annotation.VisibleForTesting
import com.oriondev.moneywallet.R
import java.text.NumberFormat
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Created by andrea on 13/08/18.
 */
open class PieChart : View {

    private val mColors = intArrayOf(
        Color.rgb(204, 198, 24),
        Color.rgb(229, 163, 25),
        Color.rgb(232, 111, 40),
        Color.rgb(212, 75, 145),
        Color.rgb(117, 96, 165),
        Color.rgb(54, 142, 92),
        Color.rgb(129, 191, 22),
        Color.rgb(224, 184, 26),
        Color.rgb(229, 138, 24),
        Color.rgb(235, 89, 92),
        Color.rgb(167, 78, 160),
        Color.rgb(66, 117, 138),
        Color.rgb(85, 169, 48)
    )

    private var mHoleEnabled = DEFAULT_HOLE_ENABLED
    private var mShadowEnabled = DEFAULT_SHADOW_ENABLED
    private var mPercentageEnabled = DEFAULT_PERCENTAGE_ENABLED

    private val mRectangle = RectF()
    private val mSlicePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mTextBounds = Rect()

    @Volatile
    private var mIsRunning = false
    private var mPieData: PieData? = null

    private var mShadowColor = DEFAULT_SHADOW_COLOR
    private var mHoleColor = DEFAULT_HOLE_COLOR
    private var mLineColor = DEFAULT_LINE_COLOR

    private val mPercentFormat: NumberFormat = NumberFormat.getPercentInstance()

    constructor(context: Context) : super(context) {
        initialize(context, null, 0, 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(context, attrs, 0, 0)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        initialize(context, attrs, defStyleAttr, 0)
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        initialize(context, attrs, defStyleAttr, defStyleRes)
    }

    private fun initialize(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) {
        mLinePaint.textAlign = Paint.Align.CENTER
        val a = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.PieChart,
            defStyleAttr,
            defStyleRes
        )
        try {
            mHoleEnabled = a.getBoolean(R.styleable.PieChart_pie_chartHoleEnabled, DEFAULT_HOLE_ENABLED)
            mShadowEnabled = a.getBoolean(R.styleable.PieChart_pie_chartShadowEnabled, DEFAULT_SHADOW_ENABLED)
            mPercentageEnabled = a.getBoolean(
                R.styleable.PieChart_pie_chartPercentageEnabled,
                DEFAULT_PERCENTAGE_ENABLED
            )
            mShadowColor = a.getColor(R.styleable.PieChart_pie_chartShadowColor, DEFAULT_SHADOW_COLOR)
            mHoleColor = a.getColor(R.styleable.PieChart_pie_chartHoleColor, DEFAULT_HOLE_COLOR)
            mLineColor = a.getColor(R.styleable.PieChart_pie_chartLineColor, DEFAULT_LINE_COLOR)
            mLinePaint.color = mLineColor
            mLinePaint.strokeWidth = a.getFloat(R.styleable.PieChart_pie_chartLineSize, DEFAULT_LINE_SIZE)
        } catch (_: Exception) {
            mHoleEnabled = DEFAULT_HOLE_ENABLED
            mShadowEnabled = DEFAULT_SHADOW_ENABLED
            mPercentageEnabled = DEFAULT_PERCENTAGE_ENABLED
            mShadowColor = DEFAULT_SHADOW_COLOR
            mHoleColor = DEFAULT_HOLE_COLOR
            mLineColor = DEFAULT_LINE_COLOR
            mLinePaint.strokeWidth = DEFAULT_LINE_SIZE
        } finally {
            a.recycle()
        }
    }

    /**
     * The label drawn on a wedge, in the digits and the percent sign of the language the app
     * is in. Public for Robolectric test verification.
     */
    @VisibleForTesting
    fun percentLabel(sweepAngle: Float): String =
        mPercentFormat.format(java.lang.Math.round(sweepAngle * 100 / 360f) / 100.0)

    fun setHoleEnabled(enabled: Boolean) {
        mHoleEnabled = enabled
        if (!mHoleEnabled) {
            mShadowEnabled = false
        }
        invalidate()
    }

    fun setLineColor(color: Int) {
        mLineColor = color
    }

    fun setHoleColor(color: Int) {
        mHoleColor = color
    }

    fun setShadowColor(color: Int) {
        mShadowColor = color
    }

    fun setChartData(pieData: PieData?) {
        mPieData = pieData
        val data = pieData
        if (data == null) {
            mIsRunning = false
            return
        }
        mIsRunning = true
        Thread {
            val maxSlices = (360f / MIN_DISTANCE).toInt()
            if (data.size() > maxSlices) {
                val slicesToRemove = data.size() - maxSlices + 1
                var extraSliceValue = 0L
                repeat(slicesToRemove) {
                    var smallSliceIndex = -1
                    var smallSliceValue = 0L
                    for (n in 0 until data.size()) {
                        val sliceValue = data[n].value
                        if (smallSliceIndex == -1 || smallSliceValue > sliceValue) {
                            smallSliceIndex = n
                            smallSliceValue = sliceValue
                        }
                    }
                    extraSliceValue += smallSliceValue
                    data.remove(smallSliceIndex)
                }
                data.add(PieSlice("others", extraSliceValue, null, true))
            }
            var totalValue = 0f
            for (slice in data.getAllSlices()) {
                totalValue += slice.value
            }
            var startAngle = 0f
            for (i in 0 until data.size()) {
                val slice = data[i]
                val sweepAngle = 360f * slice.value / totalValue
                slice.startAngle = startAngle
                slice.iconAngle = startAngle + (sweepAngle / 2)
                slice.sweepAngle = sweepAngle
                startAngle += sweepAngle
            }
            var icons = 1
            var backAngle = 0f
            var i = 1
            while ((icons > 1 || iconOverlaps(data)) && i < MAX_ITERATIONS) {
                val count = data.size()
                val previousAngle = data[(i - 1) % count].iconAngle + ((i - 1) / count * 360f)
                val currentAngle = data[i % count].iconAngle + (i / count * 360f)
                val difference = currentAngle - previousAngle + backAngle
                if (difference < MIN_DISTANCE) {
                    val moveDegrees = MIN_DISTANCE - difference
                    data[i % count].increaseIconAngle(moveDegrees)
                    icons += 1
                    backAngle += moveDegrees
                } else if (icons > 1) {
                    val moveAngle = backAngle / icons
                    for (j in (i - 1) downTo (i - icons)) {
                        data[j % count].decreaseIconAngle(moveAngle)
                    }
                    icons = 1
                    backAngle = 0f
                }
                i++
            }
            mIsRunning = false
            postInvalidate()
        }.start()
    }

    private fun iconOverlaps(data: PieData): Boolean {
        val count = data.size()
        for (i in 1..count) {
            val previousAngle = data[(i - 1) % count].iconAngle + ((i - 1) / count * 360f)
            val currentAngle = data[i % count].iconAngle + (i / count * 360f)
            if ((currentAngle - previousAngle) < MIN_DISTANCE) {
                return true
            }
        }
        return false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val bestHeight = (width * 0.75).toInt()
        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> min(bestHeight, heightSize)
            else -> bestHeight
        }
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        setMeasuredDimension(width, height)
    }

    override fun onDraw(c: Canvas) {
        val measuredWidth = measuredWidth
        val measuredHeight = measuredHeight
        if (measuredWidth == 0 || measuredHeight == 0) return
        val data = mPieData
        if (data == null || data.size() == 0 || mIsRunning || mColors.isEmpty()) return

        val centerX = (measuredWidth / 2).toFloat()
        val centerY = (measuredHeight / 2).toFloat()
        val maxRadius = min(centerX, centerY)
        val chartRadius = maxRadius * 0.5f
        val iconDistance = maxRadius * 0.8f
        val iconRadius = maxRadius * 0.1f
        val textDistance = maxRadius * 0.95f
        mLinePaint.color = mLineColor
        mLinePaint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            PERCENTAGE_TEXT_SIZE_SP,
            resources.displayMetrics
        )
        mRectangle.set(
            centerX - chartRadius,
            centerY - chartRadius,
            centerX + chartRadius,
            centerY + chartRadius
        )
        for (i in 0 until data.size()) {
            val pieSlice = data[i]
            val startAngle = pieSlice.startAngle
            val sweepAngle = pieSlice.sweepAngle
            mSlicePaint.color = mColors[i % mColors.size]
            c.drawArc(mRectangle, startAngle, sweepAngle, true, mSlicePaint)

            val startingAngle = startAngle + (sweepAngle / 2)
            val startX = (cos(Math.toRadians(startingAngle.toDouble())) * chartRadius).toFloat() + centerX
            val startY = (sin(Math.toRadians(startingAngle.toDouble())) * chartRadius).toFloat() + centerY

            val iconAngle = pieSlice.iconAngle
            val iconX = (cos(Math.toRadians(iconAngle.toDouble())) * iconDistance).toFloat() + centerX
            val iconY = (sin(Math.toRadians(iconAngle.toDouble())) * iconDistance).toFloat() + centerY
            c.drawLine(startX, startY, iconX, iconY, mLinePaint)

            val drawable = pieSlice.icon
            if (drawable != null) {
                drawable.setBounds(
                    (iconX - iconRadius).toInt(),
                    (iconY - iconRadius).toInt(),
                    (iconX + iconRadius).toInt(),
                    (iconY + iconRadius).toInt()
                )
                drawable.draw(c)
            } else {
                c.drawCircle(iconX, iconY, iconRadius, mSlicePaint)
            }

            if (mPercentageEnabled) {
                val label = percentLabel(sweepAngle)
                mLinePaint.getTextBounds(label, 0, label.length, mTextBounds)
                val halfAdvance = mLinePaint.measureText(label) / 2
                var textX = (cos(Math.toRadians(iconAngle.toDouble())) * textDistance).toFloat() + centerX
                var textY = (sin(Math.toRadians(iconAngle.toDouble())) * textDistance).toFloat() +
                    centerY - ((mTextBounds.top + mTextBounds.bottom) / 2f)
                textX = max(min(textX, measuredWidth - halfAdvance), halfAdvance)
                textY = max(min(textY, (measuredHeight - mTextBounds.bottom).toFloat()), -mTextBounds.top.toFloat())
                c.drawText(label, textX, textY, mLinePaint)
            }
        }
        if (mHoleEnabled) {
            if (mShadowEnabled) {
                mSlicePaint.color = mShadowColor
                c.drawCircle(centerX, centerY, chartRadius * 0.70f, mSlicePaint)
            }
            mSlicePaint.color = mHoleColor
            c.drawCircle(centerX, centerY, chartRadius * 0.50f, mSlicePaint)
        }
    }

    companion object {
        private const val MIN_DISTANCE = 15f
        private const val MAX_ITERATIONS = 10000
        private const val DEFAULT_HOLE_ENABLED = true
        private const val DEFAULT_SHADOW_ENABLED = true
        private const val DEFAULT_PERCENTAGE_ENABLED = false
        private val DEFAULT_SHADOW_COLOR = Color.parseColor("#40000000")
        private const val DEFAULT_HOLE_COLOR = Color.WHITE
        private const val DEFAULT_LINE_COLOR = Color.GRAY
        private const val DEFAULT_LINE_SIZE = 3f
        private const val PERCENTAGE_TEXT_SIZE_SP = 12f
    }
}
