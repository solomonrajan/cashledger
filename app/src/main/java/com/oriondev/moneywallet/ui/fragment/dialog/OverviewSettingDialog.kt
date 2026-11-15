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

package com.oriondev.moneywallet.ui.fragment.dialog

import android.app.Dialog
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.Group
import com.oriondev.moneywallet.model.OverviewSetting
import com.oriondev.moneywallet.picker.CategoryPicker
import com.oriondev.moneywallet.picker.DateTimePicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.view.text.MaterialEditText
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.ui.view.theme.ThemedSpinner
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.IconLoader
import java.util.Date

/**
 * Created by andrea on 17/08/18.
 */
class OverviewSettingDialog : DialogFragment(), DateTimePicker.Controller, CategoryPicker.Controller {

    private var mCallback: Callback? = null
    private var mOverviewSetting: OverviewSetting? = null

    private var mStartDateSpinner: MaterialEditText? = null
    private var mEndDateSpinner: MaterialEditText? = null
    private var mGroupTypeSpinner: ThemedSpinner? = null
    private var mOverviewTypeSpinner: ThemedSpinner? = null
    private var mCashFlowSpinner: ThemedSpinner? = null
    private var mCategorySpinner: MaterialEditText? = null

    private var mStartDatePicker: DateTimePicker? = null
    private var mEndDatePicker: DateTimePicker? = null
    private var mCategoryPicker: CategoryPicker? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val currentActivity = activity ?: return super.onCreateDialog(savedInstanceState)
        if (savedInstanceState != null) {
            mOverviewSetting = savedInstanceState.getParcelable(SS_OVERVIEW_SETTING)
        }
        val setting = mOverviewSetting ?: OverviewSetting(Date(), Date(), Group.DAILY, OverviewSetting.CashFlow.NET_INCOMES)
        mOverviewSetting = setting

        // create sub-pickers
        val childFm = childFragmentManager
        mStartDatePicker = DateTimePicker.createPicker(childFm, TAG_START_DATE_PICKER, setting.startDate)
        mEndDatePicker = DateTimePicker.createPicker(childFm, TAG_END_DATE_PICKER, setting.endDate)
        mCategoryPicker = CategoryPicker.createPicker(childFm, TAG_CATEGORY_PICKER, getFirstAvailableCategory(currentActivity))

