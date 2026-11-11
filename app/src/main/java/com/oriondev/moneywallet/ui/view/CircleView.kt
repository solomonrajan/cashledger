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

class CircleView : View {

    private val mPaint = Paint()
    private var mRadius = 0.9f

    constructor(context: Context) : super(context) {
        initPaint()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initPaint()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        initPaint()
    }

    private fun initPaint() {
        mPaint.color = Color.BLACK
        mPaint.isAntiAlias = true
    }

    fun setColor(color: Int) {
        mPaint.color = color
        invalidate()
    }

    fun setRadius(radius: Float) {
        mRadius = radius
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val width = measuredWidth
        val height = measuredHeight
        if (width == 0 || height == 0) return
        val radius = min(width, height) / 2
        c.drawCircle((width / 2).toFloat(), (height / 2).toFloat(), radius * mRadius, mPaint)
    }
}
