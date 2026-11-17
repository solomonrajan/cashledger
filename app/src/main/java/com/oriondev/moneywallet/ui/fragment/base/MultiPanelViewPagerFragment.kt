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
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.oriondev.moneywallet.R

/**
 * Created by andrea on 09/02/18.
 */
abstract class MultiPanelViewPagerFragment : MultiPanelAppBarFragment(), ViewPager.OnPageChangeListener {

    private var mTabLayout: TabLayout? = null
    private var mViewPager: ViewPager? = null

    override fun onCreatePrimaryAppBar(
        inflater: LayoutInflater,
        primaryAppBar: ViewGroup,
        savedInstanceState: Bundle?
    ) {
        val view = inflater.inflate(R.layout.view_multi_panel_view_pager_app_bar, primaryAppBar, true)
        mTabLayout = view.findViewById(R.id.tab_layout)
    }

    override fun onCreatePrimaryPanel(
        inflater: LayoutInflater,
        primaryPanel: ViewGroup,
        savedInstanceState: Bundle?
    ) {
        val view = inflater.inflate(R.layout.view_multi_panel_view_pager_body, primaryPanel, true)
        val viewPager: ViewPager = view.findViewById(R.id.view_pager)
        mViewPager = viewPager
        viewPager.offscreenPageLimit = 3
        viewPager.adapter = onCreatePagerAdapter(childFragmentManager)
        viewPager.addOnPageChangeListener(this)
        mTabLayout?.setupWithViewPager(viewPager)
        if (savedInstanceState == null) {
            viewPager.setCurrentItem(getDefaultViewPagerPosition(), false)
        }
    }

    /**
     * Which tab this screen opens on. Overridden where the leftmost tab is not the one people
     * reach for. The tab is preserved across a configuration change or a restore from process
     * death, but not across leaving the screen and coming back: the navigation drawer replaces
     * fragments without a back stack, so returning builds a new one and lands here again.
     */
    protected open fun getDefaultViewPagerPosition(): Int = 0

    override fun onCreateSecondaryPanel(
        inflater: LayoutInflater,
        secondaryPanel: ViewGroup,
        savedInstanceState: Bundle?
    ) {
    }

    protected abstract fun onCreatePagerAdapter(fragmentManager: FragmentManager): PagerAdapter

    protected open fun getViewPagerPosition(): Int = mViewPager?.currentItem ?: 0

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
    }

    override fun onPageSelected(position: Int) {
    }

    override fun onPageScrollStateChanged(state: Int) {
    }

    override fun onDestroy() {
        super.onDestroy()
        mViewPager?.removeOnPageChangeListener(this)
    }
}
