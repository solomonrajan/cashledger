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

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.SeekBar
import androidx.annotation.CheckResult
import androidx.annotation.ColorInt
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.drawable.DrawableCompat

/**
 * Created by andrea on 11/04/18.
 */
internal object TintHelper {

    private val COLOR_BUTTON_DISABLED_LIGHT = Color.parseColor("#1F000000")
    private val COLOR_BUTTON_DISABLED_DARK = Color.parseColor("#1F000000")

    private val COLOR_CONTROL_DISABLED_DARK = Color.parseColor("#43000000")
    private val COLOR_CONTROL_DISABLED_LIGHT = Color.parseColor("#4DFFFFFF")
    private val COLOR_CONTROL_NORMAL_DARK = Color.parseColor("#B3FFFFFF")
    private val COLOR_CONTROL_NORMAL_LIGHT = Color.parseColor("#8A000000")

    private val COLOR_SWITCH_THUMB_DISABLED_LIGHT = Color.parseColor("#FFBDBDBD")
    private val COLOR_SWITCH_THUMB_DISABLED_DARK = Color.parseColor("#FF424242")
    private val COLOR_SWITCH_THUMB_NORMAL_LIGHT = Color.parseColor("#FFFAFAFA")
    private val COLOR_SWITCH_THUMB_NORMAL_DARK = Color.parseColor("#FFBDBDBD")
    private val COLOR_SWITCH_TRACK_DISABLED_LIGHT = Color.parseColor("#1F000000")
    private val COLOR_SWITCH_TRACK_DISABLED_DARK = Color.parseColor("#1AFFFFFF")
    private val COLOR_SWITCH_TRACK_NORMAL_LIGHT = Color.parseColor("#43000000")
    private val COLOR_SWITCH_TRACK_NORMAL_DARK = Color.parseColor("#4DFFFFFF")

    // Tints the drawable it was given, after mutate() so the tint does not reach every drawable
    // sharing its state. From API 23 the wrap is the identity, so the caller's object comes back,
    // and setCursorTint relies on that: the editor keeps drawing the drawable it loaded first.
    @CheckResult
    fun createTintedDrawable(drawable: Drawable?, @ColorInt color: Int): Drawable? {
        if (drawable == null) {
            return null
        }
        val wrapped = DrawableCompat.wrap(drawable.mutate())
        DrawableCompat.setTintMode(wrapped, PorterDuff.Mode.SRC_IN)
        DrawableCompat.setTint(wrapped, color)
        return wrapped
    }

    @CheckResult
    fun createTintedDrawable(drawable: Drawable?, colorStateList: ColorStateList): Drawable? {
        if (drawable == null) {
            return null
        }
        val wrapped = DrawableCompat.wrap(drawable.mutate())
        DrawableCompat.setTintList(wrapped, colorStateList)
        return wrapped
    }

    private fun modifySwitchDrawable(
        from: Drawable,
        @ColorInt tint: Int,
        thumb: Boolean,
        useDarker: Boolean
    ): Drawable? {
        val shifted = if (useDarker) Util.shiftColor(tint, 1.1f) else tint
        val adjustedTint = Util.adjustAlpha(shifted, if (thumb) 1.0f else 0.5f)
        val disabled: Int
        val normal: Int
        if (thumb) {
            disabled = if (useDarker) COLOR_SWITCH_THUMB_DISABLED_DARK else COLOR_SWITCH_THUMB_DISABLED_LIGHT
            normal = if (useDarker) COLOR_SWITCH_THUMB_NORMAL_DARK else COLOR_SWITCH_THUMB_NORMAL_LIGHT
        } else {
            disabled = if (useDarker) COLOR_SWITCH_TRACK_DISABLED_DARK else COLOR_SWITCH_TRACK_DISABLED_LIGHT
            normal = if (useDarker) COLOR_SWITCH_TRACK_NORMAL_DARK else COLOR_SWITCH_TRACK_NORMAL_LIGHT
        }
        val stateList = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_enabled, -android.R.attr.state_activated, -android.R.attr.state_checked),
                intArrayOf(android.R.attr.state_enabled, android.R.attr.state_activated),
                intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
            ),
            intArrayOf(disabled, normal, adjustedTint, adjustedTint)
        )
        return createTintedDrawable(from, stateList)
    }

    fun applyTint(button: Button, @ColorInt color: Int, @ColorInt rippleColor: Int, useDarker: Boolean) {
        val disabled = if (useDarker) COLOR_BUTTON_DISABLED_DARK else COLOR_BUTTON_DISABLED_LIGHT
        val colorStateList = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_enabled)
            ),
            intArrayOf(disabled, color)
        )
        val background = button.background
        if (background is RippleDrawable) {
            background.setColor(ColorStateList.valueOf(rippleColor))
        }
        if (background != null) {
            button.background = createTintedDrawable(background, colorStateList)
        }
    }

    fun applyTint(button: CompoundButton, @ColorInt color: Int, useDarker: Boolean) {
        val stateList = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_enabled, -android.R.attr.state_checked),
                intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
            ),
            intArrayOf(
                Util.stripAlpha(if (useDarker) COLOR_CONTROL_DISABLED_DARK else COLOR_CONTROL_DISABLED_LIGHT),
                if (useDarker) COLOR_CONTROL_NORMAL_DARK else COLOR_CONTROL_NORMAL_LIGHT,
                color
            )
        )
        button.buttonTintList = stateList
    }

    fun applyTint(switchCompat: SwitchCompat, @ColorInt color: Int, useDarker: Boolean) {
        val trackDrawable = switchCompat.trackDrawable
        if (trackDrawable != null) {
            switchCompat.trackDrawable = modifySwitchDrawable(trackDrawable, color, false, useDarker)
        }
        val thumbDrawable = switchCompat.thumbDrawable
        if (thumbDrawable != null) {
            switchCompat.thumbDrawable = modifySwitchDrawable(thumbDrawable, color, true, useDarker)
        }
    }

    fun applyTint(seekBar: SeekBar, @ColorInt color: Int, useDarker: Boolean) {
        val colorStateList = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_enabled)
            ),
            intArrayOf(
                if (useDarker) COLOR_CONTROL_DISABLED_DARK else COLOR_CONTROL_DISABLED_LIGHT,
                color
            )
        )
        seekBar.thumbTintList = colorStateList
        seekBar.progressTintList = colorStateList
    }

    /**
     * Below API 29 the cursor drawable is private to the text view, so it keeps the theme's color.
     */
    fun setCursorTint(editText: EditText, @ColorInt color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cursor = editText.textCursorDrawable
            if (cursor != null) {
                editText.textCursorDrawable = createTintedDrawable(cursor, color)
            }
        }
    }
}
