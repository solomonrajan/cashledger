/*
 * Copyright (c) 2018. MoneyWallet
 * Copyright (c) 2026. solomonrajan/CashLedger
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

package com.oriondev.moneywallet.ui.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.activity.base.SinglePanelActivity
import com.oriondev.moneywallet.ui.fragment.single.AboutFragment

class AboutActivity : SinglePanelActivity() {

    override fun onCreatePanelView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val fragmentManager = supportFragmentManager
        val fragment = fragmentManager.findFragmentByTag(FRAGMENT_TAG)
        
        if (fragment == null) {
            fragmentManager.beginTransaction()
                .replace(parent.id, AboutFragment(), FRAGMENT_TAG)
                .commit()
        } else {
            fragmentManager.beginTransaction()
                .show(fragment)
                .commit()
        }
    }

    override fun getActivityTitleRes(): Int = R.string.title_activity_about

    override fun isFloatingActionButtonEnabled(): Boolean = false

    companion object {
        private const val FRAGMENT_TAG = "AboutActivity::Tag::AboutFragment"
    }
}
