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

package com.oriondev.moneywallet.ui.fragment.single

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.background.PeriodDetailSummaryLoader
import com.oriondev.moneywallet.model.Money
import com.oriondev.moneywallet.model.PeriodDetailSummaryData
import com.oriondev.moneywallet.model.PeriodMoney
import com.oriondev.moneywallet.storage.preference.CurrentWalletController
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.activity.TransactionListActivity
import com.oriondev.moneywallet.ui.adapter.pager.BarChartViewPagerAdapter
import com.oriondev.moneywallet.ui.adapter.recycler.PeriodDetailSummaryAdapter
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.MoneyFormatter
import java.util.Date

/**
 * Created by andrea on 01/05/18.
 */
class PeriodDetailSummaryFragment : Fragment(), PeriodDetailSummaryAdapter.Controller, LoaderManager.LoaderCallbacks<PeriodDetailSummaryData>, CurrentWalletController {

    private var mHeaderLeftTextView: TextView? = null
    private var mHeaderRightTextView: TextView? = null
    private var mRecyclerView: RecyclerView? = null

    private var mBarChartViewPagerAdapter: BarChartViewPagerAdapter? = null
    private var mChartViewPagerIndicator: View? = null
    private var mRecyclerViewAdapter: PeriodDetailSummaryAdapter? = null

    private var mStartDate: Date? = null
    private var mEndDate: Date? = null

    private val mMoneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()

    private var mBroadcastReceiver: BroadcastReceiver? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mBroadcastReceiver = PreferenceManager.registerCurrentWalletObserver(context, this)
    }

    override fun onDetach() {
        val receiver = mBroadcastReceiver
        val act = activity
        if (receiver != null && act != null) {
            PreferenceManager.unregisterCurrentWalletObserver(act, receiver)
        }
        super.onDetach()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.layout_panel_period_detail, container, false)
        mHeaderLeftTextView = view.findViewById(R.id.left_text_view)
        mHeaderRightTextView = view.findViewById(R.id.right_text_view)
        val barChartViewPager: ViewPager = view.findViewById(R.id.chart_view_pager)
        mChartViewPagerIndicator = view.findViewById(R.id.view_pager_indicator)
        mRecyclerView = view.findViewById(R.id.recycler_view)

        val arguments = arguments ?: throw IllegalStateException("The fragment must be initialized using the newInstance() method")
        mStartDate = arguments.getSerializable(ARG_START_DATE) as? Date
        mEndDate = arguments.getSerializable(ARG_END_DATE) as? Date

        val pagerAdapter = BarChartViewPagerAdapter()
        mBarChartViewPagerAdapter = pagerAdapter
        barChartViewPager.adapter = pagerAdapter

        val recyclerAdapter = PeriodDetailSummaryAdapter(this)
        mRecyclerViewAdapter = recyclerAdapter
        mRecyclerView?.layoutManager = LinearLayoutManager(activity)
        mRecyclerView?.isNestedScrollingEnabled = false
        mRecyclerView?.adapter = recyclerAdapter
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mHeaderLeftTextView?.setText(R.string.hint_net_incomes)
        LoaderManager.getInstance(this).restartLoader(LOADER_FRAGMENT_DATA, null, this)
    }

    override fun onPeriodClick(periodMoney: PeriodMoney) {
        val intent = Intent(activity, TransactionListActivity::class.java)
        intent.putExtra(TransactionListActivity.START_DATE, periodMoney.startDate)
        intent.putExtra(TransactionListActivity.END_DATE, periodMoney.endDate)
        startActivity(intent)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<PeriodDetailSummaryData> {
        val start = mStartDate ?: Date()
        val end = mEndDate ?: Date()
        val daysBetween = DateUtils.getDaysBetween(start, end)
        val groupType = when {
            daysBetween == 0 -> PeriodDetailSummaryLoader.GROUP_BY_HOUR
            daysBetween in 1..7 -> PeriodDetailSummaryLoader.GROUP_BY_DAY
            daysBetween in 8..31 -> PeriodDetailSummaryLoader.GROUP_BY_WEEK
            daysBetween in 32..365 -> PeriodDetailSummaryLoader.GROUP_BY_MONTH
            else -> PeriodDetailSummaryLoader.GROUP_BY_YEAR
        }
        return PeriodDetailSummaryLoader(requireContext(), start, end, groupType)
    }

    override fun onLoadFinished(loader: Loader<PeriodDetailSummaryData>, data: PeriodDetailSummaryData?) {
        val rightTextView = mHeaderRightTextView
        if (data != null) {
            if (rightTextView != null) {
                mMoneyFormatter.applyTinted(rightTextView, data.netIncomes)
            }
            mBarChartViewPagerAdapter?.setData(data)
            mRecyclerViewAdapter?.setData(data)
            mChartViewPagerIndicator?.visibility = if (data.getChartCount() > 1) View.VISIBLE else View.GONE
            mRecyclerView?.visibility = View.VISIBLE
        } else {
            if (rightTextView != null) {
                mMoneyFormatter.applyNotTinted(rightTextView, Money.empty())
            }
            mBarChartViewPagerAdapter?.setData(null)
            mRecyclerViewAdapter?.setData(null)
            mChartViewPagerIndicator?.visibility = View.GONE
            mRecyclerView?.visibility = View.GONE
        }
    }

    override fun onLoaderReset(loader: Loader<PeriodDetailSummaryData>) {
        // nothing to release
    }

    override fun onCurrentWalletChanged(walletId: Long) {
        LoaderManager.getInstance(this).restartLoader(LOADER_FRAGMENT_DATA, null, this)
    }

    companion object {
        private const val LOADER_FRAGMENT_DATA = 3650

        private const val ARG_START_DATE = "PeriodDetailSummaryFragment::Arguments::StartDate"
        private const val ARG_END_DATE = "PeriodDetailSummaryFragment::Arguments::EndDate"

        @JvmStatic
        fun newInstance(start: Date?, end: Date?): PeriodDetailSummaryFragment {
            val arguments = Bundle()
            arguments.putSerializable(ARG_START_DATE, start)
            arguments.putSerializable(ARG_END_DATE, end)
            val fragment = PeriodDetailSummaryFragment()
            fragment.arguments = arguments
            return fragment
        }
    }
}
