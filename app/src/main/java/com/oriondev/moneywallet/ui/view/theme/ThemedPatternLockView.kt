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
import com.andrognito.patternlockview.PatternLockView
import com.oriondev.moneywallet.R

/**
 * Created by andrea on 20/08/18.
 */
class ThemedPatternLockView : PatternLockView, ThemeEngine.ThemeConsumer {

    private var mBackgroundColor: BackgroundColor? = null

    constructor(context: Context) : super(context) {
        initialize(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(context, attrs)
    }

    private fun initialize(context: Context, attrs: AttributeSet?) {
        mBackgroundColor = context.readBackgroundColor(
            attrs = attrs,
            styleable = R.styleable.ThemedPatternLockView,
            attrIndex = R.styleable.ThemedPatternLockView_theme_backgroundColor
        )
    }

    override fun onApplyTheme(theme: ITheme) {
        val backgroundColor = getBackgroundColor(theme)
        correctStateColor = Util.visibleOr(theme.colorAccent, backgroundColor, theme.getBestColor(backgroundColor))
        normalStateColor = theme.getBestColor(backgroundColor)
        /* TODO: ensure this is visible over background color */
        wrongStateColor = theme.errorColor
    }

    private fun getBackgroundColor(theme: ITheme): Int {
        return when (mBackgroundColor) {
            null -> theme.colorWindowBackground
            BackgroundColor.COLOR_PRIMARY -> theme.colorPrimary
            else -> theme.colorPrimaryDark
        }
    }
}
