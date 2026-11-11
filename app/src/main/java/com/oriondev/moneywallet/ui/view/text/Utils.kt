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
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StyleableRes
import androidx.core.content.ContextCompat
import com.oriondev.moneywallet.storage.cache.TypefaceCache

object Utils {

    @JvmStatic
    fun getPixels(dp: Int, resources: Resources): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        )
    }

    @JvmStatic
    fun isRtl(resources: Resources): Boolean {
        return resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    }

    @JvmStatic
    fun getDrawableCompat(context: Context, @DrawableRes drawable: Int): Drawable? {
        return ContextCompat.getDrawable(context, drawable)
    }

    @Suppress("DEPRECATION")
    internal fun applyTinting(drawable: Drawable?, @ColorInt color: Int, mode: PorterDuff.Mode) {
        drawable?.mutate()?.setColorFilter(color, mode)
    }

    internal fun applyTypeface(
        context: Context,
        typedArray: TypedArray,
        @StyleableRes index: Int,
        paint: Paint
    ) {
        val typefaceName = typedArray.getString(index)
        if (typefaceName != null) {
            paint.typeface = TypefaceCache.get(context, typefaceName)
        }
    }
}
