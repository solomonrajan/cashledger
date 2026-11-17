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
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.core.graphics.ColorUtils

/**
 * This class is responsible to dynamically theme the user interface at runtime.
 */
class ThemeEngine private constructor(context: Context) : ITheme {

    private val mContext: Context = context.applicationContext ?: context
    private val mPreferences: SharedPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    // Each getter holds the override it read, since it can be cleared between a null check
    // and a use.
    override val colorPrimary: Int
        get() {
            val preview = sPreviewColorPrimary
            return if (preview != null) noAlpha(preview) else getStoredColorPrimary()
        }

    override val colorPrimaryDark: Int
        get() {
            val preview = sPreviewColorPrimary
            if (preview != null) {
                return noAlpha(Util.darkenColor(preview))
            }
            return noAlpha(mPreferences.getInt(COLOR_PRIMARY_DARK, DEFAULT_COLOR_PRIMARY_DARK))
        }

    override val colorAccent: Int
        get() {
            val preview = sPreviewColorAccent
            return if (preview != null) noAlpha(preview) else getStoredColorAccent()
        }

    private fun getStoredColorPrimary(): Int {
        return noAlpha(mPreferences.getInt(COLOR_PRIMARY, DEFAULT_COLOR_PRIMARY))
    }

    private fun getStoredColorAccent(): Int {
        return noAlpha(mPreferences.getInt(COLOR_ACCENT, DEFAULT_COLOR_ACCENT))
    }

    private fun noAlpha(color: Int): Int {
        return ColorUtils.setAlphaComponent(color, 255)
    }

    override val mode: Mode
        get() {
            return when (mPreferences.getInt(MODE, DEFAULT_MODE.index)) {
                INDEX_MODE_LIGHT -> Mode.LIGHT
                INDEX_MODE_DARK -> Mode.DARK
                INDEX_MODE_DEEP_DARK -> Mode.DEEP_DARK
                INDEX_MODE_SYSTEM_DEFAULT -> Mode.SYSTEM_DEFAULT
                // the stored value has been manually altered
                else -> Mode.LIGHT
            }
        }

