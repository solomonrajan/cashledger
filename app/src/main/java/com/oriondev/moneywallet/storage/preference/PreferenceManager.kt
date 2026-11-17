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

package com.oriondev.moneywallet.storage.preference

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.text.TextUtils
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.oriondev.moneywallet.broadcast.DailyBroadcastReceiver
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.model.Group
import com.oriondev.moneywallet.model.LockMode
import com.oriondev.moneywallet.ui.widget.WalletWidgetObserver
import java.util.Calendar
import java.util.Collections
import java.util.HashSet

/**
 * Created by andrea on 24/01/18.
 */
object PreferenceManager {

    private const val FILE_NAME = "preferences"

    private const val CURRENT_WALLET = "current_wallet_id"
    private const val ASKED_NOTIFICATION_PERMISSION = "asked_notification_permission"
    private const val CURRENT_LOCK_MODE = "current_lock_mode"
    private const val CURRENT_LOCK_CODE = "current_lock_code"
    private const val LAST_LOCK_TIMESTAMP = "last_lock_timestamp"
    private const val COLOR_INCOME = "color_income"
    private const val COLOR_EXPENSE = "color_expense"
    private const val SHOW_CURRENCY = "show_currency"
    private const val GROUP_DIGITS = "group_digits"
    private const val ROUND_DECIMALS = "round_decimals"
    private const val SHOW_PLUS_MINUS_SYMBOL = "show_plus_minus_symbol"
    private const val DEBT_FINISHED_ONLY = "debt_finished_only"
    private const val DATE_FORMAT = "date_format"
    private const val FIRST_DAY_OF_WEEK = "first_day_of_week"
    private const val FIRST_DAY_OF_MONTH = "first_day_of_month"
    private const val GROUP_TYPE = "group_type"

    private const val DAILY_REMINDER = "daily_reminder"
    private const val FIRST_START = "first_start"
    private const val SERVICE_API_KEY = "user_api_key_"

    private const val CSV_IMPORT_MAPPING = "csv_import_mapping_"

    private const val MAP_TILE_SERVER = "map_tile_server"
    private const val COLLAPSED_CATEGORIES = "collapsed_categories"
    private const val COLLAPSED_PERIODS = "collapsed_periods"

    private const val LAST_DATA_CHANGE_TIME = "last_data_change_time"

    const val LOCK_MODE_NONE = 0
    const val LOCK_MODE_PIN = 1
    const val LOCK_MODE_SEQUENCE = 2
    const val LOCK_MODE_FINGERPRINT = 3

    const val DATE_FORMAT_TYPE_0 = 0
    const val DATE_FORMAT_TYPE_1 = 1
    const val DATE_FORMAT_TYPE_2 = 2
    const val DATE_FORMAT_TYPE_3 = 3
    const val DATE_FORMAT_TYPE_4 = 4
    const val DATE_FORMAT_TYPE_5 = 5
    const val DATE_FORMAT_TYPE_6 = 6
    const val DATE_FORMAT_TYPE_7 = 7
    const val DATE_FORMAT_TYPE_8 = 8

    const val GROUP_TYPE_DAILY = 0
    const val GROUP_TYPE_WEEKLY = 1
    const val GROUP_TYPE_MONTHLY = 2
    const val GROUP_TYPE_YEARLY = 3

    const val DAILY_REMINDER_DISABLED = -1

    const val NO_CURRENT_WALLET = -1L
    const val TOTAL_WALLET_ID = 0L

    // Color.BLUE is 1.17:1 on a card, unreadable in both dark modes. These two are what a color
    // that cannot be seen falls back to, and getVisibleColor returns a fallback unchecked, so
    // each has to clear every surface an amount is drawn on by itself.
    private const val DEFAULT_COLOR_INCOME = 0xFF388E3C.toInt()
    private const val DEFAULT_COLOR_EXPENSE = 0xFFD32F2F.toInt()

    private var mPreferences: SharedPreferences? = null
    private var mApplicationContext: Context? = null

    private val prefs: SharedPreferences
        get() = checkNotNull(mPreferences) { "PreferenceManager must be initialized before use" }

    @JvmStatic
    fun initialize(context: Context) {
        mApplicationContext = context.applicationContext
        mPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    }

    @JvmStatic
    fun getApplicationContext(): Context? {
        return mApplicationContext
    }

    /**
     * @return the address the user chose for tiles, either a base address or a full template,
     *          or null to use the map library's own default. Not a promise that tiles come from
     *          there: the map re checks it and falls back if it no longer passes.
     */
    @JvmStatic
    fun getMapTileServer(): String? {
        return prefs.getString(MAP_TILE_SERVER, null)
    }

