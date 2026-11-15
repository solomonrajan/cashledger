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

package com.oriondev.moneywallet.ui.fragment.secondary

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Group
import com.oriondev.moneywallet.picker.ColorPicker
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.fragment.dialog.CustomDigitSetupDialog
import com.oriondev.moneywallet.ui.preference.ColorPreference
import com.oriondev.moneywallet.ui.preference.ThemedListPreference
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.SystemBars
import java.text.DateFormatSymbols
import java.util.Arrays
import java.util.Calendar
import java.util.Locale

/**
 * Created by andrea on 07/03/18.
 */
class UserInterfaceSettingFragment : PreferenceFragmentCompat(), ColorPicker.Controller {

    private var mColorIncomePreference: ColorPreference? = null
    private var mColorExpensePreference: ColorPreference? = null
    private var mCustomDigitsSetupPreference: Preference? = null
    private var mDateFormatPreference: ThemedListPreference? = null
    private var mTimeFormatPreference: Preference? = null
    private var mFirstDayWeekPreference: ThemedListPreference? = null
    private var mFirstDayMonthPreference: ThemedListPreference? = null
    private var mGroupTypePreference: ThemedListPreference? = null
    private var mColorPrimaryPreference: ColorPreference? = null
    private var mColorAccentPreference: ColorPreference? = null
    private var mThemeTypePreference: ThemedListPreference? = null

    private var mColorIncomePicker: ColorPicker? = null
    private var mColorExpensePicker: ColorPicker? = null
    private var mColorPrimaryPicker: ColorPicker? = null
    private var mColorAccentPicker: ColorPicker? = null

