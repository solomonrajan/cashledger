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

package com.oriondev.moneywallet.ui.fragment.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup

/**
 * Created by andrea on 03/04/18.
 */
abstract class MultiPanelCursorListItemFragment : MultiPanelCursorListFragment() {

    private var mSecondaryFragment: SecondaryPanelFragment? = null

    override fun onCreateSecondaryPanel(
        inflater: LayoutInflater,
        secondaryPanel: ViewGroup,
        savedInstanceState: Bundle?
    ) {
        val fragmentManager = childFragmentManager
        val fragmentTag = getSecondaryFragmentTag()
        val existingFragment = fragmentManager.findFragmentByTag(fragmentTag) as? SecondaryPanelFragment
        if (existingFragment != null) {
            mSecondaryFragment = existingFragment
            fragmentManager.beginTransaction().show(existingFragment).commitNow()
        } else {
            val newFragment = onCreateSecondaryPanel()
            mSecondaryFragment = newFragment
            fragmentManager.beginTransaction()
                .replace(secondaryPanel.id, newFragment, fragmentTag)
                .commitNow()
        }
    }

    protected abstract fun onCreateSecondaryPanel(): SecondaryPanelFragment

    protected abstract fun getSecondaryFragmentTag(): String

    open fun showItemId(id: Long) {
        mSecondaryFragment?.showItemId(id)
    }
}