    @JvmStatic
    fun setMapTileServer(url: String?) {
        if (TextUtils.isEmpty(url)) {
            prefs.edit().remove(MAP_TILE_SERVER).apply()
        } else {
            prefs.edit().putString(MAP_TILE_SERVER, url).apply()
        }
    }

    /**
     * @return the ids, as text, of the categories whose children the user has hidden in the
     *          category list and the category picker. Nothing stored means nothing hidden,
     *          which is how the list has always been drawn. Stored once rather than held per
     *          list, so that two lists cannot write each other's hiding away. Each list still
     *          has to re-read it to redraw, which CategoryListFragment does when it resumes.
     */
    @JvmStatic
    fun getCollapsedCategories(): Set<String> {
        val set = prefs.getStringSet(COLLAPSED_CATEGORIES, Collections.emptySet()) ?: Collections.emptySet()
        return HashSet(set)
    }

    @JvmStatic
    fun setCollapsedCategories(categoryIds: Set<String>?) {
        prefs.edit().putStringSet(COLLAPSED_CATEGORIES, categoryIds).apply()
    }

    /**
     * @return the keys of the periods whose transactions the user has hidden on the transactions
     *          list and on a filtered transactions list. A key is the group type number, a colon,
     *          and the header's start date exactly as the list's header cursor reports it, so a
     *          folded month and a folded day that begin on the same date are two different keys.
     *          Nothing stored means nothing hidden, which is how the list has always been drawn.
     *          Stored once, not held per list, so that two lists cannot write each other's hiding
     *          away. Each list still has to read it again to redraw, which
     *          TransactionListFragment does when it resumes.
     */
    @JvmStatic
    fun getCollapsedPeriods(): Set<String> {
        val set = prefs.getStringSet(COLLAPSED_PERIODS, Collections.emptySet()) ?: Collections.emptySet()
        return HashSet(set)
    }

    @JvmStatic
    fun setCollapsedPeriods(periodKeys: Set<String>?) {
        prefs.edit().putStringSet(COLLAPSED_PERIODS, periodKeys).apply()
    }

    @JvmStatic
    fun setCurrentWallet(context: Context, walletId: Long) {
        setCurrentWallet(context, walletId, null)
    }

    /**
     * @param walletName the name of the wallet being selected, when the caller has it, so a
     *                   toolbar can show it at once instead of waiting for its loader.
     */
    @JvmStatic
    fun setCurrentWallet(context: Context, walletId: Long, walletName: String?) {
        if (getCurrentWallet() != walletId) {
            prefs.edit().putLong(CURRENT_WALLET, walletId).apply()
            notifyCurrentWalletIsChanged(context, walletId, walletName)
        }
    }

    @JvmStatic
    fun setCurrentLockMode(lockMode: LockMode) {
        prefs.edit().putInt(CURRENT_LOCK_MODE, lockMode.value).apply()
        // A wallet balance sitting on the home screen is hidden while the app is locked, and
        // turning the lock on writes no transaction, so nothing else would tell the widgets to
        // stop showing it. Here and not at the ten call sites in LockActivity, which is also
        // what setCurrentDailyReminder below does with its alarm.
        WalletWidgetObserver.requestUpdate()
    }

    @JvmStatic
    fun setCurrentLockCode(code: String?) {
        prefs.edit().putString(CURRENT_LOCK_CODE, code).apply()
    }

    @JvmStatic
    fun setLastLockTime(time: Long) {
        prefs.edit().putLong(LAST_LOCK_TIMESTAMP, time).apply()
    }

    // These four decide how MoneyFormatter writes an amount, so each of them changes the figure a
    // wallet widget is showing without anything being written to the ledger. Same reason as the
    // lock above, and the same answer.

