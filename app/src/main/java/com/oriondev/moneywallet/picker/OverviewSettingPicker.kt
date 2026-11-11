/*
 * Copyright (c) 2018.
 * Copyright (c) 2026 solomonrajan.
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

package com.oriondev.moneywallet.picker

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.oriondev.moneywallet.model.Group
import com.oriondev.moneywallet.model.OverviewSetting
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.fragment.dialog.OverviewSettingDialog
import com.oriondev.moneywallet.utils.DateUtils
import java.util.Calendar
import java.util.Date

/**
 * Created by andrea on 17/08/18.
 */
class OverviewSettingPicker : Fragment(), OverviewSettingDialog.Callback {

    private var mController: Controller? = null

    private var mOverviewSetting: OverviewSetting? = null

    private var mOverviewSettingDialog: OverviewSettingDialog? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mController = findController(context, checkParent = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            mOverviewSetting = savedInstanceState.readParcelable(SS_OVERVIEW_SETTING)
        } else {
            val startDate: Date
            val endDate: Date
            val calendar = Calendar.getInstance()
            calendar.firstDayOfWeek = Calendar.SUNDAY
            val groupType = PreferenceManager.getCurrentGroupType()
            when (groupType) {
                Group.DAILY -> {
                    // Consider the current week.
                    val firstDayOfWeek = PreferenceManager.getFirstDayOfWeek()
                    val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                    if (currentDayOfWeek < firstDayOfWeek) {
                        calendar.add(Calendar.DAY_OF_MONTH, -7)
                    }
                    calendar.set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    startDate = calendar.time
                    endDate = DateUtils.addDays(calendar, 6)
                }
                Group.WEEKLY -> {
                    // Consider the current month.
                    val firstDayOfMonth = PreferenceManager.getFirstDayOfMonth()
                    val currentDayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
                    if (currentDayOfMonth < firstDayOfMonth) {
                        calendar.add(Calendar.MONTH, -1)
                    }
                    calendar.set(Calendar.DAY_OF_MONTH, firstDayOfMonth)
                    startDate = calendar.time
                    endDate = DateUtils.addMonthAndDay(calendar, 1, -1)
                }
                Group.MONTHLY -> {
                    // Consider the current year.
                    val currentYear = calendar.get(Calendar.YEAR)
                    calendar.set(currentYear, Calendar.JANUARY, 1)
                    startDate = calendar.time
                    calendar.set(currentYear, Calendar.DECEMBER, 31)
                    endDate = calendar.time
                }
                Group.YEARLY -> {
                    // Consider the last three years.
                    val currentYear = calendar.get(Calendar.YEAR)
                    calendar.set(currentYear - 3, Calendar.JANUARY, 1)
                    startDate = calendar.time
                    calendar.set(currentYear, Calendar.DECEMBER, 31)
                    endDate = calendar.time
                }
                else -> {
                    startDate = calendar.time
                    endDate = calendar.time
                }
            }
            mOverviewSetting = OverviewSetting(
                startDate,
                endDate,
                groupType,
                OverviewSetting.CashFlow.NET_INCOMES
            )
        }
        mOverviewSettingDialog = findOrCreateDialog { OverviewSettingDialog.newInstance() }
        mOverviewSettingDialog?.setCallback(this)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(SS_OVERVIEW_SETTING, mOverviewSetting)
    }

    private fun fireCallbackSafely() {
        mController?.onOverviewSettingChanged(tag, mOverviewSetting)
    }

    val currentSettings: OverviewSetting?
        get() = mOverviewSetting

    fun showPicker() {
        mOverviewSettingDialog?.showPicker(childFragmentManager, getDefaultDialogTag(), mOverviewSetting)
    }

    override fun onDetach() {
        super.onDetach()
        mController = null
    }

    override fun onOverviewSettingChanged(overviewSetting: OverviewSetting?) {
        mOverviewSetting = overviewSetting
        fireCallbackSafely()
    }

    interface Controller {
        fun onOverviewSettingChanged(tag: String?, overviewSetting: OverviewSetting?)
    }

    companion object {

        private const val SS_OVERVIEW_SETTING = "OverviewSettingPicker::SavedState::OverviewSetting"

        @JvmStatic
        fun createPicker(
            fragmentManager: FragmentManager,
            tag: String?
        ): OverviewSettingPicker {
            return fragmentManager.findOrCreatePicker(tag) { OverviewSettingPicker() }
        }
    }
}
