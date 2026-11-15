package com.oriondev.moneywallet.ui.activity.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.oriondev.moneywallet.R

abstract class SinglePanelViewPagerActivity :
    SinglePanelAppBarActivity(),
    ViewPager.OnPageChangeListener {

    private var tabLayout: TabLayout? = null
    private var viewPager: ViewPager? = null

    override fun onCreateHeaderView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?
    ) {
        val view = inflater.inflate(R.layout.view_multi_panel_view_pager_app_bar, parent, true)
        tabLayout = view.findViewById(R.id.tab_layout)
    }

    override fun onCreatePanelView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?
    ) {
        val view = inflater.inflate(R.layout.view_multi_panel_view_pager_body, parent, true)
        val vp = view.findViewById<ViewPager>(R.id.view_pager)
        viewPager = vp
        // setup the view pager and the tab layout
        vp.offscreenPageLimit = 3
        vp.adapter = onCreatePagerAdapter(supportFragmentManager)
        vp.addOnPageChangeListener(this)
        tabLayout?.setupWithViewPager(vp)
    }

    protected abstract fun onCreatePagerAdapter(fragmentManager: FragmentManager): PagerAdapter

    protected open fun setViewPagerPosition(position: Int) {
        viewPager?.currentItem = position
    }

    protected open fun getViewPagerPosition(): Int {
        return viewPager?.currentItem ?: 0
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
    }

    override fun onPageSelected(position: Int) {
    }

    override fun onPageScrollStateChanged(state: Int) {
    }

    override fun onDestroy() {
        super.onDestroy()
        viewPager?.removeOnPageChangeListener(this)
    }
}
