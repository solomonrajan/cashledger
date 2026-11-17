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

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.utils.IconLoader

/**
 * Created by andrea on 17/08/18.
 */
class DoubleIconDrawable(
    context: Context,
    icon1: Icon?,
    icon2: Icon?
) : Drawable() {

    private val mDrawable1: Drawable? = resolveDrawable(context, icon1)
    private val mDrawable2: Drawable? = resolveDrawable(context, icon2)

    private fun resolveDrawable(context: Context, icon: Icon?): Drawable? =
        icon?.getDrawable(context) ?: IconLoader.UNKNOWN.getDrawable(context)

    override fun draw(canvas: Canvas) {
        val width = canvas.width
        val height = canvas.height
        if (width == 0 || height == 0) {
            return
        }
        mDrawable1?.setBounds(0, 0, width / 2, height)
        mDrawable2?.setBounds(width / 2, 0, width, height)
        mDrawable1?.draw(canvas)
        mDrawable2?.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        // Not supported by composite icon drawables.
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // Not supported by composite icon drawables.
    }

    @Deprecated("Deprecated in Java")
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.UNKNOWN
}
