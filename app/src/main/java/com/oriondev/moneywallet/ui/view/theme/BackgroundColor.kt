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

package com.oriondev.moneywallet.ui.view.theme

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.StyleableRes

/**
 * Created by andrea on 13/04/18.
 */
internal enum class BackgroundColor(private val mValue: Int) {

    COLOR_PRIMARY(0),
    COLOR_PRIMARY_DARK(1),
    COLOR_ACCENT(2),
    COLOR_WINDOW_BACKGROUND(3),
    COLOR_WINDOW_FOREGROUND(4);

    fun getColor(theme: ITheme): Int {
        return when (mValue) {
            0 -> theme.colorPrimary
            1 -> theme.colorPrimaryDark
            2 -> theme.colorAccent
            4 -> theme.colorWindowForeground
            else -> theme.colorWindowBackground
        }
    }

    companion object {

        fun getValue(backgroundColor: BackgroundColor): Int = backgroundColor.mValue

        fun fromValue(value: Int): BackgroundColor? {
            return entries.firstOrNull { it.mValue == value }
        }
    }
}

internal inline fun <T> Context.readThemeAttribute(
    attrs: AttributeSet?,
    @StyleableRes styleable: IntArray,
    @StyleableRes attrIndex: Int,
    defStyleAttr: Int = 0,
    defaultValue: Int = 0,
    mapper: (Int) -> T?
): T? {
    val typedArray = obtainStyledAttributes(attrs, styleable, defStyleAttr, 0)
    return try {
        mapper(typedArray.getInt(attrIndex, defaultValue))
    } catch (e: Exception) {
        e.printStackTrace()
        null
    } finally {
        typedArray.recycle()
    }
}

internal fun Context.readBackgroundColor(
    attrs: AttributeSet?,
    @StyleableRes styleable: IntArray,
    @StyleableRes attrIndex: Int,
    defStyleAttr: Int = 0,
    defaultValue: Int = 0
): BackgroundColor? {
    return readThemeAttribute(
        attrs = attrs,
        styleable = styleable,
        attrIndex = attrIndex,
        defStyleAttr = defStyleAttr,
        defaultValue = defaultValue,
        mapper = BackgroundColor::fromValue
    )
}
