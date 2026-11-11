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
import androidx.appcompat.widget.AppCompatEditText
import com.oriondev.moneywallet.R

/**
 * Created by andrea on 20/08/18.
 */
class ThemedEditText : AppCompatEditText, ThemeEngine.ThemeConsumer {

    private var mBackgroundColor: BackgroundColor? = null

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
        mBackgroundColor = context.readBackgroundColor(
            attrs = attrs,
            styleable = R.styleable.ThemedEditText,
            attrIndex = R.styleable.ThemedEditText_theme_backgroundColor,
            defStyleAttr = defStyleAttr
        )
    }

    override fun onApplyTheme(theme: ITheme) {
        val background = mBackgroundColor ?: return
        val backgroundColor = background.getColor(theme)
        setTextColor(theme.getBestTextColor(backgroundColor))
        setHintTextColor(theme.getBestHintColor(backgroundColor))
        TintHelper.setCursorTint(this, theme.colorAccent)
    }
}
