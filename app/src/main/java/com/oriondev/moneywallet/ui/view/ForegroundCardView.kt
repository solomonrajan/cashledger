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
import android.util.AttributeSet
import androidx.cardview.widget.CardView
import com.oriondev.moneywallet.R
import kotlin.math.min

/**
 * Created by andrea on 24/08/18.
 */
open class ForegroundCardView : CardView {

    private var mMaxWidth = -1
    private var mMaxHeight = -1

    constructor(context: Context) : super(context) {
        initialize(context, null, 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(context, attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        initialize(context, attrs, defStyleAttr)
    }

    private fun initialize(context: Context, attrs: AttributeSet?, defStyleAttr: Int) {
        val typedArray = context.obtainStyledAttributes(
            attrs,
            R.styleable.ForegroundCardView,
            defStyleAttr,
            0
        )
        try {
            mMaxWidth = typedArray.getDimensionPixelSize(R.styleable.ForegroundCardView_fcv_max_width, -1)
            mMaxHeight = typedArray.getDimensionPixelSize(R.styleable.ForegroundCardView_fcv_max_height, -1)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            typedArray.recycle()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var finalWidthMeasureSpec = widthMeasureSpec
        var finalHeightMeasureSpec = heightMeasureSpec
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        if (mMaxWidth > -1) {
            val width = min(widthSize, mMaxWidth)
            finalWidthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        }
        if (mMaxHeight > -1) {
            val height = min(heightSize, mMaxHeight)
            finalHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        }
        super.onMeasure(finalWidthMeasureSpec, finalHeightMeasureSpec)
    }
}
