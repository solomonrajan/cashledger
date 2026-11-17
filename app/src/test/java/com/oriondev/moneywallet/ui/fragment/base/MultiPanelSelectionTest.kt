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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.activity.BackupListActivity
import com.oriondev.moneywallet.ui.adapter.recycler.TransactionCursorAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * A detail panel closes itself through navigateBackSafely after every item delete. That closes
 * the panel and nothing else, while back still ends the selection. On the phone layout the panel
 * is already hidden here, as after a back press made before the delete returned.
 */
@RunWith(RobolectricTestRunner::class)
class MultiPanelSelectionTest {

    @Test
    fun aPanelClosingItselfKeepsTheSelectionAndBackStillEndsIt() {
        checkThePanelCloseKeepsTheSelection()
    }

    @Test
    @Config(qualifiers = "w840dp")
    fun onTheTwoPanelLayoutAPanelClosingItselfKeepsTheSelectionAndBackStillEndsIt() {
        checkThePanelCloseKeepsTheSelection()
    }

    companion object {
        private fun checkThePanelCloseKeepsTheSelection() {
            ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val host = Host()
                    activity.supportFragmentManager.beginTransaction()
                        .add(android.R.id.content, host)
                        .commitNow()
                    val panel = Panel()
                    host.childFragmentManager.beginTransaction()
                        .add(R.id.secondary_panel_container, panel)
                        .commitNow()
                    val forwarder = SelectionForwarder()
                    val adapter = TransactionCursorAdapter(forwarder)
                    forwarder.selectionMode = TransactionSelectionMode(host, host, adapter)
                    adapter.setSelectedIds(longArrayOf(1L))
                    val selectedTitle = host.getString(R.string.title_transactions_selected, 1)
                    assertEquals(selectedTitle, host.getPrimaryToolbar()?.title?.toString().orEmpty())
                    panel.navigateBackSafely()
                    assertEquals(1, adapter.selectedIds.size)
                    assertEquals(selectedTitle, host.getPrimaryToolbar()?.title?.toString().orEmpty())
                    assertTrue(host.navigateBack())
                    assertEquals(0, adapter.selectedIds.size)
                    assertFalse(host.navigateBack())
                }
            }
        }
    }

    class Host : MultiPanelFragment() {

        override fun onCreatePrimaryPanel(inflater: LayoutInflater, primaryPanel: ViewGroup, savedInstanceState: Bundle?) {
            // nothing drawn
        }

        override fun onCreateSecondaryPanel(inflater: LayoutInflater, secondaryPanel: ViewGroup, savedInstanceState: Bundle?) {
            // the case adds its panel itself
        }

        override fun getTitleRes(): Int = R.string.app_name
    }

    class Panel : SecondaryPanelFragment() {

        override fun onCreateHeaderView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
            // nothing drawn
        }

        override fun onCreateBodyView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
            // nothing drawn
        }

        override fun getTitle(): String = ""

        override fun onInflateMenu(): Int = 0

        override fun onMenuItemClick(item: MenuItem): Boolean = false

        override fun onShowItemId(itemId: Long) {
            // nothing drawn
        }
    }

    private class SelectionForwarder : TransactionCursorAdapter.ActionListener {

        var selectionMode: TransactionSelectionMode? = null

        override fun onHeaderClick(startDate: Date?, endDate: Date?) {
            // never called here
        }

        override fun onTransactionClick(id: Long) {
            // never called here
        }

        override fun onSelectionChanged(count: Int) {
            selectionMode?.onSelectionChanged(count)
        }
    }
}
