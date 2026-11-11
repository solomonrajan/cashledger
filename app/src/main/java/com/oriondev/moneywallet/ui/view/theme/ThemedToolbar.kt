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
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.MenuRes
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.appcompat.widget.Toolbar
import com.oriondev.moneywallet.R

/**
 * Created by andrea on 10/04/18.
 */
class ThemedToolbar : Toolbar, ThemeEngine.ThemeConsumer {

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
        mBackgroundColor = context.readThemeAttribute(
            attrs = attrs,
            styleable = R.styleable.ThemedToolbar,
            attrIndex = R.styleable.ThemedToolbar_theme_backgroundColor,
            defStyleAttr = defStyleAttr,
            mapper = BackgroundColor::fromValue
        )
    }

    override fun onApplyTheme(theme: ITheme) {
        if (mBackgroundColor != null) {
            setBackgroundColor(getBackgroundColor(theme))
            setTitleTextColor(theme)
            setOverflowButtonColor(theme)
            setNavigationIconColor(theme)
            setMenuIconColor(theme)
        }
    }

    override fun setNavigationIcon(icon: Drawable?) {
        super.setNavigationIcon(icon)
        setNavigationIconColor(ThemeEngine.getTheme())
    }

    override fun inflateMenu(@MenuRes resId: Int) {
        super.inflateMenu(resId)
        setMenuIconColor(ThemeEngine.getTheme())
    }

    private fun getBackgroundColor(theme: ITheme): Int {
        return when (mBackgroundColor) {
            BackgroundColor.COLOR_PRIMARY, null -> theme.colorPrimary
            BackgroundColor.COLOR_PRIMARY_DARK -> theme.colorPrimaryDark
        }
    }

    private fun setTitleTextColor(theme: ITheme) {
        val background = getBackgroundColor(theme)
        val color = theme.getBestTextColor(background)
        setTitleTextColor(color)
        setSubtitleTextColor(color)
    }

    private fun setOverflowButtonColor(theme: ITheme) {
        val drawable = overflowIcon ?: return
        val background = getBackgroundColor(theme)
        val color = theme.getBestTextColor(background)
        super.setOverflowIcon(TintHelper.createTintedDrawable(drawable, color))
    }

    private fun setNavigationIconColor(theme: ITheme) {
        val drawable = navigationIcon ?: return
        val background = getBackgroundColor(theme)
        val color = theme.getBestTextColor(background)
        if (drawable is DrawerArrowDrawable) {
            drawable.color = color
            super.setNavigationIcon(drawable)
        } else {
            super.setNavigationIcon(TintHelper.createTintedDrawable(drawable, color))
        }
    }

    private fun setMenuIconColor(theme: ITheme) {
        val menu = menu ?: return
        val background = getBackgroundColor(theme)
        val color = theme.getBestTextColor(background)
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val drawable = item.icon
            if (drawable != null) {
                item.icon = TintHelper.createTintedDrawable(drawable, color)
            }
        }
    }

    enum class BackgroundColor(private val mValue: Int) {
        COLOR_PRIMARY(0),
        COLOR_PRIMARY_DARK(1);

        companion object {
            internal fun fromValue(value: Int): BackgroundColor? {
                return entries.firstOrNull { it.mValue == value }
            }
        }
    }
}