    private var mCustomDigitSetupDialog: CustomDigitSetupDialog? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_user_interface)
        mColorIncomePreference = findPreference("income_color") as? ColorPreference
        mColorExpensePreference = findPreference("expense_color") as? ColorPreference
        mCustomDigitsSetupPreference = findPreference("custom_digits")
        mDateFormatPreference = findPreference("date_format") as? ThemedListPreference
        mTimeFormatPreference = findPreference("time_format")
        mFirstDayWeekPreference = findPreference("first_day_week") as? ThemedListPreference
        mFirstDayMonthPreference = findPreference("first_day_month") as? ThemedListPreference
        mGroupTypePreference = findPreference("group_type") as? ThemedListPreference
        mColorPrimaryPreference = findPreference("theme_color_primary") as? ColorPreference
        mColorAccentPreference = findPreference("theme_color_accent") as? ColorPreference
        mThemeTypePreference = findPreference("theme_type") as? ThemedListPreference
        onCreateFragments(childFragmentManager)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // setup appearance preference logic
        mColorIncomePreference?.setOnPreferenceClickListener {
            mColorIncomePicker?.showPicker()
            false
        }
        mColorExpensePreference?.setOnPreferenceClickListener {
            mColorExpensePicker?.showPicker()
            false
        }
        // setup date format preference: we use a dynamic version that render the current
        // datetime of the device using all the available datetime patterns to help the
        // user to decide which is better for him
        setupDateFormatEntries()
        mDateFormatPreference?.setEntryValues(
            arrayOf(
                PreferenceManager.DATE_FORMAT_TYPE_0.toString(),
                PreferenceManager.DATE_FORMAT_TYPE_1.toString(),
                PreferenceManager.DATE_FORMAT_TYPE_2.toString(),
                PreferenceManager.DATE_FORMAT_TYPE_3.toString(),
                PreferenceManager.DATE_FORMAT_TYPE_4.toString(),
                PreferenceManager.DATE_FORMAT_TYPE_5.toString(),
                PreferenceManager.DATE_FORMAT_TYPE_6.toString(),
                PreferenceManager.DATE_FORMAT_TYPE_7.toString(),
                PreferenceManager.DATE_FORMAT_TYPE_8.toString()
            )
        )
        // setup the first day of week preference: we use the DateFormatSymbols class to obtain
        // the strings of the weekday names already localized with the current locale
        mFirstDayWeekPreference?.setEntries(Arrays.copyOfRange(DateFormatSymbols().weekdays, 1, 8))
        mFirstDayWeekPreference?.setEntryValues(
            arrayOf(
                Calendar.SUNDAY.toString(),
                Calendar.MONDAY.toString(),
                Calendar.TUESDAY.toString(),
                Calendar.WEDNESDAY.toString(),
                Calendar.THURSDAY.toString(),
                Calendar.FRIDAY.toString(),
                Calendar.SATURDAY.toString()
            )
        )
        // setup the first day of month preference: we set as upper bound the 28 day
        // the entries are what the user reads, the values are parsed back with Integer.parseInt
        val dayLabels = Array(28) { i -> String.format(Locale.getDefault(), "%d", i + 1) }
        val daysInMonth = Array(28) { i -> (i + 1).toString() }
        mFirstDayMonthPreference?.setEntries(dayLabels)
        mFirstDayMonthPreference?.setEntryValues(daysInMonth)
        // setup the group type preference
        mGroupTypePreference?.setEntries(R.array.group_types)
        mGroupTypePreference?.setEntryValues(
            arrayOf(
                PreferenceManager.GROUP_TYPE_DAILY.toString(),
                PreferenceManager.GROUP_TYPE_WEEKLY.toString(),
                PreferenceManager.GROUP_TYPE_MONTHLY.toString(),
                PreferenceManager.GROUP_TYPE_YEARLY.toString()
            )
        )
        // setup theme preference logic
        mColorPrimaryPreference?.setOnPreferenceClickListener {
            mColorPrimaryPicker?.showPicker()
            false
        }
        mColorAccentPreference?.setOnPreferenceClickListener {
            mColorAccentPicker?.showPicker()
            false
        }
        mThemeTypePreference?.setEntries(R.array.theme_types)
        mThemeTypePreference?.setEntryValues(arrayOf(THEME_TYPE_LIGHT, THEME_TYPE_DARK, THEME_TYPE_DEEP_DARK, THEME_TYPE_SYSTEM_DEFAULT))
        // setup current values
        setupCurrentDateFormat()
        setupCurrentTimeFormat()
        setupCurrentFirstDayOfWeek()
        setupCurrentFirstDayOfMonth()
        setupCurrentGroupType()
        setupCurrentThemeType()
        // attach listeners to each preference to get notified when a value changes
        mCustomDigitsSetupPreference?.setOnPreferenceClickListener {
            mCustomDigitSetupDialog?.show(childFragmentManager, TAG_CUSTOM_DIGIT)
            false
        }
        mDateFormatPreference?.setOnPreferenceChangeListener { _, newValue ->
            val index = (newValue as String).toInt()
            PreferenceManager.setCurrentDateFormatIndex(index)
            setupCurrentDateFormat()
            false
        }
        mTimeFormatPreference?.setOnPreferenceClickListener {
            try {
                startActivity(Intent(Settings.ACTION_DATE_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                val act = activity
                if (act != null) {
                    ThemedDialog.buildMaterialDialog(act)
                        .setTitle(R.string.title_error)
                        .setMessage(R.string.message_error_activity_not_found)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
            false
        }
        mFirstDayWeekPreference?.setOnPreferenceChangeListener { _, newValue ->
            val day = (newValue as String).toInt()
            PreferenceManager.setCurrentFirstDayOfWeek(day)
            setupCurrentFirstDayOfWeek()
            false
        }
        mFirstDayMonthPreference?.setOnPreferenceChangeListener { _, newValue ->
            val day = (newValue as String).toInt()
            PreferenceManager.setCurrentFirstDayOfMonth(day)
            setupCurrentFirstDayOfMonth()
            false
        }
        mGroupTypePreference?.setOnPreferenceChangeListener { _, newValue ->
            val groupType = (newValue as String).toInt()
            PreferenceManager.setCurrentGroupType(Group.fromType(groupType))
            setupCurrentGroupType()
            false
        }
        mThemeTypePreference?.setOnPreferenceChangeListener { _, newValue ->
            when (newValue as String) {
                THEME_TYPE_LIGHT -> ThemeEngine.setMode(ThemeEngine.Mode.LIGHT)
                THEME_TYPE_DARK -> ThemeEngine.setMode(ThemeEngine.Mode.DARK)
                THEME_TYPE_DEEP_DARK -> ThemeEngine.setMode(ThemeEngine.Mode.DEEP_DARK)
                THEME_TYPE_SYSTEM_DEFAULT -> ThemeEngine.setMode(ThemeEngine.Mode.SYSTEM_DEFAULT)
            }
            setupCurrentThemeType()
            false
        }
    }

    override fun onCreateRecyclerView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?): RecyclerView {
        val recyclerView = super.onCreateRecyclerView(inflater, parent, savedInstanceState)
        recyclerView.setPadding(0, 0, 0, 0)
        // After the reset above, not before it, or the navigation bar clearance is the
        // padding that gets zeroed.
        SystemBars.pad(
            recyclerView,
            false,
            resources.getBoolean(R.bool.secondary_panel_fills_window),
            true
        )
        return recyclerView
    }

    private fun setupCurrentDateFormat() {
        val index = PreferenceManager.getCurrentDateFormatIndex()
        val summary = DateFormatter.getFormattedDateTime(Calendar.getInstance().time, index)
        mDateFormatPreference?.value = index.toString()
        mDateFormatPreference?.summary = summary
    }

    override fun onResume() {
        super.onResume()
        // the 12 or 24 hour choice lives in the system settings, which the Time format row links
        // to, so everything that renders a time can be stale on the way back: the two summaries
        // and the nine dated examples inside the date format dialog
        setupCurrentTimeFormat()
        setupCurrentDateFormat()
        setupDateFormatEntries()
    }

    private fun setupDateFormatEntries() {
        val now = Calendar.getInstance().time
        mDateFormatPreference?.setEntries(
            arrayOf(
                DateFormatter.getFormattedDateTime(now, PreferenceManager.DATE_FORMAT_TYPE_0),
                DateFormatter.getFormattedDateTime(now, PreferenceManager.DATE_FORMAT_TYPE_1),
                DateFormatter.getFormattedDateTime(now, PreferenceManager.DATE_FORMAT_TYPE_2),
                DateFormatter.getFormattedDateTime(now, PreferenceManager.DATE_FORMAT_TYPE_3),
                DateFormatter.getFormattedDateTime(now, PreferenceManager.DATE_FORMAT_TYPE_4),
                DateFormatter.getFormattedDateTime(now, PreferenceManager.DATE_FORMAT_TYPE_5),
                DateFormatter.getFormattedDateTime(now, PreferenceManager.DATE_FORMAT_TYPE_6),
                DateFormatter.getFormattedDateTime(now, PreferenceManager.DATE_FORMAT_TYPE_7),
                DateFormatter.getFormattedDateTime(now, PreferenceManager.DATE_FORMAT_TYPE_8)
            )
        )
    }

    private fun setupCurrentTimeFormat() {
        val example = DateFormatter.getFormattedTime(Calendar.getInstance().time)
        mTimeFormatPreference?.summary = getString(R.string.setting_summary_ui_time_format, example)
    }

    private fun setupCurrentFirstDayOfWeek() {
        val index = PreferenceManager.getFirstDayOfWeek()
        val weekdays = DateFormatSymbols().weekdays
        mFirstDayWeekPreference?.value = index.toString()
        mFirstDayWeekPreference?.summary = weekdays[index]
    }

    private fun setupCurrentFirstDayOfMonth() {
        val index = PreferenceManager.getFirstDayOfMonth()
        mFirstDayMonthPreference?.value = index.toString()
        mFirstDayMonthPreference?.summary = String.format(Locale.getDefault(), "%d", index)
    }

    private fun setupCurrentGroupType() {
        val groupType = PreferenceManager.getCurrentGroupType()
        mGroupTypePreference?.value = groupType.type.toString()
        when (groupType) {
            Group.DAILY -> mGroupTypePreference?.setSummary(R.string.setting_item_ui_group_type_daily)
            Group.WEEKLY -> mGroupTypePreference?.setSummary(R.string.setting_item_ui_group_type_weekly)
            Group.MONTHLY -> mGroupTypePreference?.setSummary(R.string.setting_item_ui_group_type_monthly)
            Group.YEARLY -> mGroupTypePreference?.setSummary(R.string.setting_item_ui_group_type_yearly)
        }
    }

    private fun setupCurrentThemeType() {
        val theme = ThemeEngine.getTheme()
        when (theme.mode) {
            ThemeEngine.Mode.LIGHT -> {
                mThemeTypePreference?.value = THEME_TYPE_LIGHT
                mThemeTypePreference?.setSummary(R.string.setting_item_ui_theme_type_light)
            }
            ThemeEngine.Mode.DARK -> {
                mThemeTypePreference?.value = THEME_TYPE_DARK
                mThemeTypePreference?.setSummary(R.string.setting_item_ui_theme_type_dark)
            }
            ThemeEngine.Mode.DEEP_DARK -> {
                mThemeTypePreference?.value = THEME_TYPE_DEEP_DARK
                mThemeTypePreference?.setSummary(R.string.setting_item_ui_theme_type_deep_dark)
            }
            ThemeEngine.Mode.SYSTEM_DEFAULT -> {
                mThemeTypePreference?.value = THEME_TYPE_SYSTEM_DEFAULT
                mThemeTypePreference?.setSummary(R.string.setting_item_ui_theme_type_system_default)
            }
        }
    }

    private fun onCreateFragments(fragmentManager: FragmentManager) {
        val theme = ThemeEngine.getTheme()
        mColorIncomePicker = ColorPicker.createPicker(fragmentManager, TAG_COLOR_INCOME, PreferenceManager.getCurrentIncomeColor(), false, this)
        mColorExpensePicker = ColorPicker.createPicker(fragmentManager, TAG_COLOR_EXPENSE, PreferenceManager.getCurrentExpenseColor(), false, this)
        mColorPrimaryPicker = ColorPicker.createPicker(fragmentManager, TAG_COLOR_PRIMARY, theme.colorPrimary, false, this)
        mColorAccentPicker = ColorPicker.createPicker(fragmentManager, TAG_COLOR_ACCENT, theme.colorAccent, true, this)
        var dialog = fragmentManager.findFragmentByTag(TAG_CUSTOM_DIGIT) as? CustomDigitSetupDialog
        if (dialog == null) {
            dialog = CustomDigitSetupDialog.newInstance()
        }
        mCustomDigitSetupDialog = dialog
    }

    override fun onColorChanged(tag: String?, color: Int, autoFired: Boolean) {
        when (tag) {
            TAG_COLOR_INCOME -> {
                mColorIncomePreference?.color = color
                if (!autoFired) {
                    PreferenceManager.setCurrentIncomeColor(color)
                }
            }
            TAG_COLOR_EXPENSE -> {
                mColorExpensePreference?.color = color
                if (!autoFired) {
                    PreferenceManager.setCurrentExpenseColor(color)
                }
            }
            TAG_COLOR_PRIMARY -> {
                mColorPrimaryPreference?.color = color
                if (!autoFired) {
                    ThemeEngine.setColorPrimary(color)
                }
            }
            TAG_COLOR_ACCENT -> {
                mColorAccentPreference?.color = color
                if (!autoFired) {
                    ThemeEngine.setColorAccent(color)
                }
            }
        }
    }

    override fun onColorPreview(tag: String?, color: Int): Boolean {
        return when (tag) {
            TAG_COLOR_PRIMARY -> ThemeEngine.previewColorPrimary(color)
            TAG_COLOR_ACCENT -> ThemeEngine.previewColorAccent(color)
            else -> false
        }
    }

    override fun onColorPreviewEnded() {
        ThemeEngine.clearPreview()
    }

    companion object {
        private const val TAG_COLOR_INCOME = "UserInterfaceSettingFragment::Tag::ColorIncome"
        private const val TAG_COLOR_EXPENSE = "UserInterfaceSettingFragment::Tag::ColorExpense"
        private const val TAG_COLOR_PRIMARY = "UserInterfaceSettingFragment::Tag::ColorPrimary"
        private const val TAG_COLOR_ACCENT = "UserInterfaceSettingFragment::Tag::ColorAccent"
        private const val TAG_CUSTOM_DIGIT = "UserInterfaceSettingFragment::Tag::CustomDigit::DialogFragment"

        private const val THEME_TYPE_LIGHT = "light"
        private const val THEME_TYPE_DARK = "dark"
        private const val THEME_TYPE_DEEP_DARK = "deep_dark"
        private const val THEME_TYPE_SYSTEM_DEFAULT = "system_default"
    }
}
