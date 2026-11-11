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

package com.oriondev.moneywallet.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Created by andrea on 08/03/18.
 */
open class MaterialProgressBar : View {

    private val mPaint = Paint()
    private var mReachedBarColor = Color.RED
    private var mUnreachedBarColor = Color.GRAY
    private var mMaxValue = 0L
    private var mProgressValue = 0L

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        mPaint.isAntiAlias = true
    }

    fun setReachedBarColor(color: Int) {
        mReachedBarColor = color
        invalidate()
    }

    fun setUnreachedBarColor(color: Int) {
        mUnreachedBarColor = color
        invalidate()
    }

    fun setMaxValue(maxValue: Long) {
        mMaxValue = maxValue
        invalidate()
    }

    fun setProgressValue(progressValue: Long) {
        mProgressValue = progressValue
        invalidate()
    }

    protected open fun getProgress(): Double {
        if (mMaxValue > 0 && mProgressValue > 0) {
            val progress = mProgressValue.toDouble() / mMaxValue
            return if (progress > 1) 1.0 else progress
        }
        return 0.0
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val measuredWidth = measuredWidth
        val measuredHeight = measuredHeight
        if (min(measuredWidth, measuredHeight) == 0) {
            return
        }
        mPaint.color = mUnreachedBarColor
        c.drawRect(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat(), mPaint)
        mPaint.color = mReachedBarColor
        val reachedBarWidth = (getProgress() * measuredWidth).toInt()
        c.drawRect(0f, 0f, reachedBarWidth.toFloat(), measuredHeight.toFloat(), mPaint)
    }
}
