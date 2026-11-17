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

package com.oriondev.moneywallet.ui.widget

import android.content.Context
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@LargeTest
class WalletWidgetPreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        clear()
    }

    @After
    fun tearDown() {
        clear()
    }

    private fun clear() {
        WalletWidgetPreferences.delete(context, intArrayOf(WIDGET_A, WIDGET_B))
    }

    @Test
    @Throws(Exception::class)
    fun anUnknownWidgetHasNoWallet() {
        assertEquals(WalletWidgetPreferences.NO_WALLET, WalletWidgetPreferences.getWallet(context, WIDGET_A))
        assertFalse(WalletWidgetPreferences.isShowWhenLocked(context, WIDGET_A))
    }

    @Test
    @Throws(Exception::class)
    fun twoWidgetsKeepSeparateWallets() {
        WalletWidgetPreferences.save(context, WIDGET_A, 11L, false)
        WalletWidgetPreferences.save(context, WIDGET_B, 22L, true)
        assertEquals(11L, WalletWidgetPreferences.getWallet(context, WIDGET_A))
        assertEquals(22L, WalletWidgetPreferences.getWallet(context, WIDGET_B))
        assertFalse(WalletWidgetPreferences.isShowWhenLocked(context, WIDGET_A))
        assertTrue(WalletWidgetPreferences.isShowWhenLocked(context, WIDGET_B))
    }

    @Test
    @Throws(Exception::class)
    fun savingAgainReplacesBothValues() {
        WalletWidgetPreferences.save(context, WIDGET_A, 11L, true)
        WalletWidgetPreferences.save(context, WIDGET_A, 33L, false)
        assertEquals(33L, WalletWidgetPreferences.getWallet(context, WIDGET_A))
        assertFalse(WalletWidgetPreferences.isShowWhenLocked(context, WIDGET_A))
    }

    @Test
    @Throws(Exception::class)
    fun deletingOneWidgetLeavesTheOther() {
        WalletWidgetPreferences.save(context, WIDGET_A, 11L, true)
        WalletWidgetPreferences.save(context, WIDGET_B, 22L, true)
        WalletWidgetPreferences.delete(context, intArrayOf(WIDGET_A))
        assertEquals(WalletWidgetPreferences.NO_WALLET, WalletWidgetPreferences.getWallet(context, WIDGET_A))
        assertFalse(WalletWidgetPreferences.isShowWhenLocked(context, WIDGET_A))
        assertEquals(22L, WalletWidgetPreferences.getWallet(context, WIDGET_B))
        assertTrue(WalletWidgetPreferences.isShowWhenLocked(context, WIDGET_B))
    }

    companion object {
        private const val WIDGET_A = 900001
        private const val WIDGET_B = 900002
    }
}
