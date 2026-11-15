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
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.RecurrenceSetting
import com.oriondev.moneywallet.picker.DateTimePicker
import com.oriondev.moneywallet.ui.view.text.MaterialEditText
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.ui.view.theme.ThemedSpinner
import com.oriondev.moneywallet.utils.DateFormatter
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/**
 * Created by andrea on 07/11/18.
 */
class RecurrencePickerDialog : DialogFragment(), DateTimePicker.Controller {

    private var mCallback: Callback? = null
    private var mRecurrenceSetting: RecurrenceSetting? = null
    private var mEndTypeEnabled: Boolean = true
    private var mOnceAPeriod: Boolean = false

    private var mRecurrenceTypeSpinner: ThemedSpinner? = null
    private var mRecurrenceStartDateSpinner: MaterialEditText? = null
    private var mRecurrenceEveryNumberEditText: EditText? = null
    private var mRecurrenceEveryItemTextView: TextView? = null
    private var mRecurrenceTypeWeeklyLayout: LinearLayout? = null
    private var mRecurrenceTypeWeeklySundayRadioButton: CheckBox? = null
    private var mRecurrenceTypeWeeklyMondayRadioButton: CheckBox? = null
    private var mRecurrenceTypeWeeklyTuesdayRadioButton: CheckBox? = null
    private var mRecurrenceTypeWeeklyWednesdayRadioButton: CheckBox? = null
    private var mRecurrenceTypeWeeklyThursdayRadioButton: CheckBox? = null
    private var mRecurrenceTypeWeeklyFridayRadioButton: CheckBox? = null
    private var mRecurrenceTypeWeeklySaturdayRadioButton: CheckBox? = null
    private var mRecurrenceTypeMonthlyLayout: LinearLayout? = null
    private var mRecurrenceTypeMonthlySameDayRadioButton: RadioButton? = null
    private var mRecurrenceEndTypeSpinner: ThemedSpinner? = null
    private var mRecurrenceTimesLayout: LinearLayout? = null
    private var mRecurrenceTimesNumberEditText: EditText? = null
    private var mRecurrenceEndDateSpinner: MaterialEditText? = null

    private var mStartDatePicker: DateTimePicker? = null
    private var mEndDatePicker: DateTimePicker? = null

