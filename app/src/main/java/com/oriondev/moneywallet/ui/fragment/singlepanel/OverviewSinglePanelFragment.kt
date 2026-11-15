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

package com.oriondev.moneywallet.ui.fragment.singlepanel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentManager
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.background.OverviewDataLoader
import com.oriondev.moneywallet.model.OverviewData
import com.oriondev.moneywallet.model.OverviewSetting
import com.oriondev.moneywallet.model.PeriodMoney
import com.oriondev.moneywallet.picker.OverviewSettingPicker
import com.oriondev.moneywallet.storage.preference.CurrentWalletController
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.activity.PeriodDetailActivity
import com.oriondev.moneywallet.ui.adapter.pager.OverviewChartViewPagerAdapter
import com.oriondev.moneywallet.ui.adapter.recycler.OverviewItemAdapter
import com.oriondev.moneywallet.ui.fragment.base.SinglePanelFragment

/**
 * Created by andrea on 17/08/18.
 */
class OverviewSinglePanelFragment : SinglePanelFragment(), OverviewSettingPicker.Controller, LoaderManager.LoaderCallbacks<OverviewData>, OverviewItemAdapter.Controller, CurrentWalletController {

    private var mViewPagerAdapter: OverviewChartViewPagerAdapter? = null
    private var mRecyclerViewAdapter: OverviewItemAdapter? = null

    private var mOverviewSettingPicker: OverviewSettingPicker? = null

    private var mBroadcastReceiver: BroadcastReceiver? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mBroadcastReceiver = PreferenceManager.registerCurrentWalletObserver(context, this)
    }

    override fun onDetach() {
        PreferenceManager.unregisterCurrentWalletObserver(activity, mBroadcastReceiver)
        super.onDetach()
    }

    override fun onCreatePanelView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_panel_overview, parent, true)
        val viewPager: ViewPager = view.findViewById(R.id.chart_view_pager)
        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        // initialize adapters
        val pagerAdapter = OverviewChartViewPagerAdapter()
        mViewPagerAdapter = pagerAdapter
        val itemAdapter = OverviewItemAdapter(this)
        mRecyclerViewAdapter = itemAdapter
        // attach adapters
        viewPager.adapter = pagerAdapter
        recyclerView.adapter = itemAdapter
        // initialize picker
        val fragmentManager: FragmentManager = childFragmentManager
        mOverviewSettingPicker = OverviewSettingPicker.createPicker(fragmentManager, TAG_SETTING_PICKER)
    }

    @StringRes
    override fun getTitleRes(): Int {
        return R.string.menu_overview
    }

    override fun showsCurrentWallet(): Boolean {
        return true
    }

    @MenuRes
    override fun onInflateMenu(): Int {
        return R.menu.menu_overview_fragment
    }

    override fun isFloatingActionButtonEnabled(): Boolean {
        return false
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.itemId == R.id.advanced_settings) {
            mOverviewSettingPicker?.showPicker()
        }
        return false
    }

    override fun onOverviewSettingChanged(tag: String?, overviewSetting: OverviewSetting?) {
        LoaderManager.getInstance(this).restartLoader(LOADER_OVERVIEW_DATA, null, this)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<OverviewData> {
        val setting = mOverviewSettingPicker?.getCurrentSettings()
        return OverviewDataLoader(requireContext(), setting)
    }

    override fun onLoadFinished(loader: Loader<OverviewData>, data: OverviewData?) {
        mViewPagerAdapter?.setData(data)
        mRecyclerViewAdapter?.setData(data)
    }

    override fun onLoaderReset(loader: Loader<OverviewData>) {
        // nothing to release
    }

    override fun onPeriodClick(periodMoney: PeriodMoney) {
        val intent = Intent(activity, PeriodDetailActivity::class.java)
        intent.putExtra(PeriodDetailActivity.START_DATE, periodMoney.startDate)
        intent.putExtra(PeriodDetailActivity.END_DATE, periodMoney.endDate)
        startActivity(intent)
    }

    override fun onCurrentWalletChanged(walletId: Long) {
        LoaderManager.getInstance(this).restartLoader(LOADER_OVERVIEW_DATA, null, this)
    }

    companion object {
        private const val LOADER_OVERVIEW_DATA = 34848
        private const val TAG_SETTING_PICKER = "OverviewSinglePanelFragment::Tag::OverviewSettingPicker"
    }
}
