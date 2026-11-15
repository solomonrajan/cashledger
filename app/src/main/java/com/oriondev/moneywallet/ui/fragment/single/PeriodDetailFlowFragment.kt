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
import com.oriondev.moneywallet.background.PeriodDetailFlowLoader
import com.oriondev.moneywallet.model.Money
import com.oriondev.moneywallet.model.PeriodDetailFlowData
import com.oriondev.moneywallet.storage.preference.CurrentWalletController
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.activity.TransactionListActivity
import com.oriondev.moneywallet.ui.adapter.pager.PieChartViewPagerAdapter
import com.oriondev.moneywallet.ui.adapter.recycler.PeriodDetailFlowAdapter
import com.oriondev.moneywallet.utils.MoneyFormatter
import java.util.Date

/**
 * Created by andrea on 01/05/18.
 */
class PeriodDetailFlowFragment : Fragment(), PeriodDetailFlowAdapter.Controller, LoaderManager.LoaderCallbacks<PeriodDetailFlowData>, CurrentWalletController {

    private var mHeaderLeftTextView: TextView? = null
    private var mHeaderRightTextView: TextView? = null
    private var mRecyclerView: RecyclerView? = null

    private var mPieChartViewPagerAdapter: PieChartViewPagerAdapter? = null
    private var mPieChartViewPagerIndicator: View? = null
    private var mRecyclerViewAdapter: PeriodDetailFlowAdapter? = null

    private var mStartDate: Date? = null
    private var mEndDate: Date? = null
    private var mIncomes: Boolean = false

    private val mMoneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()

    private var mBroadcastReceiver: BroadcastReceiver? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mBroadcastReceiver = PreferenceManager.registerCurrentWalletObserver(context, this)
    }

    override fun onDetach() {
        PreferenceManager.unregisterCurrentWalletObserver(activity, mBroadcastReceiver)
        super.onDetach()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.layout_panel_period_detail, container, false)
        mHeaderLeftTextView = view.findViewById(R.id.left_text_view)
        mHeaderRightTextView = view.findViewById(R.id.right_text_view)
        val pieChartViewPager: ViewPager = view.findViewById(R.id.chart_view_pager)
        mPieChartViewPagerIndicator = view.findViewById(R.id.view_pager_indicator)
        mRecyclerView = view.findViewById(R.id.recycler_view)

        val arguments = arguments ?: throw IllegalStateException("The fragment must be initialized using the newInstance() method")
        mStartDate = arguments.getSerializable(ARG_START_DATE) as? Date
        mEndDate = arguments.getSerializable(ARG_END_DATE) as? Date
        mIncomes = arguments.getBoolean(ARG_INCOMES)

        val pagerAdapter = PieChartViewPagerAdapter()
        mPieChartViewPagerAdapter = pagerAdapter
        pieChartViewPager.adapter = pagerAdapter

        val recyclerAdapter = PeriodDetailFlowAdapter(this, mIncomes)
        mRecyclerViewAdapter = recyclerAdapter
        mRecyclerView?.layoutManager = LinearLayoutManager(activity)
        mRecyclerView?.isNestedScrollingEnabled = false
        mRecyclerView?.adapter = recyclerAdapter

        if (savedInstanceState != null) {
            val expanded = savedInstanceState.getLongArray(STATE_EXPANDED)
            if (expanded != null) {
                recyclerAdapter.setExpandedCategories(expanded)
            }
        }
        return view
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val recyclerAdapter = mRecyclerViewAdapter
        if (recyclerAdapter != null) {
            outState.putLongArray(STATE_EXPANDED, recyclerAdapter.getExpandedCategories())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mHeaderLeftTextView?.setText(if (mIncomes) R.string.hint_incomes else R.string.hint_expenses)
        LoaderManager.getInstance(this).restartLoader(LOADER_FRAGMENT_DATA, null, this)
    }

    override fun onCategoryClick(id: Long) {
        val intent = Intent(activity, TransactionListActivity::class.java)
        intent.putExtra(TransactionListActivity.CATEGORY_ID, id)
        intent.putExtra(TransactionListActivity.START_DATE, mStartDate)
        intent.putExtra(TransactionListActivity.END_DATE, mEndDate)
        startActivity(intent)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<PeriodDetailFlowData> {
        return PeriodDetailFlowLoader(requireContext(), mStartDate, mEndDate, mIncomes)
    }

    override fun onLoadFinished(loader: Loader<PeriodDetailFlowData>, data: PeriodDetailFlowData?) {
        val rightTextView = mHeaderRightTextView
        if (data != null) {
            if (rightTextView != null) {
                if (mIncomes) {
                    mMoneyFormatter.applyTintedIncome(rightTextView, data.totalMoney)
                } else {
                    mMoneyFormatter.applyTintedExpense(rightTextView, data.totalMoney)
                }
            }
            mPieChartViewPagerAdapter?.setData(data)
            mRecyclerViewAdapter?.setData(data)
            mPieChartViewPagerIndicator?.visibility = if (data.chartCount > 1) View.VISIBLE else View.GONE
            mRecyclerView?.visibility = View.VISIBLE
        } else {
            if (rightTextView != null) {
                mMoneyFormatter.applyNotTinted(rightTextView, Money.empty())
            }
            mPieChartViewPagerAdapter?.setData(null)
            mRecyclerViewAdapter?.setData(null)
            mPieChartViewPagerIndicator?.visibility = View.GONE
            mRecyclerView?.visibility = View.GONE
        }
    }

    override fun onLoaderReset(loader: Loader<PeriodDetailFlowData>) {
        // nothing to release
    }

    override fun onCurrentWalletChanged(walletId: Long) {
        LoaderManager.getInstance(this).restartLoader(LOADER_FRAGMENT_DATA, null, this)
    }

    companion object {
        private const val LOADER_FRAGMENT_DATA = 3648

        private const val ARG_START_DATE = "PeriodDetailFlowFragment::Arguments::StartDate"
        private const val ARG_END_DATE = "PeriodDetailFlowFragment::Arguments::EndDate"
        private const val ARG_INCOMES = "PeriodDetailFlowFragment::Arguments::Incomes"

        private const val STATE_EXPANDED = "PeriodDetailFlowFragment::State::Expanded"

        @JvmStatic
        fun newInstance(start: Date?, end: Date?, incomes: Boolean): PeriodDetailFlowFragment {
            val arguments = Bundle()
            arguments.putSerializable(ARG_START_DATE, start)
            arguments.putSerializable(ARG_END_DATE, end)
            arguments.putBoolean(ARG_INCOMES, incomes)
            val fragment = PeriodDetailFlowFragment()
            fragment.arguments = arguments
            return fragment
        }
    }
}
