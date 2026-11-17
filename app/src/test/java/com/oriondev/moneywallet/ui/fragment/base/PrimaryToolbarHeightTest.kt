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

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.test.core.app.ActivityScenario
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.activity.BackupListActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * On the phone layouts the primary toolbar must wrap its content so a two line Persian title
 * and subtitle both fit. A fixed action bar height leaves the wallet name clipped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp")
class PrimaryToolbarHeightTest {

    @Test
    fun theAppbarWithoutScrollLayoutLetsThePrimaryToolbarWrapItsContent() {
        checkThePrimaryToolbarWrapsItsContent(R.layout.fragment_multi_panel_appbar_without_scroll)
    }

    @Test
    fun theMultiPanelLayoutLetsThePrimaryToolbarWrapItsContent() {
        checkThePrimaryToolbarWrapsItsContent(R.layout.fragment_multi_panel)
    }

    companion object {
        private fun checkThePrimaryToolbarWrapsItsContent(@LayoutRes layoutRes: Int) {
            ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val toolbar = inflate(activity, layoutRes).findViewById<View>(R.id.primary_toolbar)
                    assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, toolbar.layoutParams.height)
                }
            }
        }

        private fun inflate(activity: Activity, @LayoutRes layoutRes: Int): View {
            return LayoutInflater.from(activity).inflate(layoutRes, null)
        }
    }
}
