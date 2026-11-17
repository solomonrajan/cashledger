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
abstract class MultiPanelViewPagerMultiItemFragment : MultiPanelViewPagerFragment() {

    companion object {
        private const val SS_INDEX = "MultiPanelViewPagerMultiItemFragment::SavedState::CurrentIndex"
    }

    private var mContainerId: Int = 0
    private var mIndex: Int = 0
    private var mSecondaryFragment: SecondaryPanelFragment? = null

    override fun onCreateSecondaryPanel(
        inflater: LayoutInflater,
        secondaryPanel: ViewGroup,
        savedInstanceState: Bundle?
    ) {
        mContainerId = secondaryPanel.id
        if (savedInstanceState != null) {
            replaceSecondaryFragment(savedInstanceState.getInt(SS_INDEX, 0))
        } else {
            replaceSecondaryFragment(0)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(SS_INDEX, mIndex)
    }

    protected abstract fun onCreateSecondaryPanel(index: Int): SecondaryPanelFragment

    protected abstract fun getSecondaryFragmentTag(index: Int): String

    open fun showItemId(index: Int, id: Long) {
        if (index != mIndex) {
            replaceSecondaryFragment(index)
        }
        mSecondaryFragment?.showItemId(id)
        showSecondaryPanel()
    }

    private fun replaceSecondaryFragment(index: Int) {
        val tag = getSecondaryFragmentTag(index)
        val fragmentManager = childFragmentManager
        val existingFragment = fragmentManager.findFragmentByTag(tag) as? SecondaryPanelFragment
        if (existingFragment != null) {
            mSecondaryFragment = existingFragment
            fragmentManager.beginTransaction().show(existingFragment).commitNow()
        } else {
            val newFragment = onCreateSecondaryPanel(index)
            mSecondaryFragment = newFragment
            fragmentManager.beginTransaction()
                .replace(mContainerId, newFragment, tag)
                .commitNow()
        }
        mIndex = index
    }
}
