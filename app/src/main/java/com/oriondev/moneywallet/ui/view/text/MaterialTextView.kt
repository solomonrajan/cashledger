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

package com.oriondev.moneywallet.ui.view.text

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatTextView
import com.oriondev.moneywallet.R
import kotlin.math.min

open class MaterialTextView : AppCompatTextView {

    private var mMode: Mode? = null
    private var mIconMargin = 0
    private var mIconColor = 0
    private var mLeftDrawable: Drawable? = null

    private var mHint: String? = null
    private val mFloatingLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var mBottomLineEnabled = false
    private val mBottomLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var mBottomLineSize = 0
    private val mMeter = Rect()

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
            R.styleable.MaterialTextView,
            defStyleAttr,
            0
        )
        try {
            onParseAttributes(typedArray)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            typedArray.recycle()
        }
        calculatePadding()
    }

    protected open fun onParseAttributes(typedArray: TypedArray) {
        mMode = Mode.getMode(
            typedArray.getInt(R.styleable.MaterialTextView_mtv_mode, Mode.STANDARD.mMode)
        )
        mIconMargin = typedArray.getDimensionPixelSize(
            R.styleable.MaterialTextView_mtv_leftIconMargin,
            getPixels(ICON_MARGIN_SIZE_DP)
        )
        mIconColor = typedArray.getColor(R.styleable.MaterialTextView_mtv_leftIconColor, Color.GRAY)
        val leftIcon = typedArray.getResourceId(R.styleable.MaterialTextView_mtv_leftIcon, -1)
        if (leftIcon > 0) {
            mLeftDrawable = Utils.getDrawableCompat(context, leftIcon)
            Utils.applyTinting(mLeftDrawable, mIconColor, PorterDuff.Mode.SRC_IN)
        }
        mHint = typedArray.getString(R.styleable.MaterialTextView_mtv_floatingLabelText)
            ?: hint?.toString()
        mFloatingLabelPaint.color = typedArray.getColor(
            R.styleable.MaterialTextView_mtv_floatingLabelTextColor,
            Color.GRAY
        )
        mFloatingLabelPaint.textSize = typedArray.getDimension(
            R.styleable.MaterialTextView_mtv_floatingLabelTextSize,
            context.resources.getDimension(R.dimen.material_component_floating_label_text_size)
        )
        Utils.applyTypeface(
            context,
            typedArray,
            R.styleable.MaterialTextView_mtv_floatingLabelTextTypeface,
            mFloatingLabelPaint
        )
        mBottomLineEnabled =
            typedArray.getBoolean(R.styleable.MaterialTextView_mtv_bottomLineEnabled, false)
        mBottomLinePaint.color = typedArray.getColor(
            R.styleable.MaterialTextView_mtv_bottomLineColor,
            Color.GRAY
        )
        mBottomLineSize = typedArray.getDimensionPixelSize(
            R.styleable.MaterialTextView_mtv_bottomLineSize,
            getPixels(1)
        )
    }

    private fun calculatePadding() {
        var paddingLeft = getPixels(SIDE_PADDING_DP)
        var paddingRight = getPixels(SIDE_PADDING_DP)
        if (mLeftDrawable != null) {
            if (isRtl()) {
                paddingRight += getPixels(ICON_CONTAINER_SIZE_DP) + mIconMargin
            } else {
                paddingLeft += getPixels(ICON_CONTAINER_SIZE_DP) + mIconMargin
            }
        }
        val paddingBottom = getPixels(BOTTOM_PADDING_DP)
        var paddingTop = getPixels(16)
        val label = mHint
        if (mMode == Mode.FLOATING_LABEL && label != null) {
            mFloatingLabelPaint.getTextBounds(label, 0, label.length, mMeter)
            paddingTop += mMeter.height()
        }
        setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (min(measuredWidth, measuredHeight) == 0) {
            return
        }
        onDrawLeftIcon(canvas)
        onDrawFloatingLabel(canvas)
        onDrawBottomLine(canvas)
    }

    protected open fun onDrawLeftIcon(canvas: Canvas) {
        val drawable = mLeftDrawable ?: return
        val rectTop = paddingTop - getPixels(12)
        val rectLeft = if (isRtl()) {
            measuredWidth - paddingRight + mIconMargin
        } else {
            getPixels(SIDE_PADDING_DP)
        }
        val pad = getPixels(12)
        val iconTop = rectTop + pad
        val iconLeft = rectLeft + pad + scrollX
        val size = getPixels(24)
        drawable.setBounds(iconLeft, iconTop, iconLeft + size, iconTop + size)
        drawable.draw(canvas)
    }

    protected open fun onDrawFloatingLabel(canvas: Canvas) {
        val label = mHint
        if (mMode == Mode.FLOATING_LABEL && label != null) {
            mFloatingLabelPaint.getTextBounds(label, 0, label.length, mMeter)
            val startY = paddingTop + mMeter.height()
            val endY = paddingTop - getPixels(8)
            val startX = scrollX + if (isRtl()) {
                measuredWidth - paddingRight - mMeter.width()
            } else {
                paddingLeft
            }
            if (endY < startY) {
                canvas.drawText(label, startX.toFloat(), endY.toFloat(), mFloatingLabelPaint)
            }
        }
    }

    protected open fun onDrawBottomLine(canvas: Canvas) {
        if (mBottomLineEnabled) {
            val top =
                measuredHeight - paddingBottom + getPixels(BOTTOM_LINE_PADDING_DP) - mBottomLineSize
            val left = paddingLeft + scrollX
            val right = scrollX + measuredWidth - paddingRight
            canvas.drawRect(
                left.toFloat(),
                top.toFloat(),
                right.toFloat(),
                (top + mBottomLineSize).toFloat(),
                mBottomLinePaint
            )
        }
    }

    private fun getPixels(dp: Int): Int {
        return Utils.getPixels(dp, resources).toInt()
    }

    private fun isRtl(): Boolean {
        return Utils.isRtl(resources)
    }

    fun setMode(mode: Mode?) {
        mMode = mode
        calculatePadding()
        invalidate()
    }

    fun getMode(): Mode? = mMode

    fun setFloatingLabelColor(@ColorInt color: Int) {
        mFloatingLabelPaint.color = color
        invalidate()
    }

    fun setBottomLineColor(@ColorInt color: Int) {
        mBottomLinePaint.color = color
        invalidate()
    }

    fun setLeftIconColor(@ColorInt iconColor: Int) {
        mIconColor = iconColor
        Utils.applyTinting(mLeftDrawable, mIconColor, PorterDuff.Mode.SRC_IN)
        invalidate()
    }

    fun setLeftDrawable(@DrawableRes drawable: Int) {
        setLeftDrawable(Utils.getDrawableCompat(context, drawable))
    }

    fun setLeftDrawable(drawable: Drawable?) {
        mLeftDrawable = drawable
        Utils.applyTinting(mLeftDrawable, mIconColor, PorterDuff.Mode.SRC_IN)
        calculatePadding()
        invalidate()
    }

    companion object {
        private const val ICON_CONTAINER_SIZE_DP = 48
        private const val ICON_MARGIN_SIZE_DP = 8
        private const val BOTTOM_PADDING_DP = 8
        private const val BOTTOM_LINE_PADDING_DP = 8
        private const val SIDE_PADDING_DP = 8
    }
}
