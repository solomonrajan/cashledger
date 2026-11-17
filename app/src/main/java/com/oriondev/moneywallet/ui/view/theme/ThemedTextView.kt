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
import androidx.appcompat.widget.AppCompatTextView
import com.oriondev.moneywallet.R

/**
 * Created by andrea on 10/04/18.
 */
class ThemedTextView : AppCompatTextView, ThemeEngine.ThemeConsumer {

    private var mTextColor: TextColor? = null

    constructor(context: Context) : super(context) {
        initialize(context, null, 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(context, attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialize(context, attrs, defStyleAttr)
    }

    private fun initialize(context: Context, attrs: AttributeSet?, defStyleAttr: Int) {
        mTextColor = context.readThemeAttribute(
            attrs = attrs,
            styleable = R.styleable.ThemedTextView,
            attrIndex = R.styleable.ThemedTextView_theme_textColor,
            defStyleAttr = defStyleAttr,
            defaultValue = 1,
            mapper = TextColor::fromValue
        )
    }

    override fun onApplyTheme(theme: ITheme) {
        when (mTextColor) {
            TextColor.PRIMARY -> setTextColor(theme.textColorPrimary)
            TextColor.SECONDARY -> setTextColor(theme.textColorSecondary)
            TextColor.PRIMARY_INVERSE -> setTextColor(theme.textColorPrimaryInverse)
            TextColor.SECONDARY_INVERSE -> setTextColor(theme.textColorSecondaryInverse)
            TextColor.COLOR_PRIMARY -> setTextColor(theme.colorPrimary)
            TextColor.COLOR_ACCENT -> setTextColor(Util.accentAsText(theme))
            TextColor.OVER_COLOR_PRIMARY -> setTextColor(theme.getBestTextColor(theme.colorPrimary))
            TextColor.OVER_COLOR_PRIMARY_DARK -> setTextColor(theme.getBestTextColor(theme.colorPrimaryDark))
            TextColor.OVER_COLOR_ACCENT -> setTextColor(theme.getBestTextColor(theme.colorAccent))
            null -> println("{WARN} IS NULL WTF?")
        }
    }

    enum class TextColor(private val mValue: Int) {
        PRIMARY(0),
        SECONDARY(1),
        PRIMARY_INVERSE(2),
        SECONDARY_INVERSE(3),
        COLOR_PRIMARY(4),
        COLOR_ACCENT(5),
        OVER_COLOR_PRIMARY(6),
        OVER_COLOR_PRIMARY_DARK(7),
        OVER_COLOR_ACCENT(8);

        companion object {
            internal fun fromValue(value: Int): TextColor? {
                return entries.firstOrNull { it.mValue == value }
            }
        }
    }
}
