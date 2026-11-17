/*
 * Copyright (c) 2026.
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

package com.oriondev.moneywallet.ui.fragment.base

import android.os.Looper
import android.util.TypedValue
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.activity.BackupListActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * On the wide layouts the primary panel card overlaps the lower part of the app bar, but never the
 * toolbar. A title and subtitle in Persian make the toolbar taller than the action bar, and the card
 * must still start below it, and so it must when the status bar pushes the toolbar down. A toolbar
 * no taller than the action bar leaves the card one action bar below the toolbar's top.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w600dp")
class PanelCardBelowToolbarTest {

    companion object {
        private const val STATUS_BAR = 100

        /**
         * Hands the app bar a status bar, which is where the insets land on a device, and lays the
         * screen out again. Returns the app bar.
         */
        private fun applyStatusBar(root: View): View {
            val appBar = root.findViewById<View>(R.id.primary_app_bar_container)
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, STATUS_BAR, 0, 0))
                .build()
            ViewCompat.dispatchApplyWindowInsets(appBar, insets)
            appBar.requestLayout()
            shadowOf(Looper.getMainLooper()).idle()
            return appBar
        }

        private fun addHost(activity: BackupListActivity): View {
            val host = MultiPanelSelectionTest.Host()
            activity.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, host)
                .commitNow()
            return host.requireView()
        }

        private fun actionBarSize(activity: BackupListActivity): Int {
            val value = TypedValue()
            activity.theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, value, true)
            return TypedValue.complexToDimensionPixelSize(value.data, activity.resources.displayMetrics)
        }
    }

    @Test
    fun aToolbarNoTallerThanTheActionBarLeavesTheCardWhereItWas() {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = addHost(activity)
                val toolbar = root.findViewById<Toolbar>(R.id.primary_toolbar)
                // Robolectric's font metrics make even a one line title plus its margins 49px against an
                // action bar of 48, so the text goes and the toolbar keeps the action bar height.
                toolbar.title = null
                toolbar.subtitle = null
                shadowOf(Looper.getMainLooper()).idle()
                val appBar = applyStatusBar(root)
                val card = root.findViewById<View>(R.id.primary_panel_body_container_card_view)
                assertEquals(STATUS_BAR, toolbar.top)
                assertEquals(actionBarSize(activity), toolbar.height)
                assertEquals(appBar.top + toolbar.top + actionBarSize(activity), card.top)
            }
        }
    }

    @Test
    fun aTallToolbarPushesTheCardBelowIt() {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = addHost(activity)
                shadowOf(Looper.getMainLooper()).idle()
                val toolbar = root.findViewById<View>(R.id.primary_toolbar)
                toolbar.minimumHeight = 3 * actionBarSize(activity)
                toolbar.requestLayout()
                shadowOf(Looper.getMainLooper()).idle()
                val appBar = root.findViewById<View>(R.id.primary_app_bar_container)
                val card = root.findViewById<View>(R.id.primary_panel_body_container_card_view)
                val toolbarBottom = appBar.top + toolbar.bottom
                assertTrue(
                    "card top " + card.top + " is above the toolbar bottom " + toolbarBottom +
                        " (toolbar " + toolbar.top + ".." + toolbar.bottom + ")",
                    card.top >= toolbarBottom
                )
            }
        }
    }

    @Test
    fun theStatusBarPushesTheCardDownWithTheToolbar() {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = addHost(activity)
                shadowOf(Looper.getMainLooper()).idle()
                val appBar = applyStatusBar(root)
                val toolbar = root.findViewById<View>(R.id.primary_toolbar)
                val card = root.findViewById<View>(R.id.primary_panel_body_container_card_view)
                assertEquals(STATUS_BAR, toolbar.top)
                val toolbarBottom = appBar.top + toolbar.bottom
                assertTrue(
                    "card top " + card.top + " is above the toolbar bottom " + toolbarBottom +
                        " (app bar " + appBar.top + ".." + appBar.bottom +
                        ", toolbar " + toolbar.top + ".." + toolbar.bottom + ")",
                    card.top >= toolbarBottom
                )
            }
        }
    }
}