        // create the dialog
        val view = ThemedDialog.inflateScrollableView(currentActivity, R.layout.dialog_overview_setting_picker)
        val dialog = ThemedDialog.buildMaterialDialog(currentActivity)
            .setTitle(R.string.dialog_overview_setting_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val currentSetting = getCurrentOverviewSetting()
                mOverviewSetting = currentSetting
                mCallback?.onOverviewSettingChanged(currentSetting)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        mStartDateSpinner = view.findViewById(R.id.start_date_spinner)
        mEndDateSpinner = view.findViewById(R.id.end_date_spinner)
        mGroupTypeSpinner = view.findViewById(R.id.group_type_spinner)
        mOverviewTypeSpinner = view.findViewById(R.id.overview_type_spinner)
        mCashFlowSpinner = view.findViewById(R.id.cash_flow_spinner)
        mCategorySpinner = view.findViewById(R.id.category_spinner)

        mStartDateSpinner?.setTextViewMode(true)
        mEndDateSpinner?.setTextViewMode(true)
        mCategorySpinner?.setTextViewMode(true)

        mGroupTypeSpinner?.setAdapter(
            ThemedSpinner.Adapter(
                currentActivity,
                getString(R.string.spinner_item_group_type_daily),
                getString(R.string.spinner_item_group_type_weekly),
                getString(R.string.spinner_item_group_type_monthly),
                getString(R.string.spinner_item_group_type_yearly)
            )
        )
        mOverviewTypeSpinner?.setAdapter(
            ThemedSpinner.Adapter(
                currentActivity,
                getString(R.string.spinner_item_type_cash_flow),
                getString(R.string.spinner_item_type_category)
            )
        )
        mCashFlowSpinner?.setAdapter(
            ThemedSpinner.Adapter(
                currentActivity,
                getString(R.string.spinner_item_cash_flow_incomes),
                getString(R.string.spinner_item_cash_flow_expenses),
                getString(R.string.spinner_item_cash_flow_net_incomes)
            )
        )

        mStartDateSpinner?.setOnClickListener {
            mStartDatePicker?.showDatePicker()
        }
        mEndDateSpinner?.setOnClickListener {
            mEndDatePicker?.showDatePicker()
        }
        mOverviewTypeSpinner?.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                mCashFlowSpinner?.visibility = if (position == 0) View.VISIBLE else View.GONE
                mCategorySpinner?.visibility = if (position == 1) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        })
        mCategorySpinner?.setOnClickListener {
            mCategoryPicker?.showPicker(true, true)
        }

        when (setting.groupType) {
            Group.DAILY -> mGroupTypeSpinner?.setSelection(0)
            Group.WEEKLY -> mGroupTypeSpinner?.setSelection(1)
            Group.MONTHLY -> mGroupTypeSpinner?.setSelection(2)
            Group.YEARLY -> mGroupTypeSpinner?.setSelection(3)
        }
        when (setting.type) {
            OverviewSetting.Type.CASH_FLOW -> {
                mOverviewTypeSpinner?.setSelection(0)
                mCashFlowSpinner?.visibility = View.VISIBLE
                mCategorySpinner?.visibility = View.GONE
            }
            OverviewSetting.Type.CATEGORY -> {
                mOverviewTypeSpinner?.setSelection(1)
                mCashFlowSpinner?.visibility = View.GONE
                mCategorySpinner?.visibility = View.VISIBLE
            }
        }
        when (setting.cashFlow) {
            OverviewSetting.CashFlow.INCOMES -> mCashFlowSpinner?.setSelection(0)
            OverviewSetting.CashFlow.EXPENSES -> mCashFlowSpinner?.setSelection(1)
            OverviewSetting.CashFlow.NET_INCOMES -> mCashFlowSpinner?.setSelection(2)
        }
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(SS_OVERVIEW_SETTING, getCurrentOverviewSetting())
    }

    private fun getCurrentOverviewSetting(): OverviewSetting {
        val startDate = mStartDatePicker?.getCurrentDateTime() ?: Date()
        val endDate = mEndDatePicker?.getCurrentDateTime() ?: Date()
        val groupType = when (mGroupTypeSpinner?.selectedItemPosition ?: 0) {
            0 -> Group.DAILY
            1 -> Group.WEEKLY
            2 -> Group.MONTHLY
            3 -> Group.YEARLY
            else -> Group.DAILY
        }
        return if (mOverviewTypeSpinner?.selectedItemPosition == 0) {
            val cashFlow = when (mCashFlowSpinner?.selectedItemPosition ?: 0) {
                0 -> OverviewSetting.CashFlow.INCOMES
                1 -> OverviewSetting.CashFlow.EXPENSES
                2 -> OverviewSetting.CashFlow.NET_INCOMES
                else -> OverviewSetting.CashFlow.NET_INCOMES
            }
            OverviewSetting(startDate, endDate, groupType, cashFlow)
        } else {
            val category = mCategoryPicker?.getCurrentCategory()
            OverviewSetting(startDate, endDate, groupType, category?.id ?: 0L)
        }
    }

    private fun getFirstAvailableCategory(context: Context): Category? {
        var uri: Uri = DataContentProvider.CONTENT_CATEGORIES
        val projection = arrayOf(
            Contract.Category.ID,
            Contract.Category.NAME,
            Contract.Category.ICON,
            Contract.Category.TYPE
        )
        var selection: String? = null
        var selectionArgs: Array<String>? = null
        val setting = mOverviewSetting
        if (setting?.type == OverviewSetting.Type.CATEGORY) {
            uri = ContentUris.withAppendedId(uri, setting.categoryId)
        } else {
            selection = Contract.Category.TYPE + " != ?"
            selectionArgs = arrayOf(Contract.CategoryType.SYSTEM.value.toString())
        }
        var category: Category? = null
        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndex(Contract.Category.ID)
                val nameIndex = cursor.getColumnIndex(Contract.Category.NAME)
                val iconIndex = cursor.getColumnIndex(Contract.Category.ICON)
                val typeIndex = cursor.getColumnIndex(Contract.Category.TYPE)
                val id = if (idIndex >= 0) cursor.getLong(idIndex) else 0L
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else ""
                val iconStr = if (iconIndex >= 0) cursor.getString(iconIndex) else null
                val typeVal = if (typeIndex >= 0) cursor.getInt(typeIndex) else 0
                category = Category(
                    id,
                    name,
                    IconLoader.parse(iconStr),
                    Contract.CategoryType.fromValue(typeVal)
                )
            }
        }
        return category
    }

    fun setCallback(callback: Callback?) {
        mCallback = callback
    }

    fun showPicker(fragmentManager: FragmentManager, tag: String?, overviewSetting: OverviewSetting?) {
        mOverviewSetting = overviewSetting
        show(fragmentManager, tag)
    }

    override fun onDateTimeChanged(tag: String?, date: Date?) {
        if (date == null) return
        when (tag) {
            TAG_START_DATE_PICKER -> {
                mStartDateSpinner?.text = getString(R.string.spinner_item_start_date, DateFormatter.getFormattedDate(date))
            }
            TAG_END_DATE_PICKER -> {
                mEndDateSpinner?.text = getString(R.string.spinner_item_end_date, DateFormatter.getFormattedDate(date))
            }
        }
    }

    override fun onCategoryChanged(tag: String?, category: Category?) {
        if (category != null) {
            mCategorySpinner?.text = category.name
        }
    }

    interface Callback {
        fun onOverviewSettingChanged(overviewSetting: OverviewSetting)
    }

    companion object {
        private const val TAG_START_DATE_PICKER = "OverviewSettingDialog::Tag::StartDatePicker"
        private const val TAG_END_DATE_PICKER = "OverviewSettingDialog::Tag::EndDatePicker"
        private const val TAG_CATEGORY_PICKER = "OverviewSettingDialog::Tag::CategoryPicker"

        private const val SS_OVERVIEW_SETTING = "OverviewSettingDialog::SavedState::OverviewSetting"

        @JvmStatic
        fun newInstance(): OverviewSettingDialog {
            return OverviewSettingDialog()
        }
    }
}