    private val mWeekdayChangeListener = CompoundButton.OnCheckedChangeListener { buttonView, _ ->
        val sun = mRecurrenceTypeWeeklySundayRadioButton?.isChecked == true
        val mon = mRecurrenceTypeWeeklyMondayRadioButton?.isChecked == true
        val tue = mRecurrenceTypeWeeklyTuesdayRadioButton?.isChecked == true
        val wed = mRecurrenceTypeWeeklyWednesdayRadioButton?.isChecked == true
        val thu = mRecurrenceTypeWeeklyThursdayRadioButton?.isChecked == true
        val fri = mRecurrenceTypeWeeklyFridayRadioButton?.isChecked == true
        val sat = mRecurrenceTypeWeeklySaturdayRadioButton?.isChecked == true
        if (!sun && !mon && !tue && !wed && !thu && !fri && !sat) {
            buttonView.isChecked = true
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val currentActivity = activity ?: return super.onCreateDialog(savedInstanceState)
        if (savedInstanceState != null) {
            mRecurrenceSetting = savedInstanceState.getParcelable(SS_RECURRENCE_SETTING)
            mEndTypeEnabled = savedInstanceState.getBoolean(SS_END_TYPE_ENABLED, true)
            mOnceAPeriod = savedInstanceState.getBoolean(SS_ONCE_A_PERIOD, false)
        }
        val setting = mRecurrenceSetting ?: RecurrenceSetting.Builder(Date(), RecurrenceSetting.TYPE_DAILY).build()
        mRecurrenceSetting = setting

        val view = ThemedDialog.inflateScrollableView(currentActivity, R.layout.dialog_recurrence_setting_picker)
        val dialog = ThemedDialog.buildMaterialDialog(currentActivity)
            .setTitle(R.string.dialog_recurrence_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val currentSetting = getCurrentRecurrenceSetting()
                mRecurrenceSetting = currentSetting
                mCallback?.onRecurrenceSettingChanged(currentSetting)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        mRecurrenceTypeSpinner = view.findViewById(R.id.type_spinner)
        mRecurrenceStartDateSpinner = view.findViewById(R.id.start_date_spinner)
        mRecurrenceEveryNumberEditText = view.findViewById(R.id.every_number_edit_text)
        mRecurrenceEveryItemTextView = view.findViewById(R.id.every_item_text_view)
        mRecurrenceTypeWeeklyLayout = view.findViewById(R.id.type_weekly_layout)
        mRecurrenceTypeWeeklySundayRadioButton = view.findViewById(R.id.type_weekly_sunday)
        mRecurrenceTypeWeeklyMondayRadioButton = view.findViewById(R.id.type_weekly_monday)
        mRecurrenceTypeWeeklyTuesdayRadioButton = view.findViewById(R.id.type_weekly_tuesday)
        mRecurrenceTypeWeeklyWednesdayRadioButton = view.findViewById(R.id.type_weekly_wednesday)
        mRecurrenceTypeWeeklyThursdayRadioButton = view.findViewById(R.id.type_weekly_thursday)
        mRecurrenceTypeWeeklyFridayRadioButton = view.findViewById(R.id.type_weekly_friday)
        mRecurrenceTypeWeeklySaturdayRadioButton = view.findViewById(R.id.type_weekly_saturday)
        mRecurrenceTypeMonthlyLayout = view.findViewById(R.id.type_monthly_layout)
        mRecurrenceTypeMonthlySameDayRadioButton = view.findViewById(R.id.type_monthly_repeat_same_day)
        mRecurrenceEndTypeSpinner = view.findViewById(R.id.end_type_spinner)
        mRecurrenceTimesLayout = view.findViewById(R.id.end_type_for_layout)
        mRecurrenceTimesNumberEditText = view.findViewById(R.id.end_type_for_edit_text)
        mRecurrenceEndDateSpinner = view.findViewById(R.id.end_date_spinner)

        mRecurrenceStartDateSpinner?.setTextViewMode(true)
        mRecurrenceEndDateSpinner?.setTextViewMode(true)

        mRecurrenceTypeSpinner?.setAdapter(
            ThemedSpinner.Adapter(
                currentActivity,
                getString(R.string.recurrence_type_daily),
                getString(R.string.recurrence_type_weekly),
                getString(R.string.recurrence_type_monthly),
                getString(R.string.recurrence_type_yearly)
            )
        )
        mRecurrenceEndTypeSpinner?.setAdapter(
            ThemedSpinner.Adapter(
                currentActivity,
                getString(R.string.recurrence_end_type_forever),
                getString(R.string.recurrence_end_type_until),
                getString(R.string.recurrence_end_type_for)
            )
        )

        mRecurrenceTypeSpinner?.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> updateRecurrenceType(RecurrenceSetting.TYPE_DAILY, true)
                    1 -> updateRecurrenceType(RecurrenceSetting.TYPE_WEEKLY, true)
                    2 -> updateRecurrenceType(RecurrenceSetting.TYPE_MONTHLY, true)
                    3 -> updateRecurrenceType(RecurrenceSetting.TYPE_YEARLY, true)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        })

        mRecurrenceStartDateSpinner?.setOnClickListener {
            mStartDatePicker?.showDatePicker()
        }

        mRecurrenceTypeWeeklySundayRadioButton?.setOnCheckedChangeListener(mWeekdayChangeListener)
        mRecurrenceTypeWeeklyMondayRadioButton?.setOnCheckedChangeListener(mWeekdayChangeListener)
        mRecurrenceTypeWeeklyTuesdayRadioButton?.setOnCheckedChangeListener(mWeekdayChangeListener)
        mRecurrenceTypeWeeklyWednesdayRadioButton?.setOnCheckedChangeListener(mWeekdayChangeListener)
        mRecurrenceTypeWeeklyThursdayRadioButton?.setOnCheckedChangeListener(mWeekdayChangeListener)
        mRecurrenceTypeWeeklyFridayRadioButton?.setOnCheckedChangeListener(mWeekdayChangeListener)
        mRecurrenceTypeWeeklySaturdayRadioButton?.setOnCheckedChangeListener(mWeekdayChangeListener)

        mRecurrenceEndTypeSpinner?.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> updateRecurrenceEndType(RecurrenceSetting.END_FOREVER, true)
                    1 -> updateRecurrenceEndType(RecurrenceSetting.END_UNTIL, true)
                    2 -> updateRecurrenceEndType(RecurrenceSetting.END_FOR, true)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        })

        mRecurrenceEndDateSpinner?.setOnClickListener {
            mEndDatePicker?.showDatePicker()
        }

        val childFm = childFragmentManager
        mStartDatePicker = DateTimePicker.createPicker(childFm, TAG_START_DATE_PICKER, setting.startDate)
        mEndDatePicker = DateTimePicker.createPicker(childFm, TAG_END_DATE_PICKER, setting.endDate)

        updateRecurrenceType(setting.type, false)
        updateRecurrenceOffsetValue(setting.offsetValue)
        updateRecurrenceWeekDays(setting.weekDays)
        updateRecurrenceMonthDay(setting.monthDay)
        updateRecurrenceEndType(setting.endType, false)
        updateRecurrenceOccurrenceValue(setting.occurrenceValue)

        if (!mEndTypeEnabled) {
            view.findViewById<View>(R.id.end_layout)?.visibility = View.GONE
        }
        if (mOnceAPeriod) {
            mRecurrenceTypeWeeklyLayout?.visibility = View.GONE
        }
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(SS_RECURRENCE_SETTING, getCurrentRecurrenceSetting())
        outState.putBoolean(SS_END_TYPE_ENABLED, mEndTypeEnabled)
        outState.putBoolean(SS_ONCE_A_PERIOD, mOnceAPeriod)
    }

    private fun updateRecurrenceType(type: Int, self: Boolean) {
        when (type) {
            RecurrenceSetting.TYPE_DAILY -> mRecurrenceEveryItemTextView?.setText(R.string.recurrence_hint_days)
            RecurrenceSetting.TYPE_WEEKLY -> mRecurrenceEveryItemTextView?.setText(R.string.recurrence_hint_weeks)
            RecurrenceSetting.TYPE_MONTHLY -> mRecurrenceEveryItemTextView?.setText(R.string.recurrence_hint_months)
            RecurrenceSetting.TYPE_YEARLY -> mRecurrenceEveryItemTextView?.setText(R.string.recurrence_hint_years)
        }
        mRecurrenceTypeWeeklyLayout?.visibility = if (type == RecurrenceSetting.TYPE_WEEKLY && !mOnceAPeriod) View.VISIBLE else View.GONE
        mRecurrenceTypeMonthlyLayout?.visibility = if (type == RecurrenceSetting.TYPE_MONTHLY) View.VISIBLE else View.GONE
        if (!self) {
            when (type) {
                RecurrenceSetting.TYPE_DAILY -> mRecurrenceTypeSpinner?.setSelection(0)
                RecurrenceSetting.TYPE_WEEKLY -> mRecurrenceTypeSpinner?.setSelection(1)
                RecurrenceSetting.TYPE_MONTHLY -> mRecurrenceTypeSpinner?.setSelection(2)
                RecurrenceSetting.TYPE_YEARLY -> mRecurrenceTypeSpinner?.setSelection(3)
            }
        }
    }

    private fun updateRecurrenceOffsetValue(offset: Int) {
        mRecurrenceEveryNumberEditText?.setText(String.format(Locale.getDefault(), "%d", offset))
    }

    private fun updateRecurrenceWeekDays(days: BooleanArray?) {
        if (days == null || days.size < 7) return
        mRecurrenceTypeWeeklySundayRadioButton?.isChecked = days[0]
        mRecurrenceTypeWeeklyMondayRadioButton?.isChecked = days[1]
        mRecurrenceTypeWeeklyTuesdayRadioButton?.isChecked = days[2]
        mRecurrenceTypeWeeklyWednesdayRadioButton?.isChecked = days[3]
        mRecurrenceTypeWeeklyThursdayRadioButton?.isChecked = days[4]
        mRecurrenceTypeWeeklyFridayRadioButton?.isChecked = days[5]
        mRecurrenceTypeWeeklySaturdayRadioButton?.isChecked = days[6]
    }

    private fun updateRecurrenceMonthDay(flag: Int) {
        mRecurrenceTypeMonthlySameDayRadioButton?.isChecked = (flag == RecurrenceSetting.FLAG_MONTHLY_SAME_DAY)
    }

    private fun updateRecurrenceEndType(type: Int, self: Boolean) {
        mRecurrenceEndDateSpinner?.visibility = if (type == RecurrenceSetting.END_UNTIL) View.VISIBLE else View.GONE
        mRecurrenceTimesLayout?.visibility = if (type == RecurrenceSetting.END_FOR) View.VISIBLE else View.GONE
        if (!self) {
            when (type) {
                RecurrenceSetting.END_FOREVER -> mRecurrenceEndTypeSpinner?.setSelection(0)
                RecurrenceSetting.END_UNTIL -> mRecurrenceEndTypeSpinner?.setSelection(1)
                RecurrenceSetting.END_FOR -> mRecurrenceEndTypeSpinner?.setSelection(2)
            }
        }
    }

    private fun updateRecurrenceOccurrenceValue(occurrences: Int) {
        mRecurrenceTimesNumberEditText?.setText(String.format(Locale.getDefault(), "%d", occurrences))
    }

    private fun getCurrentRecurrenceType(): Int {
        return when (mRecurrenceTypeSpinner?.selectedItemPosition ?: 0) {
            0 -> RecurrenceSetting.TYPE_DAILY
            1 -> RecurrenceSetting.TYPE_WEEKLY
            2 -> RecurrenceSetting.TYPE_MONTHLY
            3 -> RecurrenceSetting.TYPE_YEARLY
            else -> RecurrenceSetting.TYPE_DAILY
        }
    }

    private fun getCurrentRecurrenceOffset(): Int {
        val text = mRecurrenceEveryNumberEditText?.text?.toString() ?: return 1
        return try {
            text.toInt()
        } catch (e: NumberFormatException) {
            try {
                NumberFormat.getInstance().parse(text)?.toInt() ?: 1
            } catch (e2: Exception) {
                1
            }
        }
    }

    private fun getCurrentRecurrenceWeekDays(): BooleanArray {
        return booleanArrayOf(
            mRecurrenceTypeWeeklySundayRadioButton?.isChecked == true,
            mRecurrenceTypeWeeklyMondayRadioButton?.isChecked == true,
            mRecurrenceTypeWeeklyTuesdayRadioButton?.isChecked == true,
            mRecurrenceTypeWeeklyWednesdayRadioButton?.isChecked == true,
            mRecurrenceTypeWeeklyThursdayRadioButton?.isChecked == true,
            mRecurrenceTypeWeeklyFridayRadioButton?.isChecked == true,
            mRecurrenceTypeWeeklySaturdayRadioButton?.isChecked == true
        )
    }

    private fun getCurrentRecurrenceEndType(): Int {
        return when (mRecurrenceEndTypeSpinner?.selectedItemPosition ?: 0) {
            0 -> RecurrenceSetting.END_FOREVER
            1 -> RecurrenceSetting.END_UNTIL
            2 -> RecurrenceSetting.END_FOR
            else -> RecurrenceSetting.END_FOREVER
        }
    }

    private fun getCurrentRecurrenceEndOccurrences(): Int {
        val text = mRecurrenceTimesNumberEditText?.text?.toString() ?: return 1
        return try {
            text.toInt()
        } catch (e: NumberFormatException) {
            try {
                NumberFormat.getInstance().parse(text)?.toInt() ?: 1
            } catch (e2: Exception) {
                1
            }
        }
    }

    fun getCurrentRecurrenceSetting(): RecurrenceSetting {
        val startDate = mStartDatePicker?.getCurrentDateTime() ?: Date()
        val type = getCurrentRecurrenceType()
        val builder = RecurrenceSetting.Builder(startDate, type)
        builder.setOffset(getCurrentRecurrenceOffset())
        if (type == RecurrenceSetting.TYPE_WEEKLY && !mOnceAPeriod) {
            builder.setRepeatWeekDay(getCurrentRecurrenceWeekDays())
        } else if (type == RecurrenceSetting.TYPE_MONTHLY) {
            builder.setRepeatSameMonthDay()
        } else if (type == RecurrenceSetting.TYPE_YEARLY) {
            builder.setRepeatSameYearDay()
        }
        when (getCurrentRecurrenceEndType()) {
            RecurrenceSetting.END_UNTIL -> {
                val endDate = mEndDatePicker?.getCurrentDateTime()
                if (endDate != null) {
                    builder.setEndUntil(endDate)
                }
            }
            RecurrenceSetting.END_FOR -> {
                builder.setEndFor(getCurrentRecurrenceEndOccurrences())
            }
        }
        return builder.build()
    }

    fun setCallback(callback: Callback?) {
        mCallback = callback
    }

    fun showPicker(
        fragmentManager: FragmentManager,
        tag: String?,
        recurrenceSetting: RecurrenceSetting?,
        endTypeEnabled: Boolean,
        onceAPeriod: Boolean
    ) {
        mRecurrenceSetting = recurrenceSetting
        mEndTypeEnabled = endTypeEnabled
        mOnceAPeriod = onceAPeriod
        show(fragmentManager, tag)
    }

    override fun onDateTimeChanged(tag: String?, date: Date?) {
        if (date == null) return
        when (tag) {
            TAG_START_DATE_PICKER -> {
                mRecurrenceStartDateSpinner?.text = DateFormatter.getFormattedDate(date)
            }
            TAG_END_DATE_PICKER -> {
                mRecurrenceEndDateSpinner?.text = DateFormatter.getFormattedDate(date)
            }
        }
    }

    interface Callback {
        fun onRecurrenceSettingChanged(recurrenceSetting: RecurrenceSetting)
    }

    companion object {
        private const val TAG_START_DATE_PICKER = "RecurrencePickerDialog::Tag::StartDatePicker"
        private const val TAG_END_DATE_PICKER = "RecurrencePickerDialog::Tag::EndDatePicker"

        private const val SS_RECURRENCE_SETTING = "RecurrencePickerDialog::SavedState::RecurrenceSetting"
        private const val SS_END_TYPE_ENABLED = "RecurrencePickerDialog::SavedState::EndTypeEnabled"
        private const val SS_ONCE_A_PERIOD = "RecurrencePickerDialog::SavedState::OnceAPeriod"

        @JvmStatic
        fun newInstance(): RecurrencePickerDialog {
            return RecurrencePickerDialog()
        }
    }
}
