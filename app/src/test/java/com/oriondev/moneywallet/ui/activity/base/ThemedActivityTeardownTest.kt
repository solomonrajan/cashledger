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

package com.oriondev.moneywallet.ui.activity.base

import androidx.fragment.app.Fragment
import com.oriondev.moneywallet.ui.view.theme.ITheme
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * A color chooser ends its preview as it is torn down, and an activity being destroyed tears its
 * fragments down, so the theme can change inside the destruction of a screen that is in no state to
 * be repainted.
 */
@RunWith(RobolectricTestRunner::class)
class ThemedActivityTeardownTest {

    @After
    fun clearThePreview() {
        ThemeEngine.clearPreview()
    }

    @Test
    fun anActivityIsNotRepaintedByItsOwnFragmentsBeingTornDown() {
        val controller = Robolectric.buildActivity(CountingActivity::class.java).setup()
        val activity = controller.get()
        activity.supportFragmentManager
            .beginTransaction().add(PreviewEndingFragment(), "ending").commitNow()
        val currentPrimary = ThemeEngine.getTheme().colorPrimary
        ThemeEngine.previewColorPrimary(currentPrimary xor 0x00FFFFFF)

        controller.pause().stop().destroy()

        assertEquals("a dying activity was repainted", 0, activity.repaintsWhileDying)
    }

    /** Stands in for the color chooser, which ends its preview from the same point. */
    class PreviewEndingFragment : Fragment() {

        override fun onDestroy() {
            super.onDestroy()
            ThemeEngine.clearPreview()
        }
    }

    class CountingActivity : ThemedActivity() {

        private var mDying = false
        var repaintsWhileDying: Int = 0
            private set

        override fun onDestroy() {
            mDying = true
            super.onDestroy()
        }

        override fun onThemeChanged(theme: ITheme) {
            if (mDying) {
                repaintsWhileDying++
                return
            }
            super.onThemeChanged(theme)
        }
    }
}