    @JvmStatic
    fun setCurrencyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(SHOW_CURRENCY, enabled).apply()
        WalletWidgetObserver.requestUpdate()
    }

    @JvmStatic
    fun setGroupDigitsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(GROUP_DIGITS, enabled).apply()
        WalletWidgetObserver.requestUpdate()
    }

    @JvmStatic
    fun setRoundDecimalsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(ROUND_DECIMALS, enabled).apply()
        WalletWidgetObserver.requestUpdate()
    }

    @JvmStatic
    fun setShowPlusMinusSymbolEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(SHOW_PLUS_MINUS_SYMBOL, enabled).apply()
        WalletWidgetObserver.requestUpdate()
    }

    @JvmStatic
    fun setCurrentDateFormatIndex(index: Int) {
        prefs.edit().putInt(DATE_FORMAT, index).apply()
    }

    @JvmStatic
    fun setCurrentFirstDayOfWeek(day: Int) {
        prefs.edit().putInt(FIRST_DAY_OF_WEEK, day).apply()
    }

    @JvmStatic
    fun setCurrentFirstDayOfMonth(day: Int) {
        prefs.edit().putInt(FIRST_DAY_OF_MONTH, day).apply()
    }

    @JvmStatic
    fun setCurrentGroupType(groupType: Group) {
        prefs.edit().putInt(GROUP_TYPE, groupType.type).apply()
    }

    @JvmStatic
    fun setCurrentIncomeColor(color: Int) {
        prefs.edit().putInt(COLOR_INCOME, color).apply()
    }

    @JvmStatic
    fun setCurrentExpenseColor(color: Int) {
        prefs.edit().putInt(COLOR_EXPENSE, color).apply()
    }

    @JvmStatic
    fun setDateFormat(index: Int) {
        prefs.edit().putInt(DATE_FORMAT, index).apply()
    }

    @JvmStatic
    fun setCurrentDailyReminder(context: Context, hour: Int) {
        val old = getCurrentDailyReminder()
        prefs.edit().putInt(DAILY_REMINDER, hour).apply()
        if (old == DAILY_REMINDER_DISABLED && hour != DAILY_REMINDER_DISABLED) {
            DailyBroadcastReceiver.scheduleDailyNotification(context, hour)
        } else if (old != DAILY_REMINDER_DISABLED) {
            DailyBroadcastReceiver.cancelDailyNotification(context)
            if (hour != DAILY_REMINDER_DISABLED) {
                DailyBroadcastReceiver.scheduleDailyNotification(context, hour)
            }
        }
    }

    @JvmStatic
    fun setIsFirstStartDone(done: Boolean) {
        prefs.edit().putBoolean(FIRST_START, done).apply()
    }

    @JvmStatic
    fun setServiceApiKey(service: Int, key: String?) {
        prefs.edit().putString(SERVICE_API_KEY + service.toString(), key).apply()
    }

    @JvmStatic
    fun setLastTimeDataIsChanged(timestamp: Long) {
        prefs.edit().putLong(LAST_DATA_CHANGE_TIME, timestamp).apply()
    }

    @JvmStatic
    fun getCurrentWallet(): Long {
        return prefs.getLong(CURRENT_WALLET, NO_CURRENT_WALLET)
    }

    @JvmStatic
    fun getCurrentLockMode(): LockMode {
        return LockMode.get(prefs.getInt(CURRENT_LOCK_MODE, LOCK_MODE_NONE))
    }

    @JvmStatic
    fun getCurrentLockCode(): String? {
        return prefs.getString(CURRENT_LOCK_CODE, null)
    }

    @JvmStatic
    fun getLastLockTime(): Long {
        return prefs.getLong(LAST_LOCK_TIMESTAMP, 0)
    }

    @JvmStatic
    fun isCurrencyEnabled(): Boolean {
        return prefs.getBoolean(SHOW_CURRENCY, true)
    }

    @JvmStatic
    fun isGroupDigitEnabled(): Boolean {
        return prefs.getBoolean(GROUP_DIGITS, true)
    }

    @JvmStatic
    fun isRoundDecimalsEnabled(): Boolean {
        return prefs.getBoolean(ROUND_DECIMALS, false)
    }

    @JvmStatic
    fun isShowPlusMinusSymbolEnabled(): Boolean {
        return prefs.getBoolean(SHOW_PLUS_MINUS_SYMBOL, false)
    }

    /**
     * Whether the debt lists are showing only the debts that are finished. Stored once rather
     * than held per tab, so the two debt tabs cannot disagree about it.
     */
    @JvmStatic
    fun isDebtFinishedOnlyEnabled(): Boolean {
        return prefs.getBoolean(DEBT_FINISHED_ONLY, false)
    }

    @JvmStatic
    fun setDebtFinishedOnlyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(DEBT_FINISHED_ONLY, enabled).apply()
    }

    @JvmStatic
    fun getCurrentDateFormatIndex(): Int {
        // TODO: maybe return the default value based on the locale
        return prefs.getInt(DATE_FORMAT, DATE_FORMAT_TYPE_2)
    }

    @JvmStatic
    fun getFirstDayOfWeek(): Int {
        // TODO: maybe return the default value based on the locale
        return prefs.getInt(FIRST_DAY_OF_WEEK, Calendar.MONDAY)
    }

    @JvmStatic
    fun getFirstDayOfMonth(): Int {
        return prefs.getInt(FIRST_DAY_OF_MONTH, 1)
    }

    @JvmStatic
    fun getCurrentGroupType(): Group {
        var index = prefs.getInt(GROUP_TYPE, GROUP_TYPE_MONTHLY)
        if (index < GROUP_TYPE_DAILY || index > GROUP_TYPE_YEARLY) {
            index = GROUP_TYPE_MONTHLY
        }
        return Group.fromType(index)
    }

    @JvmStatic
    fun getDefaultColorIncome(): Int {
        return DEFAULT_COLOR_INCOME
    }

    @JvmStatic
    fun getDefaultColorExpense(): Int {
        return DEFAULT_COLOR_EXPENSE
    }

    @JvmStatic
    fun getCurrentIncomeColor(): Int {
        return prefs.getInt(COLOR_INCOME, DEFAULT_COLOR_INCOME)
    }

    @JvmStatic
    fun getCurrentExpenseColor(): Int {
        return prefs.getInt(COLOR_EXPENSE, DEFAULT_COLOR_EXPENSE)
    }

    @JvmStatic
    fun getCurrentDailyReminder(): Int {
        return prefs.getInt(DAILY_REMINDER, DAILY_REMINDER_DISABLED)
    }

    @JvmStatic
    fun hasAskedNotificationPermission(): Boolean {
        return prefs.getBoolean(ASKED_NOTIFICATION_PERMISSION, false)
    }

    @JvmStatic
    fun setAskedNotificationPermission() {
        prefs.edit().putBoolean(ASKED_NOTIFICATION_PERMISSION, true).apply()
    }

    @JvmStatic
    fun isFirstStartDone(): Boolean {
        return prefs.getBoolean(FIRST_START, false)
    }

    @JvmStatic
    fun getServiceApiKey(service: Int): String? {
        return prefs.getString(SERVICE_API_KEY + service.toString(), null)
    }

    /**
     * @return the column settings last used to import a file whose header has this signature, as
     *          CsvImportMapping encoded them, or null when no file with that header was imported.
     */
    @JvmStatic
    fun getCsvImportMapping(headerSignature: String): String? {
        return prefs.getString(CSV_IMPORT_MAPPING + headerSignature, null)
    }

    @JvmStatic
    fun setCsvImportMapping(headerSignature: String, encodedMapping: String?) {
        prefs.edit().putString(CSV_IMPORT_MAPPING + headerSignature, encodedMapping).apply()
    }

    @JvmStatic
    fun getLastTimeDataIsChanged(): Long {
        return prefs.getLong(LAST_DATA_CHANGE_TIME, 0L)
    }

    private fun notifyCurrentWalletIsChanged(context: Context, walletId: Long, walletName: String?) {
        val localBroadcastManager = LocalBroadcastManager.getInstance(context)
        val intent = Intent(LocalAction.ACTION_CURRENT_WALLET_CHANGED).apply {
            putExtra(LocalAction.ARGUMENT_WALLET_ID, walletId)
            if (walletName != null) {
                putExtra(LocalAction.ARGUMENT_WALLET_NAME, walletName)
            }
        }
        localBroadcastManager.sendBroadcast(intent)
    }

    @JvmStatic
    fun registerCurrentWalletObserver(context: Context, controller: CurrentWalletController): BroadcastReceiver {
        val localBroadcastManager = LocalBroadcastManager.getInstance(context)
        val intentFilter = IntentFilter(LocalAction.ACTION_CURRENT_WALLET_CHANGED)
        val broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                if (intent != null && TextUtils.equals(intent.action, LocalAction.ACTION_CURRENT_WALLET_CHANGED)) {
                    val currentWalletId = intent.getLongExtra(LocalAction.ARGUMENT_WALLET_ID, -1L)
                    val currentWalletName = intent.getStringExtra(LocalAction.ARGUMENT_WALLET_NAME)
                    controller.onCurrentWalletChanged(currentWalletId, currentWalletName)
                }
            }
        }
        localBroadcastManager.registerReceiver(broadcastReceiver, intentFilter)
        return broadcastReceiver
    }

    @JvmStatic
    fun unregisterCurrentWalletObserver(context: Context, broadcastReceiver: BroadcastReceiver) {
        val localBroadcastManager = LocalBroadcastManager.getInstance(context)
        localBroadcastManager.unregisterReceiver(broadcastReceiver)
    }
}
