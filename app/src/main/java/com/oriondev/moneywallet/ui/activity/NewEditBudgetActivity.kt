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

package com.oriondev.moneywallet.ui.activity

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.broadcast.RecurrenceBroadcastReceiver
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.RecurrenceSetting
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.picker.BudgetTypePicker
import com.oriondev.moneywallet.picker.CategoryPicker
import com.oriondev.moneywallet.picker.DateTimePicker
import com.oriondev.moneywallet.picker.MoneyPicker
import com.oriondev.moneywallet.picker.RecurrencePicker
import com.oriondev.moneywallet.picker.WalletPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.SQLiteDataException
import com.oriondev.moneywallet.storage.database.SyncContentProvider
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.view.text.MaterialEditText
import com.oriondev.moneywallet.ui.view.text.Validator
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.MoneyFormatter
import java.util.Calendar
import java.util.Date

/**
 * Created by andrea on 06/03/18.
 */
class NewEditBudgetActivity : NewEditItemActivity(),
    MoneyPicker.Controller,
    BudgetTypePicker.Controller,
    CategoryPicker.MultiCategoryController,
    DateTimePicker.Controller,
    RecurrencePicker.Controller,
    WalletPicker.MultiWalletController {

    private var mCurrencyTextView: TextView? = null
    private var mMoneyTextView: TextView? = null
    private var mTypeEditText: MaterialEditText? = null
    private var mCategoryEditText: MaterialEditText? = null
    private var mRepeatCheckBox: CheckBox? = null
    private var mRecurrenceEditText: MaterialEditText? = null
    private var mStartDateEditText: MaterialEditText? = null
    private var mEndDateEditText: MaterialEditText? = null
    private var mWalletsEditText: MaterialEditText? = null

    private var mMoneyPicker: MoneyPicker? = null
    private var mBudgetTypePicker: BudgetTypePicker? = null
    private var mCategoryPicker: CategoryPicker? = null
    private var mRecurrencePicker: RecurrencePicker? = null
    private var mStartDatePicker: DateTimePicker? = null
    private var mEndDatePicker: DateTimePicker? = null
    private var mWalletsPicker: WalletPicker? = null

    private var mAnchored = false
    private var mClosedPeriod = false

    private val mMoneyFormatter = MoneyFormatter.getInstance()

    override fun onCreateHeaderView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_header_new_edit_money_item, parent, true)
        mCurrencyTextView = view.findViewById(R.id.currency_text_view)
        mMoneyTextView = view.findViewById(R.id.money_text_view)
        mMoneyTextView?.setOnClickListener {
            mMoneyPicker?.showPicker()
        }
    }

    override fun onCreatePanelView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_panel_new_edit_budget, parent, true)
        val typeEditText = view.findViewById<MaterialEditText>(R.id.type_edit_text)
        val categoryEditText = view.findViewById<MaterialEditText>(R.id.category_edit_text)
        val repeatCheckBox = view.findViewById<CheckBox>(R.id.repeat_checkbox)
        val recurrenceEditText = view.findViewById<MaterialEditText>(R.id.recurrence_edit_text)
        val startDateEditText = view.findViewById<MaterialEditText>(R.id.start_date_edit_text)
        val endDateEditText = view.findViewById<MaterialEditText>(R.id.end_date_edit_text)
        val walletsEditText = view.findViewById<MaterialEditText>(R.id.wallets_edit_text)

        mTypeEditText = typeEditText
        mCategoryEditText = categoryEditText
        mRepeatCheckBox = repeatCheckBox
        mRecurrenceEditText = recurrenceEditText
        mStartDateEditText = startDateEditText
        mEndDateEditText = endDateEditText
        mWalletsEditText = walletsEditText

        typeEditText.setTextViewMode(true)
        categoryEditText.setTextViewMode(true)
        recurrenceEditText.setTextViewMode(true)
        startDateEditText.setTextViewMode(true)
        endDateEditText.setTextViewMode(true)
        walletsEditText.setTextViewMode(true)

        repeatCheckBox.setOnCheckedChangeListener { _, isChecked ->
            recurrenceEditText.visibility = if (isChecked) View.VISIBLE else View.GONE
            startDateEditText.visibility = if (isChecked) View.GONE else View.VISIBLE
            endDateEditText.visibility = if (isChecked) View.GONE else View.VISIBLE
        }
        repeatCheckBox.setOnClickListener {
            val startDate = mStartDatePicker?.currentDateTime
            if (repeatCheckBox.isChecked && startDate != null &&
                !mAnchored && mRecurrencePicker?.isChosen != true
            ) {
                mRecurrencePicker?.currentSettings = rebuiltOn(startDate)
            }
        }

        typeEditText.addValidator(object : Validator {
            override fun getErrorMessage(): String {
                return getString(R.string.error_input_missing_budget_type)
            }

            override fun isValid(charSequence: CharSequence): Boolean {
                return mBudgetTypePicker?.isSelected == true
            }

            override fun autoValidate(): Boolean = false
        })

        walletsEditText.addValidator(object : Validator {
            override fun getErrorMessage(): String {
                return getString(R.string.error_input_missing_multiple_wallets)
            }

            override fun isValid(charSequence: CharSequence): Boolean {
                return mWalletsPicker?.isSelected == true
            }

            override fun autoValidate(): Boolean = false
        })

        walletsEditText.addValidator(object : Validator {
            override fun getErrorMessage(): String {
                return getString(R.string.error_input_currency_not_valid)
            }

            override fun isValid(charSequence: CharSequence): Boolean {
                val currentWallets = mWalletsPicker?.currentWallets ?: return true
                for (wallet in currentWallets) {
                    if (wallet.currency == null) {
                        return false
                    }
                }
                return true
            }

            override fun autoValidate(): Boolean = false
        })

        walletsEditText.addValidator(object : Validator {
            override fun getErrorMessage(): String {
                return getString(R.string.error_input_invalid_multiple_wallets)
            }

            override fun isValid(charSequence: CharSequence): Boolean {
                var currencyUnit: CurrencyUnit? = null
                val wallets = mWalletsPicker?.currentWallets ?: return true
                for (wallet in wallets) {
                    if (currencyUnit == null) {
                        currencyUnit = wallet.currency
                    } else if (currencyUnit != wallet.currency) {
                        return false
                    }
                }
                return true
            }

            override fun autoValidate(): Boolean = false
        })

        recurrenceEditText.addValidator(object : Validator {
            override fun getErrorMessage(): String {
                return getString(R.string.error_input_invalid_budget_recurrence)
            }

            override fun isValid(charSequence: CharSequence): Boolean {
                return getRepeatingPeriod() != null
            }

            override fun autoValidate(): Boolean = false
        })

        startDateEditText.addValidator(object : Validator {
            override fun getErrorMessage(): String {
                return getString(R.string.error_input_missing_start_date)
            }

            override fun isValid(charSequence: CharSequence): Boolean {
                return mStartDatePicker?.isSelected == true
            }

            override fun autoValidate(): Boolean = false
        })

        endDateEditText.addValidator(object : Validator {
            override fun getErrorMessage(): String {
                return getString(R.string.error_input_missing_end_date)
            }

            override fun isValid(charSequence: CharSequence): Boolean {
                return mEndDatePicker?.isSelected == true
            }

            override fun autoValidate(): Boolean = false
        })

        startDateEditText.addValidator(object : Validator {
            override fun getErrorMessage(): String {
                return getString(R.string.error_input_invalid_date_range)
            }

            override fun isValid(charSequence: CharSequence): Boolean {
                val start = mStartDatePicker?.currentDateTime
                val end = mEndDatePicker?.currentDateTime
                return start != null && end != null && start.time <= end.time
            }

            override fun autoValidate(): Boolean = false
        })

        categoryEditText.addValidator(object : Validator {
            override fun getErrorMessage(): String {
                return getString(R.string.error_input_missing_categories)
            }

            override fun isValid(charSequence: CharSequence): Boolean {
                return mCategoryPicker?.isSelected == true
            }

            override fun autoValidate(): Boolean = false
        })

        typeEditText.setOnClickListener {
            mBudgetTypePicker?.showPicker()
        }
        categoryEditText.setOnClickListener {
            mCategoryPicker?.showMultiPicker()
        }
        recurrenceEditText.setOnClickListener {
            mRecurrencePicker?.showPicker()
        }
        startDateEditText.setOnClickListener {
            mStartDatePicker?.showDatePicker()
        }
        endDateEditText.setOnClickListener {
            mEndDatePicker?.showDatePicker()
        }
        walletsEditText.setOnClickListener {
            mWalletsPicker?.showMultiWalletPicker()
        }
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        super.onViewCreated(savedInstanceState)
        var money = 0L
        var type: Contract.BudgetType? = null
        var categories: Array<Category>? = null
        var startDate: Date? = null
        var endDate: Date? = null
        var rule: String? = null
        var ruleStart: Date? = null
        var wallets: Array<Wallet>? = null

        if (savedInstanceState == null) {
            val contentResolver = contentResolver
            if (getMode() == Mode.EDIT_ITEM) {
                var uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, getItemId())
                val projection = arrayOf(
                    Contract.Budget.TYPE,
                    Contract.Budget.CATEGORY_ID,
                    Contract.Budget.CATEGORY_NAME,
                    Contract.Budget.CATEGORY_ICON,
                    Contract.Budget.CATEGORY_TYPE,
                    Contract.Budget.START_DATE,
                    Contract.Budget.END_DATE,
                    Contract.Budget.MONEY,
                    Contract.Budget.RULE,
                    Contract.Budget.RULE_START
                )
                val cursor = contentResolver.query(uri, projection, null, null, null)
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        type = Contract.BudgetType.fromValue(cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Budget.TYPE)))
                        money = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Budget.MONEY))
                        startDate = DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
                        endDate = DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
                        rule = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.RULE))
                        val ruleStartString = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.RULE_START))
                        if (ruleStartString != null) {
                            ruleStart = DateUtils.getDateFromSQLDateString(ruleStartString)
                        }
                    }
                    cursor.close()
                }

                if (type == Contract.BudgetType.CATEGORY) {
                    val categoriesUri = Uri.withAppendedPath(uri, "categories")
                    val categoryProjection = arrayOf(
                        Contract.Category.ID,
                        Contract.Category.NAME,
                        Contract.Category.ICON,
                        Contract.Category.TYPE
                    )
                    val categoryCursor = contentResolver.query(
                        categoriesUri,
                        categoryProjection,
                        null,
                        null,
                        Contract.Category.ID + " ASC"
                    )
                    if (categoryCursor != null) {
                        val count = categoryCursor.count
                        val list = ArrayList<Category>(count)
                        for (i in 0 until count) {
                            if (categoryCursor.moveToPosition(i)) {
                                list.add(
                                    Category(
                                        categoryCursor.getLong(categoryCursor.getColumnIndexOrThrow(Contract.Category.ID)),
                                        categoryCursor.getString(categoryCursor.getColumnIndexOrThrow(Contract.Category.NAME)),
                                        IconLoader.parse(categoryCursor.getString(categoryCursor.getColumnIndexOrThrow(Contract.Category.ICON))),
                                        Contract.CategoryType.fromValue(categoryCursor.getInt(categoryCursor.getColumnIndexOrThrow(Contract.Category.TYPE)))
                                            ?: Contract.CategoryType.EXPENSE
                                    )
                                )
                            }
                        }
                        categories = list.toTypedArray()
                        categoryCursor.close()
                    }
                }

                uri = Uri.withAppendedPath(uri, "wallets")
                val walletProjection = arrayOf(
                    Contract.Wallet.ID,
                    Contract.Wallet.NAME,
                    Contract.Wallet.ICON,
                    Contract.Wallet.CURRENCY,
                    Contract.Wallet.START_MONEY
                )
                val walletCursor = contentResolver.query(uri, walletProjection, null, null, null)
                if (walletCursor != null) {
                    if (walletCursor.moveToFirst()) {
                        val count = walletCursor.count
                        val list = ArrayList<Wallet>(count)
                        var i = 0
                        while (walletCursor.moveToPosition(i) && i < count) {
                            list.add(
                                Wallet(
                                    walletCursor.getLong(walletCursor.getColumnIndexOrThrow(Contract.Wallet.ID)),
                                    walletCursor.getString(walletCursor.getColumnIndexOrThrow(Contract.Wallet.NAME)),
                                    IconLoader.parse(walletCursor.getString(walletCursor.getColumnIndexOrThrow(Contract.Wallet.ICON))),
                                    CurrencyManager.getCurrency(walletCursor.getString(walletCursor.getColumnIndexOrThrow(Contract.Wallet.CURRENCY))),
                                    walletCursor.getLong(walletCursor.getColumnIndexOrThrow(Contract.Wallet.START_MONEY)),
                                    0L
                                )
                            )
                            i++
                        }
                        wallets = list.toTypedArray()
                    }
                    walletCursor.close()
                }
            } else {
                type = Contract.BudgetType.EXPENSES
                val calendar = Calendar.getInstance()
                startDate = calendar.time
                endDate = DateUtils.addMonths(calendar, 1)
                val projection = arrayOf(
                    Contract.Wallet.ID,
                    Contract.Wallet.NAME,
                    Contract.Wallet.ICON,
                    Contract.Wallet.CURRENCY,
                    Contract.Wallet.START_MONEY,
                    Contract.Wallet.TOTAL_MONEY
                )
                val currentWallet = PreferenceManager.getCurrentWallet()
                val cursor: Cursor? = if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) {
                    val uri = DataContentProvider.CONTENT_WALLETS
                    contentResolver.query(uri, projection, null, null, null)
                } else {
                    val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, currentWallet)
                    contentResolver.query(uri, projection, null, null, null)
                }
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        wallets = arrayOf(
                            Wallet(
                                cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Wallet.ID)),
                                cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.NAME)),
                                IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.ICON))),
                                CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.CURRENCY))),
                                cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Wallet.START_MONEY)),
                                cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Wallet.TOTAL_MONEY))
                            )
                        )
                    }
                    cursor.close()
                }
            }
        }

        val fragmentManager: FragmentManager = supportFragmentManager
        mMoneyPicker = MoneyPicker.createPicker(fragmentManager, TAG_MONEY_PICKER, null, money)
        mBudgetTypePicker = BudgetTypePicker.createPicker(fragmentManager, TAG_BUDGET_TYPE_PICKER, type)
        mCategoryPicker = CategoryPicker.createPicker(fragmentManager, TAG_CATEGORY_PICKER, categories)
        mStartDatePicker = DateTimePicker.createPicker(fragmentManager, TAG_START_DATE_PICKER, startDate)
        mEndDatePicker = DateTimePicker.createPicker(fragmentManager, TAG_END_DATE_PICKER, endDate)
        mWalletsPicker = WalletPicker.createPicker(fragmentManager, TAG_WALLETS_PICKER, wallets)

        val recurrenceStartDate = ruleStart ?: (startDate ?: Date())
        val repeats = rule != null && RecurrenceSetting.periodEnd(rule, recurrenceStartDate, recurrenceStartDate) != null
        val recurrenceSetting = if (repeats && rule != null) {
            RecurrenceSetting(recurrenceStartDate, rule)
        } else {
            monthlyOn(recurrenceStartDate)
        }

        mRecurrencePicker = RecurrencePicker.createPicker(fragmentManager, TAG_RECURRENCE_PICKER, recurrenceSetting, false, true)

        if (savedInstanceState == null) {
            mAnchored = ruleStart != null
            mRepeatCheckBox?.isChecked = repeats
            mClosedPeriod = !repeats && isSupersededPeriod()
        } else {
            mAnchored = savedInstanceState.getBoolean(SS_ANCHORED, false)
            mClosedPeriod = savedInstanceState.getBoolean(SS_CLOSED_PERIOD, false)
        }

        if (mClosedPeriod) {
            mRepeatCheckBox?.visibility = View.GONE
        }
    }

    private fun isSupersededPeriod(): Boolean {
        if (getMode() != Mode.EDIT_ITEM) {
            return false
        }
        val uuid = storedColumn(Contract.BUDGET_UUID) ?: return false
        val chain = Contract.budgetChainOf(uuid)
        val selectionArgs = arrayOf(
            getItemId().toString(),
            likeLiteral(chain) + ":%",
            uuid
        )
        val cursor = contentResolver.query(
            SyncContentProvider.CONTENT_BUDGET,
            arrayOf(Contract.Budget.ID),
            Contract.LATER_PERIOD_OF_CHAIN_SELECTION,
            selectionArgs,
            null
        )
        if (cursor != null) {
            try {
                return cursor.moveToFirst()
            } finally {
                cursor.close()
            }
        }
        return false
    }

    private fun scheduleToSave(): RecurrenceSetting {
        return rebuiltOn(anchorToSave())
    }

    private fun anchorToSave(): Date {
        if (getMode() == Mode.EDIT_ITEM) {
            val stored = storedColumn(Contract.Budget.RULE_START)
            if (stored != null) {
                val date = DateUtils.getDateFromSQLDateString(stored)
                if (date != null) return date
            }
            val fieldStart = mStartDatePicker?.currentDateTime
            if (fieldStart != null) {
                return fieldStart
            }
        }
        return mRecurrencePicker?.currentSettings?.getStartDate() ?: Date()
    }

    private fun storedColumn(column: String): String? {
        val cursor = contentResolver.query(
            SyncContentProvider.CONTENT_BUDGET,
            arrayOf(column),
            Contract.Budget.ID + " = ?",
            arrayOf(getItemId().toString()),
            null
        )
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndexOrThrow(column))
                }
            } finally {
                cursor.close()
            }
        }
        return null
    }

    private fun getRepeatingPeriod(): Array<Date>? {
        val recurrenceSetting = mRecurrencePicker?.currentSettings ?: return null
        return RecurrenceSetting.periodOn(recurrenceSetting.rule, recurrenceSetting.getStartDate(), Date())
    }

    private fun rebuiltOn(startDate: Date): RecurrenceSetting {
        val current = mRecurrencePicker?.currentSettings ?: monthlyOn(startDate)
        val builder = RecurrenceSetting.Builder(startDate, current.getType())
        builder.setOffset(current.getOffsetValue())
        if (current.getType() == RecurrenceSetting.TYPE_MONTHLY) {
            builder.setRepeatSameMonthDay()
        } else if (current.getType() == RecurrenceSetting.TYPE_YEARLY) {
            builder.setRepeatSameYearDay()
        }
        return builder.build()
    }

    private fun periodToSave(mode: Mode): Array<Date>? {
        val schedule = scheduleToSave()
        val rule = schedule.rule
        val anchor = schedule.getStartDate()
        if (mode == Mode.EDIT_ITEM) {
            val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, getItemId())
            val projection = arrayOf(
                Contract.Budget.START_DATE,
                Contract.Budget.END_DATE,
                Contract.Budget.RULE,
                Contract.Budget.RULE_START
            )
            val cursor = contentResolver.query(uri, projection, null, null, null)
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        val storedStart = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.START_DATE))
                        val storedEnd = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.END_DATE))
                        val storedRule = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.RULE))
                        val storedRuleStart = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.RULE_START))
                        val scheduleHeld = TextUtils.equals(rule, storedRule) &&
                            TextUtils.equals(DateUtils.getSQLDateString(anchor), storedRuleStart)
                        if (scheduleHeld) {
                            val start = DateUtils.getDateFromSQLDateString(storedStart)
                            val end = DateUtils.getDateFromSQLDateString(storedEnd)
                            if (start != null && end != null) {
                                return arrayOf(start, end)
                            }
                        }
                        val fieldStart = mStartDatePicker?.currentDateTime
                        val periodStart = fieldStart ?: DateUtils.getDateFromSQLDateString(storedStart)
                        val periodEnd = if (periodStart != null) RecurrenceSetting.periodEnd(rule, anchor, periodStart) else null
                        if (periodStart != null && periodEnd != null) {
                            return arrayOf(periodStart, periodEnd)
                        }
                        val startFallback = DateUtils.getDateFromSQLDateString(storedStart)
                        val endFallback = DateUtils.getDateFromSQLDateString(storedEnd)
                        if (startFallback != null && endFallback != null) {
                            return arrayOf(startFallback, endFallback)
                        }
                    }
                } finally {
                    cursor.close()
                }
            }
        }
        return getRepeatingPeriod()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SS_ANCHORED, mAnchored)
        outState.putBoolean(SS_CLOSED_PERIOD, mClosedPeriod)
    }

    @StringRes
    override fun getActivityTileRes(mode: Mode): Int {
        return when (mode) {
            Mode.NEW_ITEM -> R.string.title_activity_new_budget
            Mode.EDIT_ITEM -> R.string.title_activity_edit_budget
        }
    }

    private fun validate(): Boolean {
        val datesValid = if (mRepeatCheckBox?.isChecked == true) {
            mRecurrenceEditText?.validate() == true
        } else {
            mStartDateEditText?.validate() == true && mEndDateEditText?.validate() == true
        }
        val categoryValid = mBudgetTypePicker?.currentType != Contract.BudgetType.CATEGORY ||
            mCategoryEditText?.validate() == true
        return mTypeEditText?.validate() == true &&
            mWalletsEditText?.validate() == true &&
            datesValid &&
            categoryValid
    }

    override fun onSaveChanges(mode: Mode) {
        if (validate()) {
            val budgetType = mBudgetTypePicker?.currentType ?: return
            val contentValues = ContentValues().apply {
                put(Contract.Budget.TYPE, budgetType.value)
                if (budgetType == Contract.BudgetType.CATEGORY) {
                    put(Contract.Budget.CATEGORY_IDS, Contract.getObjectIds(mCategoryPicker?.currentCategories))
                } else {
                    putNull(Contract.Budget.CATEGORY_IDS)
                    putNull(Contract.Budget.CATEGORY_ID)
                }
                if (mRepeatCheckBox?.isChecked == true && isSupersededPeriod()) {
                    ThemedDialog.buildMaterialDialog(this@NewEditBudgetActivity)
                        .setTitle(R.string.title_warning)
                        .setMessage(R.string.message_budget_period_superseded)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    return
                }
                if (mRepeatCheckBox?.isChecked == true) {
                    val period = periodToSave(mode)
                    if (period != null && period.size >= 2) {
                        put(Contract.Budget.START_DATE, DateUtils.getSQLDateString(period[0]))
                        put(Contract.Budget.END_DATE, DateUtils.getSQLDateString(period[1]))
                    }
                    val schedule = scheduleToSave()
                    put(Contract.Budget.RULE, schedule.rule)
                    put(Contract.Budget.RULE_START, DateUtils.getSQLDateString(schedule.getStartDate()))
                } else {
                    val startDate = mStartDatePicker?.currentDateTime
                    val endDate = mEndDatePicker?.currentDateTime
                    put(Contract.Budget.START_DATE, startDate?.let { DateUtils.getSQLDateString(it) })
                    put(Contract.Budget.END_DATE, endDate?.let { DateUtils.getSQLDateString(it) })
                    putNull(Contract.Budget.RULE)
                    if (!isSupersededPeriod()) {
                        putNull(Contract.Budget.RULE_START)
                    }
                }
                put(Contract.Budget.MONEY, mMoneyPicker?.currentMoney ?: 0L)
                put(Contract.Budget.WALLET_IDS, Contract.getObjectIds(mWalletsPicker?.currentWallets))
            }
            val contentResolver = contentResolver
            try {
                when (mode) {
                    Mode.NEW_ITEM -> {
                        contentResolver.insert(DataContentProvider.CONTENT_BUDGETS, contentValues)
                    }
                    Mode.EDIT_ITEM -> {
                        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, getItemId())
                        contentResolver.update(uri, contentValues, null, null)
                    }
                }
            } catch (e: SQLiteDataException) {
                var contentRes = 0
                when (e.errorCode) {
                    Contract.ErrorCode.WALLETS_NOT_FOUND -> {
                        contentRes = R.string.error_input_missing_multiple_wallets
                    }
                    Contract.ErrorCode.WALLETS_NOT_CONSISTENT -> {
                        contentRes = R.string.error_input_invalid_multiple_wallets
                    }
                }
                if (contentRes != 0) {
                    ThemedDialog.buildMaterialDialog(this)
                        .setTitle(R.string.title_error)
                        .setMessage(contentRes)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
                return
            }
            RecurrenceBroadcastReceiver.scheduleRecurrenceTask(this)
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    override fun onMoneyChanged(tag: String?, currency: CurrencyUnit?, money: Long) {
        mMoneyTextView?.text = mMoneyFormatter.getNotTintedString(
            currency,
            money,
            MoneyFormatter.CurrencyMode.ALWAYS_HIDDEN
        )
        if (currency != null) {
            mCurrencyTextView?.text = currency.symbol
        } else {
            mCurrencyTextView?.text = "?"
        }
    }

    override fun onTypeChanged(tag: String?, type: Contract.BudgetType?) {
        when (type) {
            Contract.BudgetType.INCOMES -> {
                mTypeEditText?.setText(R.string.hint_incomes)
                mCategoryEditText?.visibility = View.GONE
            }
            Contract.BudgetType.EXPENSES -> {
                mTypeEditText?.setText(R.string.hint_expenses)
                mCategoryEditText?.visibility = View.GONE
            }
            Contract.BudgetType.CATEGORY -> {
                mTypeEditText?.setText(R.string.hint_category)
                mCategoryEditText?.visibility = View.VISIBLE
            }
            null -> Unit
        }
    }

    override fun onCategoryListChanged(tag: String?, categories: Array<Category>?) {
        if (!categories.isNullOrEmpty()) {
            val builder = StringBuilder()
            for (category in categories) {
                if (builder.isNotEmpty()) {
                    builder.append(", ")
                }
                builder.append(category.name)
            }
            mCategoryEditText?.setText(builder.toString())
        } else {
            mCategoryEditText?.setText(null)
        }
    }

    override fun onDateTimeChanged(tag: String?, date: Date?) {
        when (tag) {
            TAG_START_DATE_PICKER -> {
                if (date != null) {
                    mStartDateEditText?.let { DateFormatter.applyDate(it, date) }
                } else {
                    mStartDateEditText?.setText(null)
                }
            }
            TAG_END_DATE_PICKER -> {
                if (date != null) {
                    mEndDateEditText?.let { DateFormatter.applyDate(it, date) }
                } else {
                    mEndDateEditText?.setText(null)
                }
            }
        }
    }

    override fun onRecurrenceSettingChanged(tag: String?, recurrenceSetting: RecurrenceSetting?) {
        mRecurrenceEditText?.setText(scheduleToSave().getUserReadableString(this))
    }

    override fun onWalletListChanged(tag: String?, wallets: Array<Wallet>?) {
        if (!wallets.isNullOrEmpty()) {
            val builder = StringBuilder()
            for (i in wallets.indices) {
                if (i != 0) {
                    builder.append(", ")
                }
                builder.append(wallets[i].name)
            }
            mWalletsEditText?.setText(builder)
            mMoneyPicker?.setCurrency(wallets[0].currency)
        } else {
            mWalletsEditText?.setText(null)
            mMoneyPicker?.setCurrency(null)
        }
    }

    companion object {
        private const val TAG_MONEY_PICKER = "NewEditBudgetActivity::Tag::MoneyPicker"
        private const val TAG_BUDGET_TYPE_PICKER = "NewEditBudgetActivity::Tag::BudgetTypePicker"
        private const val TAG_CATEGORY_PICKER = "NewEditBudgetActivity::Tag::CategoryPicker"
        private const val TAG_START_DATE_PICKER = "NewEditBudgetActivity::Tag::StartDatePicker"
        private const val TAG_END_DATE_PICKER = "NewEditBudgetActivity::Tag::EndDatePicker"
        private const val TAG_WALLETS_PICKER = "NewEditBudgetActivity::Tag::WalletsPicker"
        private const val TAG_RECURRENCE_PICKER = "NewEditBudgetActivity::Tag::RecurrencePicker"

        private const val SS_ANCHORED = "NewEditBudgetActivity::SavedState::Anchored"
        private const val SS_CLOSED_PERIOD = "NewEditBudgetActivity::SavedState::ClosedPeriod"

        private fun likeLiteral(text: String): String {
            return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        }

        private fun monthlyOn(startDate: Date): RecurrenceSetting {
            val builder = RecurrenceSetting.Builder(startDate, RecurrenceSetting.TYPE_MONTHLY)
            builder.setRepeatSameMonthDay()
            return builder.build()
        }
    }
}
