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

import android.view.View
import androidx.test.core.app.ActivityScenario
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.activity.BackupListActivity
import com.oriondev.moneywallet.utils.Utils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A detail panel emptied on the two panel layout must not come back full screen after rotating to one panel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w840dp")
class MultiPanelRotationTest {

    companion object {
        private const val HOST_TAG = "host"

        private fun checkRotation(
            emptyThePanel: Boolean,
            itemGoneAfterRestore: Boolean,
            primaryVisibility: Int,
            secondaryVisibility: Int
        ) {
            ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val host = MultiPanelSelectionTest.Host()
                    activity.supportFragmentManager.beginTransaction()
                        .add(android.R.id.content, host, HOST_TAG)
                        .commitNow()
                    val panel = MultiPanelSelectionTest.Panel()
                    host.childFragmentManager.beginTransaction()
                        .add(R.id.secondary_panel_container, panel)
                        .commitNow()
                    assertTrue(host.isExtendedLayout())
                    host.showSecondaryPanel()
                    panel.showItemId(5L)
                    if (emptyThePanel) {
                        panel.showItemId(0L)
                    }
                }
                RuntimeEnvironment.setQualifiers("w400dp")
                scenario.recreate()
                scenario.onActivity { activity ->
                    val host = activity.supportFragmentManager.findFragmentByTag(HOST_TAG) as? MultiPanelSelectionTest.Host
                    val hostFragment = host ?: return@onActivity
                    assertFalse(hostFragment.isExtendedLayout())
                    if (itemGoneAfterRestore) {
                        // what an item fragment's loader does when the restored id has no row
                        for (fragment in hostFragment.childFragmentManager.fragments) {
                            (fragment as? SecondaryPanelFragment)?.showItemId(0L)
                        }
                    }
                    val secondaryPanel = Utils.findViewGroupByIds(
                        hostFragment.requireView(),
                        R.id.secondary_panel_frame_layout,
                        R.id.secondary_panel_card_view
                    )
                    assertEquals(primaryVisibility, hostFragment.getPrimaryPanel()?.visibility)
                    assertEquals(secondaryVisibility, secondaryPanel?.visibility)
                }
            }
        }
    }

    @Test
    fun anEmptiedPanelIsNotRestoredFullScreen() {
        checkRotation(true, false, View.VISIBLE, View.GONE)
    }

    @Test
    fun aPanelShowingAnItemIsRestoredFullScreen() {
        checkRotation(false, false, View.GONE, View.VISIBLE)
    }

    @Test
    fun aRestoredPanelWhoseItemIsGoneCloses() {
        checkRotation(false, true, View.VISIBLE, View.GONE)
    }
}
