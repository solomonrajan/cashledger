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

package com.oriondev.moneywallet.ui.drawable

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.graphics.drawable.shapes.RectShape
import android.graphics.drawable.shapes.RoundRectShape
import java.util.Locale
import kotlin.math.min

/**
 * This class has been imported from this repository: https://github.com/amulyakhare/TextDrawable
 * It has been modified to correctly recycle Typefaces and not waste memory.
 */
class TextDrawable private constructor(builder: Builder) : ShapeDrawable(builder.shape) {

    private val textPaint: Paint
    private val borderPaint: Paint
    private val text: String = if (builder.toUpperCase) {
        builder.text.uppercase(Locale.getDefault())
    } else {
        builder.text
    }
    private val color: Int = builder.color
    private val shape: RectShape = builder.shape
    private val height: Int = builder.height
    private val width: Int = builder.width
    private val fontSize: Int = builder.fontSize
    private val radius: Float = builder.radius
    private val borderThickness: Int = builder.borderThickness

    init {
        textPaint = Paint().apply {
            color = builder.textColor
            isAntiAlias = true
            isFakeBoldText = builder.isBold
            style = Paint.Style.FILL
            typeface = builder.font
            textAlign = Paint.Align.CENTER
            strokeWidth = builder.borderThickness.toFloat()
        }
        borderPaint = Paint().apply {
            color = getDarkerShade(this@TextDrawable.color)
            style = Paint.Style.STROKE
            strokeWidth = borderThickness.toFloat()
        }
        paint.color = color
    }

    private fun getDarkerShade(color: Int): Int {
        return Color.rgb(
            (SHADE_FACTOR * Color.red(color)).toInt(),
            (SHADE_FACTOR * Color.green(color)).toInt(),
            (SHADE_FACTOR * Color.blue(color)).toInt()
        )
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        val r = bounds
        if (borderThickness > 0) {
            drawBorder(canvas)
        }
        val count = canvas.save()
        canvas.translate(r.left.toFloat(), r.top.toFloat())
        val width = r.width()
        val height = r.height()
        val resolvedFontSize = if (fontSize < 0) min(width, height) / 2 else fontSize
        textPaint.textSize = resolvedFontSize.toFloat()
        canvas.drawText(
            text,
            width / 2f,
            height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f,
            textPaint
        )
        canvas.restoreToCount(count)
    }

    private fun drawBorder(canvas: Canvas) {
        val rect = RectF(bounds)
        val inset = borderThickness / 2f
        rect.inset(inset, inset)
        when (shape) {
            is OvalShape -> canvas.drawOval(rect, borderPaint)
            is RoundRectShape -> canvas.drawRoundRect(rect, radius, radius, borderPaint)
            else -> canvas.drawRect(rect, borderPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        textPaint.alpha = alpha
    }

    override fun setColorFilter(cf: ColorFilter?) {
        textPaint.colorFilter = cf
    }

    @Deprecated("Deprecated in Java")
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = width

    override fun getIntrinsicHeight(): Int = height

    class Builder internal constructor() : IConfigBuilder, IShapeBuilder, IBuilder {

        internal var text: String = ""
        internal var color: Int = Color.GRAY
        internal var borderThickness: Int = 0
        internal var width: Int = -1
        internal var height: Int = -1
        internal var font: Typeface? = Typeface.create("sans-serif-light", Typeface.NORMAL)
        internal var shape: RectShape = RectShape()

        @JvmField
        var textColor: Int = Color.WHITE

        internal var fontSize: Int = -1
        internal var isBold: Boolean = false
        internal var toUpperCase: Boolean = false

        @JvmField
        var radius: Float = 0f

        override fun width(width: Int): IConfigBuilder {
            this.width = width
            return this
        }

        override fun height(height: Int): IConfigBuilder {
            this.height = height
            return this
        }

        override fun textColor(color: Int): IConfigBuilder {
            this.textColor = color
            return this
        }

        override fun withBorder(thickness: Int): IConfigBuilder {
            this.borderThickness = thickness
            return this
        }

        override fun useFont(font: Typeface?): IConfigBuilder {
            this.font = font
            return this
        }

        override fun fontSize(size: Int): IConfigBuilder {
            this.fontSize = size
            return this
        }

        override fun bold(): IConfigBuilder {
            this.isBold = true
            return this
        }

        override fun toUpperCase(): IConfigBuilder {
            this.toUpperCase = true
            return this
        }

        override fun beginConfig(): IConfigBuilder = this

        override fun endConfig(): IShapeBuilder = this

        override fun rect(): IBuilder {
            this.shape = RectShape()
            return this
        }

        override fun round(): IBuilder {
            this.shape = OvalShape()
            return this
        }

        override fun roundRect(radius: Int): IBuilder {
            this.radius = radius.toFloat()
            val radiusFloat = radius.toFloat()
            val radii = FloatArray(8) { radiusFloat }
            this.shape = RoundRectShape(radii, null, null)
            return this
        }

        override fun buildRect(text: String, color: Int): TextDrawable {
            rect()
            return build(text, color)
        }

        override fun buildRoundRect(text: String, color: Int, radius: Int): TextDrawable {
            roundRect(radius)
            return build(text, color)
        }

        override fun buildRound(text: String, color: Int): TextDrawable {
            round()
            return build(text, color)
        }

        override fun build(text: String, color: Int): TextDrawable {
            this.color = color
            this.text = text
            return TextDrawable(this)
        }
    }

    interface IConfigBuilder {

        fun width(width: Int): IConfigBuilder

        fun height(height: Int): IConfigBuilder

        fun textColor(color: Int): IConfigBuilder

        fun withBorder(thickness: Int): IConfigBuilder

        fun useFont(font: Typeface?): IConfigBuilder

        fun fontSize(size: Int): IConfigBuilder

        fun bold(): IConfigBuilder

        fun toUpperCase(): IConfigBuilder

        fun endConfig(): IShapeBuilder
    }

    interface IBuilder {

        fun build(text: String, color: Int): TextDrawable
    }

    interface IShapeBuilder {

        fun beginConfig(): IConfigBuilder

        fun rect(): IBuilder

        fun round(): IBuilder

        fun roundRect(radius: Int): IBuilder

        fun buildRect(text: String, color: Int): TextDrawable

        fun buildRoundRect(text: String, color: Int, radius: Int): TextDrawable

        fun buildRound(text: String, color: Int): TextDrawable
    }

    companion object {

        private const val SHADE_FACTOR = 0.9f

        @JvmStatic
        fun builder(): IShapeBuilder = Builder()
    }
}