    private fun getResolvedIndex(): Int {
        val index = mPreferences.getInt(MODE, DEFAULT_MODE.index)
        if (index == INDEX_MODE_SYSTEM_DEFAULT) {
            val currentNightMode = mContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
                INDEX_MODE_DARK
            } else {
                INDEX_MODE_LIGHT
            }
        } else if (index == INDEX_MODE_LIGHT || index == INDEX_MODE_DARK || index == INDEX_MODE_DEEP_DARK) {
            return index
        }
        return INDEX_MODE_LIGHT
    }

    override val isDark: Boolean
        get() = getResolvedIndex() != INDEX_MODE_LIGHT

    override val textColorPrimary: Int
        get() = DEFAULT_TEXT_COLOR_PRIMARY[getResolvedIndex()]

    override val textColorSecondary: Int
        get() = DEFAULT_TEXT_COLOR_SECONDARY[getResolvedIndex()]

    override val textColorPrimaryInverse: Int
        get() = DEFAULT_TEXT_COLOR_PRIMARY_INVERSE[getResolvedIndex()]

    override val textColorSecondaryInverse: Int
        get() = DEFAULT_TEXT_COLOR_SECONDARY_INVERSE[getResolvedIndex()]

    override val colorCardBackground: Int
        get() = DEFAULT_COLOR_CARD_BACKGROUND[getResolvedIndex()]

    override val colorWindowForeground: Int
        get() = DEFAULT_COLOR_WINDOW_FOREGROUND[getResolvedIndex()]

    override val colorWindowBackground: Int
        get() = DEFAULT_COLOR_WINDOW_BACKGROUND[getResolvedIndex()]

    override val colorRipple: Int
        get() = DEFAULT_COLOR_RIPPLE[getResolvedIndex()]

    override val iconColor: Int
        get() = DEFAULT_ICON_COLOR[getResolvedIndex()]

    override val hintTextColor: Int
        get() = DEFAULT_HINT_TEXT_COLOR[getResolvedIndex()]

    override val errorColor: Int
        get() = Color.RED

    override val drawerBackgroundColor: Int
        get() = DRAWER_BACKGROUND_COLOR[getResolvedIndex()]

    override val drawerIconColor: Int
        get() = DRAWER_ICON_COLOR[getResolvedIndex()]

    override val drawerSelectedIconColor: Int
        get() = drawerSelectedForeground()

    override val drawerTextColor: Int
        get() = DRAWER_TEXT_COLOR[getResolvedIndex()]

    override val drawerSelectedTextColor: Int
        get() = drawerSelectedForeground()

    // The opaque black or white, not the surface's own icon or text color: those are what the
    // closed entries are already drawn in.
    private fun drawerSelectedForeground(): Int {
        val background = drawerSelectedItemColor
        return Util.visibleOr(colorPrimary, background, getBestColor(background))
    }

    override val drawerSelectedItemColor: Int
        get() = DRAWER_SELECTED_ITEM_COLOR[getResolvedIndex()]

    override fun getBestColor(background: Int): Int {
        return if (Util.isColorLight(background)) Color.BLACK else Color.WHITE
    }

    override fun getBestTextColor(background: Int): Int {
        val index = if (Util.isColorLight(background)) INDEX_MODE_LIGHT else INDEX_MODE_DARK
        return DEFAULT_TEXT_COLOR_PRIMARY[index]
    }

    override fun getBestHintColor(background: Int): Int {
        val index = if (Util.isColorLight(background)) INDEX_MODE_LIGHT else INDEX_MODE_DARK
        return DEFAULT_HINT_TEXT_COLOR[index]
    }

    override fun getVisibleColor(color: Int, fallback: Int): Int {
        return if (Util.holdsUpAnywhere(this, color)) color else fallback
    }

    override fun getBestIconColor(background: Int): Int {
        val index = if (Util.isColorLight(background)) INDEX_MODE_LIGHT else INDEX_MODE_DARK
        return DEFAULT_ICON_COLOR[index]
    }

    enum class Mode(internal val index: Int) {
        LIGHT(INDEX_MODE_LIGHT),
        DARK(INDEX_MODE_DARK),
        DEEP_DARK(INDEX_MODE_DEEP_DARK),
        SYSTEM_DEFAULT(INDEX_MODE_SYSTEM_DEFAULT)
    }

    fun interface ThemeObserver {
        fun onThemeChanged(theme: ITheme)
    }

    fun interface ThemeConsumer {
        fun onApplyTheme(theme: ITheme)
    }

    companion object {

        private const val FILE_NAME = "theme.config"
        private const val COLOR_PRIMARY = "color_primary"
        private const val COLOR_PRIMARY_DARK = "color_primary_dark"
        private const val COLOR_ACCENT = "color_accent"
        private const val MODE = "mode"

        private val DEFAULT_COLOR_PRIMARY = Color.parseColor("#4285F4")
        private val DEFAULT_COLOR_PRIMARY_DARK = Color.parseColor("#3367D6")
        private val DEFAULT_COLOR_ACCENT = Color.parseColor("#EA4335")
        private val DEFAULT_MODE = Mode.SYSTEM_DEFAULT

        private val DEFAULT_TEXT_COLOR_PRIMARY = intArrayOf(
            Color.parseColor("#DE000000"), // OK
            Color.parseColor("#FFFFFFFF"), // OK
            Color.parseColor("#FFFFFFFF")  // OK
        )

        private val DEFAULT_TEXT_COLOR_PRIMARY_INVERSE = intArrayOf(
            Color.parseColor("#DEFFFFFF"), // OK
            Color.parseColor("#FF000000"), // OK
            Color.parseColor("#FF000000")  // OK
        )

        private val DEFAULT_TEXT_COLOR_SECONDARY = intArrayOf(
            Color.parseColor("#8A000000"), // OK
            Color.parseColor("#B3FFFFFF"), // OK
            Color.parseColor("#B3FFFFFF")  // OK
        )

        private val DEFAULT_TEXT_COLOR_SECONDARY_INVERSE = intArrayOf(
            Color.parseColor("#8AFFFFFF"), // OK
            Color.parseColor("#B3000000"), // OK
            Color.parseColor("#B3000000")
        )

        private val DEFAULT_ICON_COLOR = intArrayOf(
            Color.parseColor("#8A000000"), // OK
            Color.parseColor("#8AFFFFFF"), // OK
            Color.parseColor("#8AFFFFFF")  // OK
        )

        private val DEFAULT_HINT_TEXT_COLOR = intArrayOf(
            Color.parseColor("#61000000"), // OK
            Color.parseColor("#80FFFFFF"), // OK
            Color.parseColor("#80FFFFFF")  // OK
        )

        private val DEFAULT_COLOR_CARD_BACKGROUND = intArrayOf(
            Color.parseColor("#FFFFFF"), // OK
            Color.parseColor("#424242"), // OK
            Color.parseColor("#424242")  // OK
        )

        private val DEFAULT_COLOR_WINDOW_FOREGROUND = intArrayOf(
            Color.parseColor("#FFFFFF"), // OK
            Color.parseColor("#303030"), // OK
            Color.parseColor("#000000")  // OK
        )

        private val DEFAULT_COLOR_WINDOW_BACKGROUND = intArrayOf(
            Color.parseColor("#FAFAFA"), // OK
            Color.parseColor("#212121"), // OK
            Color.parseColor("#212121")  // OK
        )

        private val DEFAULT_COLOR_RIPPLE = intArrayOf(
            Color.parseColor("#1f000000"), // OK
            Color.parseColor("#33ffffff"), // OK
            Color.parseColor("#33ffffff")  // OK
        )

        private val DRAWER_BACKGROUND_COLOR = intArrayOf(
            Color.parseColor("#F9F9F9"),
            Color.parseColor("#303030"),
            Color.parseColor("#303030")
        )

        private val DRAWER_ICON_COLOR = intArrayOf(
            Color.parseColor("#8A000000"),
            Color.parseColor("#8AFFFFFF"),
            Color.parseColor("#8AFFFFFF")
        )

        private val DRAWER_TEXT_COLOR = intArrayOf(
            Color.parseColor("#DE000000"),
            Color.parseColor("#DEFFFFFF"),
            Color.parseColor("#DEFFFFFF")
        )

        private val DRAWER_SELECTED_ITEM_COLOR = intArrayOf(
            Color.parseColor("#E8E8E8"),
            Color.parseColor("#202020"),
            Color.parseColor("#202020")
        )

        private const val INDEX_MODE_LIGHT = 0
        private const val INDEX_MODE_DARK = 1
        private const val INDEX_MODE_DEEP_DARK = 2
        private const val INDEX_MODE_SYSTEM_DEFAULT = 3

        private val mThemeObserverList = mutableListOf<ThemeObserver>()

        private var sInstance: ThemeEngine? = null

        private var sPreviewColorPrimary: Int? = null
        private var sPreviewColorAccent: Int? = null

        @JvmStatic
        fun initialize(context: Context) {
            if (sInstance == null) {
                sInstance = ThemeEngine(context)
            }
        }

        @JvmStatic
        fun registerObserver(observer: ThemeObserver) {
            mThemeObserverList.add(observer)
        }

        @JvmStatic
        fun unregisterObserver(observer: ThemeObserver) {
            mThemeObserverList.remove(observer)
        }

        /**
         * Shows a color without storing it, so a chooser can repaint the screen behind it while it
         * is open. The stored value is untouched, which is what makes cancelling free. Clear it from
         * clearPreview once the chooser is gone.
         *
         * The color that is stored is never an override, and a chooser opens on it and can be walked
         * back to it. That is not only to save a repaint of every observer. The stored dark primary
         * is its own value while a previewed one is derived from the primary, and out of the box
         * those two are not the same color, so showing the stored primary as an override would move
         * the status bar to a color no screen will have once the chooser is gone.
         *
         * @return whether the screen is now showing something other than what is stored
         */
        @UiThread
        @JvmStatic
        fun previewColorPrimary(colorPrimary: Int): Boolean {
            val instance = sInstance ?: return false
            val showing: Int? = if (colorPrimary == instance.getStoredColorPrimary()) null else colorPrimary
            if (showing != sPreviewColorPrimary) {
                sPreviewColorPrimary = showing
                notifyObservers()
            }
            return sPreviewColorPrimary != null
        }

        @UiThread
        @JvmStatic
        fun previewColorAccent(colorAccent: Int): Boolean {
            val instance = sInstance ?: return false
            val showing: Int? = if (colorAccent == instance.getStoredColorAccent()) null else colorAccent
            if (showing != sPreviewColorAccent) {
                sPreviewColorAccent = showing
                notifyObservers()
            }
            return sPreviewColorAccent != null
        }

        @UiThread
        @JvmStatic
        fun clearPreview() {
            val previewPrimary = sPreviewColorPrimary
            val previewAccent = sPreviewColorAccent
            if (previewPrimary == null && previewAccent == null) {
                return
            }
            // OK stores the color it was previewing, so by the time the chooser is gone the override
            // and the stored value are the same color and dropping it moves nothing on screen.
            val instance = sInstance
            val showsSomethingElse = instance == null ||
                (previewPrimary != null && previewPrimary != instance.getStoredColorPrimary()) ||
                (previewAccent != null && previewAccent != instance.getStoredColorAccent())
            sPreviewColorPrimary = null
            sPreviewColorAccent = null
            if (showsSomethingElse) {
                notifyObservers()
            }
        }

        @UiThread
        @JvmStatic
        fun setColorPrimary(colorPrimary: Int) {
            val instance = sInstance ?: throw RuntimeException("ThemeEngine not initialized!")
            // Against the stored value, never the getter: a preview of this same color is showing
            // while the chooser's OK button is what calls this, and comparing with the getter would
            // read that preview and skip the write.
            if (colorPrimary != instance.getStoredColorPrimary()) {
                instance.mPreferences.edit().putInt(COLOR_PRIMARY, colorPrimary).apply()
                instance.mPreferences.edit().putInt(COLOR_PRIMARY_DARK, Util.darkenColor(colorPrimary)).apply()
                notifyObservers()
            }
        }

        @UiThread
        @JvmStatic
        fun setColorAccent(colorAccent: Int) {
            val instance = sInstance ?: throw RuntimeException("ThemeEngine not initialized!")
            if (colorAccent != instance.getStoredColorAccent()) {
                instance.mPreferences.edit().putInt(COLOR_ACCENT, colorAccent).apply()
                notifyObservers()
            }
        }

        @UiThread
        @JvmStatic
        fun setMode(mode: Mode?) {
            val instance = sInstance
            if (instance != null && mode != null) {
                if (mode != instance.mode) {
                    instance.mPreferences.edit().putInt(MODE, mode.index).apply()
                    notifyObservers()
                }
            } else {
                throw RuntimeException("ThemeEngine not initialized!")
            }
        }

        @UiThread
        private fun notifyObservers() {
            val instance = sInstance ?: return
            for (observer in mThemeObserverList) {
                observer.onThemeChanged(instance)
            }
        }

        @JvmStatic
        fun getTheme(): ITheme {
            return sInstance ?: throw RuntimeException("ThemeEngine not initialized!")
        }

        @JvmStatic
        fun applyTheme(view: View?, propagate: Boolean) {
            val instance = sInstance
            if (view is ThemeConsumer && instance != null) {
                view.onApplyTheme(instance)
            }
            if (propagate && view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    applyTheme(view.getChildAt(i), true)
                }
            }
        }
    }
}
