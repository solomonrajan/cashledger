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

package com.oriondev.moneywallet.ui.view.text

import android.content.res.Configuration
import android.content.res.Resources
import android.view.View
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Regression coverage for issue #174.
 */
class UtilsTest {

    private fun resourcesWithLayoutDirection(layoutDirection: Int): Resources {
        val configuration = mock(Configuration::class.java)
        `when`(configuration.layoutDirection).thenReturn(layoutDirection)
        val resources = mock(Resources::class.java)
        `when`(resources.configuration).thenReturn(configuration)
        return resources
    }

    @Test
    fun rightToLeftConfigurationIsRightToLeft() {
        assertTrue(Utils.isRtl(resourcesWithLayoutDirection(View.LAYOUT_DIRECTION_RTL)))
    }

    @Test
    fun leftToRightConfigurationIsNotRightToLeft() {
        assertFalse(Utils.isRtl(resourcesWithLayoutDirection(View.LAYOUT_DIRECTION_LTR)))
    }
}
