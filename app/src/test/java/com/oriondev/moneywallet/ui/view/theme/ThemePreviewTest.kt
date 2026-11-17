/*
 * Copyright (c) 2018.
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

import androidx.core.graphics.ColorUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The color a chooser is sitting on is shown without being stored.
 */
@RunWith(RobolectricTestRunner::class)
class ThemePreviewTest {

    private var originalPrimary: Int = 0
    private var originalAccent: Int = 0

    @Before
    fun readTheStoredTheme() {
        ThemeEngine.clearPreview()
        originalPrimary = ThemeEngine.getTheme().colorPrimary
        originalAccent = ThemeEngine.getTheme().colorAccent
    }

    @After
    fun restoreTheStoredTheme() {
        ThemeEngine.clearPreview()
        ThemeEngine.setColorPrimary(originalPrimary)
        ThemeEngine.setColorAccent(originalAccent)
    }

    @Test
    fun aPreviewIsWhatTheThemeReadsBackAndClearingItPutsTheStoredColorsBack() {
        val theme = ThemeEngine.getTheme()
        val previewedPrimary = inverted(originalPrimary)
        val previewedAccent = inverted(originalAccent)

        ThemeEngine.previewColorPrimary(previewedPrimary)
        ThemeEngine.previewColorAccent(previewedAccent)
        assertEquals(previewedPrimary, theme.colorPrimary)
        assertEquals(previewedAccent, theme.colorAccent)
        assertEquals(
            "the status bar follows the previewed primary, not the stored one",
            opaque(Util.darkenColor(previewedPrimary)),
            theme.colorPrimaryDark
        )

        ThemeEngine.clearPreview()
        assertEquals(originalPrimary, theme.colorPrimary)
        assertEquals(originalAccent, theme.colorAccent)
    }

    @Test
    fun aColorStoredWhileItsOwnPreviewIsUpSurvivesThePreviewBeingCleared() {
        val theme = ThemeEngine.getTheme()
        val chosenPrimary = inverted(originalPrimary)
        val chosenAccent = inverted(originalAccent)

        ThemeEngine.previewColorPrimary(chosenPrimary)
        ThemeEngine.setColorPrimary(chosenPrimary)
        ThemeEngine.previewColorAccent(chosenAccent)
        ThemeEngine.setColorAccent(chosenAccent)
        ThemeEngine.clearPreview()

        assertEquals(chosenPrimary, theme.colorPrimary)
        assertEquals(chosenAccent, theme.colorAccent)
        assertEquals(opaque(Util.darkenColor(chosenPrimary)), theme.colorPrimaryDark)
    }

    @Test
    fun theStoredColorIsNeverAnOverride() {
        val theme = ThemeEngine.getTheme()
        val repaints = IntArray(1)
        val observer = ThemeEngine.ThemeObserver { repaints[0]++ }
        ThemeEngine.registerObserver(observer)
        try {
            assertFalse(
                "opening on the stored color previewed it",
                ThemeEngine.previewColorPrimary(originalPrimary)
            )
            assertFalse(ThemeEngine.previewColorAccent(originalAccent))
            assertEquals("previewing what is already stored repainted the screen", 0, repaints[0])

            assertTrue(ThemeEngine.previewColorPrimary(inverted(originalPrimary)))
            assertEquals(1, repaints[0])
            ThemeEngine.previewColorPrimary(inverted(originalPrimary))
            assertEquals("the color that is already showing repainted again", 1, repaints[0])

            assertFalse(
                "walking back to the stored color left an override showing",
                ThemeEngine.previewColorPrimary(originalPrimary)
            )
            assertEquals(originalPrimary, theme.colorPrimary)
        } finally {
            ThemeEngine.unregisterObserver(observer)
        }
    }

    @Test
    fun droppingAPreviewThatWasJustStoredRepaintsNothing() {
        val repaints = IntArray(1)
        val observer = ThemeEngine.ThemeObserver { repaints[0]++ }
        ThemeEngine.previewColorPrimary(inverted(originalPrimary))
        ThemeEngine.setColorPrimary(inverted(originalPrimary))
        ThemeEngine.registerObserver(observer)
        try {
            ThemeEngine.clearPreview()
            assertEquals(0, repaints[0])

            ThemeEngine.previewColorPrimary(originalPrimary)
            ThemeEngine.clearPreview()
            assertEquals(
                "a color that is not stored has to repaint on the way in and on the way out",
                2,
                repaints[0]
            )
        } finally {
            ThemeEngine.unregisterObserver(observer)
        }
    }

    private fun inverted(color: Int): Int {
        return color xor 0x00FFFFFF
    }

    private fun opaque(color: Int): Int {
        return ColorUtils.setAlphaComponent(color, 255)
    }
}
