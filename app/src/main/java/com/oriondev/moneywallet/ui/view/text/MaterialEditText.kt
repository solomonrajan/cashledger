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

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.ViewCompat
import com.oriondev.moneywallet.R
import kotlin.math.min

open class MaterialEditText :
    AppCompatEditText,
    ValueAnimator.AnimatorUpdateListener,
    View.OnTouchListener {

    private var mMode: Mode? = null
    private var mIconMargin = 0
    private var mIconColorNormal = 0
    private var mIconColorFocused = 0
    private var mLeftDrawable: Array<Drawable>? = null

    private var mHint: String? = null
    private var mFloatingLabelColorNormal = 0
    private var mFloatingLabelColorFocused = 0
    private val mFloatingLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var mAnimator: ValueAnimator? = null
    private var mWasEmpty = true

    private var mBottomLineColorNormal = 0
    private var mBottomLineColorFocused = 0
    private var mBottomLineColorError = 0
    private var mDefaultBottomLineSize = 0
    private var mFocusedBottomLineSize = 0
    private val mBottomLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var mError = false
    private var mErrorMessage: String? = null
    private val mErrorTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var mShowCancelButton = false
    private var mCancelButton: Drawable? = null
    private var mCancelButtonListener: CancelButtonListener? = null

    private val mValidators = ArrayList<Validator>()
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

    @SuppressLint("ClickableViewAccessibility")
    private fun initialize(context: Context, attrs: AttributeSet?, defStyleAttr: Int) {
        background = null
        val typedArray = context.obtainStyledAttributes(
            attrs,
            R.styleable.MaterialEditText,
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
        setupValidators()
        calculatePadding()
        setOnTouchListener(this)
    }

    protected open fun onParseAttributes(typedArray: TypedArray) {
        mMode = Mode.getMode(
            typedArray.getInt(R.styleable.MaterialEditText_met_mode, Mode.STANDARD.mMode)
        )
        mIconMargin = typedArray.getDimensionPixelSize(
            R.styleable.MaterialEditText_met_leftIconMargin,
            getPixels(ICON_MARGIN_SIZE_DP)
        )
        mIconColorNormal = typedArray.getColor(
            R.styleable.MaterialEditText_met_leftIconColorNormal,
            Color.GRAY
        )
        mIconColorFocused = typedArray.getColor(
            R.styleable.MaterialEditText_met_leftIconColorFocused,
            Color.BLACK
        )
        val leftIcon = typedArray.getResourceId(R.styleable.MaterialEditText_met_leftIcon, -1)
        if (leftIcon > 0) {
            val drawable = Utils.getDrawableCompat(context, leftIcon)
            mLeftDrawable = generateDrawables(drawable)
            applyTintingToLeftDrawables(PorterDuff.Mode.SRC_ATOP)
        }
        mHint = typedArray.getString(R.styleable.MaterialEditText_met_floatingLabelText)
            ?: hint?.toString()
        mFloatingLabelColorNormal = typedArray.getColor(
            R.styleable.MaterialEditText_met_floatingLabelTextColorNormal,
            Color.GRAY
        )
        mFloatingLabelColorFocused = typedArray.getColor(
            R.styleable.MaterialEditText_met_floatingLabelTextColorFocused,
            Color.BLACK
        )
        mFloatingLabelPaint.textSize = typedArray.getDimension(
            R.styleable.MaterialEditText_met_floatingLabelTextSize,
            context.resources.getDimension(R.dimen.material_component_floating_label_text_size)
        )
        Utils.applyTypeface(
            context,
            typedArray,
            R.styleable.MaterialEditText_met_floatingLabelTextTypeface,
            mFloatingLabelPaint
        )
        mBottomLineColorNormal = typedArray.getColor(
            R.styleable.MaterialEditText_met_bottomLineColorNormal,
            Color.GRAY
        )
        mBottomLineColorFocused = typedArray.getColor(
            R.styleable.MaterialEditText_met_bottomLineColorFocused,
            Color.BLACK
        )
        mBottomLineColorError = typedArray.getColor(
            R.styleable.MaterialEditText_met_bottomLineColorError,
            Color.RED
        )
        mDefaultBottomLineSize = typedArray.getDimensionPixelSize(
            R.styleable.MaterialEditText_met_bottomLineColorUnfocusedSize,
            getPixels(1)
        )
        mFocusedBottomLineSize = typedArray.getDimensionPixelSize(
            R.styleable.MaterialEditText_met_bottomLineColorFocusedSize,
            getPixels(2)
        )
        mErrorTextPaint.color = typedArray.getColor(
            R.styleable.MaterialEditText_met_errorTextColor,
            Color.RED
        )
        mErrorTextPaint.textSize = typedArray.getDimension(
            R.styleable.MaterialEditText_met_errorTextSize,
            context.resources.getDimension(R.dimen.material_component_bottom_line_error_text_size)
        )
        Utils.applyTypeface(
            context,
            typedArray,
            R.styleable.MaterialEditText_met_errorTextTypeface,
            mErrorTextPaint
        )
        mShowCancelButton =
            typedArray.getBoolean(R.styleable.MaterialEditText_met_showCancelButton, false)
        val cancelButtonRes = typedArray.getResourceId(
            R.styleable.MaterialEditText_met_cancelButton,
            R.drawable.ic_clear_black_24dp
        )
        mCancelButton = Utils.getDrawableCompat(context, cancelButtonRes)
        Utils.applyTinting(mCancelButton, mBottomLineColorNormal, PorterDuff.Mode.SRC_ATOP)
    }

    private fun setupValidators() {
        mWasEmpty = getTextAsString().isEmpty()
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // No-op
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val isEmpty = s.isNullOrEmpty()
                if (mWasEmpty && !isEmpty) {
                    animateFloatingLabel(0f, 1f)
                } else if (!mWasEmpty && isEmpty) {
                    animateFloatingLabel(1f, 0f)
                }
                mWasEmpty = isEmpty
                if (mError) {
                    mError = false
                    mErrorMessage = null
                    calculatePadding()
                    invalidate()
                }
                validate(s ?: "", true)
            }

            override fun afterTextChanged(s: Editable?) {
                // No-op
            }
        })
    }

    private fun animateFloatingLabel(from: Float, to: Float) {
        mAnimator?.cancel()
        val animator = ValueAnimator.ofFloat(from, to)
        animator.duration = ANIMATION_DURATION
        animator.addUpdateListener(this)
        animator.start()
        mAnimator = animator
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
        if (mShowCancelButton) {
            if (isRtl()) {
                paddingLeft += getPixels(CANCEL_BUTTON_PADDING_DP)
            } else {
                paddingRight += getPixels(CANCEL_BUTTON_PADDING_DP)
            }
        }
        var paddingBottom = getPixels(NO_ERROR_BOTTOM_PADDING_DP)
        val errorMsg = mErrorMessage
        if (mError && errorMsg != null) {
            mErrorTextPaint.getTextBounds(errorMsg, 0, errorMsg.length, mMeter)
            paddingBottom += mMeter.height()
        }
        var paddingTop = getPixels(16)
        val label = mHint
        if (mMode == Mode.FLOATING_LABEL && label != null) {
            mFloatingLabelPaint.getTextBounds(label, 0, label.length, mMeter)
            paddingTop += mMeter.height()
        }
        setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (min(measuredWidth, measuredHeight) == 0) {
            return
        }
        onDrawLeftIcon(canvas)
        onDrawFloatingLabel(canvas)
        onDrawBottomLine(canvas)
        onDrawCancelButton(canvas)
        onDrawErrorMessage(canvas)
    }

    protected open fun onDrawLeftIcon(canvas: Canvas) {
        val drawables = mLeftDrawable ?: return
        val drawable = drawables[if (hasFocus()) 1 else 0]
        val rectTop = paddingTop - getPixels(8)
        val rectLeft = if (isRtl()) {
            measuredWidth - paddingRight + mIconMargin
        } else {
            getPixels(SIDE_PADDING_DP)
        }
        val padding = getPixels(12)
        val iconTop = rectTop + padding
        val iconLeft = rectLeft + padding + scrollX
        val size = getPixels(24)
        drawable.setBounds(iconLeft, iconTop, iconLeft + size, iconTop + size)
        drawable.draw(canvas)
    }

    protected open fun onDrawFloatingLabel(canvas: Canvas) {
        val label = mHint
        if (mMode == Mode.FLOATING_LABEL && label != null) {
            mFloatingLabelPaint.color =
                if (hasFocus()) mFloatingLabelColorFocused else mFloatingLabelColorNormal
            mFloatingLabelPaint.getTextBounds(label, 0, label.length, mMeter)
            val startY = paddingTop + mMeter.height()
            val endY = paddingTop - getPixels(8)
            val textY = startY - ((startY - endY) * getAnimatedY()).toInt()
            val startX = scrollX + if (isRtl()) {
                measuredWidth - paddingRight - mMeter.width()
            } else {
                paddingLeft
            }
            if (textY < startY) {
                canvas.drawText(label, startX.toFloat(), textY.toFloat(), mFloatingLabelPaint)
            }
        }
    }

    private fun getAnimatedY(): Float {
        val animator = mAnimator
        return if (animator != null) {
            (animator.animatedValue as? Float) ?: 0f
        } else if (getTextAsString().isEmpty()) {
            0f
        } else {
            1f
        }
    }

    @VisibleForTesting
    open fun onDrawBottomLine(canvas: Canvas) {
        mBottomLinePaint.color = when {
            mError -> mBottomLineColorError
            hasFocus() -> mBottomLineColorFocused
            else -> mBottomLineColorNormal
        }
        val lineHeight = if (hasFocus()) mFocusedBottomLineSize else mDefaultBottomLineSize
        val top = measuredHeight - paddingBottom + getPixels(BOTTOM_LINE_PADDING_DP) - lineHeight
        val left = paddingLeft + scrollX -
            if (mShowCancelButton && isRtl()) getPixels(CANCEL_BUTTON_PADDING_DP) else 0
        val right = scrollX + measuredWidth - paddingRight +
            if (mShowCancelButton && !isRtl()) getPixels(CANCEL_BUTTON_PADDING_DP) else 0
        canvas.drawRect(
            left.toFloat(),
            top.toFloat(),
            right.toFloat(),
            (top + lineHeight).toFloat(),
            mBottomLinePaint
        )
    }

    protected open fun onDrawCancelButton(canvas: Canvas) {
        val textLength = text?.length ?: 0
        val cancelBtn = mCancelButton
        if (mShowCancelButton && cancelBtn != null && textLength > 0) {
            val iconSize = getPixels(CANCEL_BUTTON_SIZE_DP)
            val iconPadding = getPixels(CANCEL_BUTTON_PADDING_DP)
            val internalMargin = (iconPadding - iconSize) / 2
            val left = scrollX + internalMargin + if (isRtl()) {
                paddingLeft - iconPadding
            } else {
                measuredWidth - paddingRight
            }
            val top = paddingTop
            cancelBtn.setBounds(left, top, left + iconSize, top + iconSize)
            cancelBtn.draw(canvas)
        }
    }

    protected open fun onDrawErrorMessage(canvas: Canvas) {
        val msg = mErrorMessage
        if (mError && msg != null) {
            mErrorTextPaint.getTextBounds(msg, 0, msg.length, mMeter)
            val startX = if (isRtl()) {
                measuredWidth - paddingRight - mMeter.width()
            } else {
                paddingLeft
            }
            val startY = measuredHeight - getPixels(4)
            canvas.drawText(msg, (startX + scrollX).toFloat(), startY.toFloat(), mErrorTextPaint)
        }
    }

    private fun getPixels(dp: Int): Int {
        return Utils.getPixels(dp, resources).toInt()
    }

    private fun isRtl(): Boolean {
        return Utils.isRtl(resources)
    }

    fun setFloatingLabelColorNormal(@ColorInt floatingLabelColorNormal: Int) {
        mFloatingLabelColorNormal = floatingLabelColorNormal
        invalidate()
    }

    fun setFloatingLabelColorFocused(@ColorInt floatingLabelColorFocused: Int) {
        mFloatingLabelColorFocused = floatingLabelColorFocused
        invalidate()
    }

    fun setBottomLineColorNormal(@ColorInt bottomLineColorNormal: Int) {
        mBottomLineColorNormal = bottomLineColorNormal
        Utils.applyTinting(mCancelButton, bottomLineColorNormal, PorterDuff.Mode.SRC_ATOP)
        invalidate()
    }

    fun setBottomLineColorFocused(@ColorInt bottomLineColorFocused: Int) {
        mBottomLineColorFocused = bottomLineColorFocused
        invalidate()
    }

    fun setBottomLineColorError(@ColorInt bottomLineColorError: Int) {
        mBottomLineColorError = bottomLineColorError
        invalidate()
    }

    fun setLeftIconColorNormal(@ColorInt iconColorNormal: Int) {
        mIconColorNormal = iconColorNormal
        val drawables = mLeftDrawable
        if (drawables != null) {
            Utils.applyTinting(drawables[0], iconColorNormal, PorterDuff.Mode.SRC_IN)
        }
        invalidate()
    }

    fun setLeftIconColorFocused(@ColorInt iconColorFocused: Int) {
        mIconColorFocused = iconColorFocused
        val drawables = mLeftDrawable
        if (drawables != null) {
            Utils.applyTinting(drawables[1], iconColorFocused, PorterDuff.Mode.SRC_IN)
        }
        invalidate()
    }

    fun setLeftDrawable(@DrawableRes drawable: Int) {
        setLeftDrawable(Utils.getDrawableCompat(context, drawable))
    }

    fun setLeftDrawable(drawable: Drawable?) {
        mLeftDrawable = generateDrawables(drawable)
        applyTintingToLeftDrawables(PorterDuff.Mode.SRC_ATOP)
        calculatePadding()
        invalidate()
    }

    fun setTextViewMode(textViewMode: Boolean) {
        isFocusable = !textViewMode
        isFocusableInTouchMode = !textViewMode
        isCursorVisible = !textViewMode
    }

    fun getTextAsString(): String = text?.toString().orEmpty()

    private fun generateDrawables(drawable: Drawable?): Array<Drawable>? {
        val copy = drawable?.constantState?.newDrawable() ?: return null
        return arrayOf(drawable, copy)
    }

    private fun applyTintingToLeftDrawables(mode: PorterDuff.Mode) {
        val drawables = mLeftDrawable ?: return
        Utils.applyTinting(drawables[0], mIconColorNormal, mode)
        Utils.applyTinting(drawables[1], mIconColorFocused, mode)
    }

    fun setOnCancelButtonClickListener(listener: CancelButtonListener?) {
        mCancelButtonListener = listener
    }

    fun addValidator(validator: Validator) {
        mValidators.add(validator)
    }

    fun removeValidator(validator: Validator) {
        mValidators.remove(validator)
    }

    fun removeAllValidators() {
        mValidators.clear()
    }

    fun getValidatorCount(): Int = mValidators.size

    fun validate(): Boolean = validate(getTextAsString(), false)

    protected open fun validate(charSequence: CharSequence, onlyAutoValidator: Boolean): Boolean {
        for (validator in mValidators) {
            if (!onlyAutoValidator || validator.autoValidate()) {
                if (!validator.isValid(charSequence)) {
                    showError(validator.getErrorMessage())
                    return false
                }
            }
        }
        if (mError) {
            mError = false
            mErrorMessage = null
            calculatePadding()
            invalidate()
        }
        return true
    }

    fun showError(@StringRes errorMessage: Int) {
        error = context.getString(errorMessage)
    }

    fun showError(errorMessage: String?) {
        mError = true
        mErrorMessage = errorMessage
        calculatePadding()
        invalidate()
    }

    override fun onAnimationUpdate(animation: ValueAnimator) {
        ViewCompat.postInvalidateOnAnimation(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (mShowCancelButton && mCancelButton != null && getTextAsString().isNotEmpty()) {
            val iconSize = getPixels(CANCEL_BUTTON_SIZE_DP)
            val iconPadding = getPixels(CANCEL_BUTTON_PADDING_DP)
            val internalMargin = (iconPadding - iconSize) / 2
            val left = internalMargin + if (isRtl()) {
                paddingLeft - iconPadding
            } else {
                measuredWidth - paddingRight
            }
            val top = paddingTop
            val inX = event.x >= left && event.x <= left + iconSize
            val inY = event.y >= top && event.y <= top + iconSize
            if (inX && inY) {
                if (event.action == MotionEvent.ACTION_UP) {
                    val actionConsumed = mCancelButtonListener?.onCancelButtonClick(this) ?: false
                    if (!actionConsumed) {
                        setText(null)
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    interface CancelButtonListener {
        fun onCancelButtonClick(materialEditText: MaterialEditText): Boolean
    }

    companion object {
        private const val ANIMATION_DURATION = 300L
        private const val ICON_CONTAINER_SIZE_DP = 48
        private const val ICON_MARGIN_SIZE_DP = 8
        private const val NO_ERROR_BOTTOM_PADDING_DP = 16
        private const val BOTTOM_LINE_PADDING_DP = 8
        private const val SIDE_PADDING_DP = 8
        private const val CANCEL_BUTTON_PADDING_DP = 36
        private const val CANCEL_BUTTON_SIZE_DP = 24
    }
}
