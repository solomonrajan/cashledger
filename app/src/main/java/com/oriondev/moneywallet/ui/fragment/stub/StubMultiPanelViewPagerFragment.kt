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

package com.oriondev.moneywallet.ui.fragment.stub

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.PagerAdapter
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelViewPagerFragment

/**
 * Created by andrea on 17/01/18.
 */
open class StubMultiPanelViewPagerFragment : MultiPanelViewPagerFragment() {

    override fun onCreatePagerAdapter(fragmentManager: FragmentManager): PagerAdapter {
        return object : FragmentPagerAdapter(childFragmentManager) {
            override fun getItem(position: Int): Fragment {
                return StubListFragment()
            }

            override fun getCount(): Int = 3

            override fun getPageTitle(position: Int): CharSequence {
                return "Stub $position"
            }
        }
    }

    override fun getTitleRes(): Int = R.string.app_name
}
